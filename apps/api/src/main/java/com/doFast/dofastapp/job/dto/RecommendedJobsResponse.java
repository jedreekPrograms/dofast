package com.doFast.dofastapp.job.dto;

import com.doFast.dofastapp.common.dto.PageResponse;

public record RecommendedJobsResponse(
        PageResponse<JobResponse> jobs,
        int specializationCount
) {}
