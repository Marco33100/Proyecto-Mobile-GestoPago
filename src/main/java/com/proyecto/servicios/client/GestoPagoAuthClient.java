package com.proyecto.servicios.client;

import com.proyecto.servicios.model.gestopago.GestoPagoAuthResponse;
import com.proyecto.servicios.config.GestoPagoAuthClientConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "gestoPagoAuth", url = "${gestopago.auth.url:https://gestopago.portalventas.net}",
        configuration = GestoPagoAuthClientConfiguration.class)
public interface GestoPagoAuthClient {

    @PostMapping("/sistema/app/jwt-gp/authenticate/")
    GestoPagoAuthResponse authenticate(
            @RequestParam("idDistribuidor") Integer idDistribuidor,
            @RequestParam("codigoDispositivo") String codigoDispositivo,
            @RequestParam("password") String password
    );
}
