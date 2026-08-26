package com.doFast.dofastapp.location.routing.service;

import com.doFast.dofastapp.common.exception.ConflictException;
import com.doFast.dofastapp.location.routing.dto.RoutePointRequest;
import com.doFast.dofastapp.location.routing.dto.RouteQuoteRequest;
import com.doFast.dofastapp.location.routing.dto.RouteQuoteResponse;
import com.doFast.dofastapp.location.routing.provider.RouteCoordinate;
import com.doFast.dofastapp.location.routing.provider.RouteProvider;
import com.doFast.dofastapp.location.routing.provider.RouteProviderResult;
import com.doFast.dofastapp.location.routing.provider.RouteTravelMode;
import com.doFast.dofastapp.location.routing.repository.RouteQuoteRepository;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteQuoteMultiStopTest {

    @Mock private RouteQuoteRepository routeQuoteRepository;
    @Mock private RouteProvider routeProvider;

    private RouteQuoteService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RouteQuoteService(routeQuoteRepository, routeProvider, 15);
        user = new User();
        ReflectionTestUtils.setField(user, "id", 9L);
    }

    @Test
    void createQuotePreservesStopOrderAndEstimatesWholeRoute() {
        RoutePointRequest origin = point("51.1128", "17.0601", "A", "Origin");
        RoutePointRequest firstStop = point("51.1110", "17.0500", "S1", "Stop one");
        RoutePointRequest secondStop = point("51.1100", "17.0400", "S2", "Stop two");
        RoutePointRequest destination = point("51.1090", "17.0320", "B", "Destination");
        List<RouteCoordinate> intermediates = List.of(
                new RouteCoordinate(51.1110, 17.0500),
                new RouteCoordinate(51.1100, 17.0400)
        );

        when(routeProvider.estimate(
                new RouteCoordinate(51.1128, 17.0601),
                intermediates,
                new RouteCoordinate(51.1090, 17.0320),
                RouteTravelMode.DRIVE
        )).thenReturn(new RouteProviderResult(5_200, 840, "polyline", "GOOGLE_ROUTES"));
        when(routeQuoteRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RouteQuoteResponse response = service.createQuote(
                new RouteQuoteRequest(origin, List.of(firstStop, secondStop), destination),
                user
        );

        assertEquals(2, response.stops().size());
        assertEquals("Stop one", response.stops().get(0).privateLabel());
        assertEquals("Stop two", response.stops().get(1).privateLabel());
        assertEquals(5_200, response.distanceMeters());
        assertEquals(840, response.durationSeconds());
        verify(routeProvider).estimate(
                new RouteCoordinate(51.1128, 17.0601),
                intermediates,
                new RouteCoordinate(51.1090, 17.0320),
                RouteTravelMode.DRIVE
        );
    }

    @Test
    void createQuoteRejectsTwoConsecutivePointsAtSameCoordinates() {
        RoutePointRequest origin = point("51.1128", "17.0601", "A", "Origin");
        RoutePointRequest duplicate = point("51.1128", "17.0601", "S1", "Same place");
        RoutePointRequest destination = point("51.1090", "17.0320", "B", "Destination");

        assertThrows(
                ConflictException.class,
                () -> service.createQuote(new RouteQuoteRequest(origin, List.of(duplicate), destination), user)
        );

        verifyNoInteractions(routeProvider, routeQuoteRepository);
    }

    private RoutePointRequest point(String latitude, String longitude, String publicLabel, String privateLabel) {
        return new RoutePointRequest(
                new BigDecimal(latitude),
                new BigDecimal(longitude),
                publicLabel,
                privateLabel,
                null
        );
    }
}
