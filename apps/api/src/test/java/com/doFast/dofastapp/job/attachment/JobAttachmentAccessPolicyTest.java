package com.doFast.dofastapp.job.attachment;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobAttachmentAccessPolicyTest {

    @Mock private UserBlockService userBlockService;
    @Mock private JobAttachment attachment;
    @Mock private Job job;
    @Mock private User creator;
    @Mock private User worker;
    @Mock private User outsider;

    @Test
    void executionSecretIsVisibleToWorkerOnlyWhileJobIsInProgress() {
        JobAttachmentAccessPolicy policy = new JobAttachmentAccessPolicy(userBlockService);
        prepareWorkerAttachment(JobAttachmentVisibility.EXECUTION_SECRET);
        when(job.getStatus()).thenReturn(JobStatus.IN_PROGRESS, JobStatus.COMPLETION_REQUESTED, JobStatus.DISPUTED, JobStatus.DONE);

        assertTrue(policy.canRead(attachment, worker));
        assertFalse(policy.canRead(attachment, worker));
        assertFalse(policy.canRead(attachment, worker));
        assertFalse(policy.canRead(attachment, worker));
    }

    @Test
    void creatorRetainsAccessToOwnSecretAfterLifecycleEnds() {
        JobAttachmentAccessPolicy policy = new JobAttachmentAccessPolicy(userBlockService);
        when(attachment.getJob()).thenReturn(job);
        when(job.getCreatedBy()).thenReturn(creator);
        when(creator.getId()).thenReturn(10L);

        assertTrue(policy.canRead(attachment, creator));
    }

    @Test
    void openJobViewerAttachmentHonorsBilateralBlockPolicy() {
        JobAttachmentAccessPolicy policy = new JobAttachmentAccessPolicy(userBlockService);
        when(attachment.getJob()).thenReturn(job);
        when(attachment.getVisibility()).thenReturn(JobAttachmentVisibility.JOB_VIEWERS);
        when(job.getCreatedBy()).thenReturn(creator);
        when(creator.getId()).thenReturn(10L);
        when(outsider.getId()).thenReturn(30L);
        when(job.getStatus()).thenReturn(JobStatus.OPEN);
        when(userBlockService.isInteractionBlocked(creator, outsider)).thenReturn(false, true);

        assertTrue(policy.canRead(attachment, outsider));
        assertFalse(policy.canRead(attachment, outsider));
    }

    @Test
    void participantAttachmentRemainsAvailableToAssignedWorkerHistorically() {
        JobAttachmentAccessPolicy policy = new JobAttachmentAccessPolicy(userBlockService);
        prepareWorkerAttachment(JobAttachmentVisibility.PARTICIPANTS);
        when(job.getStatus()).thenReturn(JobStatus.DONE);

        assertTrue(policy.canRead(attachment, worker));
    }

    @Test
    void nonCreatorCannotUploadAndCreatorCannotUploadAfterExecutionPhase() {
        JobAttachmentAccessPolicy policy = new JobAttachmentAccessPolicy(userBlockService);
        when(job.getCreatedBy()).thenReturn(creator);
        when(creator.getId()).thenReturn(10L);
        when(outsider.getId()).thenReturn(30L);

        assertThrows(ForbiddenOperationException.class, () -> policy.assertCanUpload(job, outsider));

        when(job.getStatus()).thenReturn(JobStatus.COMPLETION_REQUESTED);
        assertThrows(ConflictException.class, () -> policy.assertCanUpload(job, creator));
    }

    @Test
    void ordinaryAttachmentBecomesImmutableAfterWorkerSelectionButSecretCanBeRevoked() {
        JobAttachmentAccessPolicy policy = new JobAttachmentAccessPolicy(userBlockService);
        when(attachment.getJob()).thenReturn(job);
        when(job.getCreatedBy()).thenReturn(creator);
        when(creator.getId()).thenReturn(10L);
        when(job.getTakenBy()).thenReturn(worker);
        when(attachment.getVisibility()).thenReturn(JobAttachmentVisibility.JOB_VIEWERS, JobAttachmentVisibility.EXECUTION_SECRET);

        assertThrows(ConflictException.class, () -> policy.assertCanDelete(attachment, creator));
        assertDoesNotThrow(() -> policy.assertCanDelete(attachment, creator));
    }

    private void prepareWorkerAttachment(JobAttachmentVisibility visibility) {
        when(attachment.getJob()).thenReturn(job);
        when(attachment.getVisibility()).thenReturn(visibility);
        when(job.getCreatedBy()).thenReturn(creator);
        when(job.getTakenBy()).thenReturn(worker);
        when(creator.getId()).thenReturn(10L);
        when(worker.getId()).thenReturn(20L);
    }
}
