package com.doFast.dofastapp.job.attachment;

import com.doFast.dofastapp.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class AttachmentFilePolicy {

    private static final int MAX_FILENAME_LENGTH = 160;

    private final int maxFileSizeBytes;
    private final int maxAttachmentsPerJob;

    public AttachmentFilePolicy(
            @Value("${dofast.attachments.max-file-size-bytes:10485760}") int maxFileSizeBytes,
            @Value("${dofast.attachments.max-per-job:12}") int maxAttachmentsPerJob
    ) {
        if (maxFileSizeBytes <= 0 || maxFileSizeBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Attachment max file size is invalid");
        }
        if (maxAttachmentsPerJob <= 0 || maxAttachmentsPerJob > 100) {
            throw new IllegalArgumentException("Attachment count limit is invalid");
        }
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxAttachmentsPerJob = maxAttachmentsPerJob;
    }

    public ValidatedAttachmentFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Załącznik nie może być pusty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BusinessException("Załącznik jest zbyt duży");
        }

        byte[] bytes = readBounded(file);
        DetectedType type = detect(bytes);
        String filename = normalizeFilename(file.getOriginalFilename(), type.extension);
        return new ValidatedAttachmentFile(bytes, filename, type.mediaType, sha256(bytes));
    }

    public void assertCanAdd(long currentCount) {
        if (currentCount >= maxAttachmentsPerJob) {
            throw new BusinessException("Osiągnięto limit załączników dla tego zlecenia");
        }
    }

    private byte[] readBounded(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(maxFileSizeBytes + 1);
            if (bytes.length == 0) {
                throw new BusinessException("Załącznik nie może być pusty");
            }
            if (bytes.length > maxFileSizeBytes) {
                throw new BusinessException("Załącznik jest zbyt duży");
            }
            return bytes;
        } catch (IOException ex) {
            throw new BusinessException("Nie udało się odczytać załącznika");
        }
    }

    private DetectedType detect(byte[] bytes) {
        if (startsWith(bytes, new int[]{0xff, 0xd8, 0xff})) {
            return DetectedType.JPEG;
        }
        if (startsWith(bytes, new int[]{0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})) {
            return DetectedType.PNG;
        }
        if (bytes.length >= 12
                && asciiAt(bytes, 0, "RIFF")
                && asciiAt(bytes, 8, "WEBP")) {
            return DetectedType.WEBP;
        }
        if (asciiAt(bytes, 0, "%PDF-")) {
            return DetectedType.PDF;
        }
        throw new BusinessException("Dozwolone są wyłącznie pliki JPG, PNG, WebP lub PDF");
    }

    private boolean startsWith(byte[] bytes, int[] signature) {
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xff) != signature[i]) return false;
        }
        return true;
    }

    private boolean asciiAt(byte[] bytes, int offset, String value) {
        if (bytes.length < offset + value.length()) return false;
        for (int i = 0; i < value.length(); i++) {
            if ((char) (bytes[offset + i] & 0xff) != value.charAt(i)) return false;
        }
        return true;
    }

    private String normalizeFilename(String original, String extension) {
        String value = original == null ? "" : original.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        value = value.replaceAll("[\\p{Cntrl}]", "").trim();

        int dot = value.lastIndexOf('.');
        String base = dot > 0 ? value.substring(0, dot) : value;
        base = base.replaceAll("[^\\p{L}\\p{N}._() -]", "_").trim();
        if (base.isBlank()) base = "zalacznik";

        int maxBaseLength = MAX_FILENAME_LENGTH - extension.length();
        if (base.length() > maxBaseLength) base = base.substring(0, maxBaseLength).trim();
        return base + extension;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private enum DetectedType {
        JPEG("image/jpeg", ".jpg"),
        PNG("image/png", ".png"),
        WEBP("image/webp", ".webp"),
        PDF("application/pdf", ".pdf");

        private final String mediaType;
        private final String extension;

        DetectedType(String mediaType, String extension) {
            this.mediaType = mediaType;
            this.extension = extension;
        }
    }
}
