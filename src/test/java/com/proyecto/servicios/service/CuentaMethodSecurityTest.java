package com.proyecto.servicios.service;

import com.proyecto.servicios.config.CuentaProperties;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import com.proyecto.servicios.model.cliente.ActualizarEstadoCuentaRequest;
import com.proyecto.servicios.model.cliente.SaldoCuentaResponse;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import com.proyecto.servicios.exception.cliente.CuentaNoEncontradaException;
import com.proyecto.servicios.exception.cliente.ClienteValidationException;
import com.proyecto.servicios.exception.cliente.CuentaPersistenceException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.service.Impl.CuentaServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CuentaMethodSecurityTest {
    private static final String NUMERO = "CTA-0123456789ABCDEF0123";
    private final CuentaRepository repository = mock(CuentaRepository.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("app.database.enabled=true")
            .withUserConfiguration(SecurityConfig.class)
            .withBean(CuentaRepository.class, () -> repository)
            .withBean(CuentaProperties.class, CuentaProperties::new)
            .withBean(ClienteResponseMapper.class, ClienteResponseMapper::new);

    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test
    void llamadasDirectasAlServicioSinSesionNoLleganARepositorio() {
        runner.run(context -> {
            CuentaService service = context.getBean(CuentaService.class);
            for (Runnable call : llamadas(service)) {
                assertThrows(AuthenticationCredentialsNotFoundException.class, call::run);
            }
            verifyNoInteractions(repository);
        });
    }

    @Test
    void principalClienteSinIdentidadValidaNoPuedeConsultarNiModificar() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "cliente", null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
        runner.run(context -> {
            CuentaService service = context.getBean(CuentaService.class);
            for (Runnable call : llamadas(service)) {
                assertThrows(AccessDeniedException.class, call::run);
            }
            verifyNoInteractions(repository);
        });
    }

    @Test
    void clienteConsultaCuentaYSaldoSoloConSuIdentidadDeSesion() {
        UUID usuarioId = autenticarCliente();
        CuentaEntity cuenta = new CuentaEntity(UUID.randomUUID(), null, NUMERO, BigDecimal.TEN, Instant.now());
        when(repository.consultarCuentaPropia(NUMERO, usuarioId)).thenReturn(cuenta);
        var saldo = new SaldoCuentaResponse(NUMERO, BigDecimal.TEN);
        when(repository.consultarSaldoPropio(NUMERO, usuarioId)).thenReturn(saldo);
        runner.run(context -> {
            CuentaService service = context.getBean(CuentaService.class);
            assertEquals(NUMERO, service.consultarPorNumeroCuenta(" cta-0123456789abcdef0123 ").numeroCuenta());
            assertEquals(saldo, service.consultarSaldo(NUMERO));
        });
        verify(repository).consultarCuentaPropia(NUMERO, usuarioId);
        verify(repository).consultarSaldoPropio(NUMERO, usuarioId);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void cuentaAjenaEInexistenteNoSeBuscanSinFiltroDePropiedad() {
        UUID usuarioId = autenticarCliente();
        runner.run(context -> {
            CuentaService service = context.getBean(CuentaService.class);
            assertThrows(CuentaNoEncontradaException.class, () -> service.consultarPorNumeroCuenta(NUMERO));
            assertThrows(CuentaNoEncontradaException.class, () -> service.consultarSaldo(NUMERO));
        });
        verify(repository).consultarCuentaPropia(NUMERO, usuarioId);
        verify(repository).consultarSaldoPropio(NUMERO, usuarioId);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void clienteConPrincipalValidoNoAdministraCuentas() {
        autenticarCliente();
        runner.run(context -> {
            CuentaService service = context.getBean(CuentaService.class);
            for (Runnable call : List.<Runnable>of(() -> service.consultarActivas(0, 10),
                    () -> service.desactivar(NUMERO),
                    () -> service.actualizarEstado(NUMERO, new ActualizarEstadoCuentaRequest(false)),
                    () -> service.crearPara(null))) {
                assertThrows(AccessDeniedException.class, call::run);
            }
        });
        verifyNoInteractions(repository);
    }

    @Test
    void paginaPropiaUsaUsuarioDeSesionYLlevaLimitesValidados() {
        UUID usuarioId = autenticarCliente();
        when(repository.consultarCuentasPropias(eq(usuarioId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        runner.run(context -> {
            CuentaService service = context.getBean(CuentaService.class);
            assertEquals(0L, service.consultarPropias(0, 20).totalElementos());
            assertThrows(ClienteValidationException.class, () -> service.consultarPropias(-1, 20));
            assertThrows(ClienteValidationException.class, () -> service.consultarPropias(0, 101));
        });
        verify(repository).consultarCuentasPropias(eq(usuarioId), argThat(page -> page.getPageNumber() == 0
                && page.getPageSize() == 20 && page.getSort().getOrderFor("numeroCuenta") != null));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void falloDeBaseEnLecturasPropiasNoUsaConsultaAdministrativaComoAlternativa() {
        UUID usuarioId = autenticarCliente();
        when(repository.consultarCuentaPropia(NUMERO, usuarioId)).thenThrow(new DataAccessResourceFailureException("sql privado"));
        when(repository.consultarSaldoPropio(NUMERO, usuarioId)).thenThrow(new DataAccessResourceFailureException("sql privado"));
        when(repository.consultarCuentasPropias(eq(usuarioId), any(Pageable.class)))
                .thenThrow(new DataAccessResourceFailureException("sql privado"));
        runner.run(context -> {
            CuentaService service = context.getBean(CuentaService.class);
            assertThrows(CuentaPersistenceException.class, () -> service.consultarPorNumeroCuenta(NUMERO));
            assertThrows(CuentaPersistenceException.class, () -> service.consultarSaldo(NUMERO));
            assertThrows(CuentaPersistenceException.class, () -> service.consultarPropias(0, 20));
        });
        verify(repository).consultarCuentaPropia(NUMERO, usuarioId);
        verify(repository).consultarSaldoPropio(NUMERO, usuarioId);
        verify(repository).consultarCuentasPropias(eq(usuarioId), any(Pageable.class));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void ejecutivoNoUtilizaListadoPersonalDeClientes() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "ejecutivo", null, List.of(new SimpleGrantedAuthority("ROLE_EJECUTIVO"))));
        runner.run(context -> assertThrows(AccessDeniedException.class,
                () -> context.getBean(CuentaService.class).consultarPropias(0, 20)));
        verifyNoInteractions(repository);
    }

    private UUID autenticarCliente() {
        UUID usuarioId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(usuarioId, UUID.randomUUID(), "cliente@example.com", "cliente@example.com"),
                null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
        return usuarioId;
    }

    @Test
    void ejecutivoPuedeConsultarSaldoConUnaSolaConsulta() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "ejecutivo", null, List.of(new SimpleGrantedAuthority("ROLE_EJECUTIVO"))));
        var expected = new SaldoCuentaResponse(NUMERO, BigDecimal.TEN);
        when(repository.consultarSaldo(NUMERO)).thenReturn(expected);
        runner.run(context -> assertEquals(expected, context.getBean(CuentaService.class).consultarSaldo(NUMERO)));
        verify(repository).consultarSaldo(NUMERO);
        verifyNoMoreInteractions(repository);
    }

    private List<Runnable> llamadas(CuentaService service) {
        return List.of(() -> service.consultarPorNumeroCuenta(NUMERO), () -> service.consultarSaldo(NUMERO),
                () -> service.consultarPropias(0, 10), () -> service.consultarActivas(0, 10), () -> service.desactivar(NUMERO),
                () -> service.actualizarEstado(NUMERO, new ActualizarEstadoCuentaRequest(false)),
                () -> service.crearPara(null));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import(CuentaServiceImpl.class)
    static class SecurityConfig { }
}
