package com.Bank_website.loan.mapper;

import com.Bank_website.loan.dto.LoanApplicationResponse;
import com.Bank_website.loan.dto.LoanProductResponse;
import com.Bank_website.loan.dto.RepaymentResponse;
import com.Bank_website.loan.entity.LoanApplication;
import com.Bank_website.loan.entity.LoanProduct;
import com.Bank_website.loan.entity.LoanRepayment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LoanMapper {

    public LoanProductResponse toProductResponse(LoanProduct product) {
        if (product == null) {
            return null;
        }
        return new LoanProductResponse(
                product.getProductId(),
                product.getProductCode(),
                product.getProductName(),
                product.getMinAmountCents(),
                product.getMaxAmountCents(),
                product.getMinTenureMonths(),
                product.getMaxTenureMonths(),
                product.getBaseInterestRateBps(),
                product.getIsActive(),
                product.getCreatedAt()
        );
    }

    public LoanApplicationResponse toApplicationResponse(LoanApplication application, List<LoanRepayment> repayments) {
        if (application == null) {
            return null;
        }
        List<RepaymentResponse> repaymentResponses = repayments != null
                ? repayments.stream().map(this::toRepaymentResponse).toList()
                : null;

        return new LoanApplicationResponse(
                application.getApplicationId(),
                application.getCustomerId(),
                application.getProduct() != null ? application.getProduct().getProductCode() : null,
                application.getProduct() != null ? application.getProduct().getProductName() : null,
                application.getRequestedAmountCents(),
                application.getRequestedTenureMonths(),
                application.getStatus(),
                application.getApprovedAmountCents(),
                application.getApprovedInterestRateBps(),
                application.getApprovedTenureMonths(),
                application.getRejectionReason(),
                application.getReviewedBy(),
                application.getReviewedAt(),
                application.getDisbursedAt(),
                application.getDisbursementTransactionId(),
                application.getCreatedAt(),
                repaymentResponses
        );
    }

    public LoanApplicationResponse toApplicationResponse(LoanApplication application) {
        return toApplicationResponse(application, null);
    }

    public RepaymentResponse toRepaymentResponse(LoanRepayment repayment) {
        if (repayment == null) {
            return null;
        }
        return new RepaymentResponse(
                repayment.getRepaymentId(),
                repayment.getApplication() != null ? repayment.getApplication().getApplicationId() : null,
                repayment.getDueDate(),
                repayment.getPrincipalDueCents(),
                repayment.getInterestDueCents(),
                repayment.getTotalDueCents(),
                repayment.getPaidAt(),
                repayment.getPaymentTransactionId(),
                repayment.getStatus(),
                repayment.getCreatedAt()
        );
    }
}
