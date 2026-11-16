package com.proyecto.servicios.service;

import com.proyecto.servicios.model.auth.LoginDesafioResponse;
import com.proyecto.servicios.model.auth.LoginRequest;
import com.proyecto.servicios.model.auth.LoginResponse;
import com.proyecto.servicios.model.auth.VerificarLoginRequest;

/** Inicio de sesion en dos pasos: contrasena y codigo del autenticador (TOTP). */
public interface LoginSegundoFactorService {
    /** Verifica la contrasena y entrega un desafio; si no hay autenticador, su configuracion. */
    LoginDesafioResponse iniciar(LoginRequest request);

    /** Verifica el codigo del autenticador (o de recuperacion) y emite la sesion. */
    LoginResponse verificar(VerificarLoginRequest request);
}
