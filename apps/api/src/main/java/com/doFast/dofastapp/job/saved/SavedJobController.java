package com.doFast.dofastapp.job.saved;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/saved-jobs")
@Validated
public class SavedJobController {

    private final SavedJobService savedJobService;

    public SavedJobController(SavedJobService savedJobService) {
        this.savedJobService = savedJobService;
    }

    @GetMapping
    public PageResponse<JobResponse> list(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size
    ) {
        return savedJobService.list(user, page, size);
    }

    @GetMapping("/status")
    public SavedJobBatchStatusResponse statuses(
            @RequestParam @Size(min = 1, max = 50) List<@Positive Long> jobIds,
            @AuthenticationPrincipal User user
    ) {
        return savedJobService.statuses(jobIds, user);
    }

    @GetMapping("/{jobId}/status")
    public SavedJobStatusResponse status(@PathVariable Long jobId, @AuthenticationPrincipal User user) {
        return savedJobService.status(jobId, user);
    }

    @PutMapping("/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@PathVariable Long jobId, @AuthenticationPrincipal User user) {
        savedJobService.save(jobId, user);
    }

    @DeleteMapping("/{jobId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long jobId, @AuthenticationPrincipal User user) {
        savedJobService.remove(jobId, user);
    }
}
