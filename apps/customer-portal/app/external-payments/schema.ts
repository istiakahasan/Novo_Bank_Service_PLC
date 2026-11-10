import { z } from 'zod';
import { dollarsToCents } from '@novo/shared';

export const externalPaymentSchema = z
  .object({
    rail: z.enum(['ACH', 'RTGS', 'SWIFT_SIM'], {
      required_error: 'Please select a payment rail',
    }),
    fromAccountId: z.string().uuid('Please select a source account'),
    externalAccountNumber: z
      .string()
      .min(4, 'Account number must be at least 4 digits')
      .max(50, 'Account number too long'),
    externalRoutingNumber: z
      .string()
      .min(9, 'Routing number must be at least 9 characters')
      .max(20, 'Routing number too long'),
    externalBankName: z.string().min(2, 'Bank name is required').max(100),
    amount: z
      .string()
      .min(1, 'Amount is required')
      .refine((val) => {
        const cents = dollarsToCents(val);
        return cents !== null && cents > 0;
      }, 'Enter a valid dollar amount'),
  })
  .superRefine((data, ctx) => {
    const cents = dollarsToCents(data.amount);
    if (!cents) return;

    if (data.rail === 'ACH' && cents > 2_500_000) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['amount'],
        message: 'ACH transfer limit is $25,000.00 per transaction',
      });
    } else if (data.rail === 'RTGS') {
      if (cents < 1_000_000) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['amount'],
          message: 'RTGS transfers require a minimum of $10,000.00',
        });
      }
      if (cents > 100_000_000) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['amount'],
          message: 'RTGS transfer maximum limit is $1,000,000.00',
        });
      }
    } else if (data.rail === 'SWIFT_SIM' && cents > 50_000_000) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['amount'],
        message: 'SWIFT transfer maximum limit is $500,000.00',
      });
    }
  });

export type ExternalPaymentFormValues = z.infer<typeof externalPaymentSchema>;
