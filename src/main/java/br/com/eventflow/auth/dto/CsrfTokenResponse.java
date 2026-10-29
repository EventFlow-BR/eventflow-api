package br.com.eventflow.auth.dto;

public record CsrfTokenResponse(
        String headerName,
        String token
) {
}
