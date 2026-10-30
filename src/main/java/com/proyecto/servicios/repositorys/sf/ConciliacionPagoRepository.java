package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.ConciliacionPagoEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface ConciliacionPagoRepository extends JpaRepository<ConciliacionPagoEntity, UUID> {
    boolean existsByPagoIdAndEstado(UUID pagoId, ConciliacionPagoEntity.Estado estado);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConciliacionPagoEntity c where c.id=:id")
    Optional<ConciliacionPagoEntity> bloquear(@Param("id") UUID id);
}
