package com.doFast.dofastapp.location.retention;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class ExactLocationRetentionService {

    private final ExactLocationRetentionRepository repository;
    private final ExactLocationRetentionProperties properties;
    private final Clock clock;

    @Autowired
    public ExactLocationRetentionService(
            ExactLocationRetentionRepository repository,
            ExactLocationRetentionProperties properties
    ) {
        this(repository, properties, Clock.systemUTC());
    }

    ExactLocationRetentionService(
            ExactLocationRetentionRepository repository,
            ExactLocationRetentionProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public CleanupResult cleanup() {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDateTime cutoff = now.minus(properties.retention());
        int purgedJobs = repository.purgeDueJobs(cutoff, properties.batchSize());
        int deletedRouteQuotes = repository.deleteExpiredUnreferencedRouteQuotes(now, properties.batchSize());
        return new CleanupResult(purgedJobs, deletedRouteQuotes);
    }

    public record CleanupResult(int purgedJobs, int deletedRouteQuotes) {}
}
