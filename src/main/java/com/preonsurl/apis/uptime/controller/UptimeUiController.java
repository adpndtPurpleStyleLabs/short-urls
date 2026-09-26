package com.preonsurl.apis.uptime.controller;

import com.preonsurl.apis.uptime.dto.UptimeDashboardDto;
import com.preonsurl.apis.uptime.service.UptimeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UptimeUiController {

    private final UptimeService uptimeService;

    public UptimeUiController(UptimeService uptimeService) {
        this.uptimeService = uptimeService;
    }

    @GetMapping("/uptime")
    public String showUptimePage(Model model) {
        try {
            UptimeDashboardDto dashboard = uptimeService.getDashboardData();
            model.addAttribute("dashboard", dashboard);
        } catch (Exception ignored) {
            // UI JavaScript also fetches /api/public/uptime automatically
        }
        return "uptime";
    }
}
