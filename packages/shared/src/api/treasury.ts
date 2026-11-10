import { treasuryClient } from './client';
import type {
  ALMGapBucket,
  TreasuryLiquidity,
  TreasuryNIM,
  TreasurySnapshot,
} from '../types';

export async function getTreasuryLiquidity(): Promise<TreasuryLiquidity> {
  return (await treasuryClient.get<TreasuryLiquidity>('/api/treasury/liquidity')).data;
}

export async function getTreasuryNim(): Promise<TreasuryNIM> {
  return (await treasuryClient.get<TreasuryNIM>('/api/treasury/nim')).data;
}

export async function getTreasuryGap(): Promise<ALMGapBucket[]> {
  return (await treasuryClient.get<ALMGapBucket[]>('/api/treasury/gap')).data;
}

export async function createTreasurySnapshot(): Promise<TreasurySnapshot> {
  return (await treasuryClient.post<TreasurySnapshot>('/api/treasury/snapshot')).data;
}

export async function getTreasurySnapshots(from?: string, to?: string): Promise<TreasurySnapshot[]> {
  return (await treasuryClient.get<TreasurySnapshot[]>('/api/treasury/snapshots', { params: { from, to } })).data;
}
