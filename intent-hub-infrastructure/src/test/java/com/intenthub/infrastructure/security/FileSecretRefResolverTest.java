package com.intenthub.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FileSecretRefResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesSecretFromMountedFile() throws Exception {
        Files.writeString(tempDir.resolve("MODEL_TOKEN"), "secret-from-file\n");
        FileSecretRefResolver resolver = new FileSecretRefResolver(tempDir);

        assertThat(resolver.resolve("MODEL_TOKEN")).contains("secret-from-file");
    }

    @Test
    void returnsEmptyForBlankMissingOrEmptyFile() throws Exception {
        Files.writeString(tempDir.resolve("EMPTY_TOKEN"), "   ");
        FileSecretRefResolver resolver = new FileSecretRefResolver(tempDir);

        assertThat(resolver.resolve("")).isEmpty();
        assertThat(resolver.resolve("MISSING_TOKEN")).isEmpty();
        assertThat(resolver.resolve("EMPTY_TOKEN")).isEmpty();
    }

    @Test
    void rejectsPathTraversalOutsideRoot() throws Exception {
        Path outside = Files.createTempFile("intent-hub-secret", ".txt");
        Files.writeString(outside, "outside-secret");
        FileSecretRefResolver resolver = new FileSecretRefResolver(tempDir);

        assertThat(resolver.resolve("../" + outside.getFileName())).isEmpty();
    }
}
