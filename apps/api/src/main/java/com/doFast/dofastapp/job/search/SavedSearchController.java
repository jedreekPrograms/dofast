package com.doFast.dofastapp.job.search;

import com.doFast.dofastapp.job.dto.NearbyJobResponse;
import com.doFast.dofastapp.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/saved-searches")
@Validated
public class SavedSearchController {

    private final SavedSearchService savedSearchService;
    private final SavedSearchResultService savedSearchResultService;

    public SavedSearchController(
            SavedSearchService savedSearchService,
            SavedSearchResultService savedSearchResultService
    ) {
        this.savedSearchService = savedSearchService;
        this.savedSearchResultService = savedSearchResultService;
    }

    @GetMapping
    public List<SavedSearchResponse> list(@AuthenticationPrincipal User user) {
        return savedSearchService.list(user);
    }

    @GetMapping("/{id}/results")
    public List<NearbyJobResponse> radiusResults(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @AuthenticationPrincipal User user
    ) {
        return savedSearchResultService.getRadiusResults(id, user, limit);
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
