package com.cosmetic.ecommerce.release;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.Executors;

/** A temporary non-transactional process: Spring, JDBC and JWT never start. */
public final class PublicCatalogServer {
    private PublicCatalogServer() {}

    public static boolean startIfSelected() throws IOException {
        Properties settings = new Properties();
        try (var input = PublicCatalogServer.class.getResourceAsStream("/public-release.properties")) {
            if (input == null) throw new IllegalStateException("An explicit public release mode is required");
            settings.load(input);
        }
        String mode = settings.getProperty("release.mode", "").trim();
        if ("COD_STORE".equals(mode)) return false;
        if (!"PUBLIC_CATALOG".equals(mode)) throw new IllegalStateException("Unsupported public release mode");
        String configuredPort = System.getenv("PORT");
        int port = configuredPort == null || configuredPort.isBlank() ? 8080 : Integer.parseInt(configuredPort);
        String revision = System.getenv("RENDER_GIT_COMMIT");
        HttpServer server = create(new InetSocketAddress("0.0.0.0", port), revision);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(1), "catalog-maintenance-stop"));
        server.start();
        System.out.println("SEKKIN.LK PUBLIC_CATALOG maintenance started; ordering/accounts disabled; database and JWT not initialized.");
        return true;
    }

    static HttpServer create(InetSocketAddress address, String revision) throws IOException {
        String verifiedRevision = revision != null && revision.matches("[a-fA-F0-9]{40}")
                ? revision.toLowerCase(java.util.Locale.ROOT) : "unreported";
        byte[] body = ("{\"service\":\"SEKKIN.LK\",\"releaseMode\":\"PUBLIC_CATALOG\","
                + "\"ordering\":\"DISABLED\",\"accounts\":\"DISABLED\",\"payments\":\"DISABLED\","
                + "\"databaseInitialized\":false,\"jwtInitialized\":false,"
                + "\"revision\":\"" + verifiedRevision + "\","
                + "\"message\":\"Online ordering and customer accounts are temporarily unavailable.\"}")
                .getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(address, 32);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> {
            try (exchange) {
                var headers = exchange.getResponseHeaders();
                headers.set("Content-Type", "application/json; charset=utf-8");
                headers.set("Cache-Control", "no-store");
                headers.set("X-Content-Type-Options", "nosniff");
                headers.set("X-SEKKIN-Release-Mode", "PUBLIC_CATALOG");
                headers.set("X-SEKKIN-Revision", verifiedRevision);
                headers.set("Retry-After", "3600");
                // Health checks may use any GET path. Every response is the same
                // non-personal maintenance document, never application records.
                boolean read = "GET".equals(exchange.getRequestMethod()) || "HEAD".equals(exchange.getRequestMethod());
                boolean head = "HEAD".equals(exchange.getRequestMethod());
                exchange.sendResponseHeaders(read ? 200 : 503, head ? -1 : body.length);
                if (!head) exchange.getResponseBody().write(body);
            }
        });
        return server;
    }
}
