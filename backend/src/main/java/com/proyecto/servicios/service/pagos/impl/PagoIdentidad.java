package com.proyecto.servicios.service.pagos.impl;

import com.proyecto.servicios.model.auth.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class PagoIdentidad {
    private PagoIdentidad() { }
    public static AuthenticatedUser actual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AuthenticatedUser actor)
                || auth.getAuthorities().stream().noneMatch(a -> "ROLE_CLIENTE".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Se requiere una sesion de cliente");
        }
        return actor;
    }
}
