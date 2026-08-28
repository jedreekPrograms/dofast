package com.doFast.dofastapp.payout.provider;

public interface PayoutProvider {
    String code();
    PayoutDispatchResult dispatch(PayoutDispatchCommand command);
}
