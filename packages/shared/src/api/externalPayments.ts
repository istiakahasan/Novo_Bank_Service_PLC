import { externalPaymentClient } from './client';
import type {
  CreateOutboundPaymentRequest,
  ExternalPayment,
} from '../types';

export async function createOutboundPayment(data: CreateOutboundPaymentRequest): Promise<ExternalPayment> {
  const headers = data.idempotencyKey ? { 'Idempotency-Key': data.idempotencyKey } : undefined;
  return (await externalPaymentClient.post<ExternalPayment>('/api/external-payments/outbound', data, { headers })).data;
}

export async function listExternalPayments(params?: {
  direction?: string;
  status?: string;
  rail?: string;
}): Promise<ExternalPayment[]> {
  const res = (await externalPaymentClient.get<ExternalPayment[] | { content: ExternalPayment[] }>('/api/external-payments', { params })).data;
  return Array.isArray(res) ? res : res.content ?? [];
}

export async function getExternalPayment(id: string): Promise<ExternalPayment> {
  return (await externalPaymentClient.get<ExternalPayment>(`/api/external-payments/${id}`)).data;
}
