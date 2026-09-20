package com.proyecto.servicios.repositorys.gestopago;

import com.proyecto.servicios.entity.gestopago.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<ProductEntity, Integer> {

    List<ProductEntity> findAllByOrderByProductIdAsc();
}
