package com.doFast.dofastapp.job.controller;

import com.doFast.dofastapp.common.dto.PageResponse;
import com.doFast.dofastapp.job.dto.JobRequest;
import com.doFast.dofastapp.job.dto.JobResponse;
import com.doFast.dofastapp.job.dto.JobRouteResponse;
import com.doFast.dofastapp.job.dto.NearbyJobResponse;
import com.doFast.dofastapp.job.dto.RecommendedJobsResponse;
import com.doFast.dofastapp.job.service.JobDiscoveryService;
import com.doFast.dofastapp.job.service.JobRecommendationService;
import com.doFast.dofastapp.job.service.JobService;
import com.doFast.dofastapp.job.service.JobVisibilityService;
import com.doFast.dofastapp.location.dto.LocationResponse;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/jobs")
@Validated
public class JobController {

    private final JobService jobService;
    private final JobDiscoveryService jobDiscoveryService;
    private final JobRecommendationService jobRecommendationService;
    private final JobVisibilityService jobVisibilityService;

    public JobController(
            JobService jobService,
            JobDiscoveryService jobDiscoveryService,
            JobRecommendationService jobRecommendationService,
            JobVisibilityService jobVisibilityService
    ) {
        this.jobService = jobService;
        this.jobDiscoveryService = jobDiscoveryService;
        this.jobRecommendationService = jobRecommendationService;
        this.jobVisibilityService = jobVisibilityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse createJob(@RequestBody @Valid JobRequest request, @AuthenticationPrincipal User user) {
        return jobService.createJob(request, user);
    }

    @GetMapping
    public PageResponse<JobResponse> getJobs(
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) @Size(max = 80) @Pattern(regexp = "[a-z0-9-]*") String category,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @AuthenticationPrincipal User user
    ) {
        return jobDiscoveryService.getOpenJobs(query, category, minPrice, maxPrice, page, size, user);
    }

    @GetMapping("/recommended")
    public RecommendedJobsResponse getRecommendedJobs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "6") @Min(1) @Max(24) int size,
            @AuthenticationPrincipal User user
    ) {
        return jobRecommendationService.getRecommendedJobs(user, page, size);
    }

    @GetMapping("/nearby")
    public List<NearbyJobResponse> getNearbyJobs(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
            @RequestParam(defaultValue = "5000") @Min(100) @Max(50000) int radiusMeters,
            @RequestParam(required = false) @Size(max = 80) @Pattern(regexp = "[a-z0-9-]*") String category,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @AuthenticationPrincipal User user
    ) {
        return jobDiscoveryService.getNearbyJobs(latitude, longitude, radiusMeters, category, limit, user);
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable Long id, @AuthenticationPrincipal User user) {
        jobVisibilityService.assertCanViewPublicDetail(id, user);
        return jobService.getJob(id);
    }

    @GetMapping("/{id}/location")
    public LocationResponse getExactLocation(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return jobService.getExactLocation(id, user);
    }

    @GetMapping("/{id}/route")
    public JobRouteResponse getExactRoute(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return jobService.getExactRoute(id, user);
    }

    @PostMapping({"/{id}/accept", "/{id}/take"})
    public JobResponse acceptJob(@PathVariable Long id, @AuthenticationPrincipal User user) { return jobService.acceptJob(id, user); }

    @GetMapping("/my")
    public List<JobResponse> getMyJobs(@AuthenticationPrincipal User user) { return jobService.getMyJobs(user); }

    @PostMapping("/{id}/completion")
    public JobResponse requestCompletion(@PathVariable Long id, @AuthenticationPrincipal User user) { return jobService.requestCompletion(id, user); }

    @PostMapping({"/{id}/confirm", "/{id}/done"})
    public JobResponse confirmCompletion(@PathVariable Long id, @AuthenticationPrincipal User user) { return jobService.confirmCompletion(id, user); }

    @PostMapping("/{id}/cancel")
    public JobResponse cancelJob(@PathVariable Long id, @AuthenticationPrincipal User user) { return jobService.cancelJob(id, user); }
}
