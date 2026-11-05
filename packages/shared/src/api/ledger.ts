import { ledgerClient } from './client';
import type {
  AccountResponse,
  BalanceResponse,
  CreateAccountRequest,
  LedgerEntryResponse,
  TransferRequest,
  TransferResponse,
} from '../types';

export interface TransferResult {
  transfer: TransferResponse;
  /** true when the backend answered 200: this idempotency key was already processed (a new transfer is 201). */
  replayed: boolean;
}

export async function createTransfer(request: TransferRequest): Promise<TransferResult> {
  const response = await ledgerClient.post<TransferResponse>('/api/transfers', request);
  return { transfer: response.data, replayed: response.status === 200 };
}

export async function getTransfer(transactionId: string): Promise<TransferResponse> {
  return (await ledgerClient.get<TransferResponse>(`/api/transfers/${transactionId}`)).data;
}

export async function getBalance(accountId: string): Promise<BalanceResponse> {
  return (await ledgerClient.get<BalanceResponse>(`/api/accounts/${accountId}/balance`)).data;
}

export async function getEntries(accountId: string, limit = 20): Promise<LedgerEntryResponse[]> {
  return (await ledgerClient.get<LedgerEntryResponse[]>(`/api/accounts/${accountId}/entries`, { params: { limit } })).data;
}

export async function createAccount(request: CreateAccountRequest): Promise<AccountResponse> {
  return (await ledgerClient.post<AccountResponse>('/api/accounts', request)).data;
}
