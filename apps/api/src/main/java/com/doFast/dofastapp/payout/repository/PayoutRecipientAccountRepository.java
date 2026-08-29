package com.doFast.dofastapp.payout.repository;

import com.doFast.dofastapp.payout.entity.PayoutRecipientAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PayoutRecipientAccountRepository extends JpaRepository<PayoutRecipientAccount, Long> {

    Optional<PayoutRecipientAccount> findByUser_IdAndProviderCode(Long userId, String providerCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from PayoutRecipientAccount account where account.user.id = :userId and account.providerCode = :providerCode")
    Optional<PayoutRecipientAccount> findForUpdate(@Param("userId") Long userId, @Param("providerCode") String providerCode);
}
