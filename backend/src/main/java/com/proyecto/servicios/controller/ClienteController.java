package com.proyecto.servicios.controller;

import com.proyecto.servicios.model.cliente.ModificarClienteRequest;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.cliente.RegistrarClienteRequest;
import com.proyecto.servicios.model.cliente.ConsultaClientesRequest;
import com.proyecto.servicios.model.cliente.ClienteIdRequest;
import com.proyecto.servicios.model.GenericResponse;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.service.ClienteRegistroService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    public ResponseEntity<GenericResponse<ClienteResponse>> registrar(@Valid @RequestBody RegistrarClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(GenericResponse.exito(
                "Cliente registrado correctamente", clienteRegistroService.registrar(request)));
    }

    @PostMapping(value = "/registro", consumes = MediaType.APPLICATION_JSON_VALUE)
    @SecurityRequirements
    @Operation(summary = "Registro publico: la persona crea su propia cuenta y acceso",
            description = "No requiere sesion. Mismos datos y validaciones que el registro por ejecutivo; "
                    + "comparte el limite de intentos por IP del login.")
    public ResponseEntity<GenericResponse<ClienteResponse>> registrarPropio(
            @Valid @RequestBody RegistrarClienteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(GenericResponse.exito(
                "Cuenta creada correctamente", clienteRegistroService.registrarPropio(request)));
    }

    @GetMapping("/mis-datos")
    @Operation(summary = "Datos del cliente con sesion iniciada (rol CLIENTE)")
    public ResponseEntity<GenericResponse<ClienteResponse>> misDatos() {
        return ResponseEntity.ok(GenericResponse.exito("Datos consultados correctamente",
                clienteService.consultarPropio()));
    }

    @PutMapping(value = "/mis-datos", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "El cliente actualiza su contacto, domicilio, estado civil y empleo",
            description = "Nombre, fecha de nacimiento, CURP, RFC, sexo, nacionalidad y correo no se cambian aqui.")
    public ResponseEntity<GenericResponse<ClienteResponse>> actualizarMisDatos(
            @Valid @RequestBody com.proyecto.servicios.model.cliente.ActualizarMisDatosRequest request) {
        return ResponseEntity.ok(GenericResponse.exito("Datos actualizados correctamente",
                clienteService.actualizarPropio(request)));
    }

    @PostMapping(value = "/consulta", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Consulta o busca clientes de forma paginada",
            description = "Solo EJECUTIVO. Nombre, RFC y CURP admiten coincidencias parciales sin distinguir "
                    + "mayusculas; se combinan con OR. Sin coincidencias se devuelve el listado paginado, "
                    + "conservando activo y el rango de fechas. ID, correo y numeroCuenta son filtros exactos AND; "
                    + "si no existe el cliente identificado se devuelve 404. Una pagina fuera de rango no elimina "
                    + "la busqueda. Enviar {} consulta la primera pagina. Esta operacion no modifica datos.")
    public ResponseEntity<GenericResponse<PaginaResponse<ClienteResponse>>> consultarTodos(
            @Valid @RequestBody ConsultaClientesRequest filtros
    ) {
        var resultado = clienteService.consultar(filtros);
        return ResponseEntity.ok(GenericResponse.consulta("Clientes consultados correctamente",
                resultado, !resultado.contenido().isEmpty()));
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Actualiza los datos modificables del cliente")
    public ResponseEntity<GenericResponse<ClienteResponse>> actualizar(
            @Valid @RequestBody ModificarClienteRequest request
    ) {
        return ResponseEntity.ok(GenericResponse.exito("Cliente actualizado correctamente",
                clienteService.actualizar(request.id(), request.datos())));
    }

    @DeleteMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Desactiva logicamente al cliente y sus cuentas")
    public ResponseEntity<GenericResponse<ClienteResponse>> desactivar(@Valid @RequestBody ClienteIdRequest request) {
        return ResponseEntity.ok(GenericResponse.exito("Cliente desactivado correctamente", clienteService.desactivar(request.id())));
    }
}
