package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequest;
import com.proyecto.servicios.model.auth.UsuarioResponse;
import com.proyecto.servicios.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping(value = "/api/usuarios", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Usuarios", description = "Perfil y cambio de contrasena del usuario autenticado")
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class UsuarioController {
    private final UsuarioService usuarioService;

    @Autowired
    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta tu usuario sin exponer credenciales")
    public UsuarioResponse consultar(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser actor) {
        return usuarioService.consultar(id, actor == null ? null : actor.userId());
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "Cambia tu contrasena y revoca la sesion actual; requiere volver a iniciar sesion")
    public ResponseEntity<Void> cambiarContrasena(@PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody CambiarContrasenaRequest request) {
        usuarioService.cambiarContrasena(id, actor == null ? null : actor.userId(), request);
        return ResponseEntity.noContent().build();
    }
}
