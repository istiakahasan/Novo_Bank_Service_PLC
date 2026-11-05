package com.Bank_website.Novo_Bank_Service_PLC;

import com.Bank_website.Novo_Bank_Service_PLC.dto.CreateCustomerRequest;
import com.Bank_website.Novo_Bank_Service_PLC.dto.CustomerResponse;
import com.Bank_website.Novo_Bank_Service_PLC.dto.KycUpdateRequest;
import com.Bank_website.Novo_Bank_Service_PLC.entity.Customer;
import com.Bank_website.Novo_Bank_Service_PLC.event.EventPublisher;
import com.Bank_website.Novo_Bank_Service_PLC.exception.DuplicateResourceException;
import com.Bank_website.Novo_Bank_Service_PLC.mapper.CustomerMapper;
import com.Bank_website.Novo_Bank_Service_PLC.repository.CustomerRepository;
import com.Bank_website.Novo_Bank_Service_PLC.service.CustomerService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NovoBankServicePlcApplicationTests {

    private final CustomerRepository repo = mock(CustomerRepository.class);
    private final EventPublisher events = mock(EventPublisher.class);
    private final CustomerService service = new CustomerService(repo, new CustomerMapper(), events,
            mock(com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.AccountRepository.class),
            mock(com.Bank_website.Novo_Bank_Service_PLC.service.LedgerClient.class));

    private CreateCustomerRequest request() {
        return new CreateCustomerRequest("Ada", "Lovelace", LocalDate.of(1990, 1, 1),
                "01712345678", null, "nid", "1234567890", "Dhaka");
    }

    @Test
    void createsCustomerWithDefaultsAndPublishesEvent() {
        when(repo.count()).thenReturn(41L);
        when(repo.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        CustomerResponse r = service.createCustomer(request());

        assertEquals("CIF-000042", r.customerNumber());
        assertEquals("pending", r.kycStatus());
        assertEquals("Ada Lovelace", r.displayName());
        verify(events).publishCustomerCreated(any(Customer.class));
    }

    @Test
    void rejectsDuplicateMobile() {
        when(repo.existsByMobileNo("01712345678")).thenReturn(true);
        assertThrows(DuplicateResourceException.class, () -> service.createCustomer(request()));
    }

    @Test
    void kycOnlyFromPending() {
        Customer c = new Customer();
        c.setKycStatus("verified");
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(c));
        assertThrows(IllegalStateException.class,
                () -> service.updateKyc(id, new KycUpdateRequest("rejected", "officer1")));
    }

    @Test
    void verifiedKycPublishesEvent() {
        Customer c = new Customer();
        c.setKycStatus("pending");
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(c));
        when(repo.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        service.updateKyc(id, new KycUpdateRequest("verified", "officer1"));

        verify(events).publishKycVerified(c, "officer1");
    }
}
