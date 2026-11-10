package com.Bank_website.treasury.service;

import com.Bank_website.treasury.dto.AccountSummaryDto;
import com.Bank_website.treasury.dto.GapBucketResponse;
import com.Bank_website.treasury.dto.LiquidityResponse;
import com.Bank_website.treasury.dto.NimResponse;
import com.Bank_website.treasury.dto.TreasurySnapshotResponse;
import com.Bank_website.treasury.entity.AlmGapBucket;
import com.Bank_website.treasury.entity.TreasurySnapshot;
import com.Bank_website.treasury.exception.ConflictException;
import com.Bank_website.treasury.repository.AlmGapBucketRepository;
import com.Bank_website.treasury.repository.LoanApplicationViewRepository;
import com.Bank_website.treasury.repository.LoanRepaymentViewRepository;
import com.Bank_website.treasury.repository.TreasurySnapshotRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Core ALM analytics service implementing bank treasury formulas:
 * 1. Liquidity Coverage Ratio (LCR) proxy:
 *    - totalDepositsCents = SUM(balance_cents) from active savings/checking accounts in Ledger
 *    - totalLoansCents = SUM(approved_amount_cents) from DISBURSED loan applications
 *    - liquidAssetsCents = totalDepositsCents - totalLoansCents
 *    - lcrRatio = liquidAssetsCents / totalDepositsCents
 *
 * 2. Net Interest Margin (NIM):
 *    - interestIncomeCents = SUM(interest_due_cents) from PAID loan repayments in last 30 days
 *    - interestExpenseCents = (totalDepositsCents * DEPOSIT_RATE_BPS / 10000 / 12)
 *    - avgEarningAssetsCents = (totalLoansCents + liquidAssetsCents) / 2
 *    - nimBps = (interestIncomeCents - interestExpenseCents) / avgEarningAssetsCents * 10000 * 12
 *
 * 3. ALM Maturity Gap Analysis:
 *    - Assets: principal due on loans bucketed by due date (0-3m, 3-12m, 1-5y, 5y+)
 *    - Liabilities: behavioral maturity assumption (savings & checking in 0-3m bucket)
 *    - gapCents = assetsCents - liabilitiesCents per bucket
 */
@Service
@Transactional(readOnly = true)
public class TreasuryCalculationService {

    private final LedgerAggregateClient ledgerAggregateClient;
    private final LoanApplicationViewRepository loanApplicationViewRepository;
    private final LoanRepaymentViewRepository loanRepaymentViewRepository;
    private final TreasurySnapshotRepository treasurySnapshotRepository;
    private final AlmGapBucketRepository almGapBucketRepository;
    private final int depositRateBps;

    public TreasuryCalculationService(LedgerAggregateClient ledgerAggregateClient,
                                      LoanApplicationViewRepository loanApplicationViewRepository,
                                      LoanRepaymentViewRepository loanRepaymentViewRepository,
                                      TreasurySnapshotRepository treasurySnapshotRepository,
                                      AlmGapBucketRepository almGapBucketRepository,
                                      @Value("${treasury.deposit-rate-bps:300}") int depositRateBps) {
        this.ledgerAggregateClient = ledgerAggregateClient;
        this.loanApplicationViewRepository = loanApplicationViewRepository;
        this.loanRepaymentViewRepository = loanRepaymentViewRepository;
        this.treasurySnapshotRepository = treasurySnapshotRepository;
        this.almGapBucketRepository = almGapBucketRepository;
        this.depositRateBps = depositRateBps;
    }

    /**
     * Computes current liquidity position and LCR proxy.
     * Formula:
     *   liquidAssetsCents = totalDepositsCents - totalLoansCents
     *   lcrRatio = liquidAssetsCents / totalDepositsCents (0 if totalDepositsCents == 0)
     */
    public LiquidityResponse computeLiquidity() {
        AccountSummaryDto accountSummary = ledgerAggregateClient.getAccountSummary();
        Long totalDepositsCents = accountSummary.totalDepositsCents() != null ? accountSummary.totalDepositsCents() : 0L;
        Long totalLoansCents = loanApplicationViewRepository.sumApprovedAmountByStatusDisbursed();
        if (totalLoansCents == null) {
            totalLoansCents = 0L;
        }

        Long liquidAssetsCents = totalDepositsCents - totalLoansCents;
        BigDecimal lcrRatio = totalDepositsCents > 0
                ? BigDecimal.valueOf(liquidAssetsCents).divide(BigDecimal.valueOf(totalDepositsCents), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        OffsetDateTime computedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return new LiquidityResponse(totalDepositsCents, totalLoansCents, liquidAssetsCents, lcrRatio, computedAt);
    }

    /**
     * Computes Net Interest Margin (NIM) in basis points.
     * Formula:
     *   interestIncomeCents = SUM(interest_due_cents) for status='PAID' in last 30 days
     *   interestExpenseCents = (totalDepositsCents * DEPOSIT_RATE_BPS / 10000 / 12)
     *   avgEarningAssetsCents = (totalLoansCents + liquidAssetsCents) / 2
     *   nimBps = (interestIncomeCents - interestExpenseCents) / avgEarningAssetsCents * 10000 * 12
     */
    public NimResponse computeNim() {
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        Long interestIncomeCents = loanRepaymentViewRepository.sumPaidInterestSince(thirtyDaysAgo);
        if (interestIncomeCents == null) {
            interestIncomeCents = 0L;
        }

        AccountSummaryDto accountSummary = ledgerAggregateClient.getAccountSummary();
        Long totalDepositsCents = accountSummary.totalDepositsCents() != null ? accountSummary.totalDepositsCents() : 0L;
        Long totalLoansCents = loanApplicationViewRepository.sumApprovedAmountByStatusDisbursed();
        if (totalLoansCents == null) {
            totalLoansCents = 0L;
        }

        Long liquidAssetsCents = totalDepositsCents - totalLoansCents;
        Long interestExpenseCents = Math.round((double) totalDepositsCents * depositRateBps / 10000.0 / 12.0);
        Long avgEarningAssetsCents = (totalLoansCents + liquidAssetsCents) / 2;

        Integer nimBps = 0;
        if (avgEarningAssetsCents > 0) {
            double netInterestIncome = (double) interestIncomeCents - (double) interestExpenseCents;
            nimBps = (int) Math.round((netInterestIncome / (double) avgEarningAssetsCents) * 10000.0 * 12.0);
        }

        OffsetDateTime computedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return new NimResponse(nimBps, interestIncomeCents, interestExpenseCents, avgEarningAssetsCents, computedAt);
    }

    /**
     * Computes ALM interest rate gap across 4 maturity buckets:
     * 1. "0-3m":  dueDate < today + 3 months
     * 2. "3-12m": today + 3 months <= dueDate < today + 12 months
     * 3. "1-5y":  today + 12 months <= dueDate < today + 5 years
     * 4. "5y+":   dueDate >= today + 5 years
     *
     * Liabilities behavioral assumption:
     *   All transactional/savings deposits reside in the 0-3m bucket.
     *   gapCents = assetsCents - liabilitiesCents
     */
    public List<GapBucketResponse> computeGapBuckets() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate threeMonths = today.plusMonths(3);
        LocalDate twelveMonths = today.plusMonths(12);
        LocalDate fiveYears = today.plusYears(5);

        Long asset0to3m = loanRepaymentViewRepository.sumPrincipalDueBefore(threeMonths);
        Long asset3to12m = loanRepaymentViewRepository.sumPrincipalDueBetween(threeMonths, twelveMonths);
        Long asset1to5y = loanRepaymentViewRepository.sumPrincipalDueBetween(twelveMonths, fiveYears);
        Long asset5yPlus = loanRepaymentViewRepository.sumPrincipalDueFrom(fiveYears);

        AccountSummaryDto accountSummary = ledgerAggregateClient.getAccountSummary();
        Long totalDeposits = accountSummary.totalDepositsCents() != null ? accountSummary.totalDepositsCents() : 0L;

        Long a1 = asset0to3m != null ? asset0to3m : 0L;
        Long l1 = totalDeposits;
        Long g1 = a1 - l1;

        Long a2 = asset3to12m != null ? asset3to12m : 0L;
        Long l2 = 0L;
        Long g2 = a2 - l2;

        Long a3 = asset1to5y != null ? asset1to5y : 0L;
        Long l3 = 0L;
        Long g3 = a3 - l3;

        Long a4 = asset5yPlus != null ? asset5yPlus : 0L;
        Long l4 = 0L;
        Long g4 = a4 - l4;

        return List.of(
                new GapBucketResponse("0-3m", a1, l1, g1),
                new GapBucketResponse("3-12m", a2, l2, g2),
                new GapBucketResponse("1-5y", a3, l3, g3),
                new GapBucketResponse("5y+", a4, l4, g4)
        );
    }

    /**
     * Persists daily snapshot and gap buckets. Snapshot for a given date is immutable (returns 409 if exists).
     */
    @Transactional
    public TreasurySnapshotResponse createSnapshot() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (treasurySnapshotRepository.findBySnapshotDate(today).isPresent()) {
            throw new ConflictException("Treasury snapshot already exists for date: " + today);
        }

        LiquidityResponse liquidity = computeLiquidity();
        NimResponse nim = computeNim();
        List<GapBucketResponse> buckets = computeGapBuckets();

        TreasurySnapshot snapshot = new TreasurySnapshot();
        snapshot.setSnapshotDate(today);
        snapshot.setTotalDepositsCents(liquidity.totalDepositsCents());
        snapshot.setTotalLoansCents(liquidity.totalLoansCents());
        snapshot.setLiquidAssetsCents(liquidity.liquidAssetsCents());
        snapshot.setLcrRatio(liquidity.lcrRatio());
        snapshot.setNimBps(nim.nimBps());
        snapshot.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        for (GapBucketResponse b : buckets) {
            AlmGapBucket bucketEntity = new AlmGapBucket(
                    snapshot,
                    b.bucket(),
                    b.assetsCents(),
                    b.liabilitiesCents(),
                    b.gapCents()
            );
            snapshot.addGapBucket(bucketEntity);
        }

        TreasurySnapshot saved = treasurySnapshotRepository.save(snapshot);
        return toSnapshotResponse(saved, buckets);
    }

    /**
     * Retrieves historical snapshots within date range for trend analysis.
     */
    public List<TreasurySnapshotResponse> getSnapshots(LocalDate from, LocalDate to) {
        List<TreasurySnapshot> snapshots;
        if (from != null && to != null) {
            snapshots = treasurySnapshotRepository.findBySnapshotDateBetweenOrderBySnapshotDateAsc(from, to);
        } else if (from != null) {
            snapshots = treasurySnapshotRepository.findBySnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(from);
        } else if (to != null) {
            snapshots = treasurySnapshotRepository.findBySnapshotDateLessThanEqualOrderBySnapshotDateAsc(to);
        } else {
            snapshots = treasurySnapshotRepository.findAllByOrderBySnapshotDateAsc();
        }

        return snapshots.stream().map(s -> {
            List<AlmGapBucket> dbBuckets = almGapBucketRepository.findBySnapshotSnapshotId(s.getSnapshotId());
            List<GapBucketResponse> bucketResponses = dbBuckets.stream()
                    .map(b -> new GapBucketResponse(b.getBucketLabel(), b.getAssetsCents(), b.getLiabilitiesCents(), b.getGapCents()))
                    .toList();
            return toSnapshotResponse(s, bucketResponses);
        }).toList();
    }

    private TreasurySnapshotResponse toSnapshotResponse(TreasurySnapshot snapshot, List<GapBucketResponse> buckets) {
        return new TreasurySnapshotResponse(
                snapshot.getSnapshotId(),
                snapshot.getSnapshotDate(),
                snapshot.getTotalDepositsCents(),
                snapshot.getTotalLoansCents(),
                snapshot.getLiquidAssetsCents(),
                snapshot.getLcrRatio(),
                snapshot.getNimBps(),
                snapshot.getCreatedAt(),
                buckets
        );
    }
}

