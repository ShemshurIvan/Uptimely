package com.project.uptimely.uptimely1.controller;

import com.project.uptimely.uptimely1.dto.CheckResultResponse;
import com.project.uptimely.uptimely1.dto.MonitorRequest;
import com.project.uptimely.uptimely1.dto.MonitorResponse;
import com.project.uptimely.uptimely1.dto.UpdateMonitorRequest;
import com.project.uptimely.uptimely1.entity.MonitorStatus;
import com.project.uptimely.uptimely1.exception.MonitorNotFoundException;
import com.project.uptimely.uptimely1.service.MonitorService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MonitorController.class)
class MonitorControllerTest {

    private static final String VALID_BODY = """
            {"name": "Google", "url": "https://google.com", "intervalSeconds": 60}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonitorService monitorService;

    // --- POST /api/monitors ---

    @Test
    void create_returns201AndBody() throws Exception {
        when(monitorService.create(any())).thenReturn(response(1L, "Google", true));

        mockMvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Google"))
                .andExpect(jsonPath("$.intervalSeconds").value(60))
                .andExpect(jsonPath("$.currentStatus").value("UNKNOWN"));

        verify(monitorService).create(new MonitorRequest("Google", "https://google.com", 60));
    }

    @Test
    void create_returns400_whenNameBlank() throws Exception {
        assertCreateRejected("""
                {"name": "  ", "url": "https://google.com", "intervalSeconds": 60}
                """);
    }

    @Test
    void create_returns400_whenUrlInvalid() throws Exception {
        assertCreateRejected("""
                {"name": "Google", "url": "not-a-url", "intervalSeconds": 60}
                """);
    }

    @Test
    void create_returns400_whenIntervalTooSmall() throws Exception {
        assertCreateRejected("""
                {"name": "Google", "url": "https://google.com", "intervalSeconds": 5}
                """);
    }

    @Test
    void create_returns400_whenIntervalMissing() throws Exception {
        assertCreateRejected("""
                {"name": "Google", "url": "https://google.com"}
                """);
    }

    @Test
    void create_returns400_whenNameTooLong() throws Exception {
        assertCreateRejected("""
                {"name": "%s", "url": "https://google.com", "intervalSeconds": 60}
                """.formatted("a".repeat(256)));
    }

    // --- GET /api/monitors ---

    @Test
    void getAll_returnsPagedModel() throws Exception {
        when(monitorService.getAll(any())).thenReturn(
                new PageImpl<>(List.of(response(1L, "Google", true)), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/monitors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Google"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.number").value(0));
    }

    @Test
    void getAll_passesPageAndSizeToService() throws Exception {
        when(monitorService.getAll(any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

        mockMvc.perform(get("/api/monitors").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(monitorService).getAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
        assertThat(captor.getValue().getPageSize()).isEqualTo(5);
    }

    // --- GET /api/monitors/{id} ---

    @Test
    void getById_returns200() throws Exception {
        when(monitorService.getById(1L)).thenReturn(response(1L, "Google", true));

        mockMvc.perform(get("/api/monitors/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.url").value("https://google.com"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        when(monitorService.getById(99L)).thenThrow(new MonitorNotFoundException(99L));

        mockMvc.perform(get("/api/monitors/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Monitor with id 99 not found"));
    }

    @Test
    void getById_returns400_whenIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/monitors/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid value 'abc' for parameter 'id'"));
    }

    // --- PUT /api/monitors/{id} ---

    @Test
    void update_returns200() throws Exception {
        when(monitorService.update(eq(1L), any())).thenReturn(response(1L, "Google 2", true));

        mockMvc.perform(put("/api/monitors/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Google 2", "url": "https://google.com", "intervalSeconds": 60}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Google 2"));

        verify(monitorService).update(1L, new UpdateMonitorRequest("Google 2", "https://google.com", 60));
    }

    @Test
    void update_returns400_whenBodyInvalid() throws Exception {
        mockMvc.perform(put("/api/monitors/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "url": "not-a-url", "intervalSeconds": 1}
                                """))
                .andExpect(status().isBadRequest());

        verify(monitorService, never()).update(any(), any());
    }

    // --- DELETE /api/monitors/{id} ---

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/monitors/1"))
                .andExpect(status().isNoContent());

        verify(monitorService).deleteById(1L);
    }

    @Test
    void delete_returns404_whenMissing() throws Exception {
        doThrow(new MonitorNotFoundException(99L)).when(monitorService).deleteById(99L);

        mockMvc.perform(delete("/api/monitors/99"))
                .andExpect(status().isNotFound());
    }

    // --- PATCH pause / resume ---

    @Test
    void pause_returns200WithInactiveMonitor() throws Exception {
        when(monitorService.pause(1L)).thenReturn(response(1L, "Google", false));

        mockMvc.perform(patch("/api/monitors/1/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void resume_returns200WithActiveMonitor() throws Exception {
        when(monitorService.resume(1L)).thenReturn(response(1L, "Google", true));

        mockMvc.perform(patch("/api/monitors/1/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    // --- POST /api/monitors/{id}/check ---

    @Test
    void check_returns200WithResult() throws Exception {
        when(monitorService.checkNow(1L)).thenReturn(new CheckResultResponse(
                10L, Instant.parse("2026-10-24T10:00:00Z"), true, 200, 120L, null));

        mockMvc.perform(post("/api/monitors/1/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.up").value(true))
                .andExpect(jsonPath("$.statusCode").value(200))
                .andExpect(jsonPath("$.responseTimeMs").value(120))
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    void check_returnsFailedResultAs200() throws Exception {
        // a DOWN site is a successful check with a negative result, not an API error
        when(monitorService.checkNow(1L)).thenReturn(new CheckResultResponse(
                11L, Instant.parse("2026-10-24T10:00:00Z"), false, null, null, "Connection refused"));

        mockMvc.perform(post("/api/monitors/1/check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.up").value(false))
                .andExpect(jsonPath("$.errorMessage").value("Connection refused"));
    }

    @Test
    void check_returns404_whenMissing() throws Exception {
        when(monitorService.checkNow(99L)).thenThrow(new MonitorNotFoundException(99L));

        mockMvc.perform(post("/api/monitors/99/check"))
                .andExpect(status().isNotFound());
    }

    // --- error format (GlobalExceptionHandler) ---

    @Test
    void validationError_listsEveryInvalidField() throws Exception {
        mockMvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "url": "not-a-url", "intervalSeconds": 1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.url").exists())
                .andExpect(jsonPath("$.errors.intervalSeconds").exists());
    }

    @Test
    void malformedJson_returns400WithoutParserDetails() throws Exception {
        mockMvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed request body"));
    }

    @Test
    void unexpectedError_returns500WithoutLeakingMessage() throws Exception {
        when(monitorService.getById(1L)).thenThrow(new IllegalStateException("secret SQL details"));

        mockMvc.perform(get("/api/monitors/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("Unexpected error"));
    }

    @Test
    void unknownUrl_returns404NotOverriddenByCatchAll() throws Exception {
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void wrongHttpMethod_returns405NotOverriddenByCatchAll() throws Exception {
        mockMvc.perform(delete("/api/monitors"))
                .andExpect(status().isMethodNotAllowed());
    }

    private void assertCreateRejected(String body) throws Exception {
        mockMvc.perform(post("/api/monitors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(monitorService, never()).create(any());
    }

    private static MonitorResponse response(Long id, String name, boolean active) {
        return new MonitorResponse(id, name, "https://google.com", 60, active,
                MonitorStatus.UNKNOWN, Instant.parse("2026-10-23T10:00:00Z"), null);
    }
}