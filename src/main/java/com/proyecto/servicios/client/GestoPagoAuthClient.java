package com.proyecto.servicios.client;

import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "gestoPagoAuth", url = "${gestopago.auth.url:http://localhost}")
public interface GestoPagoAuthClient {

    @PostMapping("/sistema/app/jwt-gp/authenticate/")
    GestoPagoAuthResponse authenticate(
            @RequestParam("idDistribuidor") Integer idDistribuidor,
            @RequestParam("codigoDispositivo") String codigoDispositivo,
            @RequestParam("password") String password
    );
}
