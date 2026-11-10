'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Badge,
  Button,
  Card,
  ErrorBox,
  PageHeader,
  SkeletonCard,
  SkeletonTable,
  Spinner,
  createTreasurySnapshot,
  formatCents,
  formatDateTime,
  getTreasuryGap,
  getTreasuryLiquidity,
  getTreasuryNim,
  getTreasurySnapshots,
  parseApiError,
  type ALMGapBucket,
  type TreasurySnapshot,
} from '@novo/shared';

export default function TreasuryPage() {
  const queryClient = useQueryClient();

  const liquidityQuery = useQuery({
    queryKey: ['treasury-liquidity'],
    queryFn: getTreasuryLiquidity,
    refetchInterval: 30000,
  });

  const nimQuery = useQuery({
    queryKey: ['treasury-nim'],
    queryFn: getTreasuryNim,
    refetchInterval: 30000,
  });

  const gapQuery = useQuery({
    queryKey: ['treasury-gap'],
    queryFn: getTreasuryGap,
    refetchInterval: 30000,
  });

  const snapshotsQuery = useQuery({
    queryKey: ['treasury-snapshots'],
    queryFn: () => getTreasurySnapshots(),
    refetchInterval: 30000,
  });

  const snapshotMutation = useMutation({
    mutationFn: createTreasurySnapshot,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['treasury-liquidity'] }),
        queryClient.invalidateQueries({ queryKey: ['treasury-nim'] }),
        queryClient.invalidateQueries({ queryKey: ['treasury-gap'] }),
        queryClient.invalidateQueries({ queryKey: ['treasury-snapshots'] }),
      ]);
    },
  });

  // Calculate NIM trend from most recent snapshot
  const snapshots = (snapshotsQuery.data || []).slice(-20);
  const lastSnapshot = snapshots.length > 0 ? snapshots[snapshots.length - 1] : null;
  const currentNimBps = nimQuery.data?.nimBps ?? 0;
  const prevNimBps = lastSnapshot?.nimBps ?? currentNimBps;
  const nimTrend = currentNimBps >= prevNimBps ? 'up' : 'down';

  // LCR Ratio color: green >1, amber 0.5–1, red <0.5
  const lcrRatio = liquidityQuery.data?.lcrRatio ?? 0;
  const lcrColorClass =
    lcrRatio >= 1.0 ? 'text-emerald-600' : lcrRatio >= 0.5 ? 'text-amber-600' : 'text-rose-600';

  // Render Inline SVG Chart for historical snapshots
  const renderHistoricalSvgChart = () => {
    if (snapshots.length < 2) {
      return (
        <div className="flex h-48 items-center justify-center text-sm text-slate-400">
          At least 2 historical snapshots are required to display trend chart.
        </div>
      );
    }

    const width = 640;
    const height = 220;
    const paddingX = 50;
    const paddingY = 30;

    const values = snapshots.map((s) => Number(s.lcrRatio) || 0);
    const minVal = Math.max(0, Math.min(...values) - 0.2);
    const maxVal = Math.max(...values, 1.5) + 0.2;
    const valRange = maxVal - minVal || 1;

    const points = snapshots.map((s, idx) => {
      const x = paddingX + (idx / (snapshots.length - 1)) * (width - 2 * paddingX);
      const val = Number(s.lcrRatio) || 0;
      const y = height - paddingY - ((val - minVal) / valRange) * (height - 2 * paddingY);
      return { x, y, val, date: s.snapshotDate };
    });

    const polylinePoints = points.map((p) => `${p.x},${p.y}`).join(' ');

    return (
      <div className="w-full overflow-x-auto">
        <svg
          viewBox={`0 0 ${width} ${height}`}
          className="w-full h-56 text-xs"
          role="img"
          aria-label="Historical Liquidity Coverage Ratio trend line chart"
        >
          {/* Horizontal Grid lines */}
          <line
            x1={paddingX}
            y1={paddingY}
            x2={width - paddingX}
            y2={paddingY}
            stroke="#e2e8f0"
            strokeDasharray="4"
          />
          <text x={paddingX - 10} y={paddingY + 4} textAnchor="end" fill="#94a3b8">
            {maxVal.toFixed(2)}
          </text>

          <line
            x1={paddingX}
            y1={height / 2}
            x2={width - paddingX}
            y2={height / 2}
            stroke="#e2e8f0"
            strokeDasharray="4"
          />
          <text x={paddingX - 10} y={height / 2 + 4} textAnchor="end" fill="#94a3b8">
            {((maxVal + minVal) / 2).toFixed(2)}
          </text>

          <line
            x1={paddingX}
            y1={height - paddingY}
            x2={width - paddingX}
            y2={height - paddingY}
            stroke="#cbd5e1"
          />
          <text x={paddingX - 10} y={height - paddingY + 4} textAnchor="end" fill="#94a3b8">
            {minVal.toFixed(2)}
          </text>

          {/* Trend Line */}
          <polyline
            fill="none"
            stroke="#0284c7"
            strokeWidth="3"
            strokeLinecap="round"
            strokeLinejoin="round"
            points={polylinePoints}
          />

          {/* Data Points */}
          {points.map((pt, i) => (
            <g key={i}>
              <circle cx={pt.x} cy={pt.y} r="4" fill="#0284c7" className="hover:r-6 transition" />
              {/* Show date label on first, middle, last */}
              {i === 0 || i === Math.floor(points.length / 2) || i === points.length - 1 ? (
                <text x={pt.x} y={height - 10} textAnchor="middle" fill="#64748b" className="text-[10px]">
                  {pt.date}
                </text>
              ) : null}
            </g>
          ))}
        </svg>
      </div>
    );
  };

  return (
    <>
      <PageHeader
        title="Treasury & Asset-Liability Management"
        actions={
          <Button
            onClick={() => snapshotMutation.mutate()}
            disabled={snapshotMutation.isPending}
          >
            {snapshotMutation.isPending ? 'Capturing Snapshot...' : 'Take Snapshot'}
          </Button>
        }
      />

      <div className="space-y-6">
        {/* Top 2 Cards: Liquidity & NIM */}
        <div className="grid gap-6 md:grid-cols-2">
          {/* Liquidity Card */}
          <Card title="Liquidity Position (LCR Proxy)">
            {liquidityQuery.isLoading ? <SkeletonCard rows={4} /> : null}
            {liquidityQuery.isError ? (
              <ErrorBox>{parseApiError(liquidityQuery.error).message}</ErrorBox>
            ) : null}
            {liquidityQuery.data ? (
              <div className="space-y-4">
                <div className="flex items-baseline justify-between border-b pb-3">
                  <span className="text-sm text-slate-500">Liquidity Coverage Ratio (LCR):</span>
                  <span className={`text-3xl font-extrabold ${lcrColorClass}`}>
                    {Number(liquidityQuery.data.lcrRatio).toFixed(4)}
                  </span>
                </div>
                <div className="grid grid-cols-3 gap-2 text-sm">
                  <div className="rounded bg-slate-50 p-2">
                    <span className="text-xs text-slate-500 block">Total Deposits</span>
                    <span className="font-semibold text-slate-900">
                      {formatCents(liquidityQuery.data.totalDepositsCents)}
                    </span>
                  </div>
                  <div className="rounded bg-slate-50 p-2">
                    <span className="text-xs text-slate-500 block">Total Loans</span>
                    <span className="font-semibold text-slate-900">
                      {formatCents(liquidityQuery.data.totalLoansCents)}
                    </span>
                  </div>
                  <div className="rounded bg-slate-50 p-2">
                    <span className="text-xs text-slate-500 block">Liquid Assets</span>
                    <span className="font-semibold text-slate-900">
                      {formatCents(liquidityQuery.data.liquidAssetsCents)}
                    </span>
                  </div>
                </div>
                <div className="text-xs text-slate-400 text-right">
                  Refreshed: {formatDateTime(liquidityQuery.data.computedAt)}
                </div>
              </div>
            ) : null}
          </Card>

          {/* NIM Card */}
          <Card title="Net Interest Margin (NIM)">
            {nimQuery.isLoading ? <SkeletonCard rows={4} /> : null}
            {nimQuery.isError ? (
              <ErrorBox>{parseApiError(nimQuery.error).message}</ErrorBox>
            ) : null}
            {nimQuery.data ? (
              <div className="space-y-4">
                <div className="flex items-baseline justify-between border-b pb-3">
                  <span className="text-sm text-slate-500">Current NIM:</span>
                  <div className="flex items-center gap-2">
                    <span className="text-3xl font-extrabold text-slate-900">
                      {nimQuery.data.nimBps} bps
                    </span>
                    <span
                      className={`text-lg font-bold ${
                        nimTrend === 'up' ? 'text-emerald-600' : 'text-rose-600'
                      }`}
                      aria-label={`Trend: ${nimTrend}`}
                    >
                      {nimTrend === 'up' ? '▲' : '▼'}
                    </span>
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <div className="rounded bg-slate-50 p-2">
                    <span className="text-xs text-slate-500 block">Interest Income (Loans)</span>
                    <span className="font-semibold text-emerald-700">
                      {formatCents(nimQuery.data.interestIncomeCents)}
                    </span>
                  </div>
                  <div className="rounded bg-slate-50 p-2">
                    <span className="text-xs text-slate-500 block">Interest Expense (Deposits)</span>
                    <span className="font-semibold text-rose-700">
                      {formatCents(nimQuery.data.interestExpenseCents)}
                    </span>
                  </div>
                </div>
                <div className="text-xs text-slate-400 text-right">
                  Refreshed: {formatDateTime(nimQuery.data.computedAt)}
                </div>
              </div>
            ) : null}
          </Card>
        </div>

        {/* Section C: Interest Rate Gap Analysis Table */}
        <Card title="ALM Interest Rate Gap Analysis">
          {gapQuery.isLoading ? <SkeletonTable rows={4} cols={4} /> : null}
          {gapQuery.isError ? (
            <ErrorBox>{parseApiError(gapQuery.error).message}</ErrorBox>
          ) : null}
          {gapQuery.data ? (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm" aria-label="Maturity Bucket Gap Analysis">
                <caption className="sr-only">Assets vs liabilities mismatch by maturity bucket</caption>
                <thead className="border-b border-slate-200 bg-slate-50 text-xs font-semibold uppercase text-slate-500">
                  <tr>
                    <th scope="col" className="px-4 py-3">Maturity Bucket</th>
                    <th scope="col" className="px-4 py-3">Rate Sensitive Assets</th>
                    <th scope="col" className="px-4 py-3">Rate Sensitive Liabilities</th>
                    <th scope="col" className="px-4 py-3">Net Gap</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {gapQuery.data.map((bucket: ALMGapBucket) => {
                    const isNegative = bucket.gapCents < 0;
                    return (
                      <tr key={bucket.bucket} className="hover:bg-slate-50">
                        <td className="px-4 py-3 font-semibold text-slate-800">{bucket.bucket}</td>
                        <td className="px-4 py-3 text-slate-700">{formatCents(bucket.assetsCents)}</td>
                        <td className="px-4 py-3 text-slate-700">{formatCents(bucket.liabilitiesCents)}</td>
                        <td
                          className={`px-4 py-3 font-bold ${
                            isNegative ? 'text-rose-600' : 'text-emerald-700'
                          }`}
                        >
                          {formatCents(bucket.gapCents)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ) : null}
        </Card>

        {/* Historical Snapshots Trend Chart (Inline SVG) */}
        <Card title="Historical LCR Snapshots Trend (Max 20 Data Points)">
          {snapshotsQuery.isLoading ? <SkeletonCard rows={5} /> : null}
          {snapshotsQuery.isError ? (
            <ErrorBox>{parseApiError(snapshotsQuery.error).message}</ErrorBox>
          ) : null}
          {snapshotsQuery.data ? renderHistoricalSvgChart() : null}
        </Card>
      </div>
    </>
  );
}

