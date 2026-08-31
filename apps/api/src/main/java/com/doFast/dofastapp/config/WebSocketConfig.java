package com.doFast.dofastapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketSecurityInterceptor securityInterceptor;
    private final WebSocketInboundRateLimitInterceptor rateLimitInterceptor;
    private final WebSocketOriginPolicy originPolicy;

    public WebSocketConfig(
            WebSocketSecurityInterceptor securityInterceptor,
            WebSocketInboundRateLimitInterceptor rateLimitInterceptor,
            WebSocketOriginPolicy originPolicy
    ) {
        this.securityInterceptor = securityInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.originPolicy = originPolicy;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/dofastapp");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Authentication/authorization must run first so rate limiting can use the trusted principal.
        registration.interceptors(securityInterceptor, rateLimitInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] allowedOrigins = originPolicy.allowedOriginPatterns();

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins);

        registry.addEndpoint("/ws-sockjs")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }
}
