package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import com.proyecto.servicios.entity.sf.DomicilioEntity;
import com.proyecto.servicios.entity.sf.EstadoCivil;
import com.proyecto.servicios.entity.sf.Sexo;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.model.cliente.UsuarioClienteModificadoEvent;
import com.proyecto.servicios.exception.cliente.ClienteNoEncontradoException;
import com.proyecto.servicios.exception.cliente.ClienteValidationException;
import com.proyecto.servicios.exception.cliente.ClientePersistenceException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import com.proyecto.servicios.model.cliente.ActualizarClienteRequest;
import com.proyecto.servicios.model.cliente.ActualizarDomicilioRequest;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.repositorys.sf.ClienteRepository;
import com.proyecto.servicios.repositorys.sf.CuentaRepository;
import com.proyecto.servicios.repositorys.sf.DomicilioRepository;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import org.springframework.context.ApplicationEventPublisher;
import com.proyecto.servicios.service.Impl.ClienteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import org.mockito.InOrder;

@ExtendWith(MockitoExtension.class)
class ClienteServiceImplTest {

    @Mock
    private ClienteRepository clienteRepository;

    @Mock
    private DomicilioRepository domicilioRepository;

    @Mock
    private CuentaRepository cuentaRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserSessionRepository sessionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ClienteServiceImpl clienteService;

    @BeforeEach
    void setUp() {
        clienteService = new ClienteServiceImpl(
                clienteRepository,
                domicilioRepository,
                cuentaRepository,
                new ClienteResponseMapper(), userRepository, sessionRepository, eventPublisher
        );
    }

    @Test
    void debeActualizarDatosSinModificarCurpNiRfc() {
        ClienteEntity cliente = cliente();
        DomicilioEntity domicilio = domicilio(cliente);
        ActualizarClienteRequest request = requestActualizacion();
        UserEntity usuario = new UserEntity(UUID.randomUUID(), cliente.getCorreo(), "hash",
                cliente.getNombreCompleto(), cliente, Instant.now());
        when(userRepository.findByClienteIdForUpdate(cliente.getId())).thenReturn(usuario);

        when(clienteRepository.findClienteById(cliente.getId())).thenReturn(cliente);
        when(clienteRepository.existsByCorreoAndIdNot("nuevo@correo.com", cliente.getId())).thenReturn(false);
        when(domicilioRepository.findByClienteId(cliente.getId())).thenReturn(domicilio);
        when(clienteRepository.save(any(ClienteEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(domicilioRepository.saveAndFlush(any(DomicilioEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cuentaRepository.findAllByClienteId(cliente.getId())).thenReturn(List.of());

        ClienteResponse response = clienteService.actualizar(cliente.getId(), request);

        assertEquals("MOMA900101HDFRRR01", response.curp());
        assertEquals("MOMA900101AB1", response.rfc());
        assertEquals("Marco Antonio", response.nombre());
        assertEquals("Avenida Reforma", response.domicilio().calle());
        assertEquals("nuevo@correo.com", response.correo());
        assertEquals("nuevo@correo.com", usuario.getEmail());
        assertEquals("nuevo@correo.com", usuario.getIdentifier());
        verify(sessionRepository).deleteById(usuario.getId());
        verify(eventPublisher).publishEvent(any(UsuarioClienteModificadoEvent.class));
        verify(clienteRepository).existsByCorreoAndIdNot("nuevo@correo.com", cliente.getId());
        verify(clienteRepository, org.mockito.Mockito.never()).findByCorreo("nuevo@correo.com");
    }

    @Test
    void debeConsultarClientePorNumeroDeCuenta() {
        ClienteEntity cliente = cliente();
        DomicilioEntity domicilio = domicilio(cliente);
        CuentaEntity cuenta = new CuentaEntity(
                UUID.randomUUID(),
                cliente,
                "CTA-0123456789ABCDEF0123",
                BigDecimal.ZERO,
                Instant.now()
        );
        when(cuentaRepository.findByNumeroCuenta(cuenta.getNumeroCuenta())).thenReturn(cuenta);
        when(domicilioRepository.findByClienteId(cliente.getId())).thenReturn(domicilio);
        when(cuentaRepository.findAllByClienteId(cliente.getId())).thenReturn(List.of(cuenta));

        ClienteResponse response = clienteService.consultarPorNumeroCuenta(cuenta.getNumeroCuenta());

        assertEquals(cliente.getId(), response.id());
        assertEquals(cuenta.getNumeroCuenta(), response.cuentas().get(0).numeroCuenta());
        assertTrue(response.cuentas().get(0).activa());
    }

    @Test
    void debeResponderNoEncontradoCuandoNoExisteElCliente() {
        Long id = 999L;
        when(clienteRepository.findClienteById(id)).thenReturn(null);

        assertThrows(ClienteNoEncontradoException.class, () -> clienteService.consultarPorId(id));
    }

    @Test
    void debeDesactivarClienteYTodasSusCuentasSinBorrarlos() {
        ClienteEntity cliente = cliente();
        UserEntity usuario = new UserEntity(UUID.randomUUID(), cliente.getCorreo(), "hash",
                cliente.getNombreCompleto(), cliente, Instant.now());
        when(userRepository.findByClienteIdForUpdate(cliente.getId())).thenReturn(usuario);
        when(clienteRepository.findByIdForUpdate(cliente.getId())).thenReturn(cliente);
        when(domicilioRepository.findByClienteId(cliente.getId())).thenReturn(domicilio(cliente));
        when(cuentaRepository.findAllByClienteId(cliente.getId())).thenReturn(List.of());

        ClienteResponse response = clienteService.desactivar(cliente.getId());

        assertTrue(!response.activo());
        InOrder orden = inOrder(cuentaRepository, clienteRepository);
        orden.verify(cuentaRepository).desactivarTodasPorCliente(any(Long.class), any(Instant.class));
        orden.verify(clienteRepository).saveAndFlush(cliente);
        verify(cuentaRepository).findAllByClienteId(cliente.getId());
        assertTrue(!usuario.isEnabled());
        verify(sessionRepository).deleteById(usuario.getId());
        verify(eventPublisher).publishEvent(any(UsuarioClienteModificadoEvent.class));
    }

    private ClienteEntity cliente() {
        ClienteEntity cliente = new ClienteEntity(
                "Marco",
                null,
                "Morales",
                "Martinez",
                LocalDate.of(1990, 1, 1),
                "MOMA900101HDFRRR01",
                "MOMA900101AB1",
                Sexo.MASCULINO,
                "Mexicana",
                EstadoCivil.SOLTERO,
                null,
                "marco@correo.com",
                "5512345678",
                null,
                "Desarrollador",
                "Empresa",
                new BigDecimal("30000.00"),
                Instant.now()
        );
        ReflectionTestUtils.setField(cliente, "id", 1L);
        return cliente;
    }

    @Test
    void consultaPorCorreoNormalizado() {
        ClienteEntity cliente = cliente();
        when(clienteRepository.findByCorreo("marco@correo.com")).thenReturn(cliente);
        when(cuentaRepository.findAllByClienteId(cliente.getId())).thenReturn(List.of());

        assertEquals(cliente.getId(), clienteService.consultarPorCorreo(" MARCO@CORREO.COM ").id());
    }

    @Test
    void rechazaRangoDeFechasInvertido() {
        assertThrows(ClienteValidationException.class,
                () -> clienteService.consultarTodos(0, 20, true,
                        LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 1)));
        verifyNoInteractions(clienteRepository, domicilioRepository, cuentaRepository);
    }

    @Test
    void traduceFalloDeBaseDeDatosEnConsultaFiltrada() {
        when(clienteRepository.findAll(org.mockito.ArgumentMatchers.<Specification<ClienteEntity>>any(),
                any(Pageable.class)))
                .thenThrow(new DataAccessResourceFailureException("fallo"));
        assertThrows(ClientePersistenceException.class,
                () -> clienteService.consultarTodos(0, 20, true, null, null));
    }

    @Test
    void paginaCargaRelacionesPorLotesYNoUnaConsultaPorCliente() {
        ClienteEntity primero = cliente();
        ClienteEntity segundo = cliente();
        ReflectionTestUtils.setField(segundo, "id", 2L);
        var pagina = new org.springframework.data.domain.PageImpl<>(List.of(primero, segundo),
                org.springframework.data.domain.PageRequest.of(0, 20), 2);
        when(clienteRepository.findAll(any(Pageable.class))).thenReturn(pagina);
        when(domicilioRepository.findAllByClienteIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(domicilio(primero), domicilio(segundo)));
        when(cuentaRepository.findAllByClienteIdIn(List.of(1L, 2L))).thenReturn(List.of());

        var respuesta = clienteService.consultarTodos(0, 20);

        assertEquals(2, respuesta.totalElementos());
        verify(domicilioRepository).findAllByClienteIdIn(List.of(1L, 2L));
        verify(cuentaRepository).findAllByClienteIdIn(List.of(1L, 2L));
        verify(domicilioRepository, org.mockito.Mockito.never()).findByClienteId(any());
        verify(cuentaRepository, org.mockito.Mockito.never()).findAllByClienteId(any());
        org.mockito.Mockito.verifyNoMoreInteractions(clienteRepository, domicilioRepository, cuentaRepository);
    }

    @Test
    void correoDeOtroClienteImpideActualizarSinCargarSuEntidad() {
        ClienteEntity cliente = cliente();
        when(clienteRepository.findClienteById(cliente.getId())).thenReturn(cliente);
        when(clienteRepository.existsByCorreoAndIdNot("nuevo@correo.com", cliente.getId())).thenReturn(true);

        assertThrows(com.proyecto.servicios.exception.cliente.ClienteYaRegistradoException.class,
                () -> clienteService.actualizar(cliente.getId(), requestActualizacion()));
        verifyNoInteractions(domicilioRepository, cuentaRepository, userRepository);
        verify(clienteRepository, org.mockito.Mockito.never()).findByCorreo(any());
    }

    private DomicilioEntity domicilio(ClienteEntity cliente) {
        return new DomicilioEntity(
                UUID.randomUUID(),
                cliente,
                "Calle Uno",
                "10",
                null,
                "Centro",
                "Cuauhtemoc",
                "Ciudad de Mexico",
                "06000",
                "Mexico",
                Instant.now()
        );
    }

    private ActualizarClienteRequest requestActualizacion() {
        return new ActualizarClienteRequest(
                "  Marco    Antonio  ",
                null,
                "Morales",
                "Martinez",
                LocalDate.of(1990, 1, 1),
                Sexo.MASCULINO,
                "Mexicana",
                EstadoCivil.CASADO,
                null,
                "  NUEVO@CORREO.COM ",
                "5512345678",
                "",
                "Arquitecto de software",
                "Empresa Nueva",
                new BigDecimal("45000.00"),
                new ActualizarDomicilioRequest(
                        "  Avenida    Reforma ",
                        "100",
                        null,
                        "Juarez",
                        "Cuauhtemoc",
                        "Ciudad de Mexico",
                        "06600",
                        "Mexico"
                )
        );
    }
}
