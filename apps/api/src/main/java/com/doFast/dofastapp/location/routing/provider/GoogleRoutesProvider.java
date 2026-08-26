package com.doFast.dofastapp.location.routing.provider;

import com.doFast.dofastapp.location.routing.exception.RoutingProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "dofast.routing", name = "provider", havingValue = "google")
public class GoogleRoutesProvider implements RouteProvider {

    private static final String COMPUTE_ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";
    private static final String FIELD_MASK = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline";

    private final RestClient restClient;
    private final String apiKey;

    public GoogleRoutesProvider(
            RestClient.Builder restClientBuilder,
            @Value("${dofast.routing.google.api-key:}") String apiKey
    ) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
    }

    @Override
    public RouteProviderResult estimate(
            RouteCoordinate origin,
            RouteCoordinate destination,
            RouteTravelMode travelMode
    ) {
        if (!StringUtils.hasText(apiKey)) {
            throw new RoutingProviderException("Google Routes API key is not configured");
        }

        Map<String, Object> body = new HashMap<>();
        body.put("origin", waypoint(origin));
        body.put("destination", waypoint(destination));
        body.put("travelMode", travelMode.name());
        body.put("computeAlternativeRoutes", false);
        body.put("languageCode", "pl-PL");
        body.put("units", "METRIC");
        if (travelMode == RouteTravelMode.DRIVE) {
            body.put("routingPreference", "TRAFFIC_AWARE");
        }

        try {
            GoogleRoutesResponse response = restClient.post()
                    .uri(COMPUTE_ROUTES_URL)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(body)
                    .retrieve()
                    .body(GoogleRoutesResponse.class);

            if (response == null || response.routes() == null || response.routes().isEmpty()) {
                throw new RoutingProviderException("Google Routes did not return a route");
            }

            GoogleRoute route = response.routes().getFirst();
            if (route.distanceMeters() == null || route.distanceMeters() <= 0 || !StringUtils.hasText(route.duration())) {
                throw new RoutingProviderException("Google Routes returned an incomplete route");
            }

            String polyline = route.polyline() != null ? route.polyline().encodedPolyline() : null;
            return new RouteProviderResult(
                    route.distanceMeters(),
                    parseDurationSeconds(route.duration()),
                    polyline,
                    "GOOGLE_ROUTES"
            );
        } catch (RoutingProviderException ex) {
            throw ex;
        } catch (RestClientException | ArithmeticException ex) {
            throw new RoutingProviderException("Google Routes request failed", ex);
        }
    }

    private Map<String, Object> waypoint(RouteCoordinate coordinate) {
        return Map.of("location", Map.of("latLng", Map.of(
                "latitude", coordinate.latitude(),
                "longitude", coordinate.longitude()
        )));
    }

    private int parseDurationSeconds(String value) {
        if (!value.endsWith("s")) {
            throw new RoutingProviderException("Unsupported route duration format");
        }
        return new BigDecimal(value.substring(0, value.length() - 1))
                .setScale(0, RoundingMode.CEILING)
                .intValueExact();
    }

    private record GoogleRoutesResponse(List<GoogleRoute> routes) {}
    private record GoogleRoute(Integer distanceMeters, String duration, GooglePolyline polyline) {}
    private record GooglePolyline(String encodedPolyline) {}
}
