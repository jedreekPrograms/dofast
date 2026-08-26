package com.doFast.dofastapp.job.category;

import java.util.List;

public record JobCategoryResponse(
        Long id,
        String slug,
        String name,
        FulfillmentMode fulfillmentMode,
        List<JobCategoryResponse> children
) {}
