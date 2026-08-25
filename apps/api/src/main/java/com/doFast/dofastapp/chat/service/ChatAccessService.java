package com.doFast.dofastapp.chat.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

@Service
public class ChatAccessService {

    private static final EnumSet<JobStatus> SENDABLE_STATUSES = EnumSet.of(
            JobStatus.IN_PROGRESS,
            JobStatus.COMPLETION_REQUESTED,
            JobStatus.DISPUTED
    );

    private final JobRepository jobRepository;

    public ChatAccessService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public Job requireParticipant(Long jobId, User user) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));

        if (!sameUser(job.getCreatedBy(), user) && !sameUser(job.getTakenBy(), user)) {
            throw new ForbiddenOperationException("Nie masz dostępu do tego czatu");
        }

        return job;
    }

    public Job requireSendable(Long jobId, User user) {
        Job job = requireParticipant(jobId, user);
        if (job.getTakenBy() == null) {
            throw new ConflictException("Czat jest dostępny dopiero po przyjęciu zlecenia");
        }
        if (!SENDABLE_STATUSES.contains(job.getStatus())) {
            throw new ConflictException("Ten czat jest już tylko do odczytu");
        }
        return job;
    }

    public User otherParticipant(Job job, User currentUser) {
        if (sameUser(job.getCreatedBy(), currentUser)) {
            if (job.getTakenBy() == null) {
                throw new ConflictException("Zlecenie nie ma jeszcze wykonawcy");
            }
            return job.getTakenBy();
        }
        if (sameUser(job.getTakenBy(), currentUser)) {
            return job.getCreatedBy();
        }
        throw new ForbiddenOperationException("Nie masz dostępu do tego czatu");
    }

    public boolean isParticipant(Job job, User user) {
        return sameUser(job.getCreatedBy(), user) || sameUser(job.getTakenBy(), user);
    }

    private boolean sameUser(User first, User second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }
}
