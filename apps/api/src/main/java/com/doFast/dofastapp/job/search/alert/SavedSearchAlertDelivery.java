package com.doFast.dofastapp.job.search.alert;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.job.search.SavedSearch;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "saved_search_alert_deliveries")
public class SavedSearchAlertDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saved_search_id", nullable = false)
    private SavedSearch savedSearch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected SavedSearchAlertDelivery() {}

    public SavedSearchAlertDelivery(SavedSearch savedSearch, Job job) {
        this.savedSearch = savedSearch;
        this.job = job;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
}
