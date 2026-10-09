package com.proyecto.servicios.controller;

import com.proyecto.servicios.service.AuthService;
import com.proyecto.servicios.service.ClienteRegistroService;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.CuentaService;
import com.proyecto.servicios.service.ProductCatalogService;
import com.proyecto.servicios.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ApiRoutesTest {
    @Test
    void cadaOperacionTieneUnaSolaRutaBajoApi() {
        var mvc = MockMvcBuilders.standaloneSetup(
                new ClienteController(mock(ClienteService.class), mock(ClienteRegistroService.class)),
                new CuentaController(mock(CuentaService.class)),
                new UsuarioController(mock(UsuarioService.class)),
                new AuthController(mock(AuthService.class),
                        mock(com.proyecto.servicios.service.Impl.LoginAttemptLimiter.class)),
                new ProductCatalogController(mock(ProductCatalogService.class))).build();
        var mappings = mvc.getDispatcherServlet().getWebApplicationContext()
                .getBean(RequestMappingHandlerMapping.class).getHandlerMethods();

        assertFalse(mappings.isEmpty());
        for (var mapping : mappings.keySet()) {
            assertEquals(1, mapping.getPatternValues().size(), "No debe haber alias por operacion");
            assertTrue(mapping.getPatternValues().iterator().next().startsWith("/api/"));
        }
        var paths = mappings.keySet().stream().flatMap(mapping -> mapping.getPatternValues().stream()).toList();
        assertTrue(paths.containsAll(List.of("/api/clientes", "/api/cuentas/activas",
                "/api/usuarios/{id}", "/api/auth/login", "/api/auth/logout", "/api/products")));
    }
}
