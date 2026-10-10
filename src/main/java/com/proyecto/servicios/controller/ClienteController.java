package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.cliente.ActualizarClienteRequest;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.cliente.RegistrarClienteRequest;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.ClienteRegistroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import static com.proyecto.servicios.support.CuentaDatos.NUMERO_CUENTA_REGEX;
import static com.proyecto.servicios.support.CuentaDatos.NUMERO_CUENTA_MENSAJE;
import static com.proyecto.servicios.support.PaginacionDatos.PAGINA_MINIMA;
import static com.proyecto.servicios.support.PaginacionDatos.TAMANIO_MINIMO;
import static com.proyecto.servicios.support.PaginacionDatos.TAMANIO_MAXIMO;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping(value = "/api/clientes", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Clientes", description = "Consulta y actualizacion de clientes personas fisicas")
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class ClienteController {

    private final ClienteService clienteService;
    private final ClienteRegistroService clienteRegistroService;

    @Autowired
    public ClienteController(ClienteService clienteService, ClienteRegistroService clienteRegistroService) {
        this.clienteService = clienteService;
        this.clienteRegistroService = clienteRegistroService;
    }

    @PostMapping
    @Operation(summary = "Registra cliente, domicilio, cuenta y usuario de acceso en una transaccion")
    public ResponseEntity<ClienteResponse> registrar(@Valid @RequestBody RegistrarClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteRegistroService.registrar(request));
    }

    @GetMapping
    @Operation(summary = "Consulta los clientes de forma paginada")
    public ResponseEntity<PaginaResponse<ClienteResponse>> consultarTodos(
            @RequestParam(defaultValue = "0") @Min(PAGINA_MINIMA) int pagina,
            @RequestParam(defaultValue = "20") @Min(TAMANIO_MINIMO) @Max(TAMANIO_MAXIMO) int tamanio,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta
    ) {
        return ResponseEntity.ok(clienteService.consultarTodos(pagina, tamanio, activo, desde, hasta));
    }

    @GetMapping("/correo")
    @Operation(summary = "Consulta un cliente por correo electronico")
    public ResponseEntity<ClienteResponse> consultarPorCorreo(
            @RequestParam @NotBlank @Email @Size(max = 100) String correo
    ) {
        return ResponseEntity.ok(clienteService.consultarPorCorreo(correo));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta un cliente por ID")
    public ResponseEntity<ClienteResponse> consultarPorId(@PathVariable @Positive(message = "El ID debe ser mayor que cero") Long id) {
        return ResponseEntity.ok(clienteService.consultarPorId(id));
    }

    @GetMapping("/curp/{curp}")
    @Operation(summary = "Consulta un cliente por CURP")
    public ResponseEntity<ClienteResponse> consultarPorCurp(
            @PathVariable @Pattern(regexp = "(?i)^[A-Z][AEIOUX][A-Z]{2}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[HM][A-Z]{2}[B-DF-HJ-NP-TV-Z]{3}[A-Z0-9][0-9]$",
                    message = "La CURP debe tener un formato valido de 18 caracteres") String curp) {
        return ResponseEntity.ok(clienteService.consultarPorCurp(curp));
    }

    @GetMapping("/rfc/{rfc}")
    @Operation(summary = "Consulta un cliente por RFC")
    public ResponseEntity<ClienteResponse> consultarPorRfc(
            @PathVariable @Pattern(regexp = "(?i)^[A-Z\u00d1&]{3,4}[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[A-Z0-9]{3}$",
                    message = "El RFC debe tener un formato valido de 12 o 13 caracteres") String rfc) {
        return ResponseEntity.ok(clienteService.consultarPorRfc(rfc));
    }

    @GetMapping("/cuenta/{numeroCuenta}")
    @Operation(summary = "Consulta un cliente por numero de cuenta")
    public ResponseEntity<ClienteResponse> consultarPorNumeroCuenta(
            @PathVariable @Pattern(regexp = NUMERO_CUENTA_REGEX, message = NUMERO_CUENTA_MENSAJE) String numeroCuenta) {
        return ResponseEntity.ok(clienteService.consultarPorNumeroCuenta(numeroCuenta));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualiza los datos modificables del cliente")
    public ResponseEntity<ClienteResponse> actualizar(
            @PathVariable @Positive(message = "El ID debe ser mayor que cero") Long id,
            @Valid @RequestBody ActualizarClienteRequest request
    ) {
        return ResponseEntity.ok(clienteService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desactiva logicamente al cliente y sus cuentas")
    public ResponseEntity<ClienteResponse> desactivar(@PathVariable @Positive(message = "El ID debe ser mayor que cero") Long id) {
        return ResponseEntity.ok(clienteService.desactivar(id));
    }
}
