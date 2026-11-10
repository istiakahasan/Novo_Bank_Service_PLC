'use client';

import { useEffect, useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Badge,
  Button,
  Card,
  EmptyState,
  ErrorBox,
  Field,
  PageHeader,
  SkeletonCard,
  SkeletonTable,
  Spinner,
  createStatementJob,
  formatDateTime,
  getReportJob,
  inputClass,
  listReportJobs,
  maskUuid,
  parseApiError,
  type ReportJob,
} from '@novo/shared';
import { statementSchema, type StatementFormValues } from './schema';

export default function StatementsPage() {
  const queryClient = useQueryClient();

  // Default last 30 days
  const today = new Date();
  const thirtyDaysAgo = new Date();
  thirtyDaysAgo.setDate(today.getDate() - 30);

  const defaultStart = thirtyDaysAgo.toISOString().split('T')[0];
  const defaultEnd = today.toISOString().split('T')[0];

  const [activeJobId, setActiveJobId] = useState<string | null>(null);
  const [activeJob, setActiveJob] = useState<ReportJob | null>(null);
  const [pollError, setPollError] = useState<string | null>(null);
  const [isPolling, setIsPolling] = useState(false);
  const pollAttemptsRef = useRef(0);
  const pollTimerRef = useRef<NodeJS.Timeout | null>(null);

  const pastStatementsQuery = useQuery({
    queryKey: ['past-statements'],
    queryFn: () => listReportJobs(0, 50),
  });

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<StatementFormValues>({
    resolver: zodResolver(statementSchema),
    defaultValues: {
      periodStart: defaultStart,
      periodEnd: defaultEnd,
    },
  });

  const startPolling = (jobId: string) => {
    setActiveJobId(jobId);
    setActiveJob(null);
    setPollError(null);
    setIsPolling(true);
    pollAttemptsRef.current = 0;

    const poll = async () => {
      pollAttemptsRef.current += 1;
      try {
        const job = await getReportJob(jobId);
        setActiveJob(job);

        if (job.status === 'COMPLETED') {
          setIsPolling(false);
          await queryClient.invalidateQueries({ queryKey: ['past-statements'] });
          return;
        }

        if (job.status === 'FAILED') {
          setIsPolling(false);
          setPollError(job.errorMessage || 'Statement generation failed.');
          return;
        }

        if (pollAttemptsRef.current >= 20) {
          setIsPolling(false);
          setPollError('Report taking longer than expected — check back later.');
          return;
        }

        pollTimerRef.current = setTimeout(poll, 3000);
      } catch (err) {
        setIsPolling(false);
        setPollError(parseApiError(err).message);
      }
    };

    void poll();
  };

  useEffect(() => {
    return () => {
      if (pollTimerRef.current) clearTimeout(pollTimerRef.current);
    };
  }, []);

  const statementMutation = useMutation({
    mutationFn: (values: StatementFormValues) =>
      createStatementJob({
        periodStart: values.periodStart,
        periodEnd: values.periodEnd,
      }),
    onSuccess: (data) => {
      startPolling(data.jobId);
    },
  });

  const onSubmit = (values: StatementFormValues) => {
    setPollError(null);
    setActiveJob(null);
    statementMutation.mutate(values);
  };

  const getStatusTone = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return 'green';
      case 'GENERATING':
      case 'PENDING':
        return 'yellow';
      case 'FAILED':
        return 'red';
      default:
        return 'gray';
    }
  };

  const statements = pastStatementsQuery.data?.content.filter(
    (job) => job.reportType === 'CUSTOMER_STATEMENT'
  );

  return (
    <>
      <PageHeader title="Account Statements" />

      <div className="grid gap-8 lg:grid-cols-3">
        {/* Left: Request Statement Form */}
        <div className="lg:col-span-1">
          <Card title="Generate Statement">
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
              {statementMutation.isError ? (
                <ErrorBox>{parseApiError(statementMutation.error).message}</ErrorBox>
              ) : null}

              <Field label="Period Start Date" error={errors.periodStart?.message}>
                <input
                  type="date"
                  {...register('periodStart')}
                  className={inputClass}
                />
              </Field>

              <Field label="Period End Date" error={errors.periodEnd?.message}>
                <input
                  type="date"
                  {...register('periodEnd')}
                  className={inputClass}
                />
              </Field>

              <Button
                type="submit"
                className="w-full"
                disabled={isSubmitting || statementMutation.isPending || isPolling}
              >
                {statementMutation.isPending || isPolling ? 'Requesting...' : 'Generate PDF Statement'}
              </Button>
            </form>

            {/* In-Flight Generation Progress */}
            {isPolling || activeJob ? (
              <div className="mt-6 rounded-lg border border-slate-200 bg-slate-50 p-4">
                <div className="flex items-center justify-between text-sm">
                  <span className="font-semibold text-slate-800">Status:</span>
                  <Badge tone={getStatusTone(activeJob?.status || 'PENDING')}>
                    {activeJob?.status || 'PENDING'}
                  </Badge>
                </div>

                {isPolling ? (
                  <div className="mt-3 flex items-center gap-2 text-xs text-slate-600">
                    <Spinner label={`Generating PDF... (Attempt ${pollAttemptsRef.current}/20)`} />
                  </div>
                ) : null}

                {activeJob?.status === 'COMPLETED' && activeJob.fileUrl ? (
                  <div className="mt-4">
                    <a
                      href={activeJob.fileUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="block text-center"
                    >
                      <Button className="w-full bg-emerald-600 hover:bg-emerald-500">
                        Download PDF
                      </Button>
                    </a>
                  </div>
                ) : null}

                {pollError ? (
                  <div className="mt-3">
                    <ErrorBox>{pollError}</ErrorBox>
                  </div>
                ) : null}
              </div>
            ) : null}
          </Card>
        </div>

        {/* Right: Past Statements Table */}
        <div className="lg:col-span-2">
          <Card title="Past Statements">
            {pastStatementsQuery.isLoading ? <SkeletonTable rows={4} cols={5} /> : null}

            {pastStatementsQuery.isError ? (
              <ErrorBox>{parseApiError(pastStatementsQuery.error).message}</ErrorBox>
            ) : null}

            {statements ? (
              statements.length === 0 ? (
                <EmptyState
                  title="No statements generated yet"
                  description="Use the form on the left to request your first periodic statement."
                />
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm" aria-label="Past Generated Statements">
                    <caption className="sr-only">Previous statement jobs and download links</caption>
                    <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                      <tr>
                        <th scope="col" className="px-4 py-3">Statement Period</th>
                        <th scope="col" className="px-4 py-3">Status</th>
                        <th scope="col" className="px-4 py-3">Requested At</th>
                        <th scope="col" className="px-4 py-3">Job ID</th>
                        <th scope="col" className="px-4 py-3 text-right">Action</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {statements.map((job) => (
                        <tr key={job.jobId} className="hover:bg-slate-50">
                          <td className="px-4 py-3 font-medium text-slate-900">
                            {job.periodStart} &rarr; {job.periodEnd}
                          </td>
                          <td className="px-4 py-3">
                            <Badge tone={getStatusTone(job.status)}>{job.status}</Badge>
                          </td>
                          <td className="px-4 py-3 text-slate-500">{formatDateTime(job.createdAt)}</td>
                          <td className="px-4 py-3 font-mono text-xs text-slate-400">{maskUuid(job.jobId)}</td>
                          <td className="px-4 py-3 text-right">
                            {job.status === 'COMPLETED' && job.fileUrl ? (
                              <a
                                href={job.fileUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="inline-flex items-center text-sm font-semibold text-sky-600 hover:text-sky-800"
                              >
                                Download PDF &darr;
                              </a>
                            ) : job.status === 'FAILED' ? (
                              <span className="text-xs text-rose-600 font-medium">Failed</span>
                            ) : (
                              <span className="text-xs text-slate-400">Processing</span>
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )
            ) : null}
          </Card>
        </div>
      </div>
    </>
  );
}

