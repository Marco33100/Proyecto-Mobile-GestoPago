package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.PlataformaSesion;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.model.auth.SesionActivaResponse;
import java.util.Optional;
import java.util.UUID;
import com.proyecto.servicios.model.auth.LoginRequest;
import com.proyecto.servicios.model.auth.LoginResponse;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.auth.UserProfileResponse;

public interface AuthService {
    /**
     * Primer factor: valida correo, contrasena y que el usuario este activo.
     * No emite sesion; el inicio de sesion completo lo orquesta {@link LoginSegundoFactorService}.
     */
    UserEntity verificarCredenciales(LoginRequest request);

    /** Emite el JWT y reemplaza la sesion activa. Solo tras verificar el segundo factor. */
    LoginResponse emitirSesion(UserEntity user, PlataformaSesion plataforma);

    /** Sesion vigente del usuario en cualquier dispositivo, para avisar antes de reemplazarla. */
    Optional<SesionActivaResponse> sesionActiva(UUID userId);

    void logout(AuthenticatedUser usuario);
    UserProfileResponse perfilActual(AuthenticatedUser usuario);
}
