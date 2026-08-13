/*
 * Copyright (C) 2026 RedoReplicator contributors
 *
 * This file is part of RedoReplicator and is licensed under
 * the GNU Affero General Public License version 3 or later.
 */
package io.github.arlowen.redoreplicator.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class ConfigurationFingerprint {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String calculate(ResolvedConfiguration configuration) {
        Objects.requireNonNull(configuration, "configuration");
        try {
            byte[] canonical = objectMapper.writeValueAsBytes(
                    configuration.configuration());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Normalized configuration cannot be serialized", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
