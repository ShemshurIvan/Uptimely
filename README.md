# Uptimely

A small uptime monitor built with Spring Boot. You register URLs, Uptimely checks them on a schedule, stores every result, and reports uptime and response time stats. When a site goes down or comes back up, it raises an alert.

## Features

- CRUD API for monitors, with validation and pagination
- Scheduled HTTP checks, run in parallel, each monitor on its own interval (min 10s)
- Manual "check now"
- Check history with time-range filtering
- Uptime % and average response time for the last 24h / 7d / 30d
- Alerts on status change (logged, and optionally posted to a webhook)

A check counts as **UP** when the site answers with HTTP 2xx or 3xx (redirects are followed). Anything else is **DOWN**: 4xx/5xx, timeouts, DNS failures, refused connections and SSL errors.

## Tech stack

Java 21 · Spring Boot 4 (Web MVC, Data JPA, Validation) · WebClient / Reactor Netty · H2 · Lombok · JUnit 5 + Mockito

## Running

```bash
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`. The H2 console is at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:uptimely`, user `sa`, empty password).

> The database is in memory, so all data is lost when the app stops.

Run the tests with:

```bash
./mvnw test
```

## API

All endpoints are under `/api/monitors`.

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/monitors` | Create a monitor |
| `GET` | `/api/monitors` | List monitors (paged: `?page=0&size=20&sort=name`) |
| `GET` | `/api/monitors/{id}` | Get one monitor |
| `PUT` | `/api/monitors/{id}` | Update name, URL and interval |
| `DELETE` | `/api/monitors/{id}` | Delete a monitor and its history |
| `PATCH` | `/api/monitors/{id}/pause` | Stop scheduled checks |
| `PATCH` | `/api/monitors/{id}/resume` | Restart scheduled checks |
| `POST` | `/api/monitors/{id}/check` | Run a check right now |
| `GET` | `/api/monitors/{id}/checks` | Check history (`?from=&to=` as ISO-8601 instants, default last 24h, newest first) |
| `GET` | `/api/monitors/{id}/stats` | Uptime stats (`?period=24h`, `7d` or `30d`) |

Errors are returned as [RFC 9457 problem details](https://www.rfc-editor.org/rfc/rfc9457) (`404` for an unknown monitor, `400` for validation errors with a per-field `errors` map).

### Examples

```bash
# create a monitor that is checked every 60 seconds
curl -X POST localhost:8080/api/monitors \
  -H 'Content-Type: application/json' \
  -d '{"name": "Google", "url": "https://google.com", "intervalSeconds": 60}'

# check it now
curl -X POST localhost:8080/api/monitors/1/check

# last 7 days of stats
curl 'localhost:8080/api/monitors/1/stats?period=7d'
```

Example stats response:

```json
{
  "monitorId": 1,
  "period": "7d",
  "from": "2026-10-23T15:00:00Z",
  "to": "2026-10-30T15:00:00Z",
  "totalChecks": 10080,
  "failedChecks": 12,
  "uptimePercent": 99.88,
  "avgResponseMs": 143.27
}
```

## Alerts

Uptimely raises an alert when a monitor's status changes:

| Change | Alert |
|---|---|
| UP → DOWN | 🔴 `Google is DOWN (https://google.com): HTTP 503` |
| first check fails (UNKNOWN → DOWN) | 🔴 same as above |
| DOWN → UP | 🟢 `Google is back UP (https://google.com), responded in 143 ms` |
| first check succeeds, or no change | no alert |

Alerts are always written to the application log. To also get them in Slack (or any service that accepts Slack-style incoming webhooks, such as Mattermost), set the webhook URL:

```properties
uptimely.alert.webhook-url=https://hooks.slack.com/services/...
```

or as an environment variable:

```bash
UPTIMELY_ALERT_WEBHOOK_URL=https://hooks.slack.com/services/... ./mvnw spring-boot:run
```

The webhook receives a `POST` with the body `{"text": "<message>"}`. It is sent in the background, so a slow or broken webhook never delays checks.

## Configuration

Set in `src/main/resources/application.properties`:

| Property | Default | Description |
|---|---|---|
| `uptimely.check.connect-timeout` | `5s` | Time allowed to open a connection |
| `uptimely.check.response-timeout` | `10s` | Time allowed to wait for a response |
| `uptimely.scheduler.tick` | `5s` | How often the scheduler looks for monitors that are due |
| `uptimely.scheduler.concurrency` | `10` | Max checks running at the same time |
| `uptimely.alert.webhook-url` | _(empty)_ | Webhook to post alerts to; empty means log only |