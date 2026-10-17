package com.proyecto.servicios.service;

import com.proyecto.servicios.model.auth.ModificarContrasenaRequest;
import com.proyecto.servicios.model.auth.UsuarioResponse;
import com.proyecto.servicios.model.auth.UsuarioConsultaRequest;
import com.proyecto.servicios.model.auth.UsuarioBusquedaRequest;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import java.util.UUID;

public interface UsuarioService {
    UsuarioResponse consultar(UsuarioConsultaRequest request, UUID usuarioAutenticado);
    PaginaResponse<UsuarioResponse> buscar(UsuarioBusquedaRequest request);
    void cambiarContrasena(ModificarContrasenaRequest request, UUID usuarioAutenticado);
}
