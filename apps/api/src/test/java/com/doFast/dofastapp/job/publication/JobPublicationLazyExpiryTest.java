package com.doFast.dofastapp.job.publication;

import com.doFast.dofastapp.job.category.JobCategoryRepository;
import com.doFast.dofastapp.job.service.JobService;
import com.doFast.dofastapp.location.routing.service.RouteQuoteService;
import com.doFast.dofastapp.user.entity.User;
import com.doFast.dofastapp.user.repository.UserRepository;
import com.doFast.dofastapp.wallet.enums.WalletTransactionType;
import com.doFast.dofastapp.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobPublicationLazyExpiryTest {

    private final JobPublicationRepository publicationRepository = mock(JobPublicationRepository.class);
    private final WalletService walletService = mock(WalletService.class);
    private final JobPublicationService service = new JobPublicationService(
            publicationRepository,
            mock(UserRepository.class),
            mock(JobCategoryRepository.class),
            mock(RouteQuoteService.class),
            walletService,
            mock(JobService.class),
            mock(ObjectMapper.class)
    );

    @Test
    void expiresStalePaymentRequiredPublicationAndRestoresReservationSourcesExactlyOnce() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 29, 0, 30);
        User owner = mock(User.class);
        JobPublication publication = mock(JobPublication.class);
        when(owner.getId()).thenReturn(7L);
        when(publication.getId()).thenReturn(55L);
        when(publication.getUser()).thenReturn(owner);
        when(publication.getRequestKey()).thenReturn("job-publication:7:req-55");
        when(publication.getStatus()).thenReturn(JobPublicationStatus.PAYMENT_REQUIRED);
        when(publication.getExpiresAt()).thenReturn(now.minusSeconds(1));
        when(publication.getWalletReservedAmount()).thenReturn(new BigDecimal("12.50"));

        assertThat(service.expireIfNecessary(publication, now)).isTrue();

        verify(walletService).creditRestoringOperation(
                7L,
                new BigDecimal("12.50"),
                WalletTransactionType.JOB_PUBLICATION_RELEASE,
                null,
                "job-publication:55:release",
                "job-publication:7:req-55:reserve"
        );
        verify(publication).cancel(now);
        verify(publicationRepository).save(publication);
    }

    @Test
    void leavesActivePublicationAndReservationUntouched() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 29, 0, 30);
        JobPublication publication = mock(JobPublication.class);
        when(publication.getStatus()).thenReturn(JobPublicationStatus.PAYMENT_REQUIRED);
        when(publication.getExpiresAt()).thenReturn(now.plusSeconds(1));

        assertThat(service.expireIfNecessary(publication, now)).isFalse();

        verify(publication, never()).cancel(now);
        verify(publicationRepository, never()).save(publication);
    }
}
