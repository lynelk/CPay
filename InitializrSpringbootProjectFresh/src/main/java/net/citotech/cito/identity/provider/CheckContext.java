package net.citotech.cito.identity.provider;

import net.citotech.cito.identity.domain.ValidationCapability;

/**
 * Eligibility context for provider selection (ISO domain mapping: identity/provider). The router
 * filters adapters by capability, country, and merchant entitlement before any scoring.
 */
public record CheckContext(
        long merchantId,
        ValidationCapability capability,
        String countryCode,
        String documentType) {}
