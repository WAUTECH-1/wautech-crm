package com.wautech.crm.identity.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/** Email/login identifiers are deliberately immutable in self-service. */
public record SelfProfileRequest(@NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName) { }
