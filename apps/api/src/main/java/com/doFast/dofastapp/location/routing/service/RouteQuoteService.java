package com.doFast.dofastapp.location.routing.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.common.exception.ForbiddenOperationException;
import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.location.routing.dto.RouteModeComparisonResponse;
import com.doFast.dofastapp.location.routing.dto.RouteModeEstimateResponse;
import com.doFast.dofastapp.location.routing.dto.RoutePointRequest;
import com.doFast.dofastapp.location.routing.dto.RoutePointResponse;
import com.doFast.dofastapp.location.routing.dto.RouteQuoteRequest;
import com.doFast.dofastapp.location.routing.dto.RouteQuoteResponse;
import com.doFast.dofastapp.location.routing.entity.RouteQuote;
import com.doFast.dofastapp.location.routing.entity.RouteQuoteStop;
import com.doFast.dofastapp.location.routing.exception.RoutingProviderException;
import com.doFast.dofastapp.location.routing.provider.RouteCoordinate;
import com.doFast.dofastapp.location.routing.provider.RouteProvider;
import com.doFast.dofastapp.location.routing.provider.RouteProviderResult;
import com.doFast.dofastapp.location.routing.provider.RouteTravelMode;
import com.doFast.dofastapp.location.routing.repository.RouteQuoteRepository;
import com.doFast.dofastapp.location.service.GeoPointFactory;
import com.doFast.dofastapp.user.entity.User;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
        List<RoutePointRequest> stops = request.stops();
        RoutePointRequest destination = request.destination();
        validateRouteSequence(origin, stops, destination);

        RouteProviderResult estimate = routeProvider.estimate(
                coordinate(origin),
                stops.stream().map(this::coordinate).toList(),
                coordinate(destination),
                RouteTravelMode.DRIVE
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
        for (RoutePointRequest stop : stops) {
            quote.addStop(
                    GeoPointFactory.from(stop.latitude(), stop.longitude()),
                    stop.publicLabel().trim(),
                    normalizeOptional(stop.privateLabel()),
                    normalizeOptional(stop.placeId())
            );
        }

        return toResponse(routeQuoteRepository.save(quote));
    }

    @Transactional(readOnly = true)
    public RouteQuoteResponse getQuote(UUID quoteId, User user) {
        RouteQuote quote = findOwnedQuote(quoteId, user);
        return toResponse(quote);
    }

    @Transactional(readOnly = true)
    public RouteModeComparisonResponse getModeComparison(UUID quoteId, User user) {
        RouteQuote quote = findOwnedQuote(quoteId, user);
        if (!quote.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new ConflictException("Wycena trasy wygasła. Wyznacz trasę ponownie.");
        }

        RouteCoordinate origin = coordinate(quote.getOrigin());
        List<RouteCoordinate> intermediates = quote.getStops().stream()
                .map(RouteQuoteStop::getLocation)
                .map(this::coordinate)
                .toList();
        RouteCoordinate destination = coordinate(quote.getDestination());
        List<RouteModeEstimateResponse> estimates = new ArrayList<>();
        estimates.add(new RouteModeEstimateResponse(
                RouteTravelMode.DRIVE,
                quote.getDistanceMeters(),
                quote.getDurationSeconds(),
                true
        ));
        estimates.add(estimateMode(origin, intermediates, destination, RouteTravelMode.BICYCLE));
        estimates.add(estimateMode(origin, intermediates, destination, RouteTravelMode.WALK));

        return new RouteModeComparisonResponse(quote.getId(), List.copyOf(estimates), true);
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

    private RouteModeEstimateResponse estimateMode(
            RouteCoordinate origin,
            List<RouteCoordinate> intermediates,
            RouteCoordinate destination,
            RouteTravelMode mode
    ) {
        try {
            RouteProviderResult result = routeProvider.estimate(origin, intermediates, destination, mode);
            return new RouteModeEstimateResponse(mode, result.distanceMeters(), result.durationSeconds(), true);
        } catch (RoutingProviderException ex) {
            return new RouteModeEstimateResponse(mode, null, null, false);
        }
    }

    private RouteQuote findOwnedQuote(UUID quoteId, User user) {
        RouteQuote quote = routeQuoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Wycena trasy nie istnieje"));
        assertOwner(quote, user);
        return quote;
    }

    private void assertOwner(RouteQuote quote, User user) {
        if (user == null || user.getId() == null || !user.getId().equals(quote.getUser().getId())) {
            throw new ForbiddenOperationException("Ta wycena trasy należy do innego użytkownika");
        }
    }

    private void validateRouteSequence(
            RoutePointRequest origin,
            List<RoutePointRequest> stops,
            RoutePointRequest destination
    ) {
        List<RoutePointRequest> points = new ArrayList<>(stops.size() + 2);
        points.add(origin);
        points.addAll(stops);
        points.add(destination);
        for (int index = 1; index < points.size(); index++) {
            if (sameCoordinates(points.get(index - 1), points.get(index))) {
                throw new ConflictException("Kolejne punkty trasy muszą być różne");
            }
        }
    }

    private boolean sameCoordinates(RoutePointRequest first, RoutePointRequest second) {
        return first.latitude().compareTo(second.latitude()) == 0
                && first.longitude().compareTo(second.longitude()) == 0;
    }

    private RouteCoordinate coordinate(RoutePointRequest point) {
        return new RouteCoordinate(point.latitude().doubleValue(), point.longitude().doubleValue());
    }

    private RouteCoordinate coordinate(Point point) {
        return new RouteCoordinate(point.getY(), point.getX());
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
                pointResponse(
                        quote.getOrigin(),
                        quote.getOriginPublicLabel(),
                        quote.getOriginPrivateLabel(),
                        quote.getOriginPlaceId()
                ),
                quote.getStops().stream()
                        .map(stop -> pointResponse(
                                stop.getLocation(),
                                stop.getPublicLabel(),
                                stop.getPrivateLabel(),
                                stop.getPlaceId()
                        ))
                        .toList(),
                pointResponse(
                        quote.getDestination(),
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

    private RoutePointResponse pointResponse(
            Point point,
            String publicLabel,
            String privateLabel,
            String placeId
    ) {
        return new RoutePointResponse(
                BigDecimal.valueOf(point.getY()),
                BigDecimal.valueOf(point.getX()),
                publicLabel,
                privateLabel,
                placeId
        );
    }
}
