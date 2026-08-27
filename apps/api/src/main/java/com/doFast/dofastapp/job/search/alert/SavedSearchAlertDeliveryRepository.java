package com.doFast.dofastapp.job.search.alert;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedSearchAlertDeliveryRepository extends JpaRepository<SavedSearchAlertDelivery, Long> {
    boolean existsBySavedSearch_IdAndJob_Id(Long savedSearchId, Long jobId);
}
