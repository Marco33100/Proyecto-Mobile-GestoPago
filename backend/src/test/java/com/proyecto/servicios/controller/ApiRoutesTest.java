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
            assertFalse(mapping.getPatternValues().iterator().next().contains("{"),
                    "Los identificadores deben recibirse en requests, no en variables de ruta");
        }
        for (var handler : mappings.values()) {
            for (var parameter : handler.getMethodParameters()) {
                for (var annotation : parameter.getParameterAnnotations()) {
                    assertFalse(List.of("PathVariable", "RequestParam", "ModelAttribute")
                            .contains(annotation.annotationType().getSimpleName()),
                            "La entrada debe recibirse mediante un Request DTO en el cuerpo JSON");
                }
                if (parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class)) {
                    assertTrue(parameter.hasParameterAnnotation(jakarta.validation.Valid.class));
                    assertFalse(parameter.getParameterType().isAnnotationPresent(jakarta.persistence.Entity.class));
                }
            }
        }
        var paths = mappings.keySet().stream().flatMap(mapping -> mapping.getPatternValues().stream()).toList();
        assertTrue(paths.containsAll(List.of("/api/clientes", "/api/clientes/consulta", "/api/cuentas/activas/consulta",
                "/api/usuarios/consulta", "/api/usuarios/busqueda", "/api/usuarios/password", "/api/cuentas/consulta", "/api/cuentas/saldo/consulta", "/api/auth/login", "/api/auth/logout", "/api/products")));
        assertEquals(1L, mappings.keySet().stream().filter(mapping -> mapping.getPatternValues()
                .contains("/api/clientes/consulta")).count(), "Solo debe existir una ruta de consulta de clientes");
        assertFalse(paths.contains("/api/clientes/rfc"));
        assertFalse(paths.contains("/api/clientes/curp"));
        assertFalse(paths.contains("/api/clientes/correo"));
        assertEquals(1L, mappings.keySet().stream().filter(mapping -> mapping.getPatternValues()
                .contains("/api/usuarios/busqueda")).count());
        assertFalse(paths.contains("/api/usuarios/nombre"));
        assertFalse(paths.contains("/api/usuarios/correo"));
        assertFalse(paths.contains("/api/usuarios/telefono"));
    }
}
