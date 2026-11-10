import { z } from 'zod';
import { dollarsToCents } from '@novo/shared';

export const loanReviewSchema = z
  .object({
    action: z.enum(['APPROVE', 'REJECT'], {
      required_error: 'Please select an action',
    }),
    approvedAmount: z.string().optional(),
    approvedInterestRateBps: z.coerce.number().optional(),
    approvedTenureMonths: z.coerce.number().optional(),
    rejectionReason: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    if (data.action === 'APPROVE') {
      if (!data.approvedAmount || dollarsToCents(data.approvedAmount) === null) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['approvedAmount'],
          message: 'Valid approved amount in USD is required for approval',
        });
      }
      if (!data.approvedInterestRateBps || data.approvedInterestRateBps <= 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['approvedInterestRateBps'],
          message: 'Interest rate (bps) must be positive',
        });
      }
      if (!data.approvedTenureMonths || data.approvedTenureMonths < 1) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['approvedTenureMonths'],
          message: 'Tenure months must be at least 1 month',
        });
      }
    } else if (data.action === 'REJECT') {
      if (!data.rejectionReason || data.rejectionReason.trim().length === 0) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['rejectionReason'],
          message: 'Rejection reason is required',
        });
      }
    }
  });

export type LoanReviewFormValues = z.infer<typeof loanReviewSchema>;

