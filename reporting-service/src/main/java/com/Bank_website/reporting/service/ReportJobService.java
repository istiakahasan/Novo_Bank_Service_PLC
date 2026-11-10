package com.Bank_website.reporting.service;

import com.Bank_website.reporting.dto.CreateRegulatoryReportRequest;
import com.Bank_website.reporting.dto.CreateStatementRequest;
import com.Bank_website.reporting.dto.PageResponse;
import com.Bank_website.reporting.dto.ReportJobResponse;
import com.Bank_website.reporting.entity.ReportJob;
import com.Bank_website.reporting.exception.ForbiddenException;
import com.Bank_website.reporting.exception.ResourceNotFoundException;
import com.Bank_website.reporting.repository.ReportJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ReportJobService {

    private static final Logger log = LoggerFactory.getLogger(ReportJobService.class);

    private final ReportJobRepository reportJobRepository;
    private final StatementGenerator statementGenerator;
    private final RegulatoryReportGenerator regulatoryReportGenerator;

    public ReportJobService(ReportJobRepository reportJobRepository,
                            StatementGenerator statementGenerator,
                            RegulatoryReportGenerator regulatoryReportGenerator) {
        this.reportJobRepository = reportJobRepository;
        this.statementGenerator = statementGenerator;
        this.regulatoryReportGenerator = regulatoryReportGenerator;
    }

    public ReportJobResponse enqueueCustomerStatement(UUID customerId, CreateStatementRequest request, UUID requestedBy) {
        if (request.periodStart().isAfter(request.periodEnd())) {
            throw new IllegalArgumentException("periodStart cannot be after periodEnd");
        }

        ReportJob job = new ReportJob();
        job.setReportType("CUSTOMER_STATEMENT");
        job.setCustomerId(customerId);
        job.setPeriodStart(request.periodStart());
        job.setPeriodEnd(request.periodEnd());
        job.setStatus("PENDING");
        job.setRequestedBy(requestedBy != null ? requestedBy : customerId);

        ReportJob saved = reportJobRepository.save(job);
        log.info("Enqueued customer statement job {} for customer {}", saved.getJobId(), customerId);
        return ReportJobResponse.from(saved);
    }

    public ReportJobResponse enqueueRegulatoryReport(CreateRegulatoryReportRequest request, UUID staffId) {
        if (request.periodStart().isAfter(request.periodEnd())) {
            throw new IllegalArgumentException("periodStart cannot be after periodEnd");
        }

        ReportJob job = new ReportJob();
        job.setReportType(request.reportType());
        job.setCustomerId(null);
        job.setPeriodStart(request.periodStart());
        job.setPeriodEnd(request.periodEnd());
        job.setStatus("PENDING");
        job.setRequestedBy(staffId);

        ReportJob saved = reportJobRepository.save(job);
        log.info("Enqueued regulatory report job {} of type {} by staff {}", saved.getJobId(), request.reportType(), staffId);
        return ReportJobResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ReportJobResponse getJob(UUID jobId, UUID callerCustomerId, boolean isStaff) {
        ReportJob job = reportJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Report job not found: " + jobId));

        if (!isStaff) {
            if (job.getCustomerId() == null || !job.getCustomerId().equals(callerCustomerId)) {
                throw new ForbiddenException("Access denied: report does not belong to calling customer");
            }
        }

        return ReportJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportJobResponse> listJobs(UUID callerCustomerId, boolean isStaff, Pageable pageable) {
        Page<ReportJob> page;
        if (isStaff) {
            page = reportJobRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            page = reportJobRepository.findByCustomerIdOrderByCreatedAtDesc(callerCustomerId, pageable);
        }
        return PageResponse.from(page.map(ReportJobResponse::from));
    }

    /**
     * Processes ONE pending job per invocation to respect free-tier resource limits.
     */
    public Map<String, Object> processNext() {
        Optional<ReportJob> optionalJob = reportJobRepository.findFirstByStatusOrderByCreatedAtAsc("PENDING");
        if (optionalJob.isEmpty()) {
            return Map.of("status", "IDLE", "message", "No pending report jobs found");
        }

        ReportJob job = optionalJob.get();
        log.info("Report worker picked up job {} (type: {})", job.getJobId(), job.getReportType());

        job.setStatus("GENERATING");
        reportJobRepository.saveAndFlush(job);

        try {
            String signedUrl;
            if ("CUSTOMER_STATEMENT".equalsIgnoreCase(job.getReportType())) {
                signedUrl = statementGenerator.generateCustomerStatement(job);
            } else {
                signedUrl = regulatoryReportGenerator.generateRegulatoryReport(job);
            }

            job.setStatus("COMPLETED");
            job.setFileUrl(signedUrl);
            job.setErrorMessage(null);
            job.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            reportJobRepository.save(job);

            log.info("Report job {} completed successfully with signed URL", job.getJobId());
            return Map.of(
                    "status", "COMPLETED",
                    "jobId", job.getJobId().toString(),
                    "reportType", job.getReportType(),
                    "fileUrl", signedUrl
            );
        } catch (Exception ex) {
            log.error("Report job {} execution failed: {}", job.getJobId(), ex.getMessage(), ex);
            job.setStatus("FAILED");
            String msg = ex.getMessage() != null ? ex.getMessage() : "Unknown report generation failure";
            job.setErrorMessage(msg.length() > 500 ? msg.substring(0, 497) + "..." : msg);
            job.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            reportJobRepository.save(job);

            return Map.of(
                    "status", "FAILED",
                    "jobId", job.getJobId().toString(),
                    "reportType", job.getReportType(),
                    "error", job.getErrorMessage()
            );
        }
    }
}
