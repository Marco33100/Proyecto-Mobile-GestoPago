package com.proyecto.servicios.service;

import com.proyecto.servicios.config.ExecutiveBootstrapProperties;
import com.proyecto.servicios.entity.sf.RolUsuario;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.service.Impl.ExecutiveBootstrapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutiveBootstrapServiceTest {
    private UserRepository usuarios;
    private ExecutiveBootstrapProperties properties;
    private BCryptPasswordEncoder encoder;
    private ExecutiveBootstrapService service;

    @BeforeEach void setUp() {
        usuarios = mock(UserRepository.class);
        properties = new ExecutiveBootstrapProperties();
        properties.setEmail("EJECUTIVO@example.com");
        properties.setPassword("Segura123!");
        encoder = new BCryptPasswordEncoder(4);
        service = new ExecutiveBootstrapService(usuarios, encoder, properties);
    }

    @Test void deshabilitadoNoConsultaNiCreaUsuarios() {
        service.crearSiSeSolicita();
        verifyNoInteractions(usuarios);
    }

    @Test void creaSoloEjecutivoConBcryptYSinClienteFicticio() {
        properties.setEnabled(true);
        service.crearSiSeSolicita();
        var captor = org.mockito.ArgumentCaptor.forClass(UserEntity.class);
        verify(usuarios).saveAndFlush(captor.capture());
        UserEntity usuario = captor.getValue();
        assertThat(usuario.getEmail()).isEqualTo("ejecutivo@example.com");
        assertThat(usuario.getIdentifier()).isEqualTo(usuario.getEmail());
        assertThat(usuario.getRol()).isEqualTo(RolUsuario.EJECUTIVO);
        assertThat(usuario.getCliente()).isNull();
        assertThat(usuario.isEnabled()).isTrue();
        assertThat(encoder.matches(properties.getPassword(), usuario.getPasswordHash())).isTrue();
        assertThat(usuario.getPasswordHash()).isNotEqualTo(properties.getPassword());
    }

    @Test void reinicioNoCambiaContrasenaNiCreaOtroEjecutivo() {
        properties.setEnabled(true);
        when(usuarios.existsByRolAndEnabledTrue(RolUsuario.EJECUTIVO)).thenReturn(true);
        service.crearSiSeSolicita();
        verify(usuarios).existsByRolAndEnabledTrue(RolUsuario.EJECUTIVO);
        verifyNoMoreInteractions(usuarios);
    }

    @Test void correoExistenteNuncaElevaRolDeCliente() {
        properties.setEnabled(true);
        var cliente = new UserEntity(UUID.randomUUID(), "ejecutivo@example.com", "existente",
                "hash", "Cliente", true, Instant.now());
        when(usuarios.existsByEmailOrIdentifier("ejecutivo@example.com")).thenReturn(true);
        assertThatThrownBy(service::crearSiSeSolicita).isInstanceOf(IllegalStateException.class);
        assertThat(cliente.getRol()).isEqualTo(RolUsuario.CLIENTE);
        verify(usuarios, never()).saveAndFlush(any());
    }

    @Test void configuracionInvalidaNoAccedeABaseDeDatos() {
        properties.setEnabled(true);
        properties.setPassword("debil");
        assertThatThrownBy(service::crearSiSeSolicita).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(usuarios);
        properties.setEmail(null);
        assertThat(properties.isValidConfiguration()).isFalse();
    }

    @Test void fallaDePersistenciaNoExponeDetalleInterno() {
        properties.setEnabled(true);
        when(usuarios.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("password=secreto"));
        assertThatThrownBy(service::crearSiSeSolicita).isInstanceOf(IllegalStateException.class)
                .hasMessage("No fue posible crear el ejecutivo inicial").hasNoCause();
    }
}
