package com.proyecto.servicios.service;

import com.proyecto.servicios.model.auth.LoginRequest;
import com.proyecto.servicios.model.auth.LoginResponse;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.auth.UserProfileResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request);
    void logout(AuthenticatedUser usuario);
    UserProfileResponse perfilActual(AuthenticatedUser usuario);
}
