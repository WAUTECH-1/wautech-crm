package com.wautech.crm.audit.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/** Stable, intentionally small pagination envelope for audit query responses. */
public record AuditEventPageResponse(List<AuditEventResponse> content, int page, int size,
        long totalElements, int totalPages, boolean first, boolean last) {
    public static AuditEventPageResponse from(Page<AuditEventResponse> result) {
        return new AuditEventPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages(), result.isFirst(), result.isLast());
    }
}
