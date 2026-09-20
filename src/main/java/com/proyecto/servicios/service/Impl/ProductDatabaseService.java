package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.entity.gestopago.ProductEntity;
import com.proyecto.servicios.exception.ProductIntegrationErrorType;
import com.proyecto.servicios.exception.ProductIntegrationException;
import com.proyecto.servicios.mapper.ProductEntityMapper;
import com.proyecto.servicios.model.product.ProductContainer;
import com.proyecto.servicios.model.product.ProductDto;
import com.proyecto.servicios.model.product.ProductListResponse;
import com.proyecto.servicios.model.product.ProductMessage;
import com.proyecto.servicios.model.product.ProductSyncResult;
import com.proyecto.servicios.repositorys.gestopago.ProductRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class ProductDatabaseService {

    private final ProductRepository productRepository;
    private final ProductEntityMapper productEntityMapper;

    public ProductDatabaseService(
            ProductRepository productRepository,
            ProductEntityMapper productEntityMapper
    ) {
        this.productRepository = productRepository;
        this.productEntityMapper = productEntityMapper;
    }

    @Transactional(transactionManager = "sfTransactionManager", readOnly = true)
    public Optional<ProductListResponse> findCatalog() {
        try {
            List<ProductEntity> entities = productRepository.findAllByOrderByProductIdAsc();
            if (entities.isEmpty()) {
                return Optional.empty();
            }

            ProductContainer container = new ProductContainer();
            container.setProductos(entities.stream().map(productEntityMapper::toDto).toList());

            ProductMessage message = new ProductMessage();
            message.setCodigo("01");
            message.setTexto("Catálogo obtenido desde PostgreSQL");

            ProductListResponse response = new ProductListResponse();
            response.setMensaje(message);
            response.setProductos(container);
            return Optional.of(response);
        } catch (Exception exception) {
            throw databaseError("No fue posible consultar el catálogo en PostgreSQL", exception);
        }
    }

    @Transactional(transactionManager = "sfTransactionManager")
    public ProductSyncResult replaceIfLarger(ProductListResponse response) {
        try {
            List<ProductDto> products = productsOf(response);
            long previousCount = productRepository.count();

            Map<Integer, ProductDto> uniqueProducts = new LinkedHashMap<>();
            for (ProductDto product : products) {
                if (product.getIdProducto() == null) {
                    throw new IllegalArgumentException("Gestopago devolvió un producto sin idProducto");
                }
                uniqueProducts.put(product.getIdProducto(), product);
            }
            int receivedCount = uniqueProducts.size();

            if (receivedCount == 0 || receivedCount <= previousCount) {
                return new ProductSyncResult(false, previousCount, receivedCount);
            }

            productRepository.deleteAllInBatch();
            productRepository.saveAllAndFlush(
                    uniqueProducts.values().stream().map(productEntityMapper::toEntity).toList()
            );
            return new ProductSyncResult(true, previousCount, receivedCount);
        } catch (ProductIntegrationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw databaseError("Gestopago respondió, pero el catálogo no pudo guardarse en PostgreSQL", exception);
        }
    }

    private List<ProductDto> productsOf(ProductListResponse response) {
        if (response == null || response.getProductos() == null
                || response.getProductos().getProductos() == null) {
            throw new ProductIntegrationException(
                    ProductIntegrationErrorType.DATABASE_ERROR,
                    "Gestopago respondió, pero los productos no pudieron serializarse"
            );
        }
        return response.getProductos().getProductos();
    }

    private ProductIntegrationException databaseError(String message, Exception cause) {
        return new ProductIntegrationException(
                ProductIntegrationErrorType.DATABASE_ERROR,
                message,
                cause
        );
    }
}
