package com.preonsurl.apis.uptime;

import com.preonsurl.apis.uptime.controller.UptimeUiController;
import com.preonsurl.apis.uptime.dto.UptimeDashboardDto;
import com.preonsurl.apis.uptime.service.UptimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
class UptimeUiControllerTest {

    @Mock
    private UptimeService uptimeService;

    private UptimeUiController controller;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        controller = new UptimeUiController(uptimeService);
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        viewResolver.setPrefix("/templates/");
        viewResolver.setSuffix(".html");

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setViewResolvers(viewResolver)
                .build();
    }

    @Test
    void showUptimePage_returnsUptimeView() throws Exception {
        UptimeDashboardDto mockDto = new UptimeDashboardDto(
                "operational", "All systems operational", "Desc", "99.99%",
                Map.of(), "99.99% uptime", List.of(), List.of(), null, List.of(), "now", 3600L
        );
        when(uptimeService.getDashboardData()).thenReturn(mockDto);

        mockMvc.perform(get("/uptime"))
                .andExpect(status().isOk())
                .andExpect(view().name("uptime"))
                .andExpect(model().attributeExists("dashboard"));

        Model model = new ConcurrentModel();
        String viewName = controller.showUptimePage(model);
        assertEquals("uptime", viewName);
        assertTrue(model.containsAttribute("dashboard"));
    }
}
