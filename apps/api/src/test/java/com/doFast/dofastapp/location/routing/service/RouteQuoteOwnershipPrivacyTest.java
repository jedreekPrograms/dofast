package com.doFast.dofastapp.location.routing.service;

import com.doFast.dofastapp.common.exception.ResourceNotFoundException;
import com.doFast.dofastapp.location.routing.provider.RouteProvider;
import com.doFast.dofastapp.location.routing.repository.RouteQuoteRepository;
import com.doFast.dofastapp.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteQuoteOwnershipPrivacyTest {

    @Mock private RouteQuoteRepository routeQuoteRepository;
    @Mock private RouteProvider routeProvider;

    private RouteQuoteService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RouteQuoteService(routeQuoteRepository, routeProvider, 15);
        user = new User();
        ReflectionTestUtils.setField(user, "id", 41L);
    }

    @Test
    void readUsesOwnerScopedLookupAndReturnsNeutralNotFoundWhenQuoteIsNotOwned() {
        UUID quoteId = UUID.randomUUID();
        when(routeQuoteRepository.findOwnedById(quoteId, 41L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getQuote(quoteId, user));

        verify(routeQuoteRepository).findOwnedById(quoteId, 41L);
        verify(routeQuoteRepository, never()).findById(quoteId);
    }

    @Test
    void modeComparisonUsesSameOwnerScopedLookupBeforeCallingRoutingProvider() {
        UUID quoteId = UUID.randomUUID();
        when(routeQuoteRepository.findOwnedById(quoteId, 41L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getModeComparison(quoteId, user));

        verify(routeQuoteRepository).findOwnedById(quoteId, 41L);
        verify(routeProvider, never()).estimate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void consumeUsesOwnerScopedPessimisticLookupAndReturnsNeutralNotFoundWhenQuoteIsNotOwned() {
        UUID quoteId = UUID.randomUUID();
        when(routeQuoteRepository.findOwnedByIdForUpdate(quoteId, 41L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.consume(quoteId, user));

        verify(routeQuoteRepository).findOwnedByIdForUpdate(quoteId, 41L);
        verify(routeQuoteRepository, never()).findByIdForUpdate(quoteId);
    }

    @Test
    void missingAuthenticationFailsClosedWithoutGlobalQuoteLookup() {
        UUID quoteId = UUID.randomUUID();

        assertThrows(ResourceNotFoundException.class, () -> service.getQuote(quoteId, null));

        verify(routeQuoteRepository, never()).findById(quoteId);
        verify(routeQuoteRepository, never()).findOwnedById(
                org.mockito.ArgumentMatchers.eq(quoteId),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }
}
