package com.doFast.dofastapp.job.attachment;

record ValidatedAttachmentFile(
        byte[] bytes,
        String filename,
        String mediaType,
        String sha256
) {}
