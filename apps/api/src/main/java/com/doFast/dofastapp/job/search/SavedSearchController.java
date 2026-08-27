package com.doFast.dofastapp.job.search;

import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/saved-searches")
public class SavedSearchController {

    private final SavedSearchService savedSearchService;

    public SavedSearchController(SavedSearchService savedSearchService) {
        this.savedSearchService = savedSearchService;
    }

    @GetMapping
    public List<SavedSearchResponse> list(@AuthenticationPrincipal User user) {
        return savedSearchService.list(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedSearchResponse create(
            @Valid @RequestBody SavedSearchRequest request,
            @AuthenticationPrincipal User user
    ) {
        return savedSearchService.create(request, user);
    }

    @PutMapping("/{id}")
    public SavedSearchResponse update(
            @PathVariable Long id,
            @Valid @RequestBody SavedSearchRequest request,
            @AuthenticationPrincipal User user
    ) {
        return savedSearchService.update(id, request, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal User user) {
        savedSearchService.delete(id, user);
    }
}
