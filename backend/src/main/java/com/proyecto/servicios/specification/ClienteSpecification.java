package com.proyecto.servicios.specification;

import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;

import java.time.Instant;
import java.util.Locale;

/** Construye consultas JPA; normalización, permisos y decisiones de negocio permanecen en el servicio. */
public final class ClienteSpecification {
    private ClienteSpecification() { }

    public static Specification<ClienteEntity> filtrosExactos(Long id, String correo, String numeroCuenta,
            Boolean activo, Instant inicio, Instant finExclusivo) {
        Specification<ClienteEntity> filtro = (root, query, builder) -> builder.conjunction();
        if (id != null) filtro = filtro.and((root, query, builder) -> builder.equal(root.get("id"), id));
        if (correo != null) filtro = filtro.and((root, query, builder) -> builder.equal(root.get("correo"), correo));
        if (numeroCuenta != null) {
            filtro = filtro.and((root, query, builder) -> {
                var subconsulta = query.subquery(Long.class);
                var cuenta = subconsulta.from(CuentaEntity.class);
                subconsulta.select(cuenta.get("cliente").get("id"))
                        .where(builder.equal(cuenta.get("numeroCuenta"), numeroCuenta));
                return root.get("id").in(subconsulta);
            });
        }
        if (activo != null) filtro = filtro.and((root, query, builder) -> builder.equal(root.get("activo"), activo));
        if (inicio != null) filtro = filtro.and((root, query, builder) ->
                builder.greaterThanOrEqualTo(root.get("fechaCreacion"), inicio));
        if (finExclusivo != null) filtro = filtro.and((root, query, builder) ->
                builder.lessThan(root.get("fechaCreacion"), finExclusivo));
        return filtro;
    }

    /** null indica ausencia de filtros parciales; las coincidencias se combinan con OR. */
    public static Specification<ClienteEntity> coincidencias(String nombre, String rfc, String curp) {
        Specification<ClienteEntity> coincidencias = null;
        String[] campos = {"nombre", "rfc", "curp"};
        String[] valores = {nombre, rfc, curp};
        for (int i = 0; i < campos.length; i++) {
            if (valores[i] != null) {
                String campo = campos[i];
                String patron = "%" + EscapeCharacter.DEFAULT.escape(valores[i].toLowerCase(Locale.ROOT)) + "%";
                Specification<ClienteEntity> contiene = (root, query, builder) ->
                        builder.like(builder.lower(root.get(campo)), patron, '\\');
                coincidencias = coincidencias == null ? contiene : coincidencias.or(contiene);
            }
        }
        return coincidencias;
    }
}
