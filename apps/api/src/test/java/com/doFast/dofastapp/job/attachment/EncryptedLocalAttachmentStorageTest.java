package com.doFast.dofastapp.job.attachment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncryptedLocalAttachmentStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesCiphertextAndRestoresOriginalBytes() throws Exception {
        EncryptedLocalAttachmentStorage storage = storageWithKey('a');
        storage.initialize();
        String storageKey = UUID.randomUUID().toString();
        byte[] plaintext = "LOYALTY_CARD_SECRET_MARKER".getBytes(StandardCharsets.UTF_8);

        storage.store(storageKey, plaintext);

        byte[] rawDiskBytes = Files.readAllBytes(tempDir.resolve(storageKey + ".bin"));
        assertFalse(new String(rawDiskBytes, StandardCharsets.ISO_8859_1).contains("LOYALTY_CARD_SECRET_MARKER"));
        assertArrayEquals(plaintext, storage.read(storageKey));
    }

    @Test
    void wrongEncryptionKeyCannotReadStoredObject() {
        EncryptedLocalAttachmentStorage writer = storageWithKey('a');
        writer.initialize();
        String storageKey = UUID.randomUUID().toString();
        writer.store(storageKey, "private".getBytes(StandardCharsets.UTF_8));

        EncryptedLocalAttachmentStorage wrongKeyReader = storageWithKey('b');
        wrongKeyReader.initialize();

        assertThrows(IllegalStateException.class, () -> wrongKeyReader.read(storageKey));
    }

    @Test
    void storageKeyCannotEscapeConfiguredRoot() {
        EncryptedLocalAttachmentStorage storage = storageWithKey('a');
        storage.initialize();

        assertThrows(IllegalArgumentException.class, () -> storage.store("../../outside", new byte[]{1}));
    }

    private EncryptedLocalAttachmentStorage storageWithKey(char fill) {
        String raw = String.valueOf(fill).repeat(32);
        String base64 = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.US_ASCII));
        return new EncryptedLocalAttachmentStorage(tempDir.toString(), base64);
    }
}
