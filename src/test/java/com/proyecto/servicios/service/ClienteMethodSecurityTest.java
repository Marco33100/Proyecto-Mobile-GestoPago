package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.cliente.ClienteNoEncontradoException;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import com.proyecto.servicios.repositorys.sf.ClienteRepository;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.repositorys.sf.DomicilioRepository;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import com.proyecto.servicios.service.Impl.ClienteRegistroServiceImpl;
import com.proyecto.servicios.service.Impl.ClienteServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ClienteMethodSecurityTest {
    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final CuentaRepository cuentas = mock(CuentaRepository.class);
    private final DomicilioRepository domicilios = mock(DomicilioRepository.class);
    private final UserRepository usuarios = mock(UserRepository.class);
    private final UserSessionRepository sesiones = mock(UserSessionRepository.class);
    private final CuentaService servicioCuentas = mock(CuentaService.class);
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("app.database.enabled=true")
            .withUserConfiguration(SecurityConfig.class)
            .withBean(ClienteRepository.class, () -> clientes)
            .withBean(CuentaRepository.class, () -> cuentas)
            .withBean(DomicilioRepository.class, () -> domicilios)
            .withBean(UserRepository.class, () -> usuarios)
            .withBean(UserSessionRepository.class, () -> sesiones)
            .withBean(CuentaService.class, () -> servicioCuentas)
            .withBean(ClienteResponseMapper.class, ClienteResponseMapper::new)
            .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
            .withBean(ApplicationEventPublisher.class, () -> mock(ApplicationEventPublisher.class));

    @AfterEach void limpiar() { SecurityContextHolder.clearContext(); }

    @Test
    void sinSesionNoSeConsultaNiModificaInformacionPersonalOBancaria() {
        runner.run(context -> {
            for (Runnable llamada : llamadas(context.getBean(ClienteService.class),
                    context.getBean(ClienteRegistroService.class))) {
                assertThrows(AuthenticationCredentialsNotFoundException.class, llamada::run);
            }
        });
        verifyNoInteractions(clientes, cuentas, domicilios, usuarios, sesiones, servicioCuentas);
    }

    @Test
    void clienteNoPuedeEvitarLaPoliticaAdministrativaLlamandoDirectamenteAlServicio() {
        autenticar("ROLE_CLIENTE");
        runner.run(context -> {
            for (Runnable llamada : llamadas(context.getBean(ClienteService.class),
                    context.getBean(ClienteRegistroService.class))) {
                assertThrows(AccessDeniedException.class, llamada::run);
            }
        });
        verifyNoInteractions(clientes, cuentas, domicilios, usuarios, sesiones, servicioCuentas);
    }

    @Test
    void ejecutivoLlegaAlRepositorioYRecibeElErrorDeNegocioSiNoExisteElCliente() {
        autenticar("ROLE_EJECUTIVO");
        runner.run(context -> assertThrows(ClienteNoEncontradoException.class,
                () -> context.getBean(ClienteService.class).consultarPorId(1L)));
        verify(clientes).findClienteById(1L);
        verifyNoMoreInteractions(clientes);
        verifyNoInteractions(cuentas, domicilios, usuarios, sesiones);
    }

    private void autenticar(String rol) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "actor-del-contexto", null, List.of(new SimpleGrantedAuthority(rol))));
    }

    private List<Runnable> llamadas(ClienteService servicio, ClienteRegistroService registro) {
        return List.of(() -> servicio.consultarPorId(1L), () -> servicio.consultarPorId(2L),
                () -> servicio.consultarPorNumeroCuenta("CTA-FFFFFFFFFFFFFFFFFFFF"),
                () -> servicio.consultarPorCorreo("otro@example.com"),
                () -> servicio.consultarPorCurp("MOMA900101HDFRRR01"),
                () -> servicio.consultarPorRfc("MOMA900101AB1"),
                () -> servicio.consultarTodos(0, 10),
                () -> servicio.consultarTodos(0, 10, true, null, null),
                () -> servicio.actualizar(2L, null), () -> servicio.desactivar(2L),
                () -> registro.registrar(null));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import({ClienteServiceImpl.class, ClienteRegistroServiceImpl.class})
    static class SecurityConfig { }
}
