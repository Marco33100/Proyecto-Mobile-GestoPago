package com.proyecto.servicios.service;

import com.proyecto.servicios.config.CuentaProperties;
import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import com.proyecto.servicios.exception.cliente.CuentaPersistenceException;
import com.proyecto.servicios.exception.cliente.ClienteValidationException;
import com.proyecto.servicios.exception.cliente.CuentaNoEncontradaException;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.service.Impl.CuentaServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CuentaServiceImplTest {

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private ClienteEntity cliente;

    private CuentaServiceImpl cuentaService;

    @BeforeEach
    void setUp() {
        CuentaProperties properties = new CuentaProperties();
        properties.setSaldoInicial(new BigDecimal("100.00"));
        cuentaService = new CuentaServiceImpl(cuentaRepository, properties,
                new ClienteResponseMapper());
    }

    @Test
    void debeCrearUnaCuentaActivaConElSaldoConfigurado() {
        when(cliente.isActivo()).thenReturn(true);
        when(cuentaRepository.saveAndFlush(any(CuentaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CuentaEntity cuenta = cuentaService.crearPara(cliente);

        assertSame(cliente, cuenta.getCliente());
        assertEquals(new BigDecimal("100.00"), cuenta.getSaldo());
        assertTrue(cuenta.isActiva());
        assertTrue(cuenta.getNumeroCuenta().matches("CTA-[0-9A-F]{20}"));
    }

    @Test
    void debeTraducirElErrorDePersistencia() {
        when(cliente.isActivo()).thenReturn(true);
        when(cliente.getId()).thenReturn(1L);
        when(cuentaRepository.saveAndFlush(any(CuentaEntity.class)))
                .thenThrow(new DataAccessResourceFailureException("PostgreSQL no disponible"));

        assertThrows(CuentaPersistenceException.class, () -> cuentaService.crearPara(cliente));
    }

    @Test
    void noCreaCuentaParaClienteInactivo() {
        assertThrows(ClienteValidationException.class, () -> cuentaService.crearPara(cliente));
    }

    @Test
    void desactivarSoloCambiaEstadoSinAlterarSaldoNiCliente() {
        CuentaEntity cuenta = new CuentaEntity(UUID.randomUUID(), cliente,
                "CTA-0123456789ABCDEF0123", new BigDecimal("100.00"), Instant.now());
        when(cuentaRepository.findByNumeroCuentaForUpdate(cuenta.getNumeroCuenta())).thenReturn(cuenta);
        var response = cuentaService.desactivar(" cta-0123456789abcdef0123 ");
        assertEquals(false, response.activa());
        assertEquals(new BigDecimal("100.00"), response.saldo());
        verify(cuentaRepository).saveAndFlush(cuenta);
        verifyNoInteractions(cliente);
    }

    @Test
    void desactivarOtraVezNoModificaLaFechaNiReescribe() {
        CuentaEntity cuenta = new CuentaEntity(UUID.randomUUID(), cliente,
                "CTA-0123456789ABCDEF0123", BigDecimal.ZERO, Instant.now());
        Instant baja = Instant.now();
        cuenta.desactivar(baja);
        when(cuentaRepository.findByNumeroCuentaForUpdate(cuenta.getNumeroCuenta())).thenReturn(cuenta);
        assertEquals(false, cuentaService.desactivar(cuenta.getNumeroCuenta()).activa());
        assertEquals(baja, cuenta.getFechaActualizacion());
        verify(cuentaRepository, never()).saveAndFlush(any());
    }

    @Test
    void desactivarCuentaInexistenteNoGuarda() {
        assertThrows(CuentaNoEncontradaException.class, () -> cuentaService.desactivar("CTA-NOEXISTE"));
        verify(cuentaRepository, never()).saveAndFlush(any());
    }

    @Test
    void traduceFalloAlDesactivarCuenta() {
        when(cuentaRepository.findByNumeroCuentaForUpdate("CTA-NOEXISTE"))
                .thenThrow(new DataAccessResourceFailureException("fallo"));
        assertThrows(CuentaPersistenceException.class, () -> cuentaService.desactivar("CTA-NOEXISTE"));
    }

    @Test
    void consultaCuentaNormalizandoElNumero() {
        CuentaEntity cuenta = new CuentaEntity(UUID.randomUUID(), cliente,
                "CTA-0123456789ABCDEF0123", new BigDecimal("100.00"), Instant.now());
        when(cuentaRepository.findByNumeroCuenta(cuenta.getNumeroCuenta())).thenReturn(cuenta);
        var response = cuentaService.consultarPorNumeroCuenta(" cta-0123456789abcdef0123 ");
        assertEquals(cuenta.getNumeroCuenta(), response.numeroCuenta());
        assertEquals(new BigDecimal("100.00"), response.saldo());
    }

    @Test
    void cuentaInexistenteDevuelveExcepcionEspecifica() {
        assertThrows(CuentaNoEncontradaException.class,
                () -> cuentaService.consultarPorNumeroCuenta("CTA-NOEXISTE"));
    }

    @Test
    void traduceFalloAlConsultarCuenta() {
        when(cuentaRepository.findByNumeroCuenta("CTA-NOEXISTE"))
                .thenThrow(new DataAccessResourceFailureException("fallo"));
        assertThrows(CuentaPersistenceException.class,
                () -> cuentaService.consultarPorNumeroCuenta("CTA-NOEXISTE"));
    }

    @Test
    void paginaCuentasActivas() {
        CuentaEntity cuenta = new CuentaEntity(UUID.randomUUID(), cliente,
                "CTA-0123456789ABCDEF0123", BigDecimal.ZERO, Instant.now());
        when(cuentaRepository.findAllByActivaTrue(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(cuenta)));
        assertEquals(1L, cuentaService.consultarActivas(0, 20).totalElementos());
    }

    @Test
    void rechazaPaginaMayorAlLimite() {
        assertThrows(ClienteValidationException.class, () -> cuentaService.consultarActivas(0, 101));
        verifyNoInteractions(cuentaRepository);
    }

    @Test
    void traduceFalloAlConsultarCuentasActivas() {
        when(cuentaRepository.findAllByActivaTrue(any(Pageable.class)))
                .thenThrow(new DataAccessResourceFailureException("fallo"));
        assertThrows(CuentaPersistenceException.class, () -> cuentaService.consultarActivas(0, 20));
    }
}
