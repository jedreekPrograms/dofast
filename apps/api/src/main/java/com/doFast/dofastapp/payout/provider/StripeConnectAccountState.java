package com.doFast.dofastapp.payout.provider;

public record StripeConnectAccountState(
        boolean detailsSubmitted,
        boolean payoutsEnabled,
        boolean transfersEnabled,
        boolean requirementsDue
) {
    public boolean readyForPayout() {
        return detailsSubmitted && payoutsEnabled && transfersEnabled && !requirementsDue;
    }
}
