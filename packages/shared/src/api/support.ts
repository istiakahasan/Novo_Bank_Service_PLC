import { supportClient } from './client';
import type {
  CreateMessageRequest,
  CreateTicketRequest,
  TicketListParams,
  TicketListResponse,
  TicketMessageResponse,
  TicketResponse,
  UpdateTicketRequest,
} from '../types';

/**
 * The backend record component is `isInternalNote`; depending on the Jackson version the JSON key can come
 * back as `internalNote`. Normalising here guarantees the UI never treats an internal note as public.
 */
function normalizeMessage(raw: TicketMessageResponse & { internalNote?: boolean }): TicketMessageResponse {
  const { internalNote, ...rest } = raw;
  return { ...rest, isInternalNote: Boolean(raw.isInternalNote ?? internalNote ?? false) };
}

function normalizeTicket(ticket: TicketResponse): TicketResponse {
  return ticket.messages ? { ...ticket, messages: ticket.messages.map(normalizeMessage) } : ticket;
}

export async function listTickets(params: TicketListParams = {}): Promise<TicketListResponse> {
  const query: Record<string, string | number> = { page: params.page ?? 0, size: params.size ?? 20 };
  if (params.status) query.status = params.status;
  if (params.priority) query.priority = params.priority;
  if (params.assignedAgentId) query.assignedAgentId = params.assignedAgentId;
  return (await supportClient.get<TicketListResponse>('/api/tickets', { params: query })).data;
}

export async function getTicket(ticketId: string): Promise<TicketResponse> {
  return normalizeTicket((await supportClient.get<TicketResponse>(`/api/tickets/${ticketId}`)).data);
}

export async function createTicket(request: CreateTicketRequest): Promise<TicketResponse> {
  return normalizeTicket((await supportClient.post<TicketResponse>('/api/tickets', request)).data);
}

export async function updateTicket(ticketId: string, request: UpdateTicketRequest): Promise<TicketResponse> {
  return normalizeTicket((await supportClient.patch<TicketResponse>(`/api/tickets/${ticketId}`, request)).data);
}

export async function addMessage(ticketId: string, request: CreateMessageRequest): Promise<TicketMessageResponse> {
  const body = { messageBody: request.messageBody, isInternalNote: request.isInternalNote, internalNote: request.isInternalNote };
  return normalizeMessage((await supportClient.post<TicketMessageResponse>(`/api/tickets/${ticketId}/messages`, body)).data);
}
