package com.Bank_website.reporting.service;

import com.Bank_website.reporting.entity.ReportJob;
import com.Bank_website.reporting.exception.ReportGenerationException;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class StatementGenerator {

    private static final Logger log = LoggerFactory.getLogger(StatementGenerator.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TS_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final StorageService storageService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public StatementGenerator(JdbcTemplate jdbcTemplate, StorageService storageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.storageService = storageService;
    }

    public String generateCustomerStatement(ReportJob job) {
        UUID customerId = job.getCustomerId();
        if (customerId == null) {
            throw new ReportGenerationException("Customer ID is required for customer statements");
        }

        LocalDate periodStart = job.getPeriodStart();
        LocalDate periodEnd = job.getPeriodEnd();
        OffsetDateTime startUtc = periodStart.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endUtc = periodEnd.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).minusNanos(1000);

        // 1. Fetch customer details
        String customerQuery = "SELECT id, customer_number, first_name, last_name, email FROM customers WHERE id = ?";
        List<Map<String, Object>> customers = jdbcTemplate.queryForList(customerQuery, customerId);
        if (customers.isEmpty()) {
            throw new ReportGenerationException("Customer not found: " + customerId);
        }
        Map<String, Object> customer = customers.get(0);

        // 2. Fetch customer's accounts
        String accountsQuery = "SELECT id, account_number, account_type, balance_cents, status FROM accounts WHERE customer_id = ? ORDER BY created_at ASC";
        List<Map<String, Object>> accounts = jdbcTemplate.queryForList(accountsQuery, customerId);
        if (accounts.isEmpty()) {
            throw new ReportGenerationException("No accounts found for customer: " + customerId);
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"UTF-8\"/>");
        html.append("<title>Account Statement</title>");
        html.append("<style>");
        html.append("body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; font-size: 11pt; color: #2d3748; line-height: 1.4; padding: 20px; }");
        html.append(".header { border-bottom: 2px solid #2b6cb0; padding-bottom: 12px; margin-bottom: 20px; }");
        html.append(".bank-title { font-size: 20pt; font-weight: bold; color: #2b6cb0; margin: 0; }");
        html.append(".bank-subtitle { font-size: 10pt; color: #718096; margin-top: 4px; }");
        html.append(".meta-box { margin-bottom: 20px; background: #f7fafc; padding: 12px; border: 1px solid #e2e8f0; border-radius: 4px; }");
        html.append(".meta-table { width: 100%; border-collapse: collapse; }");
        html.append(".meta-table td { padding: 4px 6px; font-size: 10pt; vertical-align: top; }");
        html.append(".meta-label { font-weight: bold; color: #4a5568; width: 140px; }");
        html.append(".section-title { font-size: 13pt; font-weight: bold; color: #2d3748; margin: 20px 0 10px 0; border-bottom: 1px solid #cbd5e0; padding-bottom: 4px; }");
        html.append(".summary-box { background: #edf2f7; border-left: 4px solid #3182ce; padding: 10px 14px; margin-bottom: 16px; font-size: 10.5pt; }");
        html.append(".data-table { width: 100%; border-collapse: collapse; margin-top: 10px; margin-bottom: 25px; }");
        html.append(".data-table th { background: #2b6cb0; color: #ffffff; text-align: left; padding: 7px 9px; font-size: 9.5pt; text-transform: uppercase; letter-spacing: 0.5px; }");
        html.append(".data-table td { padding: 6px 9px; border-bottom: 1px solid #e2e8f0; font-size: 9.5pt; }");
        html.append(".data-table tr:nth-child(even) { background: #f8fafc; }");
        html.append(".money-col { text-align: right; font-family: 'Courier New', Courier, monospace; }");
        html.append(".footer { font-size: 8.5pt; color: #a0aec0; text-align: center; margin-top: 30px; border-top: 1px solid #e2e8f0; padding-top: 8px; }");
        html.append("</style></head><body>");

        html.append("<div class=\"header\">");
        html.append("<p class=\"bank-title\">NOVO BANK SERVICE PLC</p>");
        html.append("<p class=\"bank-subtitle\">Official Periodic Customer Account Statement</p>");
        html.append("</div>");

        html.append("<div class=\"meta-box\">");
        html.append("<table class=\"meta-table\">");
        html.append("<tr><td class=\"meta-label\">Account Holder:</td><td>").append(escapeXml(customer.get("first_name") + " " + customer.get("last_name"))).append("</td>");
        html.append("<td class=\"meta-label\">Statement Period:</td><td>").append(periodStart.format(DATE_FORMATTER)).append(" to ").append(periodEnd.format(DATE_FORMATTER)).append("</td></tr>");
        html.append("<tr><td class=\"meta-label\">Customer Number:</td><td>").append(escapeXml(String.valueOf(customer.get("customer_number")))).append("</td>");
        html.append("<td class=\"meta-label\">Generated At:</td><td>").append(OffsetDateTime.now(ZoneOffset.UTC).format(TS_FORMATTER)).append(" UTC</td></tr>");
        html.append("<tr><td class=\"meta-label\">Customer Email:</td><td>").append(escapeXml(String.valueOf(customer.get("email")))).append("</td>");
        html.append("<td class=\"meta-label\">Statement Job ID:</td><td>").append(job.getJobId()).append("</td></tr>");
        html.append("</table>");
        html.append("</div>");

        for (Map<String, Object> account : accounts) {
            UUID accountId = (UUID) account.get("id");
            String accountNumber = (String) account.get("account_number");
            String accountType = (String) account.get("account_type");

            // Calculate opening balance at periodStart
            String openingQuery = "SELECT COALESCE(SUM(CASE WHEN direction = 'credit' THEN amount_cents ELSE -amount_cents END), 0) " +
                    "FROM ledger_entries WHERE account_id = ? AND created_at < ?";
            Long openingBalanceCents = jdbcTemplate.queryForObject(openingQuery, Long.class, accountId, Timestamp.from(startUtc.toInstant()));
            if (openingBalanceCents == null) {
                openingBalanceCents = 0L;
            }

            // Fetch entries in period
            String entriesQuery = "SELECT le.id, le.transaction_id, le.direction, le.amount_cents, le.created_at, t.idempotency_key " +
                    "FROM ledger_entries le JOIN transactions t ON t.id = le.transaction_id " +
                    "WHERE le.account_id = ? AND le.created_at >= ? AND le.created_at <= ? " +
                    "ORDER BY le.created_at ASC, le.id ASC";
            List<Map<String, Object>> entries = jdbcTemplate.queryForList(entriesQuery, accountId,
                    Timestamp.from(startUtc.toInstant()), Timestamp.from(endUtc.toInstant()));

            long runningBalance = openingBalanceCents;
            long totalDebits = 0L;
            long totalCredits = 0L;

            html.append("<div class=\"section-title\">Account: ").append(accountNumber)
                    .append(" (").append(accountType.toUpperCase(Locale.ROOT)).append(")</div>");

            StringBuilder tableRows = new StringBuilder();
            for (Map<String, Object> entry : entries) {
                String direction = (String) entry.get("direction");
                long amount = ((Number) entry.get("amount_cents")).longValue();
                Timestamp ts = (Timestamp) entry.get("created_at");
                String dateStr = ts.toInstant().atOffset(ZoneOffset.UTC).format(TS_FORMATTER);
                UUID txnId = (UUID) entry.get("transaction_id");

                String debitStr = "";
                String creditStr = "";
                if ("debit".equalsIgnoreCase(direction)) {
                    runningBalance -= amount;
                    totalDebits += amount;
                    debitStr = formatMoney(amount);
                } else {
                    runningBalance += amount;
                    totalCredits += amount;
                    creditStr = formatMoney(amount);
                }

                tableRows.append("<tr>")
                        .append("<td>").append(dateStr).append("</td>")
                        .append("<td>Transfer (Txn #").append(txnId.toString().substring(0, 8)).append(")</td>")
                        .append("<td class=\"money-col\">").append(debitStr).append("</td>")
                        .append("<td class=\"money-col\">").append(creditStr).append("</td>")
                        .append("<td class=\"money-col\">").append(formatMoney(runningBalance)).append("</td>")
                        .append("</tr>");
            }

            long closingBalanceCents = runningBalance;

            html.append("<div class=\"summary-box\">");
            html.append("<strong>Opening Balance:</strong> ").append(formatMoney(openingBalanceCents)).append(" | ");
            html.append("<strong>Total Debits:</strong> ").append(formatMoney(totalDebits)).append(" | ");
            html.append("<strong>Total Credits:</strong> ").append(formatMoney(totalCredits)).append(" | ");
            html.append("<strong>Closing Balance:</strong> ").append(formatMoney(closingBalanceCents));
            html.append("</div>");

            html.append("<table class=\"data-table\">");
            html.append("<thead><tr><th>Date (UTC)</th><th>Description</th><th class=\"money-col\">Debit</th><th class=\"money-col\">Credit</th><th class=\"money-col\">Balance</th></tr></thead>");
            html.append("<tbody>");
            if (entries.isEmpty()) {
                html.append("<tr><td colspan=\"5\" style=\"text-align: center; color: #a0aec0; padding: 14px;\">No transactions recorded for this account during the statement period.</td></tr>");
            } else {
                html.append(tableRows);
            }
            html.append("</tbody></table>");
        }

        html.append("<div class=\"footer\">");
        html.append("<p>Novo Bank Service PLC • Licensed Commercial Banking Entity • Confidential Customer Document</p>");
        html.append("<p>For questions regarding this statement, please contact customer support or visit your nearest branch.</p>");
        html.append("</div>");
        html.append("</body></html>");

        final String finalHtml = html.toString();

        // 3. Render HTML to PDF within a strict 30-second timeout limit
        CompletableFuture<byte[]> future = CompletableFuture.supplyAsync(() -> {
            try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(finalHtml, "");
                builder.toStream(os);
                builder.run();
                return os.toByteArray();
            } catch (Exception ex) {
                throw new ReportGenerationException("Failed to render statement PDF: " + ex.getMessage(), ex);
            }
        }, executor);

        byte[] pdfBytes;
        try {
            pdfBytes = future.get(30, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            log.error("PDF generation timed out after 30 seconds for job {}", job.getJobId());
            throw new ReportGenerationException("PDF generation timed out after 30 seconds");
        } catch (Exception ex) {
            log.error("Error executing PDF generation for job {}: {}", job.getJobId(), ex.getMessage());
            throw new ReportGenerationException("Error generating statement PDF: " + ex.getMessage(), ex);
        }

        // 4. Upload to Supabase Storage bucket "reports" and return signed URL
        String path = "statements/" + job.getJobId() + ".pdf";
        return storageService.uploadAndSign(path, pdfBytes, "application/pdf");
    }

    private String formatMoney(long cents) {
        double dollars = cents / 100.0;
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.US);
        return nf.format(dollars);
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
