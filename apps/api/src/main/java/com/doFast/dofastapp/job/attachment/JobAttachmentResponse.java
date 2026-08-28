package com.doFast.dofastapp.job.attachment;

import java.time.LocalDateTime;

public record JobAttachmentResponse(
        Long id,
        Long jobId,
        Long uploadedById,
        JobAttachmentVisibility visibility,
        String originalFilename,
        String mediaType,
        long sizeBytes,
        LocalDateTime createdAt
) {}
