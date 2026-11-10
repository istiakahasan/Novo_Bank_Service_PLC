package com.Bank_website.loan.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CollectionsScheduler {

    private static final Logger log = LoggerFactory.getLogger(CollectionsScheduler.class);

    private final CollectionsService collectionsService;

    public CollectionsScheduler(CollectionsService collectionsService) {
        this.collectionsService = collectionsService;
    }

    @Scheduled(cron = "${collections.daily.cron:0 0 6 * * *}")
    public void scheduleDailyCollections() {
        log.info("Starting daily collections schedule run");
        try {
            int count = collectionsService.runDailyCollections();
            log.info("Daily collections schedule completed: {} reminders issued", count);
        } catch (Exception ex) {
            log.error("Daily collections schedule encountered an error: {}", ex.getMessage(), ex);
        }
    }
}
