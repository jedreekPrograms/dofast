package com.doFast.dofastapp.payout.provider;

import com.doFast.dofastapp.common.exception.BusinessException;
import com.doFast.dofastapp.payout.config.PayoutProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PayoutProviderRegistry {

    private final PayoutProperties properties;
    private final Map<String, PayoutProvider> providers;

    public PayoutProviderRegistry(PayoutProperties properties, List<PayoutProvider> providers) {
        this.properties = properties;
        Map<String, PayoutProvider> indexed = new HashMap<>();
        for (PayoutProvider provider : providers) {
            PayoutProvider previous = indexed.put(provider.code(), provider);
            if (previous != null) {
                throw new IllegalStateException("Duplicate payout provider code: " + provider.code());
            }
        }
        this.providers = Map.copyOf(indexed);
    }

    public boolean isConfiguredProviderAvailable() {
        return isProviderAvailable(properties.provider());
    }

    public boolean isProviderAvailable(String code) {
        if (code == null || "disabled".equals(code)) return false;
        if ("sandbox".equals(code) && !properties.sandboxEnabled()) return false;
        return providers.containsKey(code);
    }

    public String configuredProviderCode() {
        return properties.provider();
    }

    public String providerCodeForNewRequest() {
        String code = properties.provider();
        if (!isProviderAvailable(code)) {
            throw new BusinessException("Wypłaty są obecnie niedostępne");
        }
        return code;
    }

    public PayoutProvider requireProvider(String code) {
        if (!isProviderAvailable(code)) {
            throw new BusinessException("Provider wypłat jest obecnie niedostępny");
        }
        return providers.get(code);
    }

    public String providerMode() {
        return properties.providerMode();
    }
}
