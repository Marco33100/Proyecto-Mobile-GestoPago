package com.proyecto.servicios.service.Impl;

import static com.proyecto.servicios.support.ClienteDatos.normalizarTexto;
import static com.proyecto.servicios.support.ClienteDatos.normalizarOpcional;
import static com.proyecto.servicios.support.ClienteDatos.normalizarCorreo;
import static com.proyecto.servicios.support.ClienteDatos.validarMayoriaDeEdad;
import static com.proyecto.servicios.support.ClienteDatos.validarNombres;
import com.proyecto.servicios.support.PaginacionDatos;

import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import com.proyecto.servicios.entity.sf.DomicilioEntity;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import com.proyecto.servicios.model.cliente.UsuarioClienteModificadoEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.proyecto.servicios.exception.cliente.ClienteConflictException;
import com.proyecto.servicios.exception.cliente.ClienteYaRegistradoException;
import com.proyecto.servicios.exception.cliente.ClienteNoEncontradoException;
import com.proyecto.servicios.exception.cliente.ClientePersistenceException;
import com.proyecto.servicios.exception.cliente.ClienteValidationException;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import com.proyecto.servicios.model.cliente.ActualizarClienteRequest;
import com.proyecto.servicios.model.cliente.ActualizarDomicilioRequest;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.cliente.ConsultaClientesRequest;
import com.proyecto.servicios.repositorys.sf.ClienteRepository;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.repositorys.sf.DomicilioRepository;
import com.proyecto.servicios.service.ClienteService;
import com.proyecto.servicios.specification.ClienteSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
@PreAuthorize("hasRole('EJECUTIVO')")
@Transactional(transactionManager = "sfTransactionManager", readOnly = true)
public class ClienteServiceImpl implements ClienteService {

    private final ClienteRepository clienteRepository;
    private final DomicilioRepository domicilioRepository;
    private final CuentaRepository cuentaRepository;
    private final ClienteResponseMapper clienteResponseMapper;
    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public ClienteServiceImpl(
            ClienteRepository clienteRepository,
            DomicilioRepository domicilioRepository,
            CuentaRepository cuentaRepository,
            ClienteResponseMapper clienteResponseMapper,
            UserRepository userRepository,
            UserSessionRepository sessionRepository,
            ApplicationEventPublisher eventPublisher
    ) {
        this.clienteRepository = clienteRepository;
        this.domicilioRepository = domicilioRepository;
        this.cuentaRepository = cuentaRepository;
        this.clienteResponseMapper = clienteResponseMapper;
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public PaginaResponse<ClienteResponse> consultar(ConsultaClientesRequest request) {
        if (request.getId() != null) validarId(request.getId());
        String correo = request.getCorreo() == null ? null : normalizarCorreo(request.getCorreo());
        String numero = request.getNumeroCuenta() == null ? null
                : com.proyecto.servicios.support.CuentaDatos.normalizarNumeroCuenta(request.getNumeroCuenta());
        return buscar(request, correo, numero);
    }

    private PaginaResponse<ClienteResponse> buscar(ConsultaClientesRequest request, String correo, String numeroCuenta) {
        String nombre = textoBusqueda(request.getNombre(), 50);
        String rfc = textoBusqueda(request.getRfc(), 13);
        String curp = textoBusqueda(request.getCurp(), 18);
        LocalDate desde = request.getDesde();
        LocalDate hasta = request.getHasta();
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ClienteValidationException("La fecha desde no puede ser posterior a hasta");
        }
        PaginacionDatos.validar(request.getPagina(), request.getTamanio());
        Instant inicio = desde == null ? null : desde.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant finExclusivo = finExclusivo(hasta);
        var filtro = ClienteSpecification.filtrosExactos(request.getId(), correo, numeroCuenta, request.getActivo(), inicio, finExclusivo);
        try {
            PageRequest paginacion = PageRequest.of(request.getPagina(), request.getTamanio(),
                    Sort.by("fechaCreacion", "id").descending());
            boolean identificadorExacto = request.getId() != null || correo != null || numeroCuenta != null;
            boolean sinFiltros = !identificadorExacto && request.getActivo() == null && desde == null && hasta == null;
            Specification<ClienteEntity> coincidencias = ClienteSpecification.coincidencias(nombre, rfc, curp);
            Page<ClienteEntity> clientes = consultarPagina(filtro, coincidencias, paginacion, sinFiltros);
            if (identificadorExacto && clientes.getTotalElements() == 0) {
                throw new ClienteNoEncontradoException("No existe el cliente solicitado");
            }
            return mapearPagina(clientes);
        } catch (OptimisticLockingFailureException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.error("Error al consultar clientes con filtros: {}", exception.getClass().getSimpleName());
            throw new ClientePersistenceException("No fue posible consultar los clientes", exception);
        }
    }

    private Instant finExclusivo(LocalDate hasta) {
        if (hasta == null) return null;
        try {
            return hasta.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeException exception) {
            throw new ClienteValidationException("La fecha hasta no permite calcular el limite del rango");
        }
    }

    private Page<ClienteEntity> consultarPagina(Specification<ClienteEntity> filtro,
            Specification<ClienteEntity> coincidencias, PageRequest paginacion, boolean sinFiltros) {
        if (coincidencias != null) {
            var clientes = clienteRepository.findAll(filtro.and(coincidencias), paginacion);
            // Una pagina fuera de rango conserva el total y no activa el fallback.
            if (clientes.getTotalElements() > 0) return clientes;
        }
        return sinFiltros ? clienteRepository.findAll(paginacion) : clienteRepository.findAll(filtro, paginacion);
    }

    private String textoBusqueda(String texto, int maximo) {
        if (texto == null) return null;
        if (texto.length() > maximo) {
            throw new ClienteValidationException("El texto de busqueda supera la longitud permitida");
        }
        return texto.isBlank() ? null : texto.trim();
    }

    @Override
    @Transactional(transactionManager = "sfTransactionManager")
    public ClienteResponse actualizar(Long id, ActualizarClienteRequest request) {
        validarId(id);
        validarMayoriaDeEdad(request.fechaNacimiento());
        validarNombres(request.nombre(), request.apellidoPaterno(), request.apellidoMaterno());
        String correoNormalizado = normalizarCorreo(request.correo());

        try {
            ClienteEntity cliente = obtenerCliente(id);
            validarCorreoDisponible(correoNormalizado, cliente.getId());
            DomicilioEntity domicilio = obtenerDomicilio(id);
            Instant ahora = Instant.now();

            actualizarCliente(cliente, request, correoNormalizado, ahora);
            actualizarDomicilio(domicilio, request.domicilio(), ahora);
            actualizarUsuario(cliente);

            clienteRepository.save(cliente);
            domicilioRepository.saveAndFlush(domicilio);
            return clienteResponseMapper.toResponse(
                    cliente,
                    domicilio,
                    cuentaRepository.findAllByClienteId(id)
            );
        } catch (DataIntegrityViolationException exception) {
            log.error("Conflicto de datos al actualizar el cliente {}: {}", id, exception.getClass().getSimpleName());
            throw new ClienteConflictException(
                    "Los datos del cliente entran en conflicto con otro registro"
            );
        } catch (OptimisticLockingFailureException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.error("Error de base de datos al actualizar el cliente {}: {}", id, exception.getClass().getSimpleName());
            throw new ClientePersistenceException("No fue posible actualizar el cliente", exception);
        }
    }

    @Override
    @Transactional(transactionManager = "sfTransactionManager")
    public ClienteResponse desactivar(Long id) {
        validarId(id);
        try {
            ClienteEntity cliente = clienteRepository.findByIdForUpdate(id);
            if (cliente == null) {
                throw new ClienteNoEncontradoException("No existe un cliente con ID " + id);
            }
            if (cliente.isActivo()) {
                Instant ahora = Instant.now();
                cuentaRepository.desactivarTodasPorCliente(id, ahora);
                desactivarUsuario(id);
                cliente.desactivar(ahora);
                clienteRepository.saveAndFlush(cliente);
            }
            return cargarRespuesta(cliente);
        } catch (OptimisticLockingFailureException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.error("Error de base de datos al desactivar el cliente {}: {}", id, exception.getClass().getSimpleName());
            throw new ClientePersistenceException("No fue posible desactivar el cliente", exception);
        }
    }

    private void actualizarUsuario(ClienteEntity cliente) {
        UserEntity usuario = userRepository.findByClienteIdForUpdate(cliente.getId());
        if (usuario == null) {
            return; // Clientes anteriores a V11, sin usuario de acceso asociado.
        }
        String correoAnterior = usuario.getEmail();
        boolean cambioCorreo = !correoAnterior.equals(cliente.getCorreo());
        if (cambioCorreo && userRepository.existsByEmailOrIdentifier(cliente.getCorreo())) {
            throw new ClienteYaRegistradoException();
        }
        usuario.actualizarPerfilCliente(cliente.getCorreo(), cliente.getNombreCompleto());
        userRepository.saveAndFlush(usuario);
        if (cambioCorreo) {
            invalidarSesion(usuario, correoAnterior);
        }
    }

    private void desactivarUsuario(Long clienteId) {
        UserEntity usuario = userRepository.findByClienteIdForUpdate(clienteId);
        if (usuario != null) {
            usuario.desactivar();
            userRepository.saveAndFlush(usuario);
            invalidarSesion(usuario, usuario.getEmail());
        }
    }

    private void invalidarSesion(UserEntity usuario, String correoAnterior) {
        sessionRepository.deleteById(usuario.getId());
        eventPublisher.publishEvent(new UsuarioClienteModificadoEvent(
                usuario.getId(), correoAnterior, usuario.getEmail()));
    }


    private PaginaResponse<ClienteResponse> mapearPagina(Page<ClienteEntity> clientes) {
        if (clientes.isEmpty()) {
            return new PaginaResponse<>(
                    List.of(),
                    clientes.getNumber(),
                    clientes.getSize(),
                    clientes.getTotalElements(),
                    clientes.getTotalPages(),
                    clientes.isLast()
            );
        }

        List<Long> ids = clientes.stream().map(ClienteEntity::getId).toList();
        Map<Long, DomicilioEntity> domicilios = domicilioRepository.findAllByClienteIdIn(ids)
                .stream()
                .collect(Collectors.toMap(domicilio -> domicilio.getCliente().getId(), domicilio -> domicilio));
        Map<Long, List<CuentaEntity>> cuentas = cuentaRepository.findAllByClienteIdIn(ids)
                .stream()
                .collect(Collectors.groupingBy(cuenta -> cuenta.getCliente().getId()));

        Page<ClienteResponse> respuestas = clientes.map(cliente -> clienteResponseMapper.toResponse(
                cliente,
                domicilios.get(cliente.getId()),
                cuentas.getOrDefault(cliente.getId(), List.of())
        ));
        return PaginaResponse.desde(respuestas);
    }

    private ClienteResponse cargarRespuesta(ClienteEntity cliente) {
        return clienteResponseMapper.toResponse(
                cliente,
                domicilioRepository.findByClienteId(cliente.getId()),
                cuentaRepository.findAllByClienteId(cliente.getId())
        );
    }

    private void validarId(Long id) {
        if (id == null || id <= 0) {
            throw new ClienteValidationException("El ID debe ser mayor que cero");
        }
    }

    private ClienteEntity obtenerCliente(Long id) {
        ClienteEntity cliente = clienteRepository.findClienteById(id);
        if (cliente == null) {
            throw new ClienteNoEncontradoException("No existe un cliente con ID " + id);
        }
        return cliente;
    }

    private DomicilioEntity obtenerDomicilio(Long clienteId) {
        DomicilioEntity domicilio = domicilioRepository.findByClienteId(clienteId);
        if (domicilio == null) {
            throw new ClienteNoEncontradoException("El cliente no tiene un domicilio registrado");
        }
        return domicilio;
    }

    private void validarCorreoDisponible(String correo, Long clienteId) {
        if (clienteRepository.existsByCorreoAndIdNot(correo, clienteId)) {
            throw new ClienteYaRegistradoException();
        }
    }

    private void actualizarCliente(
            ClienteEntity cliente,
            ActualizarClienteRequest request,
            String correoNormalizado,
            Instant ahora
    ) {
        cliente.actualizar(new ClienteEntity.Nombre(normalizarTexto(request.nombre()), normalizarOpcional(request.segundoNombre()), normalizarTexto(request.apellidoPaterno()), normalizarTexto(request.apellidoMaterno())),
                new ClienteEntity.Perfil(request.fechaNacimiento(), request.sexo(), normalizarTexto(request.nacionalidad()), request.estadoCivil(), normalizarOpcional(request.referenciaReconocimientoFacial())),
                new ClienteEntity.Contacto(correoNormalizado, request.telefonoMovil(), normalizarOpcional(request.telefonoAlternativo())),
                new ClienteEntity.Empleo(normalizarTexto(request.ocupacion()), normalizarTexto(request.empresa()), request.ingresoMensual()),
                ahora);
    }

    private void actualizarDomicilio(
            DomicilioEntity domicilio,
            ActualizarDomicilioRequest request,
            Instant ahora
    ) {
        domicilio.actualizar(new DomicilioEntity.DatosCalle(normalizarTexto(request.calle()), normalizarTexto(request.numeroExterior()), normalizarOpcional(request.numeroInterior()), normalizarTexto(request.colonia())),
                new DomicilioEntity.Localidad(normalizarTexto(request.municipio()), normalizarTexto(request.estado()), request.codigoPostal(), normalizarTexto(request.pais())),
                ahora);
    }

}
