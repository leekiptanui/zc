package com.zimasahealth.zcare.web;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

/**
 * Stands in for the ZCare API: records the last request it received and answers with an envelope,
 * plus headers the web server must not pass on.
 */
final class StubApi implements AutoCloseable {

    record Received(String method, String pathAndQuery, Headers headers, String body) {
    }

    static final String ENVELOPE = "{\"status\":\"success\",\"data\":{\"items\":[],\"totalItems\":0}}";

    private final HttpServer server;
    private final AtomicReference<Received> last = new AtomicReference<>();

    StubApi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            last.set(new Received(exchange.getRequestMethod(),
                    exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query),
                    exchange.getRequestHeaders(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] body = ENVELOPE.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("X-Correlation-Id", "corr-1");
            exchange.getResponseHeaders().set("Set-Cookie", "upstream=leak");
            exchange.getResponseHeaders().set("X-Internal-Tenant", "acme-health");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    Received last() {
        return last.get();
    }

    void reset() {
        last.set(null);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
