package com.assistia.dto.auth;

public record CsrfTokenResponse(String headerName, String token) {
}
