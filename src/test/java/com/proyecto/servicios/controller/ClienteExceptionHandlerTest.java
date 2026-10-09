package com.proyecto.servicios.controller;

import com.proyecto.servicios.service.ClienteRegistroService;
import com.proyecto.servicios.service.ClienteService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ClienteExceptionHandlerTest {
    @Test void fallaAlAbrirOConfirmarTransaccionDevuelveJson503Seguro() throws Exception {
        for (RuntimeException error : List.of(new CannotCreateTransactionException("password=secreto"),
                new TransactionSystemException("jdbc://servidor-interno"),
                new DataAccessResourceFailureException("detalle sql"))) {
            verificar(error, 503, "CLIENT_DATABASE_ERROR", "No fue posible completar la operacion de clientes o cuentas");
        }
    }

    @Test void conflictoOptimistaDevuelve409ParaPoderReintentar() throws Exception {
        verificar(new OptimisticLockingFailureException("sql interno"), 409, "CLIENT_CONCURRENT_UPDATE",
                "La informacion cambio durante la operacion; consulta nuevamente e intenta otra vez");
    }

    @Test void excepcionInesperadaNoExponeMensajesInternos() throws Exception {
        verificar(new IllegalStateException("token=secreto"), 500, "CLIENT_INTERNAL_ERROR", "Ocurrio un error interno");
    }

    private void verificar(RuntimeException error, int status, String code, String mensaje) throws Exception {
        var clientes = mock(ClienteService.class);
        when(clientes.consultarPorId(1L)).thenThrow(error);
        var mvc = MockMvcBuilders.standaloneSetup(new ClienteController(clientes, mock(ClienteRegistroService.class)))
                .setControllerAdvice(new ClienteExceptionHandler()).build();
        mvc.perform(get("/api/clientes/1")).andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("code").value(code)).andExpect(jsonPath("message").value(mensaje))
                .andExpect(jsonPath("path").value("/api/clientes/1"))
                .andExpect(jsonPath("timestamp").exists());
    }
}
