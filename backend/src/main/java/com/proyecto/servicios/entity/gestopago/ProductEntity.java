package com.proyecto.servicios.entity.gestopago;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gestopago_products")
@Getter
@Setter
@NoArgsConstructor
public class ProductEntity {

    @Id
    @Column(name = "product_id", nullable = false)
    private Integer productId;

    @Column(name = "service_id")
    private Integer serviceId;

    @Column(name = "category_service_type_id")
    private Integer categoryServiceTypeId;

    @Column(name = "service_name", length = 200)
    private String serviceName;

    @Column(name = "product_name", nullable = false, length = 250)
    private String productName;

    @Column(name = "front_type")
    private Integer frontType;

    @Column(name = "has_check_digit")
    private Boolean hasCheckDigit;

    @Column(name = "price", precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "show_help")
    private Boolean showHelp;

    @Column(name = "reference_type", length = 20)
    private String referenceType;

    @Column(name = "legend", columnDefinition = "TEXT")
    private String legend;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
