package com.doFast.dofastapp.job.category;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobCategoryServiceTest {

    @Test
    void buildsOrderedHierarchyAndKeepsFulfillmentModeOnLeaves() {
        JobCategoryRepository repository = mock(JobCategoryRepository.class);
        JobCategory root = category(1L, null, "transport", "Transport", null, 10);
        JobCategory onSite = category(2L, root, "wnoszenie", "Wnoszenie", FulfillmentMode.ON_SITE, 10);
        JobCategory pointToPoint = category(3L, root, "przeprowadzka", "Przeprowadzka", FulfillmentMode.POINT_TO_POINT, 20);
        when(repository.findByActiveTrueOrderBySortOrderAscNameAsc()).thenReturn(List.of(root, onSite, pointToPoint));

        List<JobCategoryResponse> result = new JobCategoryService(repository).getCatalog();

        assertThat(result).hasSize(1);
        JobCategoryResponse transport = result.getFirst();
        assertThat(transport.slug()).isEqualTo("transport");
        assertThat(transport.fulfillmentMode()).isNull();
        assertThat(transport.children()).extracting(JobCategoryResponse::slug)
                .containsExactly("wnoszenie", "przeprowadzka");
        assertThat(transport.children()).extracting(JobCategoryResponse::fulfillmentMode)
                .containsExactly(FulfillmentMode.ON_SITE, FulfillmentMode.POINT_TO_POINT);
    }

    private static JobCategory category(Long id, JobCategory parent, String slug, String name,
                                        FulfillmentMode mode, int sortOrder) {
        JobCategory category = new JobCategory();
        ReflectionTestUtils.setField(category, "id", id);
        ReflectionTestUtils.setField(category, "parent", parent);
        ReflectionTestUtils.setField(category, "slug", slug);
        ReflectionTestUtils.setField(category, "name", name);
        ReflectionTestUtils.setField(category, "fulfillmentMode", mode);
        ReflectionTestUtils.setField(category, "active", true);
        ReflectionTestUtils.setField(category, "sortOrder", sortOrder);
        return category;
    }
}
