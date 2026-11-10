import { reportingClient } from './client';
import type {
  CreateRegulatoryReportRequest,
  CreateStatementRequest,
  ReportJob,
  ReportPage,
} from '../types';

export async function createStatementJob(data: CreateStatementRequest): Promise<ReportJob> {
  return (await reportingClient.post<ReportJob>('/api/reports/statement', data)).data;
}

export async function getReportJob(jobId: string): Promise<ReportJob> {
  return (await reportingClient.get<ReportJob>(`/api/reports/jobs/${jobId}`)).data;
}

export async function listReportJobs(page = 0, size = 20): Promise<ReportPage> {
  return (await reportingClient.get<ReportPage>('/api/reports/jobs', { params: { page, size } })).data;
}

export async function createRegulatoryReportJob(data: CreateRegulatoryReportRequest): Promise<ReportJob> {
  return (await reportingClient.post<ReportJob>('/api/reports/regulatory', data)).data;
}
