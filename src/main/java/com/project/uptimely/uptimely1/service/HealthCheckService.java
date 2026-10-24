package com.project.uptimely.uptimely1.service;

import com.project.uptimely.uptimely1.entity.CheckResult;
import com.project.uptimely.uptimely1.entity.Monitor;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

@Service
public class HealthCheckService {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final WebClient webClient;
    private final Duration responseTimeout;

    public HealthCheckService(WebClient webClient,
                              @Value("${uptimely.check.response-timeout}") Duration responseTimeout) {
        this.webClient = webClient;
        this.responseTimeout = responseTimeout;
    }

    /**
     * Never emits an error: every failure becomes a CheckResult with up = false.
     */
    public Mono<CheckResult> check(Monitor monitor) {
        return Mono.defer(() -> {
                    long start = System.nanoTime();
                    return webClient.get()
                            .uri(monitor.getUrl())
                            .exchangeToMono(response -> {
                                long elapsedMs = elapsedMs(start);
                                int statusCode = response.statusCode().value();

                                return response.releaseBody()
                                        .thenReturn(httpResult(monitor, statusCode, elapsedMs));
                            });
                })
                .timeout(responseTimeout.plusSeconds(5))
                .onErrorResume(ex -> Mono.just(failedResult(monitor, describe(ex))));
    }

    private CheckResult httpResult(Monitor monitor, int statusCode, long responseTimeMs) {
        boolean up = statusCode >= 200 && statusCode < 400;
        CheckResult result = newResult(monitor, up);
        result.setStatusCode(statusCode);
        result.setResponseTimeMs(responseTimeMs);
        result.setErrorMessage(up ? null : "HTTP " + statusCode);
        return result;
    }

    private CheckResult failedResult(Monitor monitor, String errorMessage) {
        CheckResult result = newResult(monitor, false);
        result.setErrorMessage(errorMessage);
        return result;
    }

    private CheckResult newResult(Monitor monitor, boolean up) {
        CheckResult result = new CheckResult();
        result.setMonitor(monitor);
        result.setCheckedAt(Instant.now());
        result.setUp(up);
        return result;
    }

    private String describe(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException || t instanceof ReadTimeoutException) {
                return "Timeout after " + responseTimeout.toSeconds() + "s";
            }
            if (t instanceof ConnectTimeoutException) {
                return "Connection timeout";
            }
            if (t instanceof UnknownHostException) {
                return "Unknown host";
            }
            if (t instanceof ConnectException) {
                return "Connection refused";
            }
            if (t instanceof SSLException) {
                return truncate("SSL error: " + t.getMessage());
            }
        }
        return truncate(ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    private static long elapsedMs(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private static String truncate(String message) {
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}