package com.proyecto.servicios.repositorys.sf;

import com.proyecto.servicios.entity.sf.PagoEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public interface PagoRepository extends JpaRepository<PagoEntity, UUID> {
    boolean existsByIdTransaccionAndIdNot(String idTransaccion, UUID id);
    Optional<PagoEntity> findByIdTransaccion(String idTransaccion);
    Page<PagoEntity> findByEstado(PagoEntity.Estado estado, Pageable pagina);
    @Query("select sum(p.monto) from PagoEntity p where p.usuarioId=:usuario and p.enviadoEn>=:inicio and p.enviadoEn<:fin and p.estado in :estados")
    Optional<BigDecimal> importeEnviadoEnPeriodo(@Param("usuario") UUID usuario, @Param("inicio") Instant inicio,
            @Param("fin") Instant fin, @Param("estados") Collection<PagoEntity.Estado> estados);
    @Query("""
            select count(p) > 0 from PagoEntity p
            where p.usuarioId = :usuario and p.id <> :id
              and (p.huellaOperacion = :huella or p.huellaOperacion is null)
              and ((p.estado = :preparado and p.venceEn > :ahora) or p.estado in :abiertos)
            """)
    boolean existeEquivalenteActivo(@Param("usuario") UUID usuario, @Param("id") UUID id,
            @Param("huella") String huella, @Param("ahora") Instant ahora,
            @Param("preparado") PagoEntity.Estado preparado, @Param("abiertos") Collection<PagoEntity.Estado> abiertos);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PagoEntity p where p.id = :id")
    Optional<PagoEntity> bloquear(@Param("id") UUID id);
    @Query("select p.id from PagoEntity p where p.estado in :estados and p.proximaConfirmacion <= :ahora order by p.proximaConfirmacion")
    List<UUID> pendientes(@Param("estados") Collection<PagoEntity.Estado> estados, @Param("ahora") Instant ahora, Pageable pagina);
}
