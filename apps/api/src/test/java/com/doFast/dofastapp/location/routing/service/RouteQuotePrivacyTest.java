package com.doFast.dofastapp.location.routing.service;

import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.location.routing.dto.RoutePointRequest;
import com.doFast.dofastapp.location.routing.dto.RouteQuoteRequest;
import com.doFast.dofastapp.location.routing.entity.RouteQuote;
import com.doFast.dofastapp.location.routing.provider.RouteProvider;
import com.doFast.dofastapp.location.routing.provider.RouteProviderResult;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteQuotePrivacyTest {

    @Mock private RouteQuoteRepository routeQuoteRepository;
    @Mock private RouteProvider routeProvider;

    private RouteQuoteService service;
    private User owner;
    private User outsider;
    private UUID quoteId;

    @BeforeEach
    void setUp() {
        service = new RouteQuoteService(routeQuoteRepository, routeProvider, 15);
        owner = user(7L);
        outsider = user(8L);
        quoteId = UUID.randomUUID();
    }

    @Test
    void outsiderCannotEnumerateExistingExactQuoteThroughRead() {
        when(routeQuoteRepository.findByIdAndUser_Id(quoteId, 8L)).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> service.getQuote(quoteId, outsider)
        );

        assertEquals("Wycena trasy nie istnieje", error.getMessage());
        verify(routeQuoteRepository, never()).findById(quoteId);
        verify(routeQuoteRepository, never()).findByIdForUpdate(quoteId);
    }

    @Test
    void missingOrTransientOwnerCannotCreateQuoteOrSpendRoutingBudget() {
        RouteQuoteRequest request = new RouteQuoteRequest(
                point("51.1128", "17.0601", "Wrocław, Śródmieście"),
                List.of(),
                point("51.1090", "17.0320", "Wrocław, Stare Miasto")
        );
        User transientUser = new User("transient@example.com", "transient");

        assertThrows(ResourceNotFoundException.class, () -> service.createQuote(request, null));
        assertThrows(ResourceNotFoundException.class, () -> service.createQuote(request, transientUser));

        verifyNoInteractions(routeProvider, routeQuoteRepository);
    }

    @Test
    void outsiderCannotEnumerateQuoteThroughModeComparisonOrSpendRoutingBudget() {
        when(routeQuoteRepository.findByIdAndUser_Id(quoteId, 8L)).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> service.getModeComparison(quoteId, outsider)
        );

        assertEquals("Wycena trasy nie istnieje", error.getMessage());
        verifyNoInteractions(routeProvider);
        verify(routeQuoteRepository, never()).findById(quoteId);
        verify(routeQuoteRepository, never()).findByIdForUpdate(quoteId);
    }

    @Test
    void outsiderCannotEnumerateOrConsumeQuoteThroughMutationPath() {
        when(routeQuoteRepository.findOwnedByIdForUpdate(quoteId, 8L)).thenReturn(Optional.empty());

        ResourceNotFoundException error = assertThrows(
                ResourceNotFoundException.class,
                () -> service.consume(quoteId, outsider)
        );

        assertEquals("Wycena trasy nie istnieje", error.getMessage());
        verify(routeQuoteRepository, never()).findByIdForUpdate(quoteId);
        verify(routeQuoteRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ownerStillReceivesExactPrivateRouteDataThroughScopedRead() {
        RouteQuote quote = exactQuote();
        when(routeQuoteRepository.findByIdAndUser_Id(quoteId, 7L)).thenReturn(Optional.of(quote));

        var response = service.getQuote(quoteId, owner);

        assertEquals(quoteId, response.id());
        assertEquals(new BigDecimal("51.1128"), response.origin().latitude());
        assertEquals(new BigDecimal("17.0601"), response.origin().longitude());
        assertEquals("ul. Grunwaldzka 10, wejście A", response.origin().privateLabel());
        assertEquals("origin-place", response.origin().placeId());
        assertEquals("ul. Oławska 20, lokal 4", response.destination().privateLabel());
        assertEquals("destination-place", response.destination().placeId());
        assertEquals("encoded-private-route", response.encodedPolyline());
    }

    private RouteQuote exactQuote() {
        LocalDateTime now = LocalDateTime.now();
        RouteQuote quote = new RouteQuote();
        quote.initialize(
                quoteId,
                owner,
                GeoPointFactory.from(new BigDecimal("51.1128"), new BigDecimal("17.0601")),
                "Wrocław, Śródmieście",
                "ul. Grunwaldzka 10, wejście A",
                "origin-place",
                GeoPointFactory.from(new BigDecimal("51.1090"), new BigDecimal("17.0320")),
                "Wrocław, Stare Miasto",
                "ul. Oławska 20, lokal 4",
                "destination-place",
                new RouteProviderResult(4_200, 600, "encoded-private-route", "GOOGLE_ROUTES"),
                now,
                now.plusMinutes(15)
        );
        return quote;
    }

    private RoutePointRequest point(String latitude, String longitude, String label) {
        return new RoutePointRequest(
                new BigDecimal(latitude),
                new BigDecimal(longitude),
                label,
                label,
                null
        );
    }

    private User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
