package com.doFast.dofastapp.dispute.dto;

import java.util.List;

public record DisputeDetailResponse(
        DisputeResponse dispute,
        List<DisputeEventResponse> events
) {}
