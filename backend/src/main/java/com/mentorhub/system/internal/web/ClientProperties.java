package com.mentorhub.system.internal.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The frontend's OIDC settings returned by {@code GET /api/v1/system/client-config}. Values that are public by nature;
 * never a secret. Startup fails when they are missing.
 */
@ConfigurationProperties("mentorhub.security.client")
@Validated
record ClientProperties(@NotNull URI issuerUri, @NotBlank String clientId) {}
