package com.proyecto.servicios.model.auth;

import com.proyecto.servicios.entity.sf.PlataformaSesion;

import java.time.Instant;

/** Sesion vigente de otro dispositivo: plataforma y momento en que se inicio. */
public record SesionActivaResponse(PlataformaSesion plataforma, Instant iniciadaEn) {
}
