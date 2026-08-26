package com.doFast.dofastapp.location.routing.service;

import com.doFast.dofastapp.location.routing.dto.RouteModeComparisonResponse;
import com.doFast.dofastapp.location.routing.entity.RouteQuote;
import com.doFast.dofastapp.location.routing.provider.RouteCoordinate;
import com.doFast.dofastapp.location.routing.provider.RouteProvider;
import com.doFast.dofastapp.location.routing.provider.RouteProviderResult;
import com.doFast.dofastapp.location.routing.provider.RouteTravelMode;
import com.doFast.dofastapp.location.routing.repository.RouteQuoteRepository;
import com.doFast.dofastapp.location.service.GeoPointFactory;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteQuoteModeComparisonTest {

    @Mock private RouteQuoteRepository routeQuoteRepository;
    @Mock private RouteProvider routeProvider;

    private RouteQuoteService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RouteQuoteService(routeQuoteRepository, routeProvider, 15);
        user = new User();
        ReflectionTestUtils.setField(user, "id", 7L);
    }

    @Test
    void comparisonReusesStoredDriveEstimateAndComputesOnlyNonDrivingModes() {
        UUID quoteId = UUID.randomUUID();
        RouteQuote quote = quote(quoteId);
        RouteCoordinate origin = new RouteCoordinate(51.1128, 17.0601);
        RouteCoordinate destination = new RouteCoordinate(51.1090, 17.0320);

        when(routeQuoteRepository.findById(quoteId)).thenReturn(Optional.of(quote));
        when(routeProvider.estimate(origin, destination, RouteTravelMode.BICYCLE))
                .thenReturn(new RouteProviderResult(3_900, 900, null, "GOOGLE_ROUTES"));
        when(routeProvider.estimate(origin, destination, RouteTravelMode.WALK))
                .thenReturn(new RouteProviderResult(3_700, 2_800, null, "GOOGLE_ROUTES"));

        RouteModeComparisonResponse response = service.getModeComparison(quoteId, user);

        assertEquals(3, response.estimates().size());
        assertEquals(RouteTravelMode.DRIVE, response.estimates().get(0).mode());
        assertEquals(4_200, response.estimates().get(0).distanceMeters());
        assertEquals(600, response.estimates().get(0).durationSeconds());
        assertTrue(response.estimates().stream().allMatch(estimate -> estimate.available()));
        verify(routeProvider, never()).estimate(origin, destination, RouteTravelMode.DRIVE);
        verify(routeProvider).estimate(origin, destination, RouteTravelMode.BICYCLE);
        verify(routeProvider).estimate(origin, destination, RouteTravelMode.WALK);
    }

    private RouteQuote quote(UUID id) {
        LocalDateTime now = LocalDateTime.now();
        RouteQuote quote = new RouteQuote();
        quote.initialize(
                id,
                user,
                GeoPointFactory.from(new BigDecimal("51.1128"), new BigDecimal("17.0601")),
                "Wrocław, Śródmieście",
                "Origin",
                "origin-place",
                GeoPointFactory.from(new BigDecimal("51.1090"), new BigDecimal("17.0320")),
                "Wrocław, Stare Miasto",
                "Destination",
                "destination-place",
                new RouteProviderResult(4_200, 600, "encoded", "GOOGLE_ROUTES"),
                now,
                now.plusMinutes(15)
        );
        return quote;
    }
}
