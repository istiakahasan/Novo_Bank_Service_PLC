import { z } from 'zod';

export const regulatoryReportSchema = z
  .object({
    reportType: z.enum(['DAILY_TRANSACTION_SUMMARY', 'LOAN_PORTFOLIO', 'KYC_STATUS'], {
      required_error: 'Please select a report type',
    }),
    periodStart: z.string().min(1, 'Start date is required'),
    periodEnd: z.string().min(1, 'End date is required'),
  })
  .refine((data) => new Date(data.periodStart) <= new Date(data.periodEnd), {
    message: 'Period start date must be before or equal to period end date',
    path: ['periodEnd'],
  });

export type RegulatoryReportFormValues = z.infer<typeof regulatoryReportSchema>;
