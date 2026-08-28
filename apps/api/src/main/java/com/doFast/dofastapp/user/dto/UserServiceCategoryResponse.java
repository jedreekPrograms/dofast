package com.doFast.dofastapp.user.dto;

import com.doFast.dofastapp.job.category.FulfillmentMode;

public record UserServiceCategoryResponse(
        Long categoryId,
        String slug,
        String name,
        FulfillmentMode fulfillmentMode,
        Long parentCategoryId,
        String parentCategorySlug,
        String parentCategoryName
) {}
