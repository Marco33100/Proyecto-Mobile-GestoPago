package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.ClienteEntity;
import com.proyecto.servicios.entity.sf.CuentaEntity;
import com.proyecto.servicios.entity.sf.DomicilioEntity;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.entity.sf.EstadoCivil;
import com.proyecto.servicios.entity.sf.Sexo;
import com.proyecto.servicios.exception.cliente.CurpDuplicadaException;
import com.proyecto.servicios.exception.cliente.RfcDuplicadoException;
import com.proyecto.servicios.exception.cliente.ClienteYaRegistradoException;
import com.proyecto.servicios.mapper.ClienteResponseMapper;
import com.proyecto.servicios.model.cliente.ActualizarDomicilioRequest;
import com.proyecto.servicios.model.cliente.ClienteResponse;
import com.proyecto.servicios.model.cliente.RegistrarClienteRequest;
import com.proyecto.servicios.repositorys.sf.ClienteRepository;
import com.proyecto.servicios.repositorys.sf.DomicilioRepository;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import com.proyecto.servicios.exception.cliente.ClientePersistenceException;
import com.proyecto.servicios.service.Impl.ClienteRegistroServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClienteRegistroServiceImplTest {

    @Mock private ClienteRepository clienteRepository;
    @Mock private DomicilioRepository domicilioRepository;
    @Mock private CuentaService cuentaService;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private ClienteRegistroServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClienteRegistroServiceImpl(
                clienteRepository, domicilioRepository, cuentaService, new ClienteResponseMapper(),
                userRepository, passwordEncoder
        );
    }

    @Test
    void registraClienteDomicilioYCuentaActiva() {
        when(passwordEncoder.encode("Segura123!")).thenReturn("hash-bcrypt");
        when(clienteRepository.saveAndFlush(any(ClienteEntity.class))).thenAnswer(invocation -> {
            ClienteEntity cliente = invocation.getArgument(0);
            ReflectionTestUtils.setField(cliente, "id", 1L);
            return cliente;
        });
        when(cuentaService.crearPara(any(ClienteEntity.class))).thenAnswer(invocation -> {
            ClienteEntity cliente = invocation.getArgument(0);
            return new CuentaEntity(
                    UUID.randomUUID(), cliente, "CTA-0123456789ABCDEF0123", BigDecimal.ZERO, Instant.now()
            );
        });

        ClienteResponse response = service.registrar(request());

        assertEquals(1L, response.id());
        assertEquals("MOMA900101HDFRRR01", response.curp());
        assertEquals("marco@example.com", response.correo());
        assertTrue(response.activo());
        assertTrue(response.cuentas().get(0).activa());
        verify(clienteRepository).saveAndFlush(any(ClienteEntity.class));
        verify(domicilioRepository).saveAndFlush(any(DomicilioEntity.class));
        ArgumentCaptor<UserEntity> usuario = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(usuario.capture());
        assertEquals("marco@example.com", usuario.getValue().getEmail());
        assertEquals("marco@example.com", usuario.getValue().getIdentifier());
        assertEquals("hash-bcrypt", usuario.getValue().getPasswordHash());
        assertEquals(1L, usuario.getValue().getCliente().getId());
        assertTrue(usuario.getValue().isEnabled());
        org.mockito.InOrder orden = org.mockito.Mockito.inOrder(cuentaService, userRepository);
        orden.verify(cuentaService).crearPara(any(ClienteEntity.class));
        orden.verify(userRepository).saveAndFlush(any(UserEntity.class));
    }

    @Test
    void noGuardaNadaSiLaCurpYaExiste() {
        when(clienteRepository.existsByCurp("MOMA900101HDFRRR01")).thenReturn(true);

        assertThrows(CurpDuplicadaException.class,
                () -> service.registrar(request()));

        verify(clienteRepository, never()).saveAndFlush(any(ClienteEntity.class));
        verify(domicilioRepository, never()).saveAndFlush(any(DomicilioEntity.class));
        verify(cuentaService, never()).crearPara(any(ClienteEntity.class));
    }

    private RegistrarClienteRequest request() {
        return new RegistrarClienteRequest(
                "Marco", null, "Morales", "Martinez", LocalDate.of(1990, 1, 1),
                "MOMA900101HDFRRR01", "MOMA900101AB1", Sexo.MASCULINO, "Mexicana",
                EstadoCivil.SOLTERO, null, "marco@example.com", "5512345678", null,
                "Ingeniero", "Empresa", new BigDecimal("1000.00"),
                new ActualizarDomicilioRequest(
                        "Reforma", "100", null, "Centro", "Cuauhtemoc",
                        "Ciudad de Mexico", "06000", "Mexico"
                ), "Segura123!"
        );
    }

    @Test
    void rechazaRfcDuplicado() {
        when(clienteRepository.existsByRfc("MOMA900101AB1")).thenReturn(true);
        assertThrows(RfcDuplicadoException.class,
                () -> service.registrar(request()));
        verify(clienteRepository, never()).saveAndFlush(any(ClienteEntity.class));
    }

    @Test
    void rechazaCorreoDuplicado() {
        when(clienteRepository.existsByCorreo("marco@example.com")).thenReturn(true);
        assertThrows(ClienteYaRegistradoException.class,
                () -> service.registrar(request()));
        verify(clienteRepository, never()).saveAndFlush(any(ClienteEntity.class));
    }

    @Test
    void noRegistraClienteSiYaExisteUsuarioConElCorreo() {
        when(userRepository.existsByEmailOrIdentifier("marco@example.com")).thenReturn(true);
        assertThrows(ClienteYaRegistradoException.class, () -> service.registrar(request()));
        verify(clienteRepository, never()).saveAndFlush(any(ClienteEntity.class));
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        verify(userRepository).existsByEmailOrIdentifier("marco@example.com");
        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).existsByIdentifier(any());
    }

    @Test
    void traduceFalloAlGuardarUsuarioSinDevolverExito() {
        when(passwordEncoder.encode("Segura123!")).thenReturn("hash-bcrypt");
        when(userRepository.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataAccessResourceFailureException("fallo"));
        assertThrows(ClientePersistenceException.class, () -> service.registrar(request()));
    }
}
