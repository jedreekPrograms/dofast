package com.doFast.dofastapp.job.attachment;

import com.doFast.dofastapp.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentFilePolicyTest {

    @Test
    void detectsRealFileTypeInsteadOfTrustingClientMimeAndNormalizesFilename() {
        AttachmentFilePolicy policy = new AttachmentFilePolicy(1024, 12);
        byte[] png = concat(
                new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a},
                "payload".getBytes(StandardCharsets.UTF_8)
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "../../Karta lojalnościowa.PDF",
                "application/pdf",
                png
        );

        ValidatedAttachmentFile validated = policy.validate(file);

        assertEquals("image/png", validated.mediaType());
        assertEquals("Karta lojalnościowa.png", validated.filename());
        assertEquals(png.length, validated.bytes().length);
        assertEquals(64, validated.sha256().length());
        assertTrue(validated.sha256().matches("[0-9a-f]{64}"));
    }

    @Test
    void rejectsActiveOrUnknownFormatsEvenWhenClientClaimsImage() {
        AttachmentFilePolicy policy = new AttachmentFilePolicy(1024, 12);
        MockMultipartFile svg = new MockMultipartFile(
                "file",
                "card.png",
                "image/png",
                "<svg><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8)
        );

        assertThrows(BusinessException.class, () -> policy.validate(svg));
    }

    @Test
    void rejectsBytesPastConfiguredLimit() {
        AttachmentFilePolicy policy = new AttachmentFilePolicy(8, 12);
        MockMultipartFile oversized = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1, 2, 3, 4, 5, 6}
        );

        assertThrows(BusinessException.class, () -> policy.validate(oversized));
    }

    @Test
    void refusesConfigurationAboveDatabaseHardLimit() {
        assertThrows(IllegalArgumentException.class, () -> new AttachmentFilePolicy(10 * 1024 * 1024 + 1, 12));
    }

    @Test
    void enforcesPerJobCountLimit() {
        AttachmentFilePolicy policy = new AttachmentFilePolicy(1024, 2);
        policy.assertCanAdd(1);
        assertThrows(BusinessException.class, () -> policy.assertCanAdd(2));
    }

    private byte[] concat(byte[] first, byte[] second) {
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
