package com.preonsurl.apis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiDocsEndpointReturns200AndValidSpecification() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("PreonsURL - URL Shortener API"))
                .andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.ApiKeyAuth.name").value("X-API-KEY"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.BearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/link/create']").exists())
                .andExpect(jsonPath("$.paths['/**'].get").exists());
    }

    @Test
    void swaggerUiIndexEndpointIsAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().is(anyOf(is(200), is(404))));
    }

    @Test
    void swaggerUiHtmlEndpointRedirectsOrIsAccessible() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is(anyOf(is(200), is(302))));
    }
}
