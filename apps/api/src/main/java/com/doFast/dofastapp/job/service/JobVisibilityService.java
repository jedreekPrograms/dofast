package com.doFast.dofastapp.job.service;

import com.doFast.dofastapp.common.enums.JobStatus;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.repository.JobRepository;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class JobVisibilityService {

    private final JobRepository jobRepository;
    private final UserBlockService userBlockService;

    public JobVisibilityService(JobRepository jobRepository, UserBlockService userBlockService) {
        this.jobRepository = jobRepository;
        this.userBlockService = userBlockService;
    }

    public void assertCanViewPublicDetail(Long jobId, User currentUser) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Zlecenie nie istnieje"));

        if (sameUser(job.getCreatedBy(), currentUser) || sameUser(job.getTakenBy(), currentUser)) {
            return;
        }

        // Only OPEN jobs belong to the public marketplace. Keeping lifecycle states behind
        // participant access prevents direct-ID enumeration from exposing accepted, completed
        // or cancelled jobs after they have disappeared from discovery.
        if (job.getStatus() != JobStatus.OPEN) {
            throw new ResourceNotFoundException("Zlecenie nie istnieje");
        }

        if (currentUser == null) {
            return;
        }

        if (userBlockService.isInteractionBlocked(job.getCreatedBy(), currentUser)) {
            throw new ResourceNotFoundException("Zlecenie nie istnieje");
        }
    }

    private boolean sameUser(User first, User second) {
        return first != null
                && second != null
                && first.getId() != null
                && first.getId().equals(second.getId());
    }
}
