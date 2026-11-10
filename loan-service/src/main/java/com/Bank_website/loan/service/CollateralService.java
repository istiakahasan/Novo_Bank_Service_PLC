package com.Bank_website.loan.service;

import com.Bank_website.loan.dto.AddCollateralRequest;
import com.Bank_website.loan.dto.CollateralResponse;
import com.Bank_website.loan.entity.LoanApplication;
import com.Bank_website.loan.entity.LoanCollateral;
import com.Bank_website.loan.exception.ForbiddenException;
import com.Bank_website.loan.exception.InvalidLoanStateException;
import com.Bank_website.loan.exception.ProductValidationException;
import com.Bank_website.loan.exception.ResourceNotFoundException;
import com.Bank_website.loan.mapper.CollateralMapper;
import com.Bank_website.loan.repository.LoanApplicationRepository;
import com.Bank_website.loan.repository.LoanCollateralRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CollateralService {

    private static final Set<String> ALLOWED_PRODUCTS = Set.of("AUTO", "HOME");
    private static final Set<String> ALLOWED_APP_STATUSES = Set.of("APPROVED", "DISBURSED");
    private static final Set<String> ALLOWED_COLLATERAL_TYPES = Set.of(
            "PROPERTY", "VEHICLE", "FIXED_DEPOSIT", "SECURITIES", "OTHER"
    );

    private final LoanCollateralRepository collateralRepository;
    private final LoanApplicationRepository applicationRepository;
    private final CollateralMapper collateralMapper;

    public CollateralService(LoanCollateralRepository collateralRepository,
                             LoanApplicationRepository applicationRepository,
                             CollateralMapper collateralMapper) {
        this.collateralRepository = collateralRepository;
        this.applicationRepository = applicationRepository;
        this.collateralMapper = collateralMapper;
    }

    @Transactional
    public CollateralResponse addCollateral(UUID applicationId, AddCollateralRequest request) {
        LoanApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found: " + applicationId));

        if (!ALLOWED_APP_STATUSES.contains(application.getStatus())) {
            throw new InvalidLoanStateException("Collateral can only be added to APPROVED or DISBURSED loans (current: " + application.getStatus() + ")");
        }

        String productCode = application.getProduct() != null ? application.getProduct().getProductCode() : null;
        if (productCode == null || !ALLOWED_PRODUCTS.contains(productCode.toUpperCase())) {
            throw new ProductValidationException("Collateral is only permitted for AUTO and HOME loans (product: " + productCode + ")");
        }

        String type = request.collateralType().toUpperCase();
        if (!ALLOWED_COLLATERAL_TYPES.contains(type)) {
            throw new IllegalArgumentException("Invalid collateral type: " + request.collateralType());
        }

        LoanCollateral collateral = new LoanCollateral();
        collateral.setApplication(application);
        collateral.setCollateralType(type);
        collateral.setDescription(request.description());
        collateral.setEstimatedValueCents(request.estimatedValueCents());
        collateral.setValuationDate(request.valuationDate());
        collateral.setStatus("PLEDGED");

        collateral = collateralRepository.save(collateral);
        return collateralMapper.toResponse(collateral);
    }

    @Transactional(readOnly = true)
    public List<CollateralResponse> getCollateral(UUID applicationId, UUID customerId, boolean isStaff) {
        LoanApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found: " + applicationId));

        if (!isStaff) {
            if (customerId == null || !application.getCustomerId().equals(customerId)) {
                throw new ForbiddenException("Access denied to loan collateral: " + applicationId);
            }
        }

        List<LoanCollateral> list = collateralRepository.findByApplicationId(applicationId);
        return list.stream().map(collateralMapper::toResponse).toList();
    }

    @Transactional
    public CollateralResponse releaseCollateral(UUID collateralId) {
        LoanCollateral collateral = collateralRepository.findById(collateralId)
                .orElseThrow(() -> new ResourceNotFoundException("Collateral not found: " + collateralId));

        LoanApplication application = collateral.getApplication();
        if (!"CLOSED".equals(application.getStatus())) {
            throw new InvalidLoanStateException("Collateral can only be released when loan application status is CLOSED (current: " + application.getStatus() + ")");
        }

        collateral.setStatus("RELEASED");
        collateral = collateralRepository.save(collateral);
        return collateralMapper.toResponse(collateral);
    }
}
