package com.proyecto.servicios.service;

import com.proyecto.servicios.exception.cliente.ClienteNoEncontradoException;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.model.cliente.ConsultaClientesRequest;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
            .withBean(ClienteResponseMapper.class, () -> org.mapstruct.factory.Mappers.getMapper(ClienteResponseMapper.class))
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
        when(clientes.findAll(org.mockito.ArgumentMatchers.<Specification<ClienteEntity>>any(), any(Pageable.class))).thenReturn(Page.empty());
        var request = new ConsultaClientesRequest();
        request.setId(1L);
        runner.run(context -> assertThrows(ClienteNoEncontradoException.class,
                () -> context.getBean(ClienteService.class).consultar(request)));
        verify(clientes).findAll(org.mockito.ArgumentMatchers.<Specification<ClienteEntity>>any(), any(Pageable.class));
        verifyNoMoreInteractions(clientes);
        verifyNoInteractions(cuentas, domicilios, usuarios, sesiones);
    }

    private void autenticar(String rol) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "actor-del-contexto", null, List.of(new SimpleGrantedAuthority(rol))));
    }

    private List<Runnable> llamadas(ClienteService servicio, ClienteRegistroService registro) {
        var identificador = new ConsultaClientesRequest();
        identificador.setId(1L);
        var cuenta = new ConsultaClientesRequest();
        cuenta.setNumeroCuenta("CTA-FFFFFFFFFFFFFFFFFFFF");
        var correo = new ConsultaClientesRequest();
        correo.setCorreo("otro@example.com");
        var filtros = new ConsultaClientesRequest();
        filtros.setNombre("Marco");
        filtros.setRfc("MOMA");
        filtros.setCurp("MOMA");
        filtros.setActivo(true);
        return List.of(() -> servicio.consultar(identificador), () -> servicio.consultar(cuenta),
                () -> servicio.consultar(correo), () -> servicio.consultar(filtros),
                () -> servicio.consultar(new ConsultaClientesRequest()),
                () -> servicio.actualizar(2L, null), () -> servicio.desactivar(2L),
                () -> registro.registrar(null));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    @Import({ClienteServiceImpl.class, ClienteRegistroServiceImpl.class})
    static class SecurityConfig { }
}
