package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.auth.ModificarContrasenaRequest;
import com.proyecto.servicios.model.auth.UsuarioResponse;
import com.proyecto.servicios.model.auth.UsuarioConsultaRequest;
import com.proyecto.servicios.model.auth.UsuarioBusquedaRequest;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.GenericResponse;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @PostMapping(value = "/consulta", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consulta tu usuario por nombre completo y correo",
            description = "Ambos campos son obligatorios y se comparan juntos con igualdad, sin distinguir mayusculas. "
                    + "No hay coincidencias parciales ni listado alternativo. Solo permite consultar al usuario "
                    + "de la sesion; una combinacion inexistente o ajena devuelve 404 sin revelar datos.")
    public GenericResponse<UsuarioResponse> consultar(@Valid @RequestBody UsuarioConsultaRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        return GenericResponse.exito("Usuario consultado correctamente",
                usuarioService.consultar(request, actor == null ? null : actor.userId()));
    }

    @PostMapping(value = "/busqueda", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Busca usuarios con filtros opcionales y paginacion",
            description = "Solo EJECUTIVO. Los filtros se combinan con AND: nombre completo parcial sin distinguir "
                    + "mayusculas, correo exacto sin distinguir mayusculas, telefono movil del cliente asociado "
                    + "exacto y activo segun el estado del usuario, no de su cuenta bancaria. null omite el filtro. "
                    + "{} devuelve la primera pagina de 20 usuarios; tamanio maximo 100. No hay listado alternativo "
                    + "si no existen coincidencias ni se devuelven credenciales.")
    public GenericResponse<PaginaResponse<UsuarioResponse>> buscar(@Valid @RequestBody UsuarioBusquedaRequest request) {
        var resultado = usuarioService.buscar(request);
        return GenericResponse.consulta("Usuarios consultados correctamente", resultado, !resultado.contenido().isEmpty());
    }

    @PutMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Cambia tu contrasena y revoca la sesion actual; requiere volver a iniciar sesion",
            description = "Localiza al usuario mediante nombre completo y correo exactos, sin distinguir mayusculas. "
                    + "Ambos campos son obligatorios; no se recibe un ID. Solo permite modificar al usuario de la "
                    + "sesion y exige la contrasena actual. Un selector inexistente o ajeno devuelve 404 sin cambios.")
    public ResponseEntity<Void> cambiarContrasena(@Valid @RequestBody ModificarContrasenaRequest request,
            @AuthenticationPrincipal AuthenticatedUser actor) {
        usuarioService.cambiarContrasena(request, actor == null ? null : actor.userId());
        return ResponseEntity.noContent().build();
    }
}
