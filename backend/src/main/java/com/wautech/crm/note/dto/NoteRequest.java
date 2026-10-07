package com.wautech.crm.note.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record NoteRequest(
        UUID companyId,
        UUID contactId,
        UUID leadId,
        UUID opportunityId,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 10000) String body
) {
    @AssertTrue(message = "Exactly one CRM record ID must be provided")
    public boolean isExactlyOneParentRecord() {
        int parentCount = (companyId == null ? 0 : 1) + (contactId == null ? 0 : 1)
                + (leadId == null ? 0 : 1) + (opportunityId == null ? 0 : 1);
        return parentCount == 1;
    }
}
