package com.preonsurl.site.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void homePageReturnsOkAndResolvesTemplate() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("site/home"))
                .andExpect(model().attributeExists("pageTitle"))
                .andExpect(model().attribute("currentYear", 2026))
                .andExpect(content().string(containsString("Create links. Control what happens next.")))
                .andExpect(content().string(containsString("Business links need more than shortening.")))
                .andExpect(content().string(containsString("One API. Complete link lifecycle.")))
                .andExpect(content().string(containsString("/css/site/home.css")));
    }
}
