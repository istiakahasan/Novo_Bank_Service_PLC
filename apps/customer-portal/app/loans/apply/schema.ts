import { z } from 'zod';
import { dollarsToCents } from '@novo/shared';

export const loanApplySchema = z.object({
  productCode: z.string().min(1, 'Please select a loan product'),
  requestedAmount: z
    .string()
    .min(1, 'Amount is required')
    .refine((val) => {
      const cents = dollarsToCents(val);
      return cents !== null && cents > 0;
    }, 'Enter a valid dollar amount (e.g. 5000.00)'),
  requestedTenureMonths: z.coerce.number().min(6, 'Minimum tenure is 6 months').max(240, 'Maximum tenure is 240 months'),
});

export type LoanApplyFormValues = z.infer<typeof loanApplySchema>;
