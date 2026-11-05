package com.Bank_website.support;

import com.Bank_website.security.AuthPrincipal;
import com.Bank_website.support.dto.CreateMessageRequest;
import com.Bank_website.support.dto.CreateTicketRequest;
import com.Bank_website.support.dto.TicketResponse;
import com.Bank_website.support.dto.UpdateTicketRequest;
import com.Bank_website.support.entity.SupportTicket;
import com.Bank_website.support.entity.TicketMessage;
import com.Bank_website.support.exception.ForbiddenException;
import com.Bank_website.support.exception.InvalidStatusTransitionException;
import com.Bank_website.support.mapper.TicketMapper;
import com.Bank_website.support.repository.SupportTicketRepository;
import com.Bank_website.support.repository.TicketMessageRepository;
import com.Bank_website.support.service.TicketEventPublisher;
import com.Bank_website.support.service.TicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"unchecked", "rawtypes"})
class TicketServiceTest {

    private final SupportTicketRepository tickets = mock(SupportTicketRepository.class);
    private final TicketMessageRepository messages = mock(TicketMessageRepository.class);
    private final TicketEventPublisher publisher = mock(TicketEventPublisher.class);
    private final TicketService service = new TicketService(tickets, messages, new TicketMapper(), publisher);

    private final UUID customerId = UUID.randomUUID();
    private final UUID otherCustomerId = UUID.randomUUID();
    private final UUID staffId = UUID.randomUUID();
    private final List<TicketMessage> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(tickets.saveAndFlush(any(SupportTicket.class))).thenAnswer(i -> {
            SupportTicket t = i.getArgument(0);
            if (t.getTicketId() == null) {
                t.setTicketId(UUID.randomUUID());
            }
            return t;
        });
        when(tickets.save(any(SupportTicket.class))).thenAnswer(i -> i.getArgument(0));
        when(messages.save(any(TicketMessage.class))).thenAnswer(i -> {
            TicketMessage m = i.getArgument(0);
            m.setMessageId(UUID.randomUUID());
            stored.add(m);
            return m;
        });
        when(messages.findByTicketIdOrderByCreatedAtAsc(any())).thenAnswer(i -> List.copyOf(stored));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void loginCustomer(UUID cid) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(UUID.randomUUID().toString(), cid, Set.of("CUSTOMER")), null,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
    }

    private void loginStaff() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthPrincipal(staffId.toString(), null, Set.of("STAFF")), null,
                List.of(new SimpleGrantedAuthority("ROLE_STAFF"))));
    }

    private SupportTicket ticket(String status) {
        SupportTicket t = new SupportTicket();
        t.setTicketId(UUID.randomUUID());
        t.setCustomerId(customerId);
        t.setCategory("GENERAL_INQUIRY");
        t.setPriority("MEDIUM");
        t.setStatus(status);
        t.setSubject("Help");
        when(tickets.findById(t.getTicketId())).thenReturn(Optional.of(t));
        return t;
    }

    private TicketMessage message(UUID ticketId, boolean internal) {
        TicketMessage m = new TicketMessage();
        m.setMessageId(UUID.randomUUID());
        m.setTicketId(ticketId);
        m.setSenderId(staffId);
        m.setSenderType("AGENT");
        m.setMessageBody(internal ? "internal" : "public");
        m.setInternalNote(internal);
        stored.add(m);
        return m;
    }

    @Test
    void createTicketTakesCustomerIdFromTheJwtAndWritesTheFirstSystemMessage() {
        loginCustomer(customerId);

        TicketResponse r = service.createTicket(new CreateTicketRequest("GENERAL_INQUIRY", null, "Card question", null));

        assertEquals(customerId, r.customerId());
        assertEquals("OPEN", r.status());
        assertEquals("MEDIUM", r.priority());
        assertEquals(1, r.messages().size());
        assertEquals("SYSTEM", r.messages().get(0).senderType());
        assertEquals("Ticket created", r.messages().get(0).messageBody());
        verify(publisher).publishTicketCreated(any(SupportTicket.class));
    }

    @Test
    void customerCannotReadAnotherCustomersTicket() {
        SupportTicket t = ticket("OPEN");
        loginCustomer(otherCustomerId);
        assertThrows(ForbiddenException.class, () -> service.getTicket(t.getTicketId()));
    }

    @Test
    void internalNotesAreHiddenFromCustomersAndVisibleToStaff() {
        SupportTicket t = ticket("IN_PROGRESS");
        message(t.getTicketId(), false);
        message(t.getTicketId(), true);

        loginCustomer(customerId);
        assertEquals(1, service.getTicket(t.getTicketId()).messages().size());

        loginStaff();
        assertEquals(2, service.getTicket(t.getTicketId()).messages().size());
    }

    @Test
    void validTransitionAdvancesOneStepLogsASystemMessageAndPublishes() {
        SupportTicket t = ticket("OPEN");
        loginStaff();

        TicketResponse r = service.updateTicket(t.getTicketId(), new UpdateTicketRequest("IN_PROGRESS", staffId, "HIGH"));

        assertEquals("IN_PROGRESS", r.status());
        assertEquals(staffId, r.assignedAgentId());
        assertEquals("HIGH", r.priority());
        assertTrue(stored.stream().anyMatch(m -> m.getMessageBody().equals("Status changed from OPEN to IN_PROGRESS")
                && m.getSenderType().equals("SYSTEM")));
        verify(publisher).publishStatusChanged(any(SupportTicket.class), org.mockito.ArgumentMatchers.eq("OPEN"));
    }

    @Test
    void skippingAndReversingAreRejected() {
        SupportTicket open = ticket("OPEN");
        SupportTicket inProgress = ticket("IN_PROGRESS");
        SupportTicket closed = ticket("CLOSED");
        loginStaff();

        assertThrows(InvalidStatusTransitionException.class,
                () -> service.updateTicket(open.getTicketId(), new UpdateTicketRequest("RESOLVED", null, null)));
        assertThrows(InvalidStatusTransitionException.class,
                () -> service.updateTicket(inProgress.getTicketId(), new UpdateTicketRequest("OPEN", null, null)));
        assertThrows(InvalidStatusTransitionException.class,
                () -> service.updateTicket(closed.getTicketId(), new UpdateTicketRequest("RESOLVED", null, null)));
        verify(publisher, never()).publishStatusChanged(any(), any());
    }

    @Test
    void resolvingSetsResolvedAt() {
        SupportTicket t = ticket("PENDING_CUSTOMER");
        loginStaff();
        TicketResponse r = service.updateTicket(t.getTicketId(), new UpdateTicketRequest("RESOLVED", null, null));
        assertNotNull(r.resolvedAt());
        SupportTicket other = ticket("IN_PROGRESS");
        assertNull(service.updateTicket(other.getTicketId(), new UpdateTicketRequest("PENDING_CUSTOMER", null, null)).resolvedAt());
    }

    @Test
    void customerMessagesAreNeverInternalAndBlockedOnClosedTickets() {
        SupportTicket t = ticket("OPEN");
        SupportTicket closed = ticket("CLOSED");
        loginCustomer(customerId);

        var posted = service.addMessage(t.getTicketId(), new CreateMessageRequest("hello", true));
        assertFalse(posted.isInternalNote());
        assertEquals("CUSTOMER", posted.senderType());
        assertEquals(customerId, posted.senderId());

        assertThrows(ForbiddenException.class,
                () -> service.addMessage(closed.getTicketId(), new CreateMessageRequest("anyone there?", false)));
    }

    @Test
    void staffCanAddInternalNotes() {
        SupportTicket t = ticket("OPEN");
        loginStaff();
        var posted = service.addMessage(t.getTicketId(), new CreateMessageRequest("call the customer", true));
        assertTrue(posted.isInternalNote());
        assertEquals("AGENT", posted.senderType());
        assertEquals(staffId, posted.senderId());
    }

    @Test
    void customersListOnlyTheirOwnTicketsAndStaffUseFilters() {
        Page<SupportTicket> page = new PageImpl<>(List.of(ticket("OPEN")));
        when(tickets.findByCustomerId(any(UUID.class), any(Pageable.class))).thenReturn(page);
        when(tickets.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);

        loginCustomer(customerId);
        service.listTickets("CLOSED", null, null, 0, 20);
        ArgumentCaptor<UUID> cid = ArgumentCaptor.forClass(UUID.class);
        verify(tickets).findByCustomerId(cid.capture(), any(Pageable.class));
        assertEquals(customerId, cid.getValue());
        verify(tickets, never()).findAll(any(Specification.class), any(Pageable.class));

        loginStaff();
        service.listTickets("OPEN", "HIGH", UUID.randomUUID(), 0, 20);
        verify(tickets).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void fraudTicketIsAHighPriorityDisputeLinkedToTheTransaction() {
        UUID txn = UUID.randomUUID();
        UUID id = service.createFraudTicket(customerId, txn);
        assertNotNull(id);
        ArgumentCaptor<SupportTicket> captor = ArgumentCaptor.forClass(SupportTicket.class);
        verify(tickets).saveAndFlush(captor.capture());
        assertEquals("TRANSACTION_DISPUTE", captor.getValue().getCategory());
        assertEquals("HIGH", captor.getValue().getPriority());
        assertEquals("Fraud flag on transaction " + txn, captor.getValue().getSubject());
        assertEquals(txn, captor.getValue().getRelatedTransactionId());
        assertEquals("SYSTEM", stored.get(0).getSenderType());
    }
}
