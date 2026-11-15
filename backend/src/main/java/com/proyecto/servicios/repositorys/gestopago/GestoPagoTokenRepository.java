package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.entity.gestopago.GestoPagoToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GestoPagoTokenRepository extends JpaRepository<GestoPagoToken, Integer> {

    Optional<GestoPagoToken> findByIdDistribuidorAndCodigoDispositivo(Integer idDistribuidor, String codigoDispositivo);
}
