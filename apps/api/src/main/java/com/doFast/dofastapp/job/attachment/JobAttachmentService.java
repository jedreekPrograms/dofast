package com.doFast.dofastapp.job.attachment;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.expense.JobExpenseClaimRepository;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.job.service.JobVisibilityService;
import com.doFast.dofastapp.user.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class JobAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(JobAttachmentService.class);

    private final JobRepository jobRepository;
    private final JobAttachmentRepository attachmentRepository;
    private final JobAttachmentAccessPolicy accessPolicy;
    private final AttachmentFilePolicy filePolicy;
    private final AttachmentStorage storage;
    private final JobVisibilityService jobVisibilityService;
    private final JobExpenseClaimRepository expenseClaimRepository;

    public JobAttachmentService(
            JobRepository jobRepository,
            JobAttachmentRepository attachmentRepository,
            JobAttachmentAccessPolicy accessPolicy,
            AttachmentFilePolicy filePolicy,
            AttachmentStorage storage,
            JobVisibilityService jobVisibilityService,
            JobExpenseClaimRepository expenseClaimRepository
    ) {
        this.jobRepository = jobRepository;
        this.attachmentRepository = attachmentRepository;
        this.accessPolicy = accessPolicy;
        this.filePolicy = filePolicy;
        this.storage = storage;
        this.jobVisibilityService = jobVisibilityService;
        this.expenseClaimRepository = expenseClaimRepository;
    }

    @Transactional
    public JobAttachmentResponse upload(
            Long jobId,
            JobAttachmentVisibility visibility,
            MultipartFile file,
            User user
    ) {
        if (visibility == null) {
            throw new IllegalArgumentException("Attachment visibility is required");
        }
        Job job = getParticipantJobForUpdate(jobId, user);
        accessPolicy.assertCanUpload(job, user, visibility);
        filePolicy.assertCanAdd(attachmentRepository.countByJob_IdAndDeletedAtIsNull(jobId));
        ValidatedAttachmentFile validated = filePolicy.validate(file);

        String storageKey = UUID.randomUUID().toString();
        storage.store(storageKey, validated.bytes());
        registerRollbackCleanup(storageKey);

        JobAttachment attachment = new JobAttachment(
                job,
                user,
                visibility,
                validated.filename(),
                validated.mediaType(),
                validated.bytes().length,
                validated.sha256(),
                storageKey,
                LocalDateTime.now()
        );
        return toResponse(attachmentRepository.save(attachment));
    }

    public List<JobAttachmentResponse> listVisible(Long jobId, User user) {
        jobVisibilityService.assertCanViewPublicDetail(jobId, user);
        return attachmentRepository.findAllByJob_IdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(jobId)
                .stream()
                .filter(attachment -> accessPolicy.canRead(attachment, user))
                .map(this::toResponse)
                .toList();
    }

    public JobAttachmentContent download(Long jobId, Long attachmentId, User user) {
        JobAttachment attachment = getAttachment(jobId, attachmentId);
        accessPolicy.assertCanRead(attachment, user);
        return new JobAttachmentContent(toResponse(attachment), storage.read(attachment.getStorageKey()));
    }

    @Transactional
    public void delete(Long jobId, Long attachmentId, User user) {
        getParticipantJobForUpdate(jobId, user);
        JobAttachment attachment = getAttachment(jobId, attachmentId);
        accessPolicy.assertCanDelete(attachment, user);
        if (expenseClaimRepository.existsByAttachment_Id(attachmentId)) {
            throw new ConflictException("Paragon użyty do rozliczenia wydatku jest dokumentem finansowym i nie może zostać usunięty");
        }
        attachment.markDeleted(LocalDateTime.now());
        attachmentRepository.save(attachment);
        registerCommitDeletion(attachment.getStorageKey());
    }

    private Job getParticipantJobForUpdate(Long jobId, User user) {
        Long userId = user == null ? null : user.getId();
        if (userId == null) {
            throw new ResourceNotFoundException("Zlecenie nie istnieje");
        }
        return jobRepository.findParticipantByIdForUpdate(jobId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));
    }

    private JobAttachment getAttachment(Long jobId, Long attachmentId) {
        return attachmentRepository.findByIdAndJob_IdAndDeletedAtIsNull(attachmentId, jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Załącznik nie istnieje"));
    }

    private JobAttachmentResponse toResponse(JobAttachment attachment) {
        return new JobAttachmentResponse(
                attachment.getId(),
                attachment.getJob().getId(),
                attachment.getUploadedBy().getId(),
                attachment.getVisibility(),
                attachment.getOriginalFilename(),
                attachment.getMediaType(),
                attachment.getSizeBytes(),
                attachment.getCreatedAt()
        );
    }

    private void registerRollbackCleanup(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    deleteStorageQuietly(storageKey);
                }
            }
        });
    }

    private void registerCommitDeletion(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteStorageQuietly(storageKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteStorageQuietly(storageKey);
            }
        });
    }

    private void deleteStorageQuietly(String storageKey) {
        try {
            storage.delete(storageKey);
        } catch (RuntimeException ex) {
            log.warn("Could not remove attachment object {}", storageKey, ex);
        }
    }
}
