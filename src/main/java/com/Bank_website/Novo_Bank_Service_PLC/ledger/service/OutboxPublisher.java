package com.Bank_website.Novo_Bank_Service_PLC.ledger.service;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.entity.OutboxEvent;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.event.EventPublisher;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Transactional-outbox relay. Delivery is at-least-once: a row is marked published only
 * after QStash accepted it, and QStash de-duplicates on the Upstash-Deduplication-Id header.
 */
@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final EventPublisher eventPublisher;

    public OutboxPublisher(OutboxRepository outboxRepository,
                           @org.springframework.beans.factory.annotation.Qualifier("ledgerEventPublisher")
                           EventPublisher eventPublisher) {
        this.outboxRepository = outboxRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollOutbox() {
        List<OutboxEvent> pending = outboxRepository.findByPublishedFalseOrderByCreatedAtAsc();
        for (OutboxEvent event : pending) {
            try {
                eventPublisher.publish(event.getEventType(), event.getPayload(), "outbox-" + event.getId());
                event.setPublished(true);
                outboxRepository.save(event);
            } catch (Exception ex) {
                // Keep order and retry on the next tick; the row stays published=false.
                log.warn("Outbox event {} not delivered, will retry: {}", event.getId(), ex.getMessage());
                return;
            }
        }
    }
}
