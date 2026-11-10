package com.Bank_website.treasury.controller;

import com.Bank_website.treasury.dto.GapBucketResponse;
import com.Bank_website.treasury.dto.LiquidityResponse;
import com.Bank_website.treasury.dto.NimResponse;
import com.Bank_website.treasury.dto.TreasurySnapshotResponse;
import com.Bank_website.treasury.service.TreasuryCalculationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/treasury")
@PreAuthorize("hasRole('STAFF')")
public class TreasuryController {

    private final TreasuryCalculationService treasuryCalculationService;

    public TreasuryController(TreasuryCalculationService treasuryCalculationService) {
        this.treasuryCalculationService = treasuryCalculationService;
    }

    @GetMapping("/liquidity")
    public ResponseEntity<LiquidityResponse> getLiquidity() {
        return ResponseEntity.ok(treasuryCalculationService.computeLiquidity());
    }

    @GetMapping("/nim")
    public ResponseEntity<NimResponse> getNim() {
        return ResponseEntity.ok(treasuryCalculationService.computeNim());
    }

    @GetMapping("/gap")
    public ResponseEntity<List<GapBucketResponse>> getGap() {
        return ResponseEntity.ok(treasuryCalculationService.computeGapBuckets());
    }

    @PostMapping("/snapshot")
    public ResponseEntity<TreasurySnapshotResponse> createSnapshot() {
        return ResponseEntity.status(HttpStatus.CREATED).body(treasuryCalculationService.createSnapshot());
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<TreasurySnapshotResponse>> getSnapshots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(treasuryCalculationService.getSnapshots(from, to));
    }
}

