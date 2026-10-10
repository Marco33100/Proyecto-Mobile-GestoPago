package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.config.CuentaProperties;
import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import com.proyecto.servicios.exception.cliente.CuentaPersistenceException;
import com.proyecto.servicios.exception.cliente.ClienteValidationException;
import com.proyecto.servicios.exception.cliente.CuentaNoEncontradaException;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import com.proyecto.servicios.model.cliente.CuentaResponse;
import com.proyecto.servicios.model.cliente.PaginaResponse;
import com.proyecto.servicios.model.cliente.SaldoCuentaResponse;
import com.proyecto.servicios.model.cliente.ActualizarEstadoCuentaRequest;
import com.proyecto.servicios.exception.cliente.CuentaConflictException;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import static com.proyecto.servicios.support.CuentaDatos.normalizarNumeroCuenta;
import com.proyecto.servicios.support.PaginacionDatos;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.service.CuentaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class CuentaServiceImpl implements CuentaService {

    private static final String PREFIJO_CUENTA = "CTA-";

    private final CuentaRepository cuentaRepository;
    private final CuentaProperties cuentaProperties;
    private final ClienteResponseMapper responseMapper;

    @Autowired
    public CuentaServiceImpl(CuentaRepository cuentaRepository, CuentaProperties cuentaProperties,
                             ClienteResponseMapper responseMapper) {
        this.cuentaRepository = cuentaRepository;
        this.cuentaProperties = cuentaProperties;
        this.responseMapper = responseMapper;
    }

    @Override
    @PreAuthorize("hasAnyRole('EJECUTIVO', 'CLIENTE')")
    @Transactional(transactionManager = "sfTransactionManager", readOnly = true)
    public CuentaResponse consultarPorNumeroCuenta(String numeroCuenta) {
        String numeroNormalizado = normalizarNumeroCuenta(numeroCuenta);
        UUID propietario = propietarioParaConsulta();
        try {
            CuentaEntity cuenta = propietario == null
                    ? cuentaRepository.findByNumeroCuenta(numeroNormalizado)
                    : cuentaRepository.consultarCuentaPropia(numeroNormalizado, propietario);
            if (cuenta == null) {
                throw new CuentaNoEncontradaException("No existe la cuenta solicitada");
            }
            return responseMapper.toCuentaResponse(cuenta);
        } catch (DataAccessException exception) {
            log.error("Error al consultar cuenta: {}", exception.getClass().getSimpleName());
            throw new CuentaPersistenceException("No fue posible consultar la cuenta", exception);
        }
    }

    @Override
    @PreAuthorize("hasRole('EJECUTIVO')")
    @Transactional(transactionManager = "sfTransactionManager")
    public CuentaResponse desactivar(String numeroCuenta) {
        return desactivarNormalizada(normalizarNumeroCuenta(numeroCuenta));
    }

    // Ambos puntos de entrada tienen autorizacion y transaccion propias; no dependen de auto-invocacion AOP.
    private CuentaResponse desactivarNormalizada(String numeroNormalizado) {
        try {
            CuentaEntity cuenta = cuentaRepository.findByNumeroCuentaForUpdate(numeroNormalizado);
            if (cuenta == null) {
                throw new CuentaNoEncontradaException("No existe la cuenta solicitada");
            }
            if (cuenta.isActiva()) {
                cuenta.desactivar(Instant.now());
                cuentaRepository.saveAndFlush(cuenta);
            }
            // No modificar cliente, usuario, saldo ni sesiones al suspender una cuenta.
            return responseMapper.toCuentaResponse(cuenta);
        } catch (OptimisticLockingFailureException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            log.error("Error al desactivar cuenta: {}", exception.getClass().getSimpleName());
            throw new CuentaPersistenceException("No fue posible desactivar la cuenta", exception);
        }
    }

    @Override
    @PreAuthorize("hasRole('EJECUTIVO')")
    @Transactional(transactionManager = "sfTransactionManager")
    public CuentaResponse actualizarEstado(String numeroCuenta, ActualizarEstadoCuentaRequest request) {
        String numeroNormalizado = normalizarNumeroCuenta(numeroCuenta);
        if (request == null || request.activa() == null) {
            throw new ClienteValidationException("El campo activa es obligatorio");
        }
        if (request.activa()) {
            throw new CuentaConflictException("La reactivacion de cuentas no esta permitida");
        }
        return desactivarNormalizada(numeroNormalizado);
    }

    @Override
    @PreAuthorize("hasAnyRole('EJECUTIVO', 'CLIENTE')")
    @Transactional(transactionManager = "sfTransactionManager", readOnly = true)
    public SaldoCuentaResponse consultarSaldo(String numeroCuenta) {
        String numeroNormalizado = normalizarNumeroCuenta(numeroCuenta);
        UUID propietario = propietarioParaConsulta();
        try {
            SaldoCuentaResponse saldo = propietario == null
                    ? cuentaRepository.consultarSaldo(numeroNormalizado)
                    : cuentaRepository.consultarSaldoPropio(numeroNormalizado, propietario);
            if (saldo == null) {
                throw new CuentaNoEncontradaException("No existe la cuenta solicitada");
            }
            return saldo;
        } catch (DataAccessException exception) {
            log.error("Error al consultar saldo: {}", exception.getClass().getSimpleName());
            throw new CuentaPersistenceException("No fue posible consultar el saldo", exception);
        }
    }

    @Override
    @PreAuthorize("hasRole('CLIENTE')")
    @Transactional(transactionManager = "sfTransactionManager", readOnly = true)
    public PaginaResponse<CuentaResponse> consultarPropias(int pagina, int tamanio) {
        PaginacionDatos.validar(pagina, tamanio);
        UUID usuarioId = usuarioClienteAutenticado(autenticacionActual());
        try {
            return PaginaResponse.desde(cuentaRepository.consultarCuentasPropias(usuarioId,
                    PageRequest.of(pagina, tamanio, Sort.by("numeroCuenta")))
                    .map(responseMapper::toCuentaResponse));
        } catch (DataAccessException exception) {
            log.error("Error al consultar cuentas propias: {}", exception.getClass().getSimpleName());
            throw new CuentaPersistenceException("No fue posible consultar las cuentas propias", exception);
        }
    }

    // null identifica exclusivamente la consulta administrativa autorizada, nunca una sesion ausente.
    private UUID propietarioParaConsulta() {
        Authentication authentication = autenticacionActual();
        if (tieneRol(authentication, "ROLE_EJECUTIVO")) {
            return null;
        }
        return usuarioClienteAutenticado(authentication);
    }

    private Authentication autenticacionActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Se requiere una sesion valida");
        }
        return authentication;
    }

    private UUID usuarioClienteAutenticado(Authentication authentication) {
        if (!tieneRol(authentication, "ROLE_CLIENTE")
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)
                || user.userId() == null) {
            throw new AccessDeniedException("No tienes permisos para consultar esta cuenta");
        }
        return user.userId();
    }

    private boolean tieneRol(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    @Override
    @PreAuthorize("hasRole('EJECUTIVO')")
    @Transactional(transactionManager = "sfTransactionManager", readOnly = true)
    public PaginaResponse<CuentaResponse> consultarActivas(int pagina, int tamanio) {
        PaginacionDatos.validar(pagina, tamanio);
        try {
            return PaginaResponse.desde(cuentaRepository.findAllByActivaTrue(
                    PageRequest.of(pagina, tamanio, Sort.by("numeroCuenta")))
                    .map(responseMapper::toCuentaResponse));
        } catch (DataAccessException exception) {
            log.error("Error al consultar cuentas activas: {}", exception.getClass().getSimpleName());
            throw new CuentaPersistenceException("No fue posible consultar las cuentas activas", exception);
        }
    }

    @Override
    @PreAuthorize("hasRole('EJECUTIVO')")
    @Transactional(transactionManager = "sfTransactionManager")
    public CuentaEntity crearPara(ClienteEntity cliente) {
        if (!cliente.isActivo()) {
            throw new ClienteValidationException("No se puede crear una cuenta activa para un cliente inactivo");
        }
        try {
            Instant ahora = Instant.now();
            CuentaEntity cuenta = new CuentaEntity(
                    UUID.randomUUID(),
                    cliente,
                    generarNumeroCuenta(),
                    cuentaProperties.getSaldoInicial(),
                    ahora
            );
            return cuentaRepository.saveAndFlush(cuenta);
        } catch (DataAccessException exception) {
            log.error("No fue posible crear la cuenta bancaria para el cliente {}: {}",
                    cliente.getId(), exception.getClass().getSimpleName());
            throw new CuentaPersistenceException("No fue posible crear la cuenta bancaria", exception);
        }
    }

    private String generarNumeroCuenta() {
        return PREFIJO_CUENTA
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase(Locale.ROOT);
    }
}
