import { z } from 'zod';

export const collectionActionSchema = z
  .object({
    actionType: z.enum([
      'REMINDER_SENT',
      'PHONE_CALL',
      'DEMAND_LETTER',
      'LEGAL_NOTICE',
      'WRITE_OFF_PROPOSED',
      'WRITE_OFF_APPROVED',
    ]),
    notes: z.string().min(1, 'Please record notes regarding this collection action'),
    typedConfirmation: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    if (data.actionType === 'WRITE_OFF_APPROVED') {
      if (data.typedConfirmation !== 'WRITE OFF') {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['typedConfirmation'],
          message: 'You must type "WRITE OFF" in all caps to approve a write-off',
        });
      }
    }
  });

export type CollectionActionFormValues = z.infer<typeof collectionActionSchema>;

