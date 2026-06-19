package com.churnguard.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

/**
 * Custom principal stored in SecurityContext after JWT validation.
 * Carries tenantId so controllers can scope queries to the correct tenant
 * without an extra DB lookup on every request.
 */
@Getter
@AllArgsConstructor
public class ChurnGuardUserDetails {
    private final UUID userId;
    private final String email;
    private final UUID tenantId;
    private final String role;
}