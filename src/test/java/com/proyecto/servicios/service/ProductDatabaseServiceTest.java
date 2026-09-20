package com.proyecto.servicios.service;

import com.proyecto.servicios.mapper.ProductEntityMapper;
import com.proyecto.servicios.model.product.ProductContainer;
import com.proyecto.servicios.model.product.ProductDto;
import com.proyecto.servicios.model.product.ProductListResponse;
import com.proyecto.servicios.model.product.ProductSyncResult;
import com.proyecto.servicios.repositorys.gestopago.ProductRepository;
import com.proyecto.servicios.service.Impl.ProductDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductDatabaseServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductDatabaseService service;

    @BeforeEach
    void setUp() {
        service = new ProductDatabaseService(productRepository, new ProductEntityMapper());
    }

    @Test
    void doesNotReplaceDatabaseWhenNewCatalogIsSmaller() {
        when(productRepository.count()).thenReturn(3L);

        ProductSyncResult result = service.replaceIfLarger(responseWithProducts(1, 2));

        assertFalse(result.updated());
        verify(productRepository, never()).deleteAllInBatch();
        verify(productRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void doesNotReplaceDatabaseWhenNewCatalogHasEqualSize() {
        when(productRepository.count()).thenReturn(2L);

        ProductSyncResult result = service.replaceIfLarger(responseWithProducts(1, 2));

        assertFalse(result.updated());
        verify(productRepository, never()).deleteAllInBatch();
        verify(productRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void doesNotReplaceDatabaseWhenCatalogIsEmpty() {
        when(productRepository.count()).thenReturn(0L);

        ProductSyncResult result = service.replaceIfLarger(responseWithProducts());

        assertFalse(result.updated());
        verify(productRepository, never()).deleteAllInBatch();
        verify(productRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void replacesDatabaseOnlyWhenNewCatalogIsLarger() {
        when(productRepository.count()).thenReturn(1L);

        ProductSyncResult result = service.replaceIfLarger(responseWithProducts(1, 2));

        assertTrue(result.updated());
        verify(productRepository).deleteAllInBatch();
        verify(productRepository).saveAllAndFlush(anyList());
    }

    private ProductListResponse responseWithProducts(Integer... ids) {
        List<ProductDto> products = java.util.Arrays.stream(ids).map(id -> {
            ProductDto product = new ProductDto();
            product.setIdProducto(id);
            product.setProducto("Producto " + id);
            return product;
        }).toList();

        ProductContainer container = new ProductContainer();
        container.setProductos(products);

        ProductListResponse response = new ProductListResponse();
        response.setProductos(container);
        return response;
    }
}
