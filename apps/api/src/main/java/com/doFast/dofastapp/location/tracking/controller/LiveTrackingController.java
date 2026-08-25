package com.doFast.dofastapp.location.tracking.controller;

import com.doFast.dofastapp.location.tracking.dto.LiveLocationUpdateRequest;
import com.doFast.dofastapp.location.tracking.dto.LiveTrackingResponse;
import com.doFast.dofastapp.location.tracking.service.LiveTrackingService;
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

    public LiveTrackingController(LiveTrackingService liveTrackingService) {
        this.liveTrackingService = liveTrackingService;
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
        return liveTrackingService.updateLocation(jobId, request, user);
    }

    @PostMapping("/pickup")
    public LiveTrackingResponse confirmPickup(
            @PathVariable Long jobId,
            @AuthenticationPrincipal User user
    ) {
        return liveTrackingService.confirmPickup(jobId, user);
    }
}
