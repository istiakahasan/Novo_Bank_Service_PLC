import { loanClient } from './client';
import type {
  AddCollateralRequest,
  Collateral,
  CollectionAction,
  CollectionActionRequest,
  CreateLoanApplicationRequest,
  LoanApplication,
  LoanProduct,
  OverdueRepayment,
  RepaymentSchedule,
  ReviewLoanRequest,
} from '../types';

export async function getLoanProducts(): Promise<LoanProduct[]> {
  return (await loanClient.get<LoanProduct[]>('/api/loans/products')).data;
}

export async function createLoanApplication(data: CreateLoanApplicationRequest): Promise<LoanApplication> {
  return (await loanClient.post<LoanApplication>('/api/loans/applications', data)).data;
}

export async function listLoanApplications(params?: { status?: string; productCode?: string }): Promise<LoanApplication[]> {
  return (await loanClient.get<LoanApplication[]>('/api/loans/applications', { params })).data;
}

export async function getLoanApplication(id: string): Promise<LoanApplication> {
  return (await loanClient.get<LoanApplication>(`/api/loans/applications/${id}`)).data;
}

export async function reviewLoanApplication(id: string, data: ReviewLoanRequest): Promise<LoanApplication> {
  return (await loanClient.patch<LoanApplication>(`/api/loans/applications/${id}/review`, data)).data;
}

export async function disburseLoan(id: string): Promise<LoanApplication> {
  return (await loanClient.post<LoanApplication>(`/api/loans/applications/${id}/disburse`)).data;
}

export async function payRepayment(id: string): Promise<RepaymentSchedule> {
  return (await loanClient.post<RepaymentSchedule>(`/api/loans/repayments/${id}/pay`)).data;
}

export async function getLoanCollateral(id: string): Promise<Collateral[]> {
  return (await loanClient.get<Collateral[]>(`/api/loans/applications/${id}/collateral`)).data;
}

export async function addLoanCollateral(id: string, data: AddCollateralRequest): Promise<Collateral> {
  return (await loanClient.post<Collateral>(`/api/loans/applications/${id}/collateral`, data)).data;
}

export async function releaseCollateral(id: string): Promise<Collateral> {
  return (await loanClient.patch<Collateral>(`/api/loans/collateral/${id}/release`)).data;
}

export async function getOverdueRepayments(): Promise<OverdueRepayment[]> {
  return (await loanClient.get<OverdueRepayment[]>('/api/loans/collections/overdue')).data;
}

export async function recordCollectionAction(repaymentId: string, data: CollectionActionRequest): Promise<CollectionAction> {
  return (await loanClient.post<CollectionAction>(`/api/loans/collections/${repaymentId}/action`, data)).data;
}

