package com.cosmetic.ecommerce.release;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicCatalogServerTest {
    private HttpServer server;
    private String origin;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach void start() throws Exception {
        server = PublicCatalogServer.create(new InetSocketAddress("127.0.0.1", 0), "a".repeat(40));
        server.start(); origin = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void stop() { server.stop(0); }

    @ParameterizedTest
    @CsvSource({"POST,/api/orders/direct", "POST,/api/orders", "POST,/api/auth/register",
            "POST,/api/auth/login", "POST,/api/admin/orders/1/cod-collection",
            "PUT,/api/products/1", "PATCH,/api/orders/1/status", "DELETE,/api/products/1",
            "POST,/api/orders/direct/", "POST,/api/orders%2fdirect"})
    void everyMutationIsDeniedBeforeAnyApplicationProcessing(String method, String path) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(origin + path))
                .header("Authorization", "Bearer synthetic-untrusted-token")
                .method(method, HttpRequest.BodyPublishers.ofString("{}" )).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(503, response.statusCode());
        assertTrue(response.body().contains("\"ordering\":\"DISABLED\""));
        assertTrue(response.body().contains("\"databaseInitialized\":false"));
        assertTrue(response.headers().firstValue("access-control-allow-origin").isEmpty());
    }

    @ParameterizedTest @CsvSource({"/", "/health", "/.well-known/sekkin-release", "/api/admin/users", "/api/orders", "/api/products"})
    void readsContainOnlyMaintenanceInformationEvenWithAnOldToken(String path) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(origin + path))
                .header("Authorization", "Bearer synthetic-old-token").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"releaseMode\":\"PUBLIC_CATALOG\""));
        assertTrue(response.body().contains("\"jwtInitialized\":false"));
        assertFalse(response.body().contains("password"));
        assertEquals("no-store", response.headers().firstValue("cache-control").orElseThrow());
    }

    @Test void revisionCannotInjectResponseContent() throws Exception {
        HttpServer isolated = PublicCatalogServer.create(new InetSocketAddress("127.0.0.1", 0), "untrusted\"\nvalue");
        try {
            isolated.start();
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + isolated.getAddress().getPort() + "/")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertTrue(response.body().contains("\"revision\":\"unreported\""));
            assertFalse(response.body().contains("untrusted"));
        } finally { isolated.stop(0); }
    }
}
