package com.wautech.crm.contact.dto;

import com.wautech.crm.contact.entity.Contact;

import java.time.Instant;
import java.util.UUID;

public record ContactResponse(
        UUID id,
        UUID companyId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String jobTitle,
        String status,
        Instant createdAt,
        Instant updatedAt,
        boolean archived
) {
    public static ContactResponse from(Contact contact) {
        return new ContactResponse(contact.getId(), contact.getCompany().getId(), contact.getFirstName(),
                contact.getLastName(), contact.getEmail(), contact.getPhone(), contact.getJobTitle(),
                contact.getStatus(), contact.getCreatedAt(), contact.getUpdatedAt(), contact.isArchived());
    }
}
