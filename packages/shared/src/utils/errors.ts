import axios from 'axios';
import type { ApiErrorBody } from '../types';

export interface ParsedError {
  status?: number;
  message: string;
}

/** Turns an axios error from the backend ({status,message,timestamp}) into something displayable. */
export function parseApiError(error: unknown): ParsedError {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as ApiErrorBody | undefined;
    if (error.response) {
      return { status: error.response.status, message: body?.message ?? error.message };
    }
    return { message: 'Cannot reach the server. Check your connection and retry.' };
  }
  return { message: error instanceof Error ? error.message : 'Unexpected error' };
}
