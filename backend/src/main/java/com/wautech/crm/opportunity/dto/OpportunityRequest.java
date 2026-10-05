package com.wautech.crm.opportunity.dto;

import com.wautech.crm.opportunity.entity.OpportunityStage;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OpportunityRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 10000) String description,
        @DecimalMin("0.0") BigDecimal amount,
        @Pattern(regexp = "[A-Z]{3}") String currency,
        OpportunityStage stage,
        LocalDate expectedCloseDate,
        @NotNull UUID companyId,
        UUID contactId
) {
    @AssertTrue(message = "Amount and currency must be provided together")
    public boolean isAmountAndCurrencyConsistent() {
        return (amount == null) == (currency == null);
    }
}
