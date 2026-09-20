package com.proyecto.servicios.client;

import com.proyecto.servicios.config.ProductClientConfiguration;
import com.proyecto.servicios.model.product.ProductListResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(
        name = "productCatalogClient",
        url = "${product.service.url}",
        configuration = ProductClientConfiguration.class
)
public interface ProductCatalogClient {

    @GetMapping("/sistema/service/getProductList.do")
    ResponseEntity<ProductListResponse> getProductList();
}
