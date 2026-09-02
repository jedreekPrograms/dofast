package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.user.dto.UpdateUserServiceCategoriesRequest;
import com.doFast.dofastapp.user.dto.UserServiceCategoryResponse;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.entity.UserServiceCategory;
import com.doFast.dofastapp.user.repository.UserServiceCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UserServiceCategoryService {

    public static final int MAX_SPECIALIZATIONS = 10;

    private final UserServiceCategoryRepository userServiceCategoryRepository;
    private final JobCategoryRepository jobCategoryRepository;

    public UserServiceCategoryService(
            UserServiceCategoryRepository userServiceCategoryRepository,
            JobCategoryRepository jobCategoryRepository
    ) {
        this.userServiceCategoryRepository = userServiceCategoryRepository;
        this.jobCategoryRepository = jobCategoryRepository;
    }

    public List<UserServiceCategoryResponse> getForUser(Long userId) {
        Long verifiedUserId = requireUserId(userId);
        return userServiceCategoryRepository.findForUser(verifiedUserId)
                .stream()
                .filter(relation -> isSelectableLeaf(relation.getCategory()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<UserServiceCategoryResponse> replaceForUser(
            User user,
            UpdateUserServiceCategoriesRequest request
    ) {
        Long userId = requireUserId(user);
        Set<Long> requestedIds = new LinkedHashSet<>(request.categoryIds());
        if (requestedIds.size() > MAX_SPECIALIZATIONS) {
            throw new BusinessException("Możesz wybrać maksymalnie 10 specjalizacji");
        }

        List<Long> requestedIdList = List.copyOf(requestedIds);
        List<JobCategory> requestedCategories = requestedIdList.isEmpty()
                ? List.of()
                : jobCategoryRepository.findByIdInAndActiveTrue(requestedIdList);

        if (requestedCategories.size() != requestedIds.size()) {
            throw new BusinessException("Co najmniej jedna wybrana specjalizacja nie istnieje lub jest nieaktywna");
        }
        if (requestedCategories.stream().anyMatch(category -> !isSelectableLeaf(category))) {
            throw new BusinessException("Wybierz wyłącznie konkretne podkategorie usług");
        }

        Map<Long, JobCategory> categoriesById = requestedCategories.stream()
                .collect(Collectors.toMap(JobCategory::getId, Function.identity()));
        List<UserServiceCategory> existing = userServiceCategoryRepository.findForUser(userId);
        Set<Long> existingIds = existing.stream()
                .map(relation -> relation.getCategory().getId())
                .collect(Collectors.toSet());

        List<UserServiceCategory> removed = existing.stream()
                .filter(relation -> !requestedIds.contains(relation.getCategory().getId()))
                .toList();
        if (!removed.isEmpty()) {
            userServiceCategoryRepository.deleteAll(removed);
        }

        List<UserServiceCategory> added = new ArrayList<>();
        for (Long categoryId : requestedIds) {
            if (!existingIds.contains(categoryId)) {
                added.add(new UserServiceCategory(user, categoriesById.get(categoryId)));
            }
        }
        if (!added.isEmpty()) {
            userServiceCategoryRepository.saveAll(added);
        }

        return getForUser(userId);
    }

    private Long requireUserId(User user) {
        if (user == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby zarządzać specjalizacjami");
        }
        return requireUserId(user.getId());
    }

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new ForbiddenOperationException("Zaloguj się, aby zarządzać specjalizacjami");
        }
        return userId;
    }

    private boolean isSelectableLeaf(JobCategory category) {
        return category.isActive()
                && category.getParent() != null
                && category.getFulfillmentMode() != null;
    }

    private UserServiceCategoryResponse toResponse(UserServiceCategory relation) {
        JobCategory category = relation.getCategory();
        JobCategory parent = category.getParent();
        return new UserServiceCategoryResponse(
                category.getId(),
                category.getSlug(),
                category.getName(),
                category.getFulfillmentMode(),
                parent.getId(),
                parent.getSlug(),
                parent.getName()
        );
    }
}
