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
    @Transactional(transactionManager = "sfTransactionManager", readOnly = true)
    public CuentaResponse consultarPorNumeroCuenta(String numeroCuenta) {
        String numeroNormalizado = normalizarNumeroCuenta(numeroCuenta);
        try {
            CuentaEntity cuenta = cuentaRepository.findByNumeroCuenta(numeroNormalizado);
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
    @Transactional(transactionManager = "sfTransactionManager")
    public CuentaResponse desactivar(String numeroCuenta) {
        String numeroNormalizado = normalizarNumeroCuenta(numeroCuenta);
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

    private String normalizarNumeroCuenta(String numeroCuenta) {
        if (numeroCuenta == null || numeroCuenta.isBlank() || numeroCuenta.trim().length() > 24) {
            throw new ClienteValidationException("El numero de cuenta debe contener entre 1 y 24 caracteres");
        }
        return numeroCuenta.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    @Transactional(transactionManager = "sfTransactionManager", readOnly = true)
    public PaginaResponse<CuentaResponse> consultarActivas(int pagina, int tamanio) {
        if (pagina < 0 || tamanio < 1 || tamanio > 100) {
            throw new ClienteValidationException("Pagina o tamanio fuera del rango permitido");
        }
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
