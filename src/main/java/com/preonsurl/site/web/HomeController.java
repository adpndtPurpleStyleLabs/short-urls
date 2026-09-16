package com.preonsurl.site.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("pageTitle", "PreonsURL — Business Link Infrastructure");
        model.addAttribute("currentYear", 2026);
        return "site/home";
    }
}
