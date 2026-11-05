package assignment1.demo;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
public class CountryService {
    private final WebClient client;
    private final Duration timeout;
    private final long intervalMillis;
    private long lastRequestNanos;
    private final Map<String, CachedCountry> cache = new HashMap<>();
    private static final String ATTRIBUTION =
            "Data © OpenStreetMap contributors, ODbL 1.0: https://www.openstreetmap.org/copyright";

    public CountryService(
            @Value("${geocoding.base-url}") String baseUrl,
            @Value("${geocoding.user-agent}") String userAgent,
            @Value("${geocoding.timeout-ms:15000}") long timeoutMillis,
            @Value("${geocoding.min-interval-ms:1100}") long intervalMillis) {
        this.timeout = Duration.ofMillis(timeoutMillis);
        this.intervalMillis = intervalMillis;
        this.client = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new JdkClientHttpConnector(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5)).build()))
                .defaultHeader("User-Agent", userAgent)
                .defaultHeader("Accept-Language", "en")
                .build();
    }

    // Serialises requests within one app instance. Kubernetes also uses one shared gateway.
    public synchronized CountryResponse lookup(double latitude, double longitude) {
        String key = latitude + "," + longitude;
        CachedCountry cached = cache.get(key);
        if (cached != null && System.nanoTime() - cached.savedAt() < Duration.ofHours(24).toNanos()) {
            return cached.response();
        }
        try {
            waitForRequestSlot();
            Map<String, Object> body = client.get()
                    .uri(builder -> builder.path("/reverse")
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", 1)
                            .queryParam("zoom", 3)
                            .build())
                    .exchangeToMono(response -> {
                        if (!response.statusCode().is2xxSuccessful()) {
                            return response.releaseBody().then(Mono.error(
                                    new IllegalStateException("External HTTP error")));
                        }
                        return response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
                    })
                    .timeout(timeout)
                    .block();
            CountryResponse result = extractCountry(body);
            if (cache.size() >= 256) {
                cache.clear();
            }
            cache.put(key, new CachedCountry(result, System.nanoTime()));
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (RuntimeException exception) {
            // Includes HTTP failures, invalid JSON, empty responses and timeouts.
            throw unavailable();
        }
    }

    private void waitForRequestSlot() throws InterruptedException {
        if (lastRequestNanos != 0) {
            long remaining = Duration.ofMillis(intervalMillis).toNanos()
                    - (System.nanoTime() - lastRequestNanos);
            if (remaining > 0) {
                java.util.concurrent.TimeUnit.NANOSECONDS.sleep(remaining);
            }
        }
        lastRequestNanos = System.nanoTime();
    }

    private CountryResponse extractCountry(Map<String, Object> body) {
        if (body == null || body.containsKey("error")
                || !(body.get("address") instanceof Map<?, ?> address)
                || !(address.get("country") instanceof String country)
                || country.isBlank()
                || !(address.get("country_code") instanceof String code)
                || !code.matches("[a-zA-Z]{2}")) {
            throw new IllegalStateException("Unusable external response");
        }
        return new CountryResponse(country.trim(), code.toUpperCase(Locale.ROOT), ATTRIBUTION);
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "Country lookup provider failed or returned no usable country information");
    }

    private record CachedCountry(CountryResponse response, long savedAt) {}
}
