package com.proyecto.servicios.service.Impl;

import static com.proyecto.servicios.support.ClienteDatos.normalizarTexto;
import static com.proyecto.servicios.support.ClienteDatos.normalizarOpcional;
import static com.proyecto.servicios.support.ClienteDatos.normalizarMayusculas;
import static com.proyecto.servicios.support.ClienteDatos.normalizarCorreo;
import static com.proyecto.servicios.support.ClienteDatos.validarMayoriaDeEdad;
import static com.proyecto.servicios.support.ClienteDatos.validarNombres;

import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import com.proyecto.servicios.entity.sf.DomicilioEntity;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.cliente.ClienteConflictException;
import com.proyecto.servicios.exception.cliente.CurpDuplicadaException;
import com.proyecto.servicios.exception.cliente.RfcDuplicadoException;
import com.proyecto.servicios.exception.cliente.ClienteYaRegistradoException;
import com.proyecto.servicios.exception.cliente.ClientePersistenceException;
import com.proyecto.servicios.exception.cliente.ClienteValidationException;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import com.proyecto.servicios.model.cliente.ActualizarDomicilioRequest;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.RegistrarClienteRequest;
import com.proyecto.servicios.model.cliente.ContrasenaSeguraValidator;
import com.proyecto.servicios.repositorys.sf.ClienteRepository;
import com.proyecto.servicios.repositorys.sf.DomicilioRepository;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.service.ClienteRegistroService;
import com.proyecto.servicios.service.CuentaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class ClienteRegistroServiceImpl implements ClienteRegistroService {

    private final ClienteRepository clienteRepository;
    private final DomicilioRepository domicilioRepository;
    private final CuentaService cuentaService;
    private final ClienteResponseMapper responseMapper;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public ClienteRegistroServiceImpl(
            ClienteRepository clienteRepository,
            DomicilioRepository domicilioRepository,
            CuentaService cuentaService,
            ClienteResponseMapper responseMapper,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.clienteRepository = clienteRepository;
        this.domicilioRepository = domicilioRepository;
        this.cuentaService = cuentaService;
        this.responseMapper = responseMapper;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(transactionManager = "sfTransactionManager")
    public ClienteResponse registrar(RegistrarClienteRequest request) {
        validarReglas(request);
        String curp = normalizarMayusculas(request.curp());
        String rfc = normalizarMayusculas(request.rfc());
        String correo = normalizarCorreo(request.correo());

        try {
            validarDuplicados(curp, rfc, correo);
            Instant ahora = Instant.now();
            ClienteEntity cliente = crearCliente(request, curp, rfc, correo, ahora);
            clienteRepository.saveAndFlush(cliente);
            DomicilioEntity domicilio = crearDomicilio(request.domicilio(), cliente, ahora);
            domicilioRepository.saveAndFlush(domicilio);
            CuentaEntity cuenta = cuentaService.crearPara(cliente);
            crearUsuario(cliente, request.contrasena(), ahora);
            return responseMapper.toResponse(cliente, domicilio, List.of(cuenta));
        } catch (DataIntegrityViolationException exception) {
            log.error("Conflicto de datos durante el registro del cliente: {}", exception.getClass().getSimpleName());
            throw new ClienteConflictException("Los datos del cliente entran en conflicto con otro registro");
        } catch (DataAccessException exception) {
            log.error("Error de base de datos durante el registro del cliente: {}", exception.getClass().getSimpleName());
            throw new ClientePersistenceException("No fue posible registrar el cliente", exception);
        }
    }

    private void validarReglas(RegistrarClienteRequest request) {
        if (!ContrasenaSeguraValidator.esValida(request.contrasena())) {
            throw new ClienteValidationException("La contrasena no cumple los requisitos de seguridad");
        }
        validarMayoriaDeEdad(request.fechaNacimiento());
        validarNombres(request.nombre(), request.apellidoPaterno(), request.apellidoMaterno());
    }

    private void validarDuplicados(String curp, String rfc, String correo) {
        if (clienteRepository.existsByCurp(curp)) {
            throw new CurpDuplicadaException();
        }
        if (clienteRepository.existsByRfc(rfc)) {
            throw new RfcDuplicadoException();
        }
        if (clienteRepository.existsByCorreo(correo)) {
            throw new ClienteYaRegistradoException();
        }
        if (userRepository.existsByEmailOrIdentifier(correo)) {
            throw new ClienteYaRegistradoException();
        }
    }

    private void crearUsuario(ClienteEntity cliente, String contrasena, Instant ahora) {
        UserEntity usuario = new UserEntity(UUID.randomUUID(), cliente.getCorreo(),
                passwordEncoder.encode(contrasena), cliente.getNombreCompleto(), cliente, ahora);
        userRepository.saveAndFlush(usuario);
    }

    private ClienteEntity crearCliente(
            RegistrarClienteRequest request,
            String curp,
            String rfc,
            String correo,
            Instant ahora
    ) {
        return new ClienteEntity(
                normalizarTexto(request.nombre()),
                normalizarOpcional(request.segundoNombre()),
                normalizarTexto(request.apellidoPaterno()),
                normalizarTexto(request.apellidoMaterno()),
                request.fechaNacimiento(),
                curp,
                rfc,
                request.sexo(),
                normalizarTexto(request.nacionalidad()),
                request.estadoCivil(),
                normalizarOpcional(request.referenciaReconocimientoFacial()),
                correo,
                request.telefonoMovil(),
                normalizarOpcional(request.telefonoAlternativo()),
                normalizarTexto(request.ocupacion()),
                normalizarTexto(request.empresa()),
                request.ingresoMensual(),
                ahora
        );
    }

    private DomicilioEntity crearDomicilio(
            ActualizarDomicilioRequest request,
            ClienteEntity cliente,
            Instant ahora
    ) {
        return new DomicilioEntity(
                UUID.randomUUID(),
                cliente,
                normalizarTexto(request.calle()),
                normalizarTexto(request.numeroExterior()),
                normalizarOpcional(request.numeroInterior()),
                normalizarTexto(request.colonia()),
                normalizarTexto(request.municipio()),
                normalizarTexto(request.estado()),
                request.codigoPostal(),
                normalizarTexto(request.pais()),
                ahora
        );
    }

}
