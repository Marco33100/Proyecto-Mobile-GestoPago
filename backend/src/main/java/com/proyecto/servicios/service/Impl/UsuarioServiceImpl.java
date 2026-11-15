package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import com.proyecto.servicios.exception.auth.ContrasenaInvalidaException;
import com.proyecto.servicios.exception.auth.UsuarioInactivoException;
import com.proyecto.servicios.exception.auth.UsuarioNoEncontradoException;
import com.proyecto.servicios.model.auth.ModificarContrasenaRequest;
import com.proyecto.servicios.model.auth.UsuarioResponse;
import com.proyecto.servicios.model.auth.UsuarioConsultaRequest;
import com.proyecto.servicios.model.auth.UsuarioBusquedaRequest;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.support.PaginacionDatos;
import com.proyecto.servicios.model.cliente.ContrasenaSeguraValidator;
import com.proyecto.servicios.model.cliente.UsuarioClienteModificadoEvent;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import com.proyecto.servicios.service.UsuarioService;
import com.proyecto.servicios.specification.UsuarioSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.Locale;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
@Transactional(transactionManager = "sfTransactionManager", readOnly = true)
public class UsuarioServiceImpl implements UsuarioService {
    private final UserRepository usuarios;
    private final UserSessionRepository sesiones;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;

    @Autowired
    public UsuarioServiceImpl(UserRepository usuarios, UserSessionRepository sesiones,
                              PasswordEncoder passwordEncoder, ApplicationEventPublisher events) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
    }

    @Override
    public UsuarioResponse consultar(UsuarioConsultaRequest request, UUID usuarioAutenticado) {
        if (usuarioAutenticado == null) {
            throw new AccessDeniedException("Se requiere un usuario autenticado");
        }
        try {
            UserEntity usuario = usuarios.consultarPropioPorNombreYCorreo(
                    request.nombre().trim().toLowerCase(Locale.ROOT),
                    request.correo().trim().toLowerCase(Locale.ROOT), usuarioAutenticado)
                    .orElseThrow(UsuarioNoEncontradoException::new);
            validarUsuario(usuario);
            validarPropietario(usuario.getId(), usuarioAutenticado);
            return toResponse(usuario);
        } catch (DataAccessException exception) {
            log.error("Error al consultar usuario: {}", exception.getClass().getSimpleName());
            throw new AuthPersistenceException("No fue posible consultar el usuario", exception);
        }
    }

    @Override
    @PreAuthorize("hasRole('EJECUTIVO')")
    public PaginaResponse<UsuarioResponse> buscar(UsuarioBusquedaRequest request) {
        PaginacionDatos.validar(request.getPagina(), request.getTamanio());
        var filtro = UsuarioSpecification.conCriterios(request);
        try {
            var pagina = usuarios.findAll(filtro, PageRequest.of(request.getPagina(), request.getTamanio(),
                    Sort.by("createdAt", "id").descending()));
            return PaginaResponse.desde(pagina.map(this::toResponse));
        } catch (DataAccessException exception) {
            log.error("Error al buscar usuarios: {}", exception.getClass().getSimpleName());
            throw new AuthPersistenceException("No fue posible buscar usuarios", exception);
        }
    }

    private UsuarioResponse toResponse(UserEntity usuario) {
        var cliente = usuario.getCliente();
        return new UsuarioResponse(usuario.getId(), cliente == null ? null : cliente.getId(),
                usuario.getFullName(), usuario.getEmail(), cliente == null ? null : cliente.getTelefonoMovil(),
                usuario.isEnabled(), usuario.getCreatedAt(), usuario.getUpdatedAt());
    }

    @Override
    @Transactional(transactionManager = "sfTransactionManager")
    public void cambiarContrasena(ModificarContrasenaRequest request, UUID usuarioAutenticado) {
        if (usuarioAutenticado == null) {
            throw new AccessDeniedException("Se requiere un usuario autenticado");
        }
        var credenciales = request.credenciales();
        if (!ContrasenaSeguraValidator.esValida(credenciales.nuevaContrasena())) {
            throw new ContrasenaInvalidaException("La nueva contrasena no cumple los requisitos de seguridad");
        }
        try {
            UserEntity usuario = usuarios.consultarPropioPorNombreYCorreoParaActualizar(
                    request.nombre().trim().toLowerCase(Locale.ROOT),
                    request.correo().trim().toLowerCase(Locale.ROOT), usuarioAutenticado)
                    .orElseThrow(UsuarioNoEncontradoException::new);
            validarUsuario(usuario);
            validarPropietario(usuario.getId(), usuarioAutenticado);
            String actual = credenciales.contrasenaActual();
            if (actual == null || actual.getBytes(StandardCharsets.UTF_8).length > 72
                    || !passwordEncoder.matches(actual, usuario.getPasswordHash())) {
                throw new ContrasenaInvalidaException("La contrasena actual es incorrecta");
            }
            if (passwordEncoder.matches(credenciales.nuevaContrasena(), usuario.getPasswordHash())) {
                throw new ContrasenaInvalidaException("La nueva contrasena debe ser diferente de la actual");
            }
            usuario.cambiarContrasena(passwordEncoder.encode(credenciales.nuevaContrasena()), Instant.now());
            usuarios.saveAndFlush(usuario);
            sesiones.deleteById(usuario.getId());
            events.publishEvent(new UsuarioClienteModificadoEvent(usuario.getId(), usuario.getEmail(), usuario.getEmail()));
        } catch (DataAccessException exception) {
            log.error("Error al actualizar credenciales: {}", exception.getClass().getSimpleName());
            throw new AuthPersistenceException("No fue posible cambiar la contrasena", exception);
        }
    }

    private void validarPropietario(UUID id, UUID usuarioAutenticado) {
        if (usuarioAutenticado == null || !usuarioAutenticado.equals(id)) {
            throw new AccessDeniedException("No tienes permiso para acceder a este usuario");
        }
    }

    private void validarUsuario(UserEntity usuario) {
        if (usuario == null) {
            throw new UsuarioNoEncontradoException();
        }
        if (!usuario.isEnabled() || (usuario.getCliente() != null && !usuario.getCliente().isActivo())) {
            throw new UsuarioInactivoException();
        }
    }
}
