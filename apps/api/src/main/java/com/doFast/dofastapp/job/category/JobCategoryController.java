package com.doFast.dofastapp.job.category;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/job-categories")
public class JobCategoryController {

    private final JobCategoryService service;

    public JobCategoryController(JobCategoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<JobCategoryResponse> getCatalog() {
        return service.getCatalog();
    }
}
