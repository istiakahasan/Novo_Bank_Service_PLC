package com.Bank_website.Novo_Bank_Service_PLC.service;

import com.Bank_website.Novo_Bank_Service_PLC.dto.CreateCustomerRequest;
import com.Bank_website.Novo_Bank_Service_PLC.dto.CustomerResponse;
import com.Bank_website.Novo_Bank_Service_PLC.dto.KycUpdateRequest;
import com.Bank_website.Novo_Bank_Service_PLC.entity.Customer;
import com.Bank_website.Novo_Bank_Service_PLC.event.EventPublisher;
import com.Bank_website.Novo_Bank_Service_PLC.exception.DuplicateResourceException;
import com.Bank_website.Novo_Bank_Service_PLC.exception.ResourceNotFoundException;
import com.Bank_website.Novo_Bank_Service_PLC.mapper.CustomerMapper;
import com.Bank_website.Novo_Bank_Service_PLC.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final CustomerMapper mapper;
    private final EventPublisher eventPublisher;

    public CustomerService(CustomerRepository repository, CustomerMapper mapper, EventPublisher eventPublisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        if (repository.existsByMobileNo(request.mobileNo())) {
            throw new DuplicateResourceException("Mobile number already registered");
        }
        if (repository.existsByIdNumber(request.idNumber())) {
            throw new DuplicateResourceException("ID number already registered");
        }

        Customer customer = mapper.toEntity(request);
        customer.setCustomerNumber(String.format("CIF-%06d", repository.count() + 1));
        customer.setKycStatus("pending");
        customer.setAmlRiskRating("low");
        customer.setStatus("active");
        OffsetDateTime now = OffsetDateTime.now();
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);

        Customer saved = repository.save(customer);
        eventPublisher.publishCustomerCreated(saved);
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        return mapper.toResponse(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id)));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getByMobileNo(String mobileNo) {
        return mapper.toResponse(repository.findByMobileNo(mobileNo)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found for mobile: " + mobileNo)));
    }

    @Transactional
    public CustomerResponse updateKyc(UUID id, KycUpdateRequest request) {
        Customer customer = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));

        if (!"pending".equals(customer.getKycStatus())) {
            throw new IllegalStateException(
                    "KYC already finalised with status '" + customer.getKycStatus() + "'");
        }

        customer.setKycStatus(request.kycStatus());
        customer.setUpdatedAt(OffsetDateTime.now());
        Customer saved = repository.save(customer);

        if ("verified".equals(request.kycStatus())) {
            eventPublisher.publishKycVerified(saved, request.verifiedBy());
        }
        return mapper.toResponse(saved);
    }
}
