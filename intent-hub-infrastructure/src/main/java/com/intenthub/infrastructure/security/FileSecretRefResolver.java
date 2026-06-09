package com.intenthub.infrastructure.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Optional;

public class FileSecretRefResolver implements SecretRefResolver {
    private final Path rootDirectory;

    public FileSecretRefResolver(Path rootDirectory) {
        this.rootDirectory = rootDirectory == null ? null : rootDirectory.toAbsolutePath().normalize();
    }

    @Override
    public Optional<String> resolve(String ref) {
        if (rootDirectory == null || ref == null || ref.isBlank()) {
            return Optional.empty();
        }
        Path secretPath = resolveSafePath(ref.trim());
        if (secretPath == null || !Files.isRegularFile(secretPath)) {
            return Optional.empty();
        }
        try {
            String value = Files.readString(secretPath, StandardCharsets.UTF_8).trim();
            return value.isBlank() ? Optional.empty() : Optional.of(value);
        } catch (IOException ex) {
            return Optional.empty();
        }
    }

    private Path resolveSafePath(String ref) {
        try {
            Path candidate = rootDirectory.resolve(ref).normalize();
            return candidate.startsWith(rootDirectory) ? candidate : null;
        } catch (InvalidPathException ex) {
            return null;
        }
    }
}
