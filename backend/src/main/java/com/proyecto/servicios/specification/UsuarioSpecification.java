package com.proyecto.servicios.specification;

import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.model.auth.UsuarioBusquedaRequest;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;

import java.util.Locale;

/** Condiciones de persistencia de la búsqueda administrativa; los filtros se combinan con AND. */
public final class UsuarioSpecification {
    private UsuarioSpecification() { }

    public static Specification<UserEntity> conCriterios(UsuarioBusquedaRequest request) {
        Specification<UserEntity> filtro = (root, query, builder) -> builder.conjunction();
        if (request.getNombre() != null) {
            String patron = "%" + EscapeCharacter.DEFAULT.escape(request.getNombre().trim().toLowerCase(Locale.ROOT)) + "%";
            filtro = filtro.and((root, query, builder) -> builder.like(builder.lower(root.get("fullName")), patron, '\\'));
        }
        if (request.getCorreo() != null) {
            String correo = request.getCorreo().trim().toLowerCase(Locale.ROOT);
            filtro = filtro.and((root, query, builder) -> builder.equal(builder.lower(builder.trim(root.get("email"))), correo));
        }
        if (request.getTelefono() != null) {
            String telefono = request.getTelefono();
            filtro = filtro.and((root, query, builder) -> builder.equal(
                    root.join("cliente", JoinType.INNER).get("telefonoMovil"), telefono));
        }
        if (request.getActivo() != null) {
            Boolean activo = request.getActivo();
            filtro = filtro.and((root, query, builder) -> builder.equal(root.get("enabled"), activo));
        }
        return filtro;
    }
}
