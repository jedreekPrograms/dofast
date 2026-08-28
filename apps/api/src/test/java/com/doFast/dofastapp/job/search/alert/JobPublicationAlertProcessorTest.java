package com.doFast.dofastapp.job.search.alert;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.search.SavedSearch;
import com.doFast.dofastapp.job.search.SavedSearchRepository;
import com.doFast.dofastapp.notification.service.NotificationService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.service.UserBlockService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPublicationAlertProcessorTest {

    @Test
    void blockedRelationshipDoesNotCreateDeliveryOrNotification() {
        JobPublicationOutboxRepository outboxRepository = mock(JobPublicationOutboxRepository.class);
        SavedSearchRepository savedSearchRepository = mock(SavedSearchRepository.class);
        SavedSearchAlertDeliveryRepository deliveryRepository = mock(SavedSearchAlertDeliveryRepository.class);
        SavedSearchMatcher matcher = mock(SavedSearchMatcher.class);
        NotificationService notificationService = mock(NotificationService.class);
        UserBlockService userBlockService = mock(UserBlockService.class);

        User jobAuthor = new User("author@example.com", "author");
        User alertOwner = new User("owner@example.com", "owner");
        Job job = new Job();
        job.setCreatedBy(jobAuthor);
        job.setTitle("Blocked author job");
        SavedSearch savedSearch = new SavedSearch(alertOwner);
        savedSearch.setName("My alert");
        JobPublicationOutbox event = new JobPublicationOutbox(job);

        when(outboxRepository.findPendingForUpdate(any(Pageable.class))).thenReturn(List.of(event));
        when(savedSearchRepository.findAllAlertEnabled()).thenReturn(List.of(savedSearch));
        when(matcher.matches(savedSearch, job)).thenReturn(true);
        when(userBlockService.isInteractionBlocked(alertOwner, jobAuthor)).thenReturn(true);

        JobPublicationAlertProcessor processor = new JobPublicationAlertProcessor(
                outboxRepository,
                savedSearchRepository,
                deliveryRepository,
                matcher,
                notificationService,
                userBlockService
        );

        int processed = processor.processBatch();

        assertThat(processed).isEqualTo(1);
        assertThat(event.getProcessedAt()).isNotNull();
        verify(deliveryRepository, never()).save(any());
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }
}
