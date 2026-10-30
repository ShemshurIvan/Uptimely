package com.project.uptimely.uptimely1.service;

import com.project.uptimely.uptimely1.entity.CheckResult;
import com.project.uptimely.uptimely1.entity.Monitor;
import com.project.uptimely.uptimely1.entity.MonitorStatus;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AlertServiceTest {

    private HttpServer server;
    private String webhookUrl;
    private final BlockingQueue<String> received = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hook", exchange -> {
            received.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/broken", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        webhookUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void alerts_whenUpGoesDown() {
        assertThat(alertService("").notifyIfChanged(monitor(), MonitorStatus.UP, result(false))).isTrue();
    }

    @Test
    void alerts_whenFirstCheckIsDown() {
        assertThat(alertService("").notifyIfChanged(monitor(), MonitorStatus.UNKNOWN, result(false))).isTrue();
    }

    @Test
    void alerts_whenDownRecovers() {
        assertThat(alertService("").notifyIfChanged(monitor(), MonitorStatus.DOWN, result(true))).isTrue();
    }

    @Test
    void doesNotAlert_whenFirstCheckIsUp() {
        assertThat(alertService("").notifyIfChanged(monitor(), MonitorStatus.UNKNOWN, result(true))).isFalse();
    }

    @Test
    void doesNotAlert_whenStatusUnchanged() {
        AlertService alertService = alertService("");

        assertThat(alertService.notifyIfChanged(monitor(), MonitorStatus.UP, result(true))).isFalse();
        assertThat(alertService.notifyIfChanged(monitor(), MonitorStatus.DOWN, result(false))).isFalse();
    }

    @Test
    void postsMessageToWebhook_whenSiteGoesDown() throws InterruptedException {
        alertService(webhookUrl + "/hook").notifyIfChanged(monitor(), MonitorStatus.UP, result(false));

        String body = received.poll(5, TimeUnit.SECONDS);
        assertThat(body).contains("\"text\"", "Google is DOWN", "https://google.com", "Connection refused");
    }

    @Test
    void postsRecoveryToWebhook() throws InterruptedException {
        alertService(webhookUrl + "/hook").notifyIfChanged(monitor(), MonitorStatus.DOWN, result(true));

        assertThat(received.poll(5, TimeUnit.SECONDS)).contains("Google is back UP", "120 ms");
    }

    @Test
    void doesNotPostToWebhook_whenStatusUnchanged() throws InterruptedException {
        alertService(webhookUrl + "/hook").notifyIfChanged(monitor(), MonitorStatus.UP, result(true));

        assertThat(received.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void doesNotThrow_whenWebhookFails() {
        assertThat(alertService(webhookUrl + "/broken").notifyIfChanged(monitor(), MonitorStatus.UP, result(false)))
                .isTrue();
        assertThat(alertService("http://localhost:1/unreachable").notifyIfChanged(monitor(), MonitorStatus.UP, result(false)))
                .isTrue();
    }

    private static AlertService alertService(String url) {
        return new AlertService(WebClient.create(), url);
    }

    private static CheckResult result(boolean up) {
        CheckResult result = new CheckResult();
        result.setUp(up);
        if (up) {
            result.setStatusCode(200);
            result.setResponseTimeMs(120L);
        } else {
            result.setErrorMessage("Connection refused");
        }
        return result;
    }

    private static Monitor monitor() {
        Monitor monitor = new Monitor();
        monitor.setName("Google");
        monitor.setUrl("https://google.com");
        monitor.setIntervalSeconds(60);
        ReflectionTestUtils.setField(monitor, "id", 1L);
        return monitor;
    }
}