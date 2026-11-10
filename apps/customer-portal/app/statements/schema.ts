import { z } from 'zod';

export const statementSchema = z
  .object({
    periodStart: z.string().min(1, 'Start date is required'),
    periodEnd: z.string().min(1, 'End date is required'),
  })
  .refine((data) => new Date(data.periodStart) <= new Date(data.periodEnd), {
    message: 'Start date must be before or equal to end date',
    path: ['periodEnd'],
  });

export type StatementFormValues = z.infer<typeof statementSchema>;
