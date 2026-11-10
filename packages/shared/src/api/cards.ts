import { cardClient } from './client';
import type {
  Card,
  CreateCardRequest,
  UpdateCardStatusRequest,
} from '../types';

export async function listCards(): Promise<Card[]> {
  const res = (await cardClient.get<Card[] | { content: Card[] }>('/api/cards')).data;
  return Array.isArray(res) ? res : res.content ?? [];
}

export async function createCard(data: CreateCardRequest): Promise<Card> {
  return (await cardClient.post<Card>('/api/cards', data)).data;
}

export async function updateCardStatus(id: string, data: UpdateCardStatusRequest): Promise<Card> {
  return (await cardClient.patch<Card>(`/api/cards/${id}/status`, data)).data;
}
