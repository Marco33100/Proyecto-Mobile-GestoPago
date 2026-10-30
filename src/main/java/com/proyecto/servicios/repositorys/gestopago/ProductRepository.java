package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.entity.gestopago.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<ProductEntity, Integer> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from ProductEntity p where p.productId = :id")
    Optional<ProductEntity> consultarParaPago(@Param("id") Integer id);

    List<ProductEntity> findAllByOrderByProductIdAsc();
}
