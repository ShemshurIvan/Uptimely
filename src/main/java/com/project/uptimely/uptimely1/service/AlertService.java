package com.project.uptimely.uptimely1.service;

import com.project.uptimely.uptimely1.entity.CheckResult;
import com.project.uptimely.uptimely1.entity.Monitor;
import com.project.uptimely.uptimely1.entity.MonitorStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static com.project.uptimely.uptimely1.entity.MonitorStatus.DOWN;
import static com.project.uptimely.uptimely1.entity.MonitorStatus.UNKNOWN;
import static com.project.uptimely.uptimely1.entity.MonitorStatus.UP;

@Service
@Slf4j
public class AlertService {

    private final WebClient webClient;
    private final String webhookUrl;

    public AlertService(WebClient webClient,
                        @Value("${uptimely.alert.webhook-url:}") String webhookUrl) {
        this.webClient = webClient;
        this.webhookUrl = webhookUrl;
    }

    /**
     * Alerts only on a real change: UP/UNKNOWN -> DOWN and DOWN -> UP.
     * The first successful check (UNKNOWN -> UP) is not worth an alert.
     * The webhook is sent in the background and never throws.
     *
     * @return true if an alert was raised
     */
    public boolean notifyIfChanged(Monitor monitor, MonitorStatus previous, CheckResult result) {
        MonitorStatus current = result.getUp() ? UP : DOWN;
        if (current == previous || (previous == UNKNOWN && current == UP)) {
            return false;
        }

        String message;
        if (current == DOWN) {
            message = "🔴 %s is DOWN (%s): %s".formatted(monitor.getName(), monitor.getUrl(), result.getErrorMessage());
            log.warn(message);
        } else {
            message = "🟢 %s is back UP (%s), responded in %d ms".formatted(
                    monitor.getName(), monitor.getUrl(), result.getResponseTimeMs());
            log.info(message);
        }

        if (!webhookUrl.isBlank()) {
            sendWebhook(monitor, message);
        }
        return true;
    }

    private void sendWebhook(Monitor monitor, String message) {
        // {"text": ...} is the format Slack and Mattermost incoming webhooks expect
        webClient.post()
                .uri(webhookUrl)
                .bodyValue(Map.of("text", message))
                .retrieve()
                .toBodilessEntity()
                .subscribe(
                        response -> { },
                        ex -> log.warn("Could not send alert for monitor {}: {}", monitor.getId(), ex.getMessage()));
    }
}