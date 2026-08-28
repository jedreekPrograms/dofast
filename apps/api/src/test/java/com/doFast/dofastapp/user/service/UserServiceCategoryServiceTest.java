package com.doFast.dofastapp.user.service;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.job.category.FulfillmentMode;
import com.doFast.dofastapp.job.category.JobCategory;
import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.user.dto.UpdateUserServiceCategoriesRequest;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.entity.UserServiceCategory;
import com.doFast.dofastapp.user.repository.UserServiceCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceCategoryServiceTest {

    @Mock private UserServiceCategoryRepository userServiceCategoryRepository;
    @Mock private JobCategoryRepository jobCategoryRepository;

    @Test
    void replacesSelectionByDiffAndReturnsPublicCategoryMetadata() {
        User user = user(7L);
        JobCategory parent = category(1L, null, "transport", "Transport", null, true, 10);
        JobCategory existingCategory = category(2L, parent, "paczki", "Paczki i przesyłki", FulfillmentMode.POINT_TO_POINT, true, 20);
        JobCategory addedCategory = category(3L, parent, "meble", "Transport mebli", FulfillmentMode.POINT_TO_POINT, true, 30);
        UserServiceCategory existingRelation = new UserServiceCategory(user, existingCategory);
        UserServiceCategory addedRelation = new UserServiceCategory(user, addedCategory);

        when(jobCategoryRepository.findByIdInAndActiveTrue(List.of(2L, 3L)))
                .thenReturn(List.of(existingCategory, addedCategory));
        when(userServiceCategoryRepository.findAllByUser_IdOrderByCategory_SortOrderAscCategory_NameAsc(7L))
                .thenReturn(List.of(existingRelation), List.of(existingRelation, addedRelation));

        UserServiceCategoryService service = new UserServiceCategoryService(
                userServiceCategoryRepository,
                jobCategoryRepository
        );

        var result = service.replaceForUser(user, new UpdateUserServiceCategoriesRequest(List.of(2L, 3L)));

        assertEquals(2, result.size());
        assertEquals("paczki", result.get(0).slug());
        assertEquals("Transport", result.get(0).parentCategoryName());
        assertEquals("meble", result.get(1).slug());
        verify(userServiceCategoryRepository, never()).deleteAll(org.mockito.ArgumentMatchers.anyCollection());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<UserServiceCategory>> addedCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(userServiceCategoryRepository).saveAll(addedCaptor.capture());
        List<UserServiceCategory> added = ((Iterable<UserServiceCategory>) addedCaptor.getValue()) instanceof List<UserServiceCategory> list
                ? list
                : java.util.stream.StreamSupport.stream(addedCaptor.getValue().spliterator(), false).toList();
        assertEquals(1, added.size());
        assertEquals(3L, added.getFirst().getCategory().getId());
    }

    @Test
    void rejectsParentCategoryAsSpecialization() {
        User user = user(8L);
        JobCategory parent = category(10L, null, "remonty", "Remonty", null, true, 10);
        when(jobCategoryRepository.findByIdInAndActiveTrue(List.of(10L))).thenReturn(List.of(parent));

        UserServiceCategoryService service = new UserServiceCategoryService(
                userServiceCategoryRepository,
                jobCategoryRepository
        );

        assertThrows(BusinessException.class, () -> service.replaceForUser(
                user,
                new UpdateUserServiceCategoriesRequest(List.of(10L))
        ));
        verify(userServiceCategoryRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyCollection());
    }

    @Test
    void rejectsInactiveOrUnknownCategoryBeforeMutatingSelection() {
        User user = user(9L);
        when(jobCategoryRepository.findByIdInAndActiveTrue(List.of(99L))).thenReturn(List.of());

        UserServiceCategoryService service = new UserServiceCategoryService(
                userServiceCategoryRepository,
                jobCategoryRepository
        );

        assertThrows(BusinessException.class, () -> service.replaceForUser(
                user,
                new UpdateUserServiceCategoriesRequest(List.of(99L))
        ));
        verify(userServiceCategoryRepository, never())
                .findAllByUser_IdOrderByCategory_SortOrderAscCategory_NameAsc(9L);
    }

    private User user(Long id) {
        User user = new User("user@example.com", "Użytkownik");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private JobCategory category(
            Long id,
            JobCategory parent,
            String slug,
            String name,
            FulfillmentMode fulfillmentMode,
            boolean active,
            int sortOrder
    ) {
        JobCategory category = new JobCategory();
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "parent", parent);
        ReflectionTestUtils.setField(category, "slug", slug);
        ReflectionTestUtils.setField(category, "name", name);
        ReflectionTestUtils.setField(category, "fulfillmentMode", fulfillmentMode);
        ReflectionTestUtils.setField(category, "active", active);
        ReflectionTestUtils.setField(category, "sortOrder", sortOrder);
        return category;
    }
}
