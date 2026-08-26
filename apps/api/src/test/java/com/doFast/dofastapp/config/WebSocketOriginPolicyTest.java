package com.doFast.dofastapp.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebSocketOriginPolicyTest {

    @Test
    void parsesTrimsAndDeduplicatesConfiguredOrigins() {
        assertThat(WebSocketOriginPolicy.parse("https://dofast.pl, https://app.dofast.pl,https://dofast.pl"))
                .containsExactly("https://dofast.pl", "https://app.dofast.pl");
    }

    @Test
    void rejectsGlobalWildcardOrigin() {
        assertThatThrownBy(() -> WebSocketOriginPolicy.parse("https://dofast.pl,*"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wildcard WebSocket origin");
    }

    @Test
    void rejectsBlankConfiguration() {
        assertThatThrownBy(() -> WebSocketOriginPolicy.parse(" , "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one WebSocket allowed origin");
    }
}
