package com.doFast.dofastapp.job.attachment;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.service.JobVisibilityService;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobAttachmentServiceTest {

    @Mock private JobRepository jobRepository;
    @Mock private JobAttachmentRepository attachmentRepository;
    @Mock private JobAttachmentAccessPolicy accessPolicy;
    @Mock private AttachmentFilePolicy filePolicy;
    @Mock private AttachmentStorage storage;
    @Mock private JobVisibilityService jobVisibilityService;
    @Mock private Job job;
    @Mock private User creator;
    @Mock private JobAttachment attachment;

    @Test
    void uploadStoresOpaqueObjectAndPersistsOnlyValidatedMetadata() {
        JobAttachmentService service = service();
        byte[] bytes = "validated".getBytes(StandardCharsets.UTF_8);
        String sha = "a".repeat(64);
        MockMultipartFile upload = new MockMultipartFile("file", "user.exe", "application/octet-stream", bytes);
        when(jobRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(job));
        when(attachmentRepository.countByJob_IdAndDeletedAtIsNull(50L)).thenReturn(0L);
        when(filePolicy.validate(upload)).thenReturn(new ValidatedAttachmentFile(bytes, "lista.png", "image/png", sha));
        when(attachmentRepository.save(any(JobAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(job.getId()).thenReturn(50L);
        when(creator.getId()).thenReturn(7L);

        JobAttachmentResponse response = service.upload(50L, JobAttachmentVisibility.PARTICIPANTS, upload, creator);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(storage).store(key.capture(), any(byte[].class));
        assertEquals(36, key.getValue().length());
        assertEquals("lista.png", response.originalFilename());
        assertEquals("image/png", response.mediaType());
        assertEquals(JobAttachmentVisibility.PARTICIPANTS, response.visibility());
        verify(accessPolicy).assertCanUpload(job, creator);
        verify(filePolicy).assertCanAdd(0L);
    }

    @Test
    void listChecksJobVisibilityBeforeReturningOnlyReadableAttachmentMetadata() {
        JobAttachmentService service = service();
        when(attachmentRepository.findAllByJob_IdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(50L))
                .thenReturn(List.of(attachment));
        when(accessPolicy.canRead(attachment, creator)).thenReturn(false);

        List<JobAttachmentResponse> result = service.listVisible(50L, creator);

        assertEquals(List.of(), result);
        verify(jobVisibilityService).assertCanViewPublicDetail(50L, creator);
    }

    private JobAttachmentService service() {
        return new JobAttachmentService(
                jobRepository,
                attachmentRepository,
                accessPolicy,
                filePolicy,
                storage,
                jobVisibilityService
        );
    }
}
