package assignment1.demo;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CountryApiTests {
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static final AtomicInteger UNIQUE = new AtomicInteger();
    private static volatile String body;
    private static volatile int providerStatus;
    private static volatile long delay;
    private static volatile String query;
    private static volatile String userAgent;
    private static final HttpServer PROVIDER = startProvider();
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    @Value("${local.server.port}") int port;

    private static HttpServer startProvider() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(EXECUTOR);
            server.createContext("/reverse", exchange -> {
                CALLS.incrementAndGet();
                query = exchange.getRequestURI().getRawQuery();
                userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
                String responseBody = body;
                int responseStatus = providerStatus;
                try {
                    Thread.sleep(delay);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(responseStatus, bytes.length == 0 ? -1 : bytes.length);
                    if (bytes.length > 0) exchange.getResponseBody().write(bytes);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("geocoding.base-url", () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort());
        registry.add("geocoding.timeout-ms", () -> 500);
        registry.add("geocoding.min-interval-ms", () -> 0);
        registry.add("geocoding.user-agent", () -> "CountryLookupTest/1.0");
    }

    @BeforeEach
    void resetProvider() {
        body = "{\"address\":{\"country\":\"Luxembourg\",\"country_code\":\"lu\"}}";
        providerStatus = 200;
        delay = 0;
    }

    @AfterAll
    static void stopProvider() {
        PROVIDER.stop(0);
        EXECUTOR.shutdownNow();
    }

    private HttpResponse<String> get(String params) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/api/country" + params)).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String uniqueRequest() {
        return "?latitude=49.6116&longitude=" + (6 + UNIQUE.incrementAndGet() / 1000.0);
    }

    @Test
    void returnsCountryAndSendsExpectedProviderParameters() throws Exception {
        var response = get(uniqueRequest());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"country\":\"Luxembourg\"", "\"countryCode\":\"LU\"", "OpenStreetMap");
        assertThat(query).contains("lat=49.6116", "lon=", "format=jsonv2", "addressdetails=1", "zoom=3");
        assertThat(userAgent).isEqualTo("CountryLookupTest/1.0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?latitude=49", "?longitude=6", "?latitude=&longitude=6",
            "?latitude=abc&longitude=6", "?latitude=49&longitude=abc", "?latitude=NaN&longitude=6",
            "?latitude=Infinity&longitude=6", "?latitude=49&longitude=-Infinity",
            "?latitude=90.0001&longitude=6", "?latitude=-90.0001&longitude=6",
            "?latitude=49&longitude=180.0001", "?latitude=49&longitude=-180.0001"})
    void rejectsInvalidInputWithoutCallingProvider(String params) throws Exception {
        int before = CALLS.get();
        assertThat(get(params).statusCode()).isEqualTo(400);
        assertThat(CALLS.get()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?latitude=90&longitude=180", "?latitude=-90&longitude=-180", "?latitude=0&longitude=0"})
    void acceptsInclusiveBoundariesWithSimulatedProvider(String params) throws Exception {
        assertThat(get(params).statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 400, 403, 429, 500, 503})
    void mapsExternalNonSuccessToBadGateway(int status) throws Exception {
        providerStatus = status;
        assertThat(get(uniqueRequest()).statusCode()).isEqualTo(502);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "[]", "{}", "{\"error\":\"Unable to geocode\"}",
            "{\"address\":{\"country\":\"Luxembourg\"}}",
            "{\"address\":{\"country\":\" \",\"country_code\":\"lu\"}}",
            "{\"address\":{\"country\":42,\"country_code\":\"lu\"}}",
            "{\"address\":{\"country\":\"Luxembourg\",\"country_code\":\"INVALID\"}}"})
    void mapsUnusableResponseToBadGateway(String invalidBody) throws Exception {
        body = invalidBody;
        assertThat(get(uniqueRequest()).statusCode()).isEqualTo(502);
    }

    @Test
    void mapsProviderTimeoutToBadGateway() throws Exception {
        delay = 1500;
        assertThat(get(uniqueRequest()).statusCode()).isEqualTo(502);
    }

    @Test
    void repeatedCoordinatesUseCache() throws Exception {
        String request = uniqueRequest();
        int before = CALLS.get();
        assertThat(get(request).statusCode()).isEqualTo(200);
        assertThat(get(request).statusCode()).isEqualTo(200);
        assertThat(CALLS.get() - before).isEqualTo(1);
    }
}
