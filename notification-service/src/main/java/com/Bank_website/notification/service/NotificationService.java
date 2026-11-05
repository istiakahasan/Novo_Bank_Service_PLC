package com.Bank_website.notification.service;

import com.Bank_website.notification.dto.CustomerCreatedEvent;
import com.Bank_website.notification.dto.CustomerInfo;
import com.Bank_website.notification.dto.NotificationResult;
import com.Bank_website.notification.dto.TransferCompletedEvent;
import com.Bank_website.security.MaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Duration DEDUPE_TTL = Duration.ofHours(24);

    private final JavaMailSender mailSender;
    private final CustomerLookupService lookupService;
    private final RedisTemplate<String, String> redis;
    private final String from;

    public NotificationService(JavaMailSender mailSender,
                               CustomerLookupService lookupService,
                               RedisTemplate<String, String> redis,
                               @Value("${mail.from}") String from) {
        this.mailSender = mailSender;
        this.lookupService = lookupService;
        this.redis = redis;
        this.from = from;
    }

    public NotificationResult notifyTransferCompleted(TransferCompletedEvent event) {
        String ref = event.transactionId().toString();
        String doneKey = "notif_done:" + ref;
        if (Boolean.TRUE.equals(redis.hasKey(doneKey))) {
            return new NotificationResult(ref, false, false, true);
        }

        Optional<CustomerInfo> customer = lookupService.findByAccount(event.fromAccount());
        if (customer.isEmpty()) {
            log.warn("No customer for account {}, nothing to notify (txn {})", event.fromAccount(), ref);
            redis.opsForValue().set(doneKey, "1", DEDUPE_TTL);
            return new NotificationResult(ref, false, false, false);
        }

        boolean emailSent = sendTransferEmail(customer.get(), event);
        log.info("SMS to {}: Txn {} of {} completed", MaskingUtil.maskMobile(customer.get().mobileNo()), ref, money(event.amountCents()));
        // Mark done only after the email went out; a mail failure throws and QStash retries.
        redis.opsForValue().set(doneKey, "1", DEDUPE_TTL);
        return new NotificationResult(ref, emailSent, true, false);
    }

    public NotificationResult notifyCustomerCreated(CustomerCreatedEvent event) {
        String ref = event.customerId().toString();
        String doneKey = "welcome_done:" + ref;
        if (Boolean.TRUE.equals(redis.hasKey(doneKey))) {
            return new NotificationResult(ref, false, false, true);
        }
        boolean emailSent = sendWelcomeEmail(event);
        redis.opsForValue().set(doneKey, "1", DEDUPE_TTL);
        return new NotificationResult(ref, emailSent, false, false);
    }

    /** @return true if an email was sent, false if the customer has no email address. */
    public boolean sendTransferEmail(CustomerInfo customer, TransferCompletedEvent event) {
        if (customer.email() == null || customer.email().isBlank()) {
            log.info("Customer {} has no email; skipping transfer email", customer.id());
            return false;
        }
        send(customer.email(), "Transfer completed",
                "Hello " + customer.displayName() + ",\n\n"
                        + "You sent " + money(event.amountCents()) + " to account " + event.toAccount() + ".\n"
                        + "Reference: " + event.transactionId() + "\n\n"
                        + "If you did not make this transfer, contact Novo Bank immediately.");
        return true;
    }

    /** @return true if an email was sent, false if the customer has no email address. */
    public boolean sendWelcomeEmail(CustomerCreatedEvent event) {
        if (event.email() == null || event.email().isBlank()) {
            log.info("Customer {} has no email; skipping welcome email", event.customerId());
            return false;
        }
        send(event.email(), "Welcome to Novo Bank",
                "Hello " + (event.displayName() == null ? "" : event.displayName()) + ",\n\n"
                        + "Welcome to Novo Bank. Your customer number is " + event.customerNumber() + ".\n"
                        + "Your KYC verification is in progress; we will notify you when it completes.");
        return true;
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.info("EMAIL sent to {} subject='{}'", MaskingUtil.maskEmail(to), subject);
    }

    static String money(long cents) {
        return String.format("$%d.%02d", cents / 100, cents % 100);
    }
}
