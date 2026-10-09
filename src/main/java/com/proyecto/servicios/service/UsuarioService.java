package com.proyecto.servicios.service;

import com.proyecto.servicios.model.auth.CambiarContrasenaRequest;
import com.proyecto.servicios.model.auth.UsuarioResponse;
import java.util.UUID;

public interface UsuarioService {
    UsuarioResponse consultar(UUID id, UUID usuarioAutenticado);
    void cambiarContrasena(UUID id, UUID usuarioAutenticado, CambiarContrasenaRequest request);
}
