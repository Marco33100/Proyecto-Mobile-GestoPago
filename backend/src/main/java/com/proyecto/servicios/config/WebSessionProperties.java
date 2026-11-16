package com.proyecto.servicios.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * Sesion del cliente web: JWT en cookie HttpOnly, proteccion CSRF y CORS.
 *
 * <p>La app movil no usa estas opciones: sigue enviando el JWT en Authorization.
 */
@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "app.web-session")
public class WebSessionProperties {

    /**
     * Origenes del cliente web que pueden llamar a la API con credenciales.
     * Acepta patrones de Spring, por ejemplo {@code http://localhost:[*]}.
     * Vacio desactiva CORS.
     */
    @NotNull
    private List<String> allowedOrigins = new ArrayList<>();

    @NotBlank
    private String cookieName = "GP_SESSION";

    /** Solo debe desactivarse para pruebas locales sin HTTPS fuera de localhost. */
    private boolean cookieSecure = true;

    /** Lax si web y API comparten sitio; None (requiere Secure) si estan en sitios distintos. */
    @Pattern(regexp = "Strict|Lax|None")
    private String cookieSameSite = "Lax";

    @NotBlank
    private String csrfCookieName = "GP_XSRF";

    @NotBlank
    private String csrfHeaderName = "X-XSRF-TOKEN";
}
