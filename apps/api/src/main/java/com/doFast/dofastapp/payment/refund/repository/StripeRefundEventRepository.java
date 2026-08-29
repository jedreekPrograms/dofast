package com.doFast.dofastapp.payment.refund.repository;

import com.doFast.dofastapp.payment.refund.entity.StripeRefundEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StripeRefundEventRepository extends JpaRepository<StripeRefundEvent, String> {
}
