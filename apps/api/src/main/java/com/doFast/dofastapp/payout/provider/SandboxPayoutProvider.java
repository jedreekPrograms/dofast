package com.doFast.dofastapp.payout.provider;

import org.springframework.stereotype.Component;

@Component
public class SandboxPayoutProvider implements PayoutProvider {

    @Override
    public String code() {
        return "sandbox";
    }

    @Override
    public PayoutDispatchResult dispatch(PayoutDispatchCommand command) {
        if (command.payoutId() == null || command.amount() == null || command.amount().signum() <= 0) {
            return PayoutDispatchResult.definitiveFailure("INVALID_COMMAND");
        }
        return PayoutDispatchResult.success("sandbox-payout-" + command.payoutId());
    }
}
