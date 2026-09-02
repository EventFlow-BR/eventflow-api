package br.com.eventflow.auth.dto;

public record LoginResult(
        String token,
        LoginResponse user
) {
}
