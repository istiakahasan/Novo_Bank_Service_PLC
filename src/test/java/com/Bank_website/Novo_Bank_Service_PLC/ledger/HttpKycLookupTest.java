package com.Bank_website.Novo_Bank_Service_PLC.ledger;

import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.ResourceNotFoundException;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.exception.ServiceUnavailableException;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.service.HttpKycLookup;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpKycLookupTest {

    private final UUID account = UUID.randomUUID();

    private HttpKycLookup lookupReturning(org.springframework.test.web.client.ResponseCreator response) {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://customer.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://customer.test/api/customers/by-account/" + account)).andRespond(response);
        return new HttpKycLookup(builder.build());
    }

    @Test
    void readsKycStatusFromCustomerService() {
        HttpKycLookup lookup = lookupReturning(withSuccess("{\"kycStatus\":\"verified\",\"id\":\"x\"}", MediaType.APPLICATION_JSON));
        assertEquals("verified", lookup.statusByAccount(account));
    }

    @Test
    void notFoundBecomesResourceNotFound() {
        HttpKycLookup lookup = lookupReturning(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(ResourceNotFoundException.class, () -> lookup.statusByAccount(account));
    }

    @Test
    void serverErrorBecomesServiceUnavailable() {
        HttpKycLookup lookup = lookupReturning(withStatus(HttpStatus.BAD_GATEWAY));
        assertThrows(ServiceUnavailableException.class, () -> lookup.statusByAccount(account));
    }

    @Test
    void connectionFailureBecomesServiceUnavailable() {
        HttpKycLookup lookup = lookupReturning(request -> {
            throw new java.net.SocketTimeoutException("Read timed out");
        });
        assertThrows(ServiceUnavailableException.class, () -> lookup.statusByAccount(account));
    }
}
