package com.preonsurl.apis.uptime;

import com.preonsurl.apis.uptime.controller.UptimeApiController;
import com.preonsurl.apis.uptime.dto.CreateIncidentRequest;
import com.preonsurl.apis.uptime.dto.IncidentDto;
import com.preonsurl.apis.uptime.dto.UptimeDashboardDto;
import com.preonsurl.apis.uptime.service.UptimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UptimeApiControllerTest {

    @Mock
    private UptimeService uptimeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UptimeApiController controller = new UptimeApiController(uptimeService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getUptimeDashboard_returnsSuccessWithCachedData() throws Exception {
        UptimeDashboardDto mockDto = new UptimeDashboardDto(
                "operational",
                "All systems operational",
                "No active incidents",
                "99.99%",
                Map.of("currentUptime", "99.99%"),
                "99.99% uptime",
                List.of(),
                List.of(),
                null,
                List.of(),
                "2026-09-26T14:32:00Z",
                3600L
        );

        when(uptimeService.getDashboardData()).thenReturn(mockDto);

        mockMvc.perform(get("/api/public/uptime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.overallStatus").value("operational"))
                .andExpect(jsonPath("$.data.currentSla").value("99.99%"))
                .andExpect(jsonPath("$.data.cachedTtlSeconds").value(3600));
    }

    @Test
    void triggerHealthCheck_returnsStatus() throws Exception {
        when(uptimeService.checkHealthAndRecordIfChanged()).thenReturn(false);
        UptimeDashboardDto mockDto = new UptimeDashboardDto(
                "operational", "All systems operational", "Desc", "99.99%",
                Map.of("responseLatency", "35ms"), "99.99%", List.of(), List.of(), null, List.of(), "now", 3600L
        );
        when(uptimeService.getDashboardData()).thenReturn(mockDto);

        mockMvc.perform(post("/api/public/uptime/check-health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.statusChanged").value(false))
                .andExpect(jsonPath("$.data.responseLatency").value("35ms"));
    }
}
