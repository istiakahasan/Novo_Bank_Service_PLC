package com.Bank_website.rails.service;

import com.Bank_website.rails.dto.TransferRequestDto;
import com.Bank_website.rails.entity.ExternalPayment;
import com.Bank_website.rails.repository.ExternalPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RailProcessorService {

    private static final Logger log = LoggerFactory.getLogger(RailProcessorService.class);

    private static final Map<String, Integer> REQUIRED_TICKS = Map.of(
            "ACH", 3,
            "RTGS", 1,
            "SWIFT_SIM", 2
    );

    private final ExternalPaymentRepository paymentRepository;
    private final LedgerClient ledgerClient;
    private final StringRedisTemplate redisTemplate;
    private final UUID railSettlementAccountId;
    private final double failureRate;

    public RailProcessorService(ExternalPaymentRepository paymentRepository,
                                LedgerClient ledgerClient,
                                StringRedisTemplate redisTemplate,
                                @Value("${rail.settlement-account-id}") UUID railSettlementAccountId,
                                @Value("${rail.failure-rate:0.10}") double failureRate) {
        this.paymentRepository = paymentRepository;
        this.ledgerClient = ledgerClient;
        this.redisTemplate = redisTemplate;
        this.railSettlementAccountId = railSettlementAccountId;
        this.failureRate = failureRate;
    }

    @Transactional
    public Map<String, Object> processNextPendingPayment() {
        Optional<ExternalPayment> nextOpt = paymentRepository
                .findFirstByDirectionAndStatusOrderByCreatedAtAsc("OUTBOUND", "PROCESSING");

        if (nextOpt.isEmpty()) {
            return Map.of("processed", false, "message", "No outbound payments pending processing");
        }

        ExternalPayment payment = nextOpt.get();
        UUID paymentId = payment.getPaymentId();
        String rail = payment.getRail();
        int requiredTicks = REQUIRED_TICKS.getOrDefault(rail, 1);

        String tickKey = "rail:ticks:" + paymentId;
        Long ticks = redisTemplate.opsForValue().increment(tickKey);
        if (ticks != null && ticks == 1) {
            redisTemplate.expire(tickKey, Duration.ofHours(2));
        }
        long currentTicks = ticks != null ? ticks : 1L;

        Map<String, Object> result = new HashMap<>();
        result.put("paymentId", paymentId.toString());
        result.put("rail", rail);
        result.put("currentTicks", currentTicks);
        result.put("requiredTicks", requiredTicks);

        if (currentTicks < requiredTicks) {
            log.info("Payment {} (rail {}) waiting for ticks ({}/{})", paymentId, rail, currentTicks, requiredTicks);
            result.put("processed", true);
            result.put("status", "PROCESSING");
            return result;
        }

        // Ticks completed: simulate rail execution outcome
        boolean failed = ThreadLocalRandom.current().nextDouble() < failureRate;
        if (failed) {
            log.warn("Payment {} failed rail simulation. Executing compensating reversal transfer.", paymentId);
            TransferRequestDto reversal = new TransferRequestDto(
                    railSettlementAccountId,
                    payment.getInternalAccountId(),
                    payment.getAmountCents(),
                    "ext-reverse-" + paymentId,
                    "Reversal of failed " + rail + " payment"
            );
            ledgerClient.postTransfer(reversal);

            payment.setStatus("RETURNED");
            payment.setFailureReason("Simulated rail decline / settlement failure");
            payment.setCompletedAt(Instant.now());
            paymentRepository.save(payment);

            redisTemplate.delete(tickKey);

            result.put("processed", true);
            result.put("status", "RETURNED");
            result.put("failureReason", payment.getFailureReason());
            return result;
        }

        log.info("Payment {} completed successfully via simulated rail {}", paymentId, rail);
        payment.setStatus("COMPLETED");
        payment.setCompletedAt(Instant.now());
        paymentRepository.save(payment);

        redisTemplate.delete(tickKey);

        result.put("processed", true);
        result.put("status", "COMPLETED");
        return result;
    }
}
