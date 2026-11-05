package com.Bank_website.support.service;

import com.Bank_website.security.AuthPrincipal;
import com.Bank_website.support.dto.CreateMessageRequest;
import com.Bank_website.support.dto.CreateTicketRequest;
import com.Bank_website.support.dto.TicketListResponse;
import com.Bank_website.support.dto.TicketMessageResponse;
import com.Bank_website.support.dto.TicketResponse;
import com.Bank_website.support.dto.UpdateTicketRequest;
import com.Bank_website.support.entity.SupportTicket;
import com.Bank_website.support.entity.TicketMessage;
import com.Bank_website.support.exception.ForbiddenException;
import com.Bank_website.support.exception.InvalidStatusTransitionException;
import com.Bank_website.support.exception.ResourceNotFoundException;
import com.Bank_website.support.mapper.TicketMapper;
import com.Bank_website.support.repository.SupportTicketRepository;
import com.Bank_website.support.repository.TicketMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TicketService {

    /** Sender id used for SYSTEM messages (no user behind them). */
    public static final UUID SYSTEM_SENDER = new UUID(0L, 0L);

    /** The only legal transitions: no skipping, no reversing. */
    static final Map<String, String> NEXT_STATUS = Map.of(
            "OPEN", "IN_PROGRESS",
            "IN_PROGRESS", "PENDING_CUSTOMER",
            "PENDING_CUSTOMER", "RESOLVED",
            "RESOLVED", "CLOSED");

    private static final int MAX_PAGE_SIZE = 100;

    private final SupportTicketRepository tickets;
    private final TicketMessageRepository messages;
    private final TicketMapper mapper;
    private final TicketEventPublisher publisher;

    public TicketService(SupportTicketRepository tickets, TicketMessageRepository messages,
                         TicketMapper mapper, TicketEventPublisher publisher) {
        this.tickets = tickets;
        this.messages = messages;
        this.mapper = mapper;
        this.publisher = publisher;
    }

    /** POST /api/tickets: customer id comes from the JWT, never from the body. */
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        UUID customerId = requireCustomerId(currentPrincipal());
        String priority = request.priority() == null ? "MEDIUM" : request.priority();
        return openTicket(customerId, request.category(), priority, request.subject(),
                request.relatedTransactionId(), "Ticket created");
    }

    /** Used by the QStash webhook (no user in the security context). */
    @Transactional
    public UUID createFraudTicket(UUID customerId, UUID transactionId) {
        TicketResponse created = openTicket(customerId, "TRANSACTION_DISPUTE", "HIGH",
                "Fraud flag on transaction " + transactionId, transactionId,
                "Ticket created automatically after a fraud flag on the transaction");
        return created.ticketId();
    }

    @Transactional(readOnly = true)
    public TicketListResponse listTickets(String status, String priority, UUID assignedAgentId, int page, int size) {
        AuthPrincipal principal = currentPrincipal();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(1, Math.min(size, MAX_PAGE_SIZE)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SupportTicket> result;
        if (principal.hasRole("STAFF")) {
            Specification<SupportTicket> spec = (root, query, cb) -> cb.conjunction();
            if (status != null && !status.isBlank()) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
            }
            if (priority != null && !priority.isBlank()) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("priority"), priority));
            }
            if (assignedAgentId != null) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("assignedAgentId"), assignedAgentId));
            }
            result = tickets.findAll(spec, pageable);
        } else {
            result = tickets.findByCustomerId(requireCustomerId(principal), pageable);
        }
        return mapper.toListResponse(result);
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicket(UUID ticketId) {
        AuthPrincipal principal = currentPrincipal();
        SupportTicket ticket = load(ticketId);
        boolean staff = principal.hasRole("STAFF");
        if (!staff) {
            assertOwner(principal, ticket);
        }
        return mapper.toResponse(ticket, visibleMessages(ticketId, staff));
    }

    /** PATCH /api/tickets/{id} (STAFF). Status must advance exactly one step; every transition is logged as a SYSTEM message. */
    @Transactional
    public TicketResponse updateTicket(UUID ticketId, UpdateTicketRequest request) {
        SupportTicket ticket = load(ticketId);
        String previous = ticket.getStatus();

        if (request.priority() != null) {
            ticket.setPriority(request.priority());
        }
        if (request.assignedAgentId() != null) {
            ticket.setAssignedAgentId(request.assignedAgentId());
        }

        boolean transitioned = request.status() != null && !request.status().equals(previous);
        if (transitioned) {
            String allowed = NEXT_STATUS.get(previous);
            if (!request.status().equals(allowed)) {
                throw new InvalidStatusTransitionException("Cannot change status from " + previous + " to "
                        + request.status() + (allowed == null ? " (ticket is " + previous + ")" : " (next allowed: " + allowed + ")"));
            }
            ticket.setStatus(request.status());
            if ("RESOLVED".equals(request.status())) {
                ticket.setResolvedAt(OffsetDateTime.now());
            }
        }

        SupportTicket saved = tickets.save(ticket);
        if (transitioned) {
            addSystemMessage(saved.getTicketId(), "Status changed from " + previous + " to " + saved.getStatus());
            afterCommit(() -> publisher.publishStatusChanged(saved, previous));
        }
        return mapper.toResponse(saved, messages.findByTicketIdOrderByCreatedAtAsc(ticketId));
    }

    /** POST /api/tickets/{id}/messages. Customers: own tickets, not CLOSED, never internal. Staff: may add internal notes. */
    @Transactional
    public TicketMessageResponse addMessage(UUID ticketId, CreateMessageRequest request) {
        AuthPrincipal principal = currentPrincipal();
        SupportTicket ticket = load(ticketId);

        TicketMessage message = new TicketMessage();
        message.setTicketId(ticketId);
        message.setMessageBody(request.messageBody());

        if (principal.hasRole("STAFF")) {
            message.setSenderId(parseUserId(principal));
            message.setSenderType("AGENT");
            message.setInternalNote(Boolean.TRUE.equals(request.isInternalNote()));
        } else {
            assertOwner(principal, ticket);
            if ("CLOSED".equals(ticket.getStatus())) {
                throw new ForbiddenException("Ticket is closed");
            }
            message.setSenderId(principal.customerId());
            message.setSenderType("CUSTOMER");
            message.setInternalNote(false); // forced: customers cannot create internal notes
        }
        return mapper.toMessageResponse(messages.save(message));
    }

    // ---------------------------------------------------------------- helpers

    private TicketResponse openTicket(UUID customerId, String category, String priority, String subject,
                                      UUID relatedTransactionId, String firstMessage) {
        SupportTicket ticket = new SupportTicket();
        ticket.setCustomerId(customerId);
        ticket.setCategory(category);
        ticket.setPriority(priority);
        ticket.setStatus("OPEN");
        ticket.setSubject(subject);
        ticket.setRelatedTransactionId(relatedTransactionId);
        SupportTicket saved = tickets.saveAndFlush(ticket);

        TicketMessage first = addSystemMessage(saved.getTicketId(), firstMessage);
        afterCommit(() -> publisher.publishTicketCreated(saved));
        return mapper.toResponse(saved, List.of(first));
    }

    private TicketMessage addSystemMessage(UUID ticketId, String body) {
        TicketMessage message = new TicketMessage();
        message.setTicketId(ticketId);
        message.setSenderId(SYSTEM_SENDER);
        message.setSenderType("SYSTEM");
        message.setMessageBody(body);
        message.setInternalNote(false);
        return messages.save(message);
    }

    private List<TicketMessage> visibleMessages(UUID ticketId, boolean staff) {
        List<TicketMessage> all = messages.findByTicketIdOrderByCreatedAtAsc(ticketId);
        return staff ? all : all.stream().filter(m -> !m.isInternalNote()).toList();
    }

    private SupportTicket load(UUID ticketId) {
        return tickets.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found: " + ticketId));
    }

    private void assertOwner(AuthPrincipal principal, SupportTicket ticket) {
        UUID customerId = requireCustomerId(principal);
        if (!customerId.equals(ticket.getCustomerId())) {
            throw new ForbiddenException("This ticket belongs to another customer");
        }
    }

    private AuthPrincipal currentPrincipal() {
        AuthPrincipal principal = AuthPrincipal.of(SecurityContextHolder.getContext().getAuthentication());
        if (principal == null) {
            throw new ForbiddenException("Authentication required");
        }
        return principal;
    }

    private UUID requireCustomerId(AuthPrincipal principal) {
        if (principal.customerId() == null) {
            throw new ForbiddenException("Token has no customer id");
        }
        return principal.customerId();
    }

    private UUID parseUserId(AuthPrincipal principal) {
        try {
            return UUID.fromString(principal.subject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ForbiddenException("Token subject is not a valid user id");
        }
    }

    /** Events go out only after the database commit; without a transaction (unit tests) they run immediately. */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
