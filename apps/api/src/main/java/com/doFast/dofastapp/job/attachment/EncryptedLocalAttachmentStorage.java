package com.doFast.dofastapp.job.attachment;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.regex.Pattern;

@Component
public class EncryptedLocalAttachmentStorage implements AttachmentStorage {

    private static final Logger log = LoggerFactory.getLogger(EncryptedLocalAttachmentStorage.class);
    private static final Pattern SAFE_KEY = Pattern.compile("[0-9a-f-]{36}");
    private static final byte FORMAT_VERSION = 1;
    private static final int NONCE_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final Path root;
    private final SecretKeySpec encryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public EncryptedLocalAttachmentStorage(
            @Value("${dofast.attachments.storage-root:./data/attachments}") String root,
            @Value("${dofast.attachments.encryption-key-base64:ZG9mYXN0LWxvY2FsLWF0dGFjaG1lbnQta2V5LTIwMjY=}") String encryptionKeyBase64
    ) {
        this.root = Path.of(root).toAbsolutePath().normalize();
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encryptionKeyBase64.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Attachment encryption key must be valid base64", ex);
        }
        if (decoded.length != 32) {
            throw new IllegalArgumentException("Attachment encryption key must decode to exactly 32 bytes");
        }
        this.encryptionKey = new SecretKeySpec(decoded, "AES");
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot initialize attachment storage", ex);
        }
    }

    @Override
    public void store(String storageKey, byte[] plaintext) {
        Path target = target(storageKey);
        if (Files.exists(target)) {
            throw new IllegalStateException("Attachment storage key already exists");
        }

        Path temp = null;
        try {
            Files.createDirectories(root);
            temp = Files.createTempFile(root, ".upload-", ".tmp");
            Files.write(temp, encrypt(storageKey, plaintext));
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target);
            }
        } catch (IOException | GeneralSecurityException ex) {
            if (temp != null) deletePathQuietly(temp);
            throw new IllegalStateException("Cannot store attachment", ex);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        Path target = target(storageKey);
        try {
            return decrypt(storageKey, Files.readAllBytes(target));
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException("Cannot read attachment", ex);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = target(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot delete attachment", ex);
        }
    }

    private byte[] encrypt(String storageKey, byte[] plaintext) throws GeneralSecurityException, IOException {
        byte[] nonce = new byte[NONCE_LENGTH];
        secureRandom.nextBytes(nonce);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        cipher.updateAAD(storageKey.getBytes(StandardCharsets.US_ASCII));
        byte[] ciphertext = cipher.doFinal(plaintext);

        try (ByteArrayOutputStream output = new ByteArrayOutputStream(1 + nonce.length + ciphertext.length)) {
            output.write(FORMAT_VERSION);
            output.write(nonce);
            output.write(ciphertext);
            return output.toByteArray();
        }
    }

    private byte[] decrypt(String storageKey, byte[] stored) throws GeneralSecurityException {
        if (stored.length <= 1 + NONCE_LENGTH || stored[0] != FORMAT_VERSION) {
            throw new GeneralSecurityException("Unsupported attachment storage format");
        }
        byte[] nonce = new byte[NONCE_LENGTH];
        System.arraycopy(stored, 1, nonce, 0, NONCE_LENGTH);
        byte[] ciphertext = new byte[stored.length - 1 - NONCE_LENGTH];
        System.arraycopy(stored, 1 + NONCE_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, nonce));
        cipher.updateAAD(storageKey.getBytes(StandardCharsets.US_ASCII));
        return cipher.doFinal(ciphertext);
    }

    private Path target(String storageKey) {
        if (storageKey == null || !SAFE_KEY.matcher(storageKey).matches()) {
            throw new IllegalArgumentException("Invalid attachment storage key");
        }
        Path target = root.resolve(storageKey + ".bin").normalize();
        if (!target.getParent().equals(root)) {
            throw new IllegalArgumentException("Invalid attachment storage path");
        }
        return target;
    }

    void deleteQuietly(String storageKey) {
        try {
            delete(storageKey);
        } catch (RuntimeException ex) {
            log.warn("Could not clean up attachment object {}", storageKey, ex);
        }
    }

    private void deletePathQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Could not clean up temporary attachment file", ex);
        }
    }
}
