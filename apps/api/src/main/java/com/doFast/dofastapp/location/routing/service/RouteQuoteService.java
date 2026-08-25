package com.doFast.dofastapp.location.routing.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.location.routing.dto.RoutePointRequest;
import com.doFast.dofastapp.location.routing.dto.RoutePointResponse;
import com.doFast.dofastapp.location.routing.dto.RouteQuoteRequest;
import com.doFast.dofastapp.location.routing.dto.RouteQuoteResponse;
import com.doFast.dofastapp.location.routing.entity.RouteQuote;
import com.doFast.dofastapp.location.routing.provider.RouteCoordinate;
import com.doFast.dofastapp.location.routing.provider.RouteProvider;
import com.doFast.dofastapp.location.routing.provider.RouteProviderResult;
import com.doFast.dofastapp.location.routing.repository.RouteQuoteRepository;
import com.doFast.dofastapp.location.service.GeoPointFactory;
import com.doFast.dofastapp.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RouteQuoteService {

    private final RouteQuoteRepository routeQuoteRepository;
    private final RouteProvider routeProvider;
    private final long quoteTtlMinutes;

    public RouteQuoteService(
            RouteQuoteRepository routeQuoteRepository,
            RouteProvider routeProvider,
            @Value("${dofast.routing.quote-ttl-minutes:15}") long quoteTtlMinutes
    ) {
        this.routeQuoteRepository = routeQuoteRepository;
        this.routeProvider = routeProvider;
        this.quoteTtlMinutes = quoteTtlMinutes;
    }

    @Transactional
    public RouteQuoteResponse createQuote(RouteQuoteRequest request, User user) {
        RoutePointRequest origin = request.origin();
        RoutePointRequest destination = request.destination();

        if (sameCoordinates(origin, destination)) {
            throw new ConflictException("Punkt A i punkt B muszą być różne");
        }

        RouteProviderResult estimate = routeProvider.estimate(
                new RouteCoordinate(origin.latitude().doubleValue(), origin.longitude().doubleValue()),
                new RouteCoordinate(destination.latitude().doubleValue(), destination.longitude().doubleValue())
        );

        LocalDateTime now = LocalDateTime.now();
        RouteQuote quote = new RouteQuote();
        quote.initialize(
                UUID.randomUUID(),
                user,
                GeoPointFactory.from(origin.latitude(), origin.longitude()),
                origin.publicLabel().trim(),
                normalizeOptional(origin.privateLabel()),
                normalizeOptional(origin.placeId()),
                GeoPointFactory.from(destination.latitude(), destination.longitude()),
                destination.publicLabel().trim(),
                normalizeOptional(destination.privateLabel()),
                normalizeOptional(destination.placeId()),
                estimate,
                now,
                now.plusMinutes(quoteTtlMinutes)
        );

        return toResponse(routeQuoteRepository.save(quote));
    }

    @Transactional(readOnly = true)
    public RouteQuoteResponse getQuote(UUID quoteId, User user) {
        RouteQuote quote = routeQuoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Wycena trasy nie istnieje"));
        assertOwner(quote, user);
        return toResponse(quote);
    }

    @Transactional
    public RouteQuote consume(UUID quoteId, User user) {
        RouteQuote quote = routeQuoteRepository.findByIdForUpdate(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Wycena trasy nie istnieje"));
        assertOwner(quote, user);

        LocalDateTime now = LocalDateTime.now();
        if (quote.getConsumedAt() != null) {
            throw new ConflictException("Ta wycena trasy została już użyta");
        }
        if (!quote.getExpiresAt().isAfter(now)) {
            throw new ConflictException("Wycena trasy wygasła. Wyznacz trasę ponownie.");
        }

        quote.markConsumed(now);
        return routeQuoteRepository.save(quote);
    }

    private void assertOwner(RouteQuote quote, User user) {
        if (user == null || user.getId() == null || !user.getId().equals(quote.getUser().getId())) {
            throw new ForbiddenOperationException("Ta wycena trasy należy do innego użytkownika");
        }
    }

    private boolean sameCoordinates(RoutePointRequest first, RoutePointRequest second) {
        return first.latitude().compareTo(second.latitude()) == 0
                && first.longitude().compareTo(second.longitude()) == 0;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private RouteQuoteResponse toResponse(RouteQuote quote) {
        return new RouteQuoteResponse(
                quote.getId(),
                new RoutePointResponse(
                        BigDecimal.valueOf(quote.getOrigin().getY()),
                        BigDecimal.valueOf(quote.getOrigin().getX()),
                        quote.getOriginPublicLabel(),
                        quote.getOriginPrivateLabel(),
                        quote.getOriginPlaceId()
                ),
                new RoutePointResponse(
                        BigDecimal.valueOf(quote.getDestination().getY()),
                        BigDecimal.valueOf(quote.getDestination().getX()),
                        quote.getDestinationPublicLabel(),
                        quote.getDestinationPrivateLabel(),
                        quote.getDestinationPlaceId()
                ),
                quote.getDistanceMeters(),
                quote.getDurationSeconds(),
                quote.getEncodedPolyline(),
                quote.getProvider(),
                quote.getCreatedAt(),
                quote.getExpiresAt()
        );
    }
}
