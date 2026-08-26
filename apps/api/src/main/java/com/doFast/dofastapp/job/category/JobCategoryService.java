package com.doFast.dofastapp.job.category;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class JobCategoryService {

    private final JobCategoryRepository repository;

    public JobCategoryService(JobCategoryRepository repository) {
        this.repository = repository;
    }

    public List<JobCategoryResponse> getCatalog() {
        List<JobCategory> categories = repository.findByActiveTrueOrderBySortOrderAscNameAsc();
        Map<Long, List<JobCategory>> childrenByParent = new HashMap<>();
        List<JobCategory> roots = new ArrayList<>();

        for (JobCategory category : categories) {
            JobCategory parent = category.getParent();
            if (parent == null) {
                roots.add(category);
            } else {
                childrenByParent.computeIfAbsent(parent.getId(), ignored -> new ArrayList<>()).add(category);
            }
        }

        return roots.stream()
                .map(root -> toResponse(root, childrenByParent))
                .toList();
    }

    private JobCategoryResponse toResponse(JobCategory category, Map<Long, List<JobCategory>> childrenByParent) {
        List<JobCategoryResponse> children = childrenByParent.getOrDefault(category.getId(), List.of())
                .stream()
                .map(child -> toResponse(child, childrenByParent))
                .toList();
        return new JobCategoryResponse(
                category.getId(),
                category.getSlug(),
                category.getName(),
                category.getFulfillmentMode(),
                children
        );
    }
}
