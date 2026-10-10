package com.wautech.crm.identity.dto;

public record CsrfTokenResponse(String headerName, String parameterName, String token) { }
