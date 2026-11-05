package com.Bank_website.support;

import com.Bank_website.support.event.TransferCompletedWebhookHandler;
import com.Bank_website.support.event.TransferCompletedWebhookHandler.WebhookResult;
import com.Bank_website.support.repository.SupportTicketRepository;
import com.Bank_website.support.service.TicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WebhookHandlerTest {

    private final SupportTicketRepository tickets = mock(SupportTicketRepository.class);
    private final TicketService ticketService = mock(TicketService.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> ops = mock(ValueOperations.class);

    private final UUID txn = UUID.randomUUID();
    private final UUID account = UUID.randomUUID();
    private final UUID customer = UUID.randomUUID();

    private TransferCompletedWebhookHandler handler(MockRestServiceServer[] serverOut) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://customer.test");
        serverOut[0] = MockRestServiceServer.bindTo(builder).build();
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(true);
        return new TransferCompletedWebhookHandler(new ObjectMapper(), tickets, ticketService, builder.build(), redis);
    }

    private String body(boolean flagged) {
        return "{\"event\":\"transfer.completed\",\"transaction_id\":\"" + txn + "\",\"from_account\":\"" + account
                + "\",\"to_account\":\"" + UUID.randomUUID() + "\",\"amount_cents\":600000,\"fraud_flagged\":" + flagged + "}";
    }

    @Test
    void flaggedTransferCreatesOneTicketForTheResolvedCustomer() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        TransferCompletedWebhookHandler handler = handler(server);
        server[0].expect(requestTo("http://customer.test/api/customers/by-account/" + account))
                .andRespond(withSuccess("{\"id\":\"" + customer + "\",\"kycStatus\":\"verified\"}", MediaType.APPLICATION_JSON));
        UUID ticketId = UUID.randomUUID();
        when(ticketService.createFraudTicket(customer, txn)).thenReturn(ticketId);

        WebhookResult r = handler.handle(body(true));

        assertEquals("created", r.status());
        assertEquals(ticketId, r.ticketId());
        verify(redis).delete("support:txn-lock:" + txn);
    }

    @Test
    void repeatedDeliveryDoesNotCreateAnotherTicket() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        TransferCompletedWebhookHandler handler = handler(server);
        when(tickets.existsByRelatedTransactionId(txn)).thenReturn(true);

        WebhookResult r = handler.handle(body(true));

        assertEquals("duplicate", r.status());
        verify(ticketService, never()).createFraudTicket(any(), any());
        server[0].verify(); // no customer-service call was needed
    }

    @Test
    void concurrentDeliveryIsTurnedAwayByTheRedisLock() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        TransferCompletedWebhookHandler handler = handler(server);
        when(ops.setIfAbsent(any(), any(), any(Duration.class))).thenReturn(false);

        assertEquals("duplicate", handler.handle(body(true)).status());
        verifyNoInteractions(tickets, ticketService);
    }

    @Test
    void unflaggedTransfersAndOtherEventsAreIgnoredWithoutTouchingTheDatabase() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        TransferCompletedWebhookHandler handler = handler(server);

        assertEquals("ignored", handler.handle(body(false)).status());
        assertEquals("ignored", handler.handle("{\"event\":\"customer.created\",\"fraud_flagged\":true}").status());
        assertEquals("ignored", handler.handle("{\"event\":\"ticket.created\"}").status());
        verifyNoInteractions(tickets, ticketService);
    }

    @Test
    void unknownCustomerIsIgnoredSoQStashDoesNotRetryForever() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        TransferCompletedWebhookHandler handler = handler(server);
        server[0].expect(requestTo("http://customer.test/api/customers/by-account/" + account))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertEquals("ignored", handler.handle(body(true)).status());
        verify(ticketService, never()).createFraudTicket(any(), any());
    }

    @Test
    void malformedPayloadIsABadRequest() {
        MockRestServiceServer[] server = new MockRestServiceServer[1];
        TransferCompletedWebhookHandler handler = handler(server);
        assertThrows(IllegalArgumentException.class, () -> handler.handle("not json"));
        assertThrows(IllegalArgumentException.class,
                () -> handler.handle("{\"event\":\"transfer.completed\",\"fraud_flagged\":true}"));
    }
}
