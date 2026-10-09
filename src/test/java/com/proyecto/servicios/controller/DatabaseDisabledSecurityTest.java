package com.proyecto.servicios.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.PublicSecurityConfiguration;
import com.proyecto.servicios.config.RestAuthenticationEntryPoint;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DatabaseDisabledSecurityTest {
    @Test
    void deshabilitarBaseDeDatosNuncaAbreLosServiciosProtegidos() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("app.database.enabled", "false")));
            context.register(PublicSecurityConfiguration.class, TestConfiguration.class);
            context.refresh();
            var mvc = MockMvcBuilders.standaloneSetup(new HealthController())
                    .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
            for (String ruta : new String[]{"/api/products", "/api/clientes", "/api/usuarios/1", "/api/cuentas/123"}) {
                mvc.perform(get(ruta)).andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("code").value("AUTH_DATABASE_ERROR"));
            }
            mvc.perform(post("/api/auth/login")).andExpect(status().isServiceUnavailable());
            mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        }
    }

    @RestController
    static class HealthController {
        @GetMapping("/actuator/health")
        Map<String, String> health() { return Map.of("status", "UP"); }
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    static class TestConfiguration {
        @Bean RestAuthenticationEntryPoint entryPoint() {
            return new RestAuthenticationEntryPoint(new ObjectMapper().findAndRegisterModules());
        }
    }
}
