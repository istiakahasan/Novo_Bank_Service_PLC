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
  Pagination,
  SkeletonTable,
  Spinner,
  createRegulatoryReportJob,
  formatDateTime,
  getReportJob,
  inputClass,
  listReportJobs,
  maskUuid,
  parseApiError,
  type ReportJob,
} from '@novo/shared';
import { regulatoryReportSchema, type RegulatoryReportFormValues } from './schema';

export default function RegulatoryReportsPage() {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [activeJobId, setActiveJobId] = useState<string | null>(null);
  const [activeJob, setActiveJob] = useState<ReportJob | null>(null);
  const [pollError, setPollError] = useState<string | null>(null);
  const [isPolling, setIsPolling] = useState(false);
  const pollAttemptsRef = useRef(0);
  const pollTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Default dates: last 30 days
  const today = new Date();
  const thirtyDaysAgo = new Date();
  thirtyDaysAgo.setDate(today.getDate() - 30);
  const defaultStart = thirtyDaysAgo.toISOString().split('T')[0];
  const defaultEnd = today.toISOString().split('T')[0];

  const jobsQuery = useQuery({
    queryKey: ['all-report-jobs', page],
    queryFn: () => listReportJobs(page, 15),
  });

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<RegulatoryReportFormValues>({
    resolver: zodResolver(regulatoryReportSchema),
    defaultValues: {
      reportType: 'DAILY_TRANSACTION_SUMMARY',
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
          await queryClient.invalidateQueries({ queryKey: ['all-report-jobs'] });
          return;
        }

        if (job.status === 'FAILED') {
          setIsPolling(false);
          setPollError(job.errorMessage || 'Report extraction failed.');
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

  const reportMutation = useMutation({
    mutationFn: (values: RegulatoryReportFormValues) => createRegulatoryReportJob(values),
    onSuccess: (data) => {
      startPolling(data.jobId);
    },
  });

  const onSubmit = (values: RegulatoryReportFormValues) => {
    setPollError(null);
    setActiveJob(null);
    reportMutation.mutate(values);
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

  return (
    <>
      <PageHeader title="Regulatory & Compliance Reporting" />

      <div className="space-y-8">
        {/* Top: Generate Regulatory Report Form */}
        <Card title="Generate Regulatory Report">
          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            {reportMutation.isError ? (
              <ErrorBox>{parseApiError(reportMutation.error).message}</ErrorBox>
            ) : null}

            <div className="grid gap-4 md:grid-cols-3">
              <Field label="Report Type" error={errors.reportType?.message}>
                <select {...register('reportType')} className={inputClass}>
                  <option value="DAILY_TRANSACTION_SUMMARY">DAILY_TRANSACTION_SUMMARY (CSV)</option>
                  <option value="LOAN_PORTFOLIO">LOAN_PORTFOLIO (CSV)</option>
                  <option value="KYC_STATUS">KYC_STATUS (CSV)</option>
                </select>
              </Field>

              <Field label="Period Start" error={errors.periodStart?.message}>
                <input type="date" {...register('periodStart')} className={inputClass} />
              </Field>

              <Field label="Period End" error={errors.periodEnd?.message}>
                <input type="date" {...register('periodEnd')} className={inputClass} />
              </Field>
            </div>

            <div className="flex justify-end pt-2">
              <Button
                type="submit"
                disabled={isSubmitting || reportMutation.isPending || isPolling}
              >
                {reportMutation.isPending || isPolling ? 'Requesting...' : 'Generate Extract'}
              </Button>
            </div>
          </form>

          {/* Real-time Polling Feedback */}
          {isPolling || activeJob ? (
            <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-4">
              <div className="flex items-center justify-between text-sm">
                <span className="font-semibold text-slate-800">Job {maskUuid(activeJobId || '')}</span>
                <Badge tone={getStatusTone(activeJob?.status || 'PENDING')}>
                  {activeJob?.status || 'PENDING'}
                </Badge>
              </div>

              {isPolling ? (
                <div className="mt-2 flex items-center gap-2 text-xs text-slate-600">
                  <Spinner label={`Generating CSV file... (Attempt ${pollAttemptsRef.current}/20)`} />
                </div>
              ) : null}

              {activeJob?.status === 'COMPLETED' && activeJob.fileUrl ? (
                <div className="mt-3">
                  <a
                    href={activeJob.fileUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 font-semibold text-sm text-emerald-700 hover:text-emerald-900"
                  >
                    <span>Download Report File &darr;</span>
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

        {/* Table of Report Jobs */}
        <Card title="Generated Reports Log">
          {jobsQuery.isLoading ? <SkeletonTable rows={5} cols={6} /> : null}

          {jobsQuery.isError ? (
            <ErrorBox>{parseApiError(jobsQuery.error).message}</ErrorBox>
          ) : null}

          {jobsQuery.data ? (
            jobsQuery.data.content.length === 0 ? (
              <EmptyState
                title="No reports generated"
                description="Use the generator above to create your first regulatory extract."
              />
            ) : (
              <div className="space-y-4">
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-sm" aria-label="Regulatory Reports Log">
                    <caption className="sr-only">Audit log of all regulatory and statement extraction jobs</caption>
                    <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                      <tr>
                        <th scope="col" className="px-4 py-3">Job ID</th>
                        <th scope="col" className="px-4 py-3">Report Type</th>
                        <th scope="col" className="px-4 py-3">Reporting Period</th>
                        <th scope="col" className="px-4 py-3">Status</th>
                        <th scope="col" className="px-4 py-3">Requested By</th>
                        <th scope="col" className="px-4 py-3">Created</th>
                        <th scope="col" className="px-4 py-3 text-right">Download</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {jobsQuery.data.content.map((job) => (
                        <tr key={job.jobId} className="hover:bg-slate-50">
                          <td className="px-4 py-3 font-mono text-slate-700">{maskUuid(job.jobId)}</td>
                          <td className="px-4 py-3 font-semibold text-slate-900">{job.reportType}</td>
                          <td className="px-4 py-3 text-slate-700">
                            {job.periodStart} &rarr; {job.periodEnd}
                          </td>
                          <td className="px-4 py-3">
                            <Badge tone={getStatusTone(job.status)}>{job.status}</Badge>
                          </td>
                          <td className="px-4 py-3 font-mono text-xs text-slate-500">
                            {maskUuid(job.requestedBy)}
                          </td>
                          <td className="px-4 py-3 text-slate-500">{formatDateTime(job.createdAt)}</td>
                          <td className="px-4 py-3 text-right">
                            {job.status === 'COMPLETED' && job.fileUrl ? (
                              <a
                                href={job.fileUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="font-semibold text-sky-600 hover:text-sky-800"
                              >
                                Download &darr;
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

                <Pagination
                  page={page}
                  totalPages={jobsQuery.data.totalPages}
                  onChange={(p) => setPage(p)}
                />
              </div>
            )
          ) : null}
        </Card>
      </div>
    </>
  );
}

