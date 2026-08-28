package com.doFast.dofastapp.job.attachment;

public record JobAttachmentContent(
        JobAttachmentResponse metadata,
        byte[] bytes
) {}
