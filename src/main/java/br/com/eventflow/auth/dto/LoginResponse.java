package br.com.eventflow.auth.dto;

import br.com.eventflow.user.UserRole;

public record LoginResponse(
        Long id,
        String name,
        String email,
        UserRole role
) {
}
