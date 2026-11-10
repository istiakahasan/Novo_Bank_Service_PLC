package com.Bank_website.reporting.dto;

import com.Bank_website.reporting.entity.ReportJob;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ReportJobResponse(
        UUID jobId,
        String reportType,
        UUID customerId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String status,
        String fileUrl,
        String errorMessage,
        UUID requestedBy,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {
    public static ReportJobResponse from(ReportJob job) {
        if (job == null) {
            return null;
        }
        return new ReportJobResponse(
                job.getJobId(),
                job.getReportType(),
                job.getCustomerId(),
                job.getPeriodStart(),
                job.getPeriodEnd(),
                job.getStatus(),
                job.getFileUrl(),
                job.getErrorMessage(),
                job.getRequestedBy(),
                job.getCreatedAt(),
                job.getCompletedAt()
        );
    }
}
