import { customerClient } from './client';
import type { CustomerPage, CustomerResponse, KycStatus, KycUpdateRequest } from '../types';

export async function getCustomer(customerId: string): Promise<CustomerResponse> {
  return (await customerClient.get<CustomerResponse>(`/api/customers/${customerId}`)).data;
}

export async function getCustomerByAccount(accountId: string): Promise<CustomerResponse> {
  return (await customerClient.get<CustomerResponse>(`/api/customers/by-account/${accountId}`)).data;
}

export async function updateKyc(customerId: string, body: KycUpdateRequest): Promise<CustomerResponse> {
  return (await customerClient.patch<CustomerResponse>(`/api/customers/${customerId}/kyc`, body)).data;
}

/** Needs the GET /api/customers?kycStatus= endpoint described in docs/FRONTEND.md (not in the backend yet). */
export async function listCustomersByKyc(kycStatus: KycStatus, page = 0, size = 20): Promise<CustomerPage> {
  return (await customerClient.get<CustomerPage>('/api/customers', { params: { kycStatus, page, size } })).data;
}
