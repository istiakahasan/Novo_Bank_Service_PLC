package com.Bank_website.Novo_Bank_Service_PLC.service;

import com.Bank_website.Novo_Bank_Service_PLC.dto.AccountSummaryResponse;
import com.Bank_website.Novo_Bank_Service_PLC.dto.CreateCustomerRequest;
import com.Bank_website.Novo_Bank_Service_PLC.dto.CustomerResponse;
import com.Bank_website.Novo_Bank_Service_PLC.dto.KycUpdateRequest;
import com.Bank_website.Novo_Bank_Service_PLC.dto.PaginatedCustomerResponse;
import com.Bank_website.Novo_Bank_Service_PLC.entity.Customer;
import com.Bank_website.Novo_Bank_Service_PLC.event.EventPublisher;
import com.Bank_website.Novo_Bank_Service_PLC.exception.BadRequestException;
import com.Bank_website.Novo_Bank_Service_PLC.exception.DuplicateResourceException;
import com.Bank_website.Novo_Bank_Service_PLC.exception.ResourceNotFoundException;
import com.Bank_website.Novo_Bank_Service_PLC.ledger.repository.AccountRepository;
import com.Bank_website.Novo_Bank_Service_PLC.mapper.CustomerMapper;
import com.Bank_website.Novo_Bank_Service_PLC.repository.CustomerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomerService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> KYC_STATUSES = Set.of("pending", "verified", "rejected", "flagged");
    private static final Set<String> STATUSES = Set.of("active", "dormant", "suspended", "closed");
    private static final Set<String> RISK_TIERS = Set.of("low", "medium", "high");

    private final CustomerRepository repository;
    private final CustomerMapper mapper;
    private final EventPublisher eventPublisher;
    private final AccountRepository accountRepository;
    private final LedgerClient ledgerClient;

    public CustomerService(CustomerRepository repository, CustomerMapper mapper, EventPublisher eventPublisher,
                           AccountRepository accountRepository, LedgerClient ledgerClient) {
        this.repository = repository;
        this.mapper = mapper;
        this.eventPublisher = eventPublisher;
        this.accountRepository = accountRepository;
        this.ledgerClient = ledgerClient;
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

    @Transactional(readOnly = true)
    public CustomerResponse getByAccountId(UUID accountId) {
        UUID customerId = accountRepository.findCustomerIdByAccountId(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + accountId));
        return getById(customerId);
    }

    /**
     * GET /api/customers: optional filters, newest first. Filter values are case-insensitive
     * (PENDING == pending; riskTier maps to aml_risk_rating); size defaults to 20 and is capped at 100.
     */
    @Transactional(readOnly = true)
    public PaginatedCustomerResponse listCustomers(String kycStatus, String status, String riskTier, int page, int size) {
        String kyc = normalise("kycStatus", kycStatus, KYC_STATUSES);
        String customerStatus = normalise("status", status, STATUSES);
        String risk = normalise("riskTier", riskTier, RISK_TIERS);

        Specification<Customer> spec = (root, query, cb) -> cb.conjunction();
        if (kyc != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("kycStatus"), kyc));
        }
        if (customerStatus != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), customerStatus));
        }
        if (risk != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("amlRiskRating"), risk));
        }

        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Page<Customer> result = repository.findAll(spec,
                PageRequest.of(Math.max(page, 0), pageSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<CustomerResponse> content = result.getContent().stream().map(mapper::toResponse).toList();
        return new PaginatedCustomerResponse(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    /**
     * GET /api/customers/{id}/accounts. Deliberately not @Transactional: the Ledger call can take up to 5 s
     * and must not hold a database connection. Ledger down -> ServiceUnavailableException (503).
     */
    public List<AccountSummaryResponse> getAccounts(UUID customerId) {
        if (!repository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer not found: " + customerId);
        }
        return ledgerClient.getAccountsByCustomer(customerId);
    }

    @Transactional
    public CustomerResponse updateAmlRisk(UUID id, String amlRiskRating) {
        Customer customer = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + id));
        customer.setAmlRiskRating(amlRiskRating);
        customer.setUpdatedAt(OffsetDateTime.now());
        return mapper.toResponse(repository.save(customer));
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

    private static String normalise(String name, String value, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String lower = value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(lower)) {
            throw new BadRequestException(name + " must be one of " + String.join(", ", allowed.stream().sorted().toList()).toUpperCase(Locale.ROOT));
        }
        return lower;
    }
}
