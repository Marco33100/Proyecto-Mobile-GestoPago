package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.UserSessionEntity;
import com.proyecto.servicios.entity.sf.RolUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, UUID> {
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from UserSessionEntity s where s.userId = :userId and s.sessionId = :sessionId")
    int eliminarSesionSiCoincide(@Param("userId") UUID userId, @Param("sessionId") UUID sessionId);

    @Query("select u.rol from UserSessionEntity s, UserEntity u left join u.cliente c "
            + "where s.userId = :userId and u.id = s.userId and s.sessionId = :sessionId "
            + "and s.expiresAt > :ahora and u.enabled = true and (c.id is null or c.activo = true)")
    RolUsuario encontrarRolVigente(@Param("userId") UUID userId, @Param("sessionId") UUID sessionId,
                               @Param("ahora") Instant ahora);

    default boolean existeSesionValida(UUID userId, UUID sessionId, Instant ahora) {
        return encontrarRolVigente(userId, sessionId, ahora) != null;
    }
}
