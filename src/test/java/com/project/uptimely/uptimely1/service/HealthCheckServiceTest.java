package com.project.uptimely.uptimely1.service;

import com.project.uptimely.uptimely1.config.WebClientConfig;
import com.project.uptimely.uptimely1.entity.CheckResult;
import com.project.uptimely.uptimely1.entity.Monitor;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckServiceTest {

    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(1);

    private HttpServer server;
    private String baseUrl;
    private HealthCheckService healthCheckService;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/ok", exchange -> {
            byte[] body = "hello".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/error", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.createContext("/not-found", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/redirect", exchange -> {
            exchange.getResponseHeaders().add("Location", baseUrl + "/ok");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/user-agent", exchange -> {
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            exchange.sendResponseHeaders("Uptimely/1.0".equals(userAgent) ? 200 : 400, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        WebClient webClient = new WebClientConfig()
                .webClient(WebClient.builder(), Duration.ofSeconds(1), RESPONSE_TIMEOUT);
        healthCheckService = new HealthCheckService(webClient, RESPONSE_TIMEOUT);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void status200_isUpWithResponseTime() {
        CheckResult result = check(baseUrl + "/ok");

        assertThat(result.getUp()).isTrue();
        assertThat(result.getStatusCode()).isEqualTo(200);
        assertThat(result.getResponseTimeMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getCheckedAt()).isNotNull();
        assertThat(result.getMonitor().getUrl()).isEqualTo(baseUrl + "/ok");
    }

    @Test
    void status503_isDownWithStatusCode() {
        CheckResult result = check(baseUrl + "/error");

        assertThat(result.getUp()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(503);
        assertThat(result.getResponseTimeMs()).isNotNull();
        assertThat(result.getErrorMessage()).isEqualTo("HTTP 503");
    }

    @Test
    void status404_isDown() {
        CheckResult result = check(baseUrl + "/not-found");

        assertThat(result.getUp()).isFalse();
        assertThat(result.getStatusCode()).isEqualTo(404);
    }

    @Test
    void redirect_isFollowedToFinalPage() {
        CheckResult result = check(baseUrl + "/redirect");

        assertThat(result.getUp()).isTrue();
        assertThat(result.getStatusCode()).isEqualTo(200);
    }

    @Test
    void slowResponse_isTimeout() {
        CheckResult result = check(baseUrl + "/slow");

        assertThat(result.getUp()).isFalse();
        assertThat(result.getStatusCode()).isNull();
        assertThat(result.getResponseTimeMs()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("Timeout after 1s");
    }

    @Test
    void closedPort_isConnectionRefused() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        CheckResult result = check("http://localhost:" + freePort);

        assertThat(result.getUp()).isFalse();
        assertThat(result.getStatusCode()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("Connection refused");
    }

    @Test
    void unknownDomain_isUnknownHost() {
        // the .invalid TLD is reserved and never resolves
        CheckResult result = check("http://does-not-exist.invalid");

        assertThat(result.getUp()).isFalse();
        assertThat(result.getStatusCode()).isNull();
        assertThat(result.getErrorMessage()).isEqualTo("Unknown host");
    }

    @Test
    void sendsUptimelyUserAgent() {
        CheckResult result = check(baseUrl + "/user-agent");

        assertThat(result.getStatusCode()).isEqualTo(200);
    }

    private CheckResult check(String url) {
        return healthCheckService.check(monitor(url)).block(Duration.ofSeconds(10));
    }

    private static Monitor monitor(String url) {
        Monitor monitor = new Monitor();
        monitor.setName("test");
        monitor.setUrl(url);
        monitor.setIntervalSeconds(60);
        return monitor;
    }
}