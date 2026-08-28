package com.doFast.dofastapp.job.attachment;

import com.doFast.dofastapp.job.entity.Job;
import com.doFast.dofastapp.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

@Entity
@Table(name = "job_attachments")
public class JobAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    private int version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobAttachmentVisibility visibility;

    @Column(name = "original_filename", nullable = false, length = 180)
    private String originalFilename;

    @Column(name = "media_type", nullable = false, length = 80)
    private String mediaType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, unique = true, length = 64)
    private String storageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected JobAttachment() {}

    public JobAttachment(
            Job job,
            User uploadedBy,
            JobAttachmentVisibility visibility,
            String originalFilename,
            String mediaType,
            long sizeBytes,
            String sha256,
            String storageKey,
            LocalDateTime createdAt
    ) {
        this.job = job;
        this.uploadedBy = uploadedBy;
        this.visibility = visibility;
        this.originalFilename = originalFilename;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.createdAt = createdAt;
    }

    public void markDeleted(LocalDateTime at) {
        if (deletedAt == null) deletedAt = at;
    }

    public Long getId() { return id; }
    public Job getJob() { return job; }
    public User getUploadedBy() { return uploadedBy; }
    public JobAttachmentVisibility getVisibility() { return visibility; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMediaType() { return mediaType; }
    public Long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStorageKey() { return storageKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
}
