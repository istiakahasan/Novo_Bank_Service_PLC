import { z } from 'zod';

export const issueCardSchema = z.object({
  accountId: z.string().uuid('Please select a valid account'),
});

export type IssueCardFormValues = z.infer<typeof issueCardSchema>;

