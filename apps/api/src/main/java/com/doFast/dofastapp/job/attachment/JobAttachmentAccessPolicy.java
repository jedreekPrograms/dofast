package com.doFast.dofastapp.job.attachment;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.springframework.stereotype.Component;

@Component
public class JobAttachmentAccessPolicy {

    private final UserBlockService userBlockService;

    public JobAttachmentAccessPolicy(UserBlockService userBlockService) {
        this.userBlockService = userBlockService;
    }

    public void assertCanUpload(Job job, User user, JobAttachmentVisibility visibility) {
        if (sameUser(job.getCreatedBy(), user)) {
            if (job.getStatus() != JobStatus.OPEN && job.getStatus() != JobStatus.IN_PROGRESS) {
                throw new ConflictException("Załączniki można dodawać tylko przed realizacją lub w jej trakcie");
            }
            return;
        }

        if (sameUser(job.getTakenBy(), user)) {
            if (job.getStatus() != JobStatus.IN_PROGRESS) {
                throw new ConflictException("Wykonawca może dodawać załączniki tylko w trakcie realizacji zlecenia");
            }
            if (visibility != JobAttachmentVisibility.PARTICIPANTS) {
                throw new ForbiddenOperationException("Wykonawca może dodawać tylko załączniki widoczne dla uczestników zlecenia");
            }
            return;
        }

        throw new ForbiddenOperationException("Tylko strony aktywnego zlecenia mogą dodawać załączniki");
    }

    public boolean canRead(JobAttachment attachment, User user) {
        if (user == null) return false;
        Job job = attachment.getJob();
        if (sameUser(job.getCreatedBy(), user)) return true;

        boolean assignedWorker = sameUser(job.getTakenBy(), user);
        return switch (attachment.getVisibility()) {
            case JOB_VIEWERS -> assignedWorker || canReadOpenJobViewerAttachment(job, user);
            case PARTICIPANTS -> assignedWorker;
            case EXECUTION_SECRET -> assignedWorker && job.getStatus() == JobStatus.IN_PROGRESS;
        };
    }

    public void assertCanRead(JobAttachment attachment, User user) {
        if (!canRead(attachment, user)) {
            throw new ResourceNotFoundException("Załącznik nie istnieje");
        }
    }

    public void assertCanDelete(JobAttachment attachment, User user) {
        Job job = attachment.getJob();
        if (!sameUser(job.getCreatedBy(), user)) {
            throw new ForbiddenOperationException("Tylko zleceniodawca może usuwać załączniki");
        }
        if (attachment.getVisibility() == JobAttachmentVisibility.EXECUTION_SECRET) {
            return;
        }
        if (job.getTakenBy() != null) {
            throw new ConflictException("Po wyborze wykonawcy zwykłe załączniki są zachowywane jako historia zlecenia");
        }
    }

    private boolean canReadOpenJobViewerAttachment(Job job, User user) {
        return job.getStatus() == JobStatus.OPEN
                && !userBlockService.isInteractionBlocked(job.getCreatedBy(), user);
    }

    private boolean sameUser(User first, User second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }
}
