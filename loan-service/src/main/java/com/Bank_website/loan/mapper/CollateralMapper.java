package com.Bank_website.loan.mapper;

import com.Bank_website.loan.dto.CollateralResponse;
import com.Bank_website.loan.dto.CollectionActionResponse;
import com.Bank_website.loan.entity.CollectionAction;
import com.Bank_website.loan.entity.LoanCollateral;
import org.springframework.stereotype.Component;

@Component
public class CollateralMapper {

    public CollateralResponse toResponse(LoanCollateral collateral) {
        if (collateral == null) {
            return null;
        }
        return new CollateralResponse(
                collateral.getCollateralId(),
                collateral.getApplication() != null ? collateral.getApplication().getApplicationId() : null,
                collateral.getCollateralType(),
                collateral.getDescription(),
                collateral.getEstimatedValueCents(),
                collateral.getValuationDate(),
                collateral.getStatus(),
                collateral.getCreatedAt()
        );
    }

    public CollectionActionResponse toActionResponse(CollectionAction action) {
        if (action == null) {
            return null;
        }
        return new CollectionActionResponse(
                action.getActionId(),
                action.getApplication() != null ? action.getApplication().getApplicationId() : null,
                action.getRepayment() != null ? action.getRepayment().getRepaymentId() : null,
                action.getActionType(),
                action.getPerformedBy(),
                action.getNotes(),
                action.getCreatedAt()
        );
    }
}
