package com.doFast.dofastapp.job.search.alert;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.search.SavedSearch;
import com.doFast.dofastapp.job.search.SavedSearchRepository;
import com.doFast.dofastapp.notification.enums.NotificationType;
import com.doFast.dofastapp.notification.service.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class JobPublicationAlertProcessor {

    static final int BATCH_SIZE = 20;

    private final JobPublicationOutboxRepository outboxRepository;
    private final SavedSearchRepository savedSearchRepository;
    private final SavedSearchAlertDeliveryRepository deliveryRepository;
    private final SavedSearchMatcher matcher;
    private final NotificationService notificationService;

    public JobPublicationAlertProcessor(
            JobPublicationOutboxRepository outboxRepository,
            SavedSearchRepository savedSearchRepository,
            SavedSearchAlertDeliveryRepository deliveryRepository,
            SavedSearchMatcher matcher,
            NotificationService notificationService
    ) {
        this.outboxRepository = outboxRepository;
        this.savedSearchRepository = savedSearchRepository;
        this.deliveryRepository = deliveryRepository;
        this.matcher = matcher;
        this.notificationService = notificationService;
    }

    @Transactional
    public int processBatch() {
        List<JobPublicationOutbox> events = outboxRepository.findPendingForUpdate(PageRequest.of(0, BATCH_SIZE));
        if (events.isEmpty()) return 0;

        List<SavedSearch> alertSearches = savedSearchRepository.findAllAlertEnabled();
        LocalDateTime processedAt = LocalDateTime.now();

        for (JobPublicationOutbox event : events) {
            Job job = event.getJob();
            for (SavedSearch savedSearch : alertSearches) {
                if (!matcher.matches(savedSearch, job)) continue;
                if (deliveryRepository.existsBySavedSearch_IdAndJob_Id(savedSearch.getId(), job.getId())) continue;

                deliveryRepository.save(new SavedSearchAlertDelivery(savedSearch, job));
                notificationService.notify(
                        savedSearch.getUser(),
                        NotificationType.SAVED_SEARCH_MATCH,
                        "Nowe zlecenie pasuje do wyszukiwania",
                        "Nowe zlecenie „" + job.getTitle() + "” pasuje do zapisanego wyszukiwania „" + savedSearch.getName() + "”.",
                        job,
                        null
                );
            }
            event.markProcessed(processedAt);
        }

        return events.size();
    }
}
