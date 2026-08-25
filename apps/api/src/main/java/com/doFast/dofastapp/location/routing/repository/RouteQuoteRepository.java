package com.doFast.dofastapp.location.routing.repository;

import com.doFast.dofastapp.location.routing.entity.RouteQuote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RouteQuoteRepository extends JpaRepository<RouteQuote, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from RouteQuote q join fetch q.user where q.id = :id")
    Optional<RouteQuote> findByIdForUpdate(@Param("id") UUID id);
}
