package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.auth.AuthPersistenceException;
import com.proyecto.servicios.exception.auth.ContrasenaInvalidaException;
import com.proyecto.servicios.exception.auth.UsuarioInactivoException;
import com.proyecto.servicios.exception.auth.UsuarioNoEncontradoException;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequest;
import com.proyecto.servicios.model.auth.UsuarioResponse;
import com.proyecto.servicios.model.cliente.ContrasenaSeguraValidator;
import com.proyecto.servicios.model.cliente.UsuarioClienteModificadoEvent;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import com.proyecto.servicios.service.UsuarioService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

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
    public UsuarioResponse consultar(UUID id, UUID usuarioAutenticado) {
        validarPropietario(id, usuarioAutenticado);
        try {
            UserEntity usuario = usuarios.findUserById(id);
            validarUsuario(usuario);
            return new UsuarioResponse(usuario.getId(),
                    usuario.getCliente() == null ? null : usuario.getCliente().getId(),
                    usuario.getEmail(), usuario.isEnabled(), usuario.getCreatedAt(), usuario.getUpdatedAt());
        } catch (DataAccessException exception) {
            log.error("Error al consultar usuario: {}", exception.getClass().getSimpleName());
            throw new AuthPersistenceException("No fue posible consultar el usuario", exception);
        }
    }

    @Override
    @Transactional(transactionManager = "sfTransactionManager")
    public void cambiarContrasena(UUID id, UUID usuarioAutenticado, CambiarContrasenaRequest request) {
        validarPropietario(id, usuarioAutenticado);
        if (!ContrasenaSeguraValidator.esValida(request.nuevaContrasena())) {
            throw new ContrasenaInvalidaException("La nueva contrasena no cumple los requisitos de seguridad");
        }
        try {
            UserEntity usuario = usuarios.findByIdForUpdate(id);
            validarUsuario(usuario);
            String actual = request.contrasenaActual();
            if (actual == null || actual.getBytes(StandardCharsets.UTF_8).length > 72
                    || !passwordEncoder.matches(actual, usuario.getPasswordHash())) {
                throw new ContrasenaInvalidaException("La contrasena actual es incorrecta");
            }
            if (passwordEncoder.matches(request.nuevaContrasena(), usuario.getPasswordHash())) {
                throw new ContrasenaInvalidaException("La nueva contrasena debe ser diferente de la actual");
            }
            usuario.cambiarContrasena(passwordEncoder.encode(request.nuevaContrasena()), Instant.now());
            usuarios.saveAndFlush(usuario);
            sesiones.deleteById(id);
            events.publishEvent(new UsuarioClienteModificadoEvent(id, usuario.getEmail(), usuario.getEmail()));
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
