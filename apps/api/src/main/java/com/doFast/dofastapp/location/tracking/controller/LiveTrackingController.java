package com.doFast.dofastapp.location.tracking.controller;

import com.doFast.dofastapp.location.tracking.dto.LiveLocationUpdateRequest;
import com.doFast.dofastapp.location.tracking.dto.LiveTrackingResponse;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
import com.doFast.dofastapp.location.tracking.service.TrackingSampleFreshnessValidator;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs/{jobId}/tracking")
public class LiveTrackingController {

    private final LiveTrackingService liveTrackingService;
    private final TrackingSampleFreshnessValidator sampleFreshnessValidator;

    public LiveTrackingController(
            LiveTrackingService liveTrackingService,
            TrackingSampleFreshnessValidator sampleFreshnessValidator
    ) {
        this.liveTrackingService = liveTrackingService;
        this.sampleFreshnessValidator = sampleFreshnessValidator;
    }

    @GetMapping
    public LiveTrackingResponse getTracking(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user
    ) {
        return liveTrackingService.getTracking(jobId, user);
    }

    @PutMapping("/location")
    public LiveTrackingResponse updateLocation(
            @PathVariable Long jobId,
            @RequestBody @Valid LiveLocationUpdateRequest request,
            @AuthenticationPrincipal User user
    ) {
        sampleFreshnessValidator.validate(request.capturedAt());
        return liveTrackingService.updateLocation(jobId, request, user);
    }

    @PostMapping("/checkpoint")
    public LiveTrackingResponse confirmCheckpoint(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user
    ) {
        return liveTrackingService.confirmCheckpoint(jobId, user);
    }

    @PostMapping("/pickup")
    public LiveTrackingResponse confirmPickup(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user
    ) {
        return liveTrackingService.confirmPickup(jobId, user);
    }
}
