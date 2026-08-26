package com.doFast.dofastapp.location.routing.provider;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleRoutesProviderContextTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withPropertyValues(
                    "dofast.routing.provider=google",
                    "dofast.routing.google.api-key=test-key"
            )
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void googleRoutesProviderStartsWithBootManagedRestClientBuilder() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RestClient.Builder.class);
            assertThat(context).hasSingleBean(GoogleRoutesProvider.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(GoogleRoutesProvider.class)
    static class TestConfiguration {
    }
}
