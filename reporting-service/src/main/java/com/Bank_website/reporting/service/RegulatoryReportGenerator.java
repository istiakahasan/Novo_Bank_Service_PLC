package com.Bank_website.reporting.service;

import com.Bank_website.reporting.entity.ReportJob;
import com.Bank_website.reporting.exception.ReportGenerationException;
import com.opencsv.CSVWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Service
public class RegulatoryReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(RegulatoryReportGenerator.class);

    private final JdbcTemplate jdbcTemplate;
    private final StorageService storageService;

    public RegulatoryReportGenerator(JdbcTemplate jdbcTemplate, StorageService storageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageService = storageService;
    }

    public String generateRegulatoryReport(ReportJob job) {
        String reportType = job.getReportType();
        log.info("Generating regulatory report {} for job {}", reportType, job.getJobId());

        byte[] csvBytes;
        String path;

        switch (reportType) {
            case "DAILY_TRANSACTION_SUMMARY":
                csvBytes = generateDailyTransactionSummary(job);
                path = "regulatory/daily_txn_" + job.getJobId() + ".csv";
                break;
            case "LOAN_PORTFOLIO":
                csvBytes = generateLoanPortfolio(job);
                path = "regulatory/loan_portfolio_" + job.getJobId() + ".csv";
                break;
            case "KYC_STATUS":
                csvBytes = generateKycStatus(job);
                path = "regulatory/kyc_status_" + job.getJobId() + ".csv";
                break;
            default:
                throw new ReportGenerationException("Unsupported regulatory report type: " + reportType);
        }

        return storageService.uploadAndSign(path, csvBytes, "text/csv");
    }

    private byte[] generateDailyTransactionSummary(ReportJob job) {
        LocalDate start = job.getPeriodStart();
        LocalDate end = job.getPeriodEnd();
        OffsetDateTime startUtc = start.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endUtc = end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1000);

        String sql = "SELECT DATE(t.created_at) AS txn_date, " +
                "COUNT(t.id) AS transaction_count, " +
                "COALESCE(SUM(t.amount_cents), 0) AS total_amount_cents, " +
                "COUNT(t.id) FILTER (WHERE t.status = 'flagged' OR fa.id IS NOT NULL) AS fraud_flagged_count " +
                "FROM transactions t " +
                "LEFT JOIN fraud_alerts fa ON fa.transaction_id = t.id " +
                "WHERE t.created_at >= ? AND t.created_at <= ? " +
                "GROUP BY DATE(t.created_at) " +
                "ORDER BY txn_date ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql,
                Timestamp.from(startUtc.toInstant()), Timestamp.from(endUtc.toInstant()));

        StringWriter sw = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sw)) {
            writer.writeNext(new String[]{"date", "transaction_count", "total_amount_cents", "fraud_flagged_count"});
            for (Map<String, Object> row : rows) {
                writer.writeNext(new String[]{
                        String.valueOf(row.get("txn_date")),
                        String.valueOf(row.get("transaction_count")),
                        String.valueOf(row.get("total_amount_cents")),
                        String.valueOf(row.get("fraud_flagged_count"))
                });
            }
        } catch (Exception ex) {
            throw new ReportGenerationException("Failed to write daily transaction summary CSV: " + ex.getMessage(), ex);
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generateLoanPortfolio(ReportJob job) {
        String sql = "SELECT la.application_id, " +
                "la.customer_id, " +
                "lp.product_code AS product, " +
                "COALESCE(la.approved_amount_cents, la.requested_amount_cents) AS approved_amount, " +
                "la.disbursed_at AS disbursed_date, " +
                "COALESCE(SUM(CASE WHEN lr.status = 'PAID' THEN lr.principal_due_cents ELSE 0 END), 0) AS total_repaid, " +
                "la.status " +
                "FROM loan_applications la " +
                "JOIN loan_products lp ON lp.product_id = la.product_id " +
                "LEFT JOIN loan_repayments lr ON lr.application_id = la.application_id " +
                "WHERE la.created_at::date >= ? AND la.created_at::date <= ? " +
                "GROUP BY la.application_id, la.customer_id, lp.product_code, la.approved_amount_cents, la.requested_amount_cents, la.disbursed_at, la.status " +
                "ORDER BY la.created_at ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, job.getPeriodStart(), job.getPeriodEnd());

        StringWriter sw = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sw)) {
            writer.writeNext(new String[]{
                    "application_id",
                    "customer_id",
                    "product",
                    "approved_amount",
                    "disbursed_date",
                    "total_repaid",
                    "outstanding_principal",
                    "status"
            });
            for (Map<String, Object> row : rows) {
                long approvedAmount = ((Number) row.get("approved_amount")).longValue();
                long totalRepaid = ((Number) row.get("total_repaid")).longValue();
                long outstandingPrincipal = Math.max(0, approvedAmount - totalRepaid);
                Object disbursedDate = row.get("disbursed_date");

                writer.writeNext(new String[]{
                        String.valueOf(row.get("application_id")),
                        String.valueOf(row.get("customer_id")),
                        String.valueOf(row.get("product")),
                        String.valueOf(approvedAmount),
                        disbursedDate != null ? String.valueOf(disbursedDate) : "",
                        String.valueOf(totalRepaid),
                        String.valueOf(outstandingPrincipal),
                        String.valueOf(row.get("status"))
                });
            }
        } catch (Exception ex) {
            throw new ReportGenerationException("Failed to write loan portfolio CSV: " + ex.getMessage(), ex);
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] generateKycStatus(ReportJob job) {
        String sql = "SELECT id AS customer_id, kyc_status, aml_risk_rating, created_at " +
                "FROM customers " +
                "WHERE created_at::date >= ? AND created_at::date <= ? " +
                "ORDER BY created_at ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, job.getPeriodStart(), job.getPeriodEnd());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        StringWriter sw = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sw)) {
            writer.writeNext(new String[]{
                    "customer_id",
                    "kyc_status",
                    "aml_risk_rating",
                    "created_at",
                    "days_since_onboarding"
            });
            for (Map<String, Object> row : rows) {
                Timestamp createdAtTs = (Timestamp) row.get("created_at");
                LocalDate createdDate = createdAtTs.toInstant().atOffset(ZoneOffset.UTC).toLocalDate();
                long daysSinceOnboarding = ChronoUnit.DAYS.between(createdDate, today);

                writer.writeNext(new String[]{
                        String.valueOf(row.get("customer_id")),
                        String.valueOf(row.get("kyc_status")),
                        String.valueOf(row.get("aml_risk_rating")),
                        String.valueOf(row.get("created_at")),
                        String.valueOf(daysSinceOnboarding)
                });
            }
        } catch (Exception ex) {
            throw new ReportGenerationException("Failed to write KYC status CSV: " + ex.getMessage(), ex);
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }
}
