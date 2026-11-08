package com.proyecto.servicios.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.auth.*;
import com.proyecto.servicios.model.auth.CambiarContrasenaRequest;
import com.proyecto.servicios.model.auth.ModificarContrasenaRequest;
import com.proyecto.servicios.model.auth.UsuarioConsultaRequest;
import com.proyecto.servicios.model.auth.UsuarioBusquedaRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import com.proyecto.servicios.model.cliente.UsuarioClienteModificadoEvent;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.repositorys.sf.UserSessionRepository;
import com.proyecto.servicios.service.Impl.UsuarioServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceImplTest {
    @Mock private UserRepository usuarios;
    @Mock private UserSessionRepository sesiones;
    @Mock private ApplicationEventPublisher events;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private UsuarioServiceImpl service;
    private UserEntity usuario;

    @BeforeEach
    void setUp() {
        service = new UsuarioServiceImpl(usuarios, sesiones, encoder, events);
        usuario = new UserEntity(UUID.randomUUID(), "uno@example.com", "uno", encoder.encode("Actual123!"),
                "Marco Martinez", true, Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void consultaSoloPerfilSinCredenciales() throws Exception {
        when(usuarios.consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", usuario.getId()))
                .thenReturn(java.util.Optional.of(usuario));
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(
                service.consultar(new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail()), usuario.getId()));
        assertThat(json).contains("uno@example.com").doesNotContain("password", "hash", "Actual123!");
        verify(usuarios).consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", usuario.getId());
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void consultaNormalizaMayusculasSinHacerBusquedasParciales() {
        when(usuarios.consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", usuario.getId()))
                .thenReturn(java.util.Optional.of(usuario));

        assertThat(service.consultar(new UsuarioConsultaRequest(" MARCO MARTINEZ ", "UNO@EXAMPLE.COM"),
                usuario.getId()).id()).isEqualTo(usuario.getId());

        verify(usuarios).consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", usuario.getId());
        verifyNoMoreInteractions(usuarios);
    }

    @Test
    void consultaSinIdentidadAutenticadaNoLlegaAlRepositorio() {
        var solicitud1 = new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail());
        UUID idActor1 = null;
        assertThatThrownBy(() -> service.consultar(solicitud1, idActor1))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(usuarios, sesiones, events);
    }

    @Test
    void noDevuelveUnUsuarioAjenoAunqueElRepositorioLoEntreguePorError() {
        UUID actor = UUID.randomUUID();
        when(usuarios.consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", actor))
                .thenReturn(java.util.Optional.of(usuario));

        var solicitud2 = new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail());
        var idActor2 = actor;
        assertThatThrownBy(() -> service.consultar(solicitud2, idActor2))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void falloEnConsultaCompuestaNoSeDisfrazaComoUsuarioInexistente() {
        when(usuarios.consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", usuario.getId()))
                .thenThrow(new DataAccessResourceFailureException("sql privado"));

        var solicitud3 = new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail());
        var idActor3 = usuario.getId();
        assertThatThrownBy(() -> service.consultar(solicitud3, idActor3))
                .isInstanceOf(AuthPersistenceException.class);
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void consultaCompuestaNoPermiteAccesoAlUsuarioInactivo() {
        usuario.desactivar();
        when(usuarios.consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", usuario.getId()))
                .thenReturn(java.util.Optional.of(usuario));

        var solicitud4 = new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail());
        var idActor4 = usuario.getId();
        assertThatThrownBy(() -> service.consultar(solicitud4, idActor4))
                .isInstanceOf(UsuarioInactivoException.class);
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void busquedaPaginadaDevuelveDtoSinCredenciales() throws Exception {
        when(usuarios.findAll(org.mockito.ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(usuario)));

        var pagina = service.buscar(new UsuarioBusquedaRequest());
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(pagina);

        assertThat(pagina.contenido()).hasSize(1);
        assertThat(pagina.contenido().get(0).nombre()).isEqualTo(usuario.getFullName());
        assertThat(json).doesNotContain("password", "hash", "Actual123!");
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void busquedaSinCoincidenciasNoVuelveAConsultarTodosLosUsuarios() {
        var request = new UsuarioBusquedaRequest();
        request.setNombre("inexistente");
        when(usuarios.findAll(org.mockito.ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThat(service.buscar(request).contenido()).isEmpty();

        verify(usuarios).findAll(org.mockito.ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class));
        verifyNoMoreInteractions(usuarios);
    }

    @Test
    void limitesDePaginacionSeAplicanTambienEnElServicio() {
        var request = new UsuarioBusquedaRequest();
        request.setTamanio(101);
        assertThatThrownBy(() -> service.buscar(request))
                .isInstanceOf(com.proyecto.servicios.exception.cliente.ClienteValidationException.class);
        verifyNoInteractions(usuarios, sesiones, events);
    }

    @Test
    void falloDeBusquedaNoSeConvierteEnListaVacia() {
        when(usuarios.findAll(org.mockito.ArgumentMatchers.<Specification<UserEntity>>any(), any(Pageable.class)))
                .thenThrow(new DataAccessResourceFailureException("sql privado"));

        var solicitud = new UsuarioBusquedaRequest();
        assertThatThrownBy(() -> service.buscar(solicitud)).isInstanceOf(AuthPersistenceException.class);
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void noPuedeConsultarNiCambiarOtroUsuario() {
        UUID actor = UUID.randomUUID();
        var solicitud5 = new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail());
        var idActor5 = actor;
        assertThatThrownBy(() -> service.consultar(solicitud5, idActor5))
                .isInstanceOf(UsuarioNoEncontradoException.class);
        var solicitud6 = cambio("Actual123!", "Nueva456!");
        var idActor6 = actor;
        assertThatThrownBy(() -> service.cambiarContrasena(solicitud6, idActor6)).isInstanceOf(UsuarioNoEncontradoException.class);
        verify(usuarios).consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", actor);
        verify(usuarios).consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", actor);
        verifyNoMoreInteractions(usuarios);
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void cambioContrasenaUsaBcryptAuditoriaYRevocaSesion() {
        when(usuarios.consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", usuario.getId())).thenReturn(java.util.Optional.of(usuario));
        service.cambiarContrasena(cambio("Actual123!", "Nueva456!"), usuario.getId());
        assertThat(encoder.matches("Nueva456!", usuario.getPasswordHash())).isTrue();
        assertThat(encoder.matches("Actual123!", usuario.getPasswordHash())).isFalse();
        assertThat(usuario.getUpdatedAt()).isAfter(usuario.getCreatedAt());
        verify(usuarios).saveAndFlush(usuario);
        verify(sesiones).deleteById(usuario.getId());
        verify(events).publishEvent(new UsuarioClienteModificadoEvent(
                usuario.getId(), usuario.getEmail(), usuario.getEmail()));
    }

    @Test
    void contrasenaActualIncorrectaNoModificaNada() {
        when(usuarios.consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", usuario.getId())).thenReturn(java.util.Optional.of(usuario));
        var solicitud7 = cambio("Incorrecta1!", "Nueva456!");
        var idActor7 = usuario.getId();
        assertThatThrownBy(() -> service.cambiarContrasena(solicitud7, idActor7))
                .isInstanceOf(ContrasenaInvalidaException.class);
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void nuevaContrasenaSinEspecialSeRechaza() {
        var solicitud8 = cambio("Actual123!", "Nueva456");
        var idActor8 = usuario.getId();
        assertThatThrownBy(() -> service.cambiarContrasena(solicitud8, idActor8))
                .isInstanceOf(ContrasenaInvalidaException.class);
        verifyNoInteractions(usuarios, sesiones, events);
    }

    @Test
    void usuarioNoEncontradoTieneExcepcionEspecifica() {
        var solicitud9 = new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail());
        var idActor9 = usuario.getId();
        assertThatThrownBy(() -> service.consultar(solicitud9, idActor9))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }

    @Test
    void usuarioInactivoNoPuedeCambiarContrasena() {
        usuario.desactivar();
        when(usuarios.consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", usuario.getId())).thenReturn(java.util.Optional.of(usuario));
        var solicitud10 = cambio("Actual123!", "Nueva456!");
        var idActor10 = usuario.getId();
        assertThatThrownBy(() -> service.cambiarContrasena(solicitud10, idActor10))
                .isInstanceOf(UsuarioInactivoException.class);
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void falloPostgresNoSeOcultaComoExito() {
        when(usuarios.consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", usuario.getId()))
                .thenThrow(new DataAccessResourceFailureException("fallo"));
        var solicitud11 = cambio("Actual123!", "Nueva456!");
        var idActor11 = usuario.getId();
        assertThatThrownBy(() -> service.cambiarContrasena(solicitud11, idActor11))
                .isInstanceOf(AuthPersistenceException.class);
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void cambioSinIdentidadAutenticadaNoConsultaNiEscribe() {
        var solicitud12 = cambio("Actual123!", "Nueva456!");
        UUID idActor12 = null;
        assertThatThrownBy(() -> service.cambiarContrasena(solicitud12, idActor12))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(usuarios, sesiones, events);
    }

    @Test
    void cambioNormalizaElSelectorYUsaLaConsultaConBloqueo() {
        when(usuarios.consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", usuario.getId()))
                .thenReturn(java.util.Optional.of(usuario));
        service.cambiarContrasena(new ModificarContrasenaRequest(" MARCO MARTINEZ ", "UNO@EXAMPLE.COM",
                new CambiarContrasenaRequest("Actual123!", "Nueva456!")), usuario.getId());
        verify(usuarios).consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", usuario.getId());
        verify(usuarios).saveAndFlush(usuario);
        verifyNoMoreInteractions(usuarios);
        verify(sesiones).deleteById(usuario.getId());
    }

    @Test
    void cambioRechazaUsuarioAjenoAunqueElRepositorioLoEntregue() {
        UUID actor = UUID.randomUUID();
        when(usuarios.consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", actor))
                .thenReturn(java.util.Optional.of(usuario));
        var solicitud13 = cambio("Actual123!", "Nueva456!");
        var idActor13 = actor;
        assertThatThrownBy(() -> service.cambiarContrasena(solicitud13, idActor13))
                .isInstanceOf(AccessDeniedException.class);
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void contrasenaNuevaIgualALaActualNoEscribeNiRevocaSesion() {
        when(usuarios.consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", usuario.getId()))
                .thenReturn(java.util.Optional.of(usuario));
        var solicitud14 = cambio("Actual123!", "Actual123!");
        var idActor14 = usuario.getId();
        assertThatThrownBy(() -> service.cambiarContrasena(solicitud14, idActor14))
                .isInstanceOf(ContrasenaInvalidaException.class);
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(sesiones, events);
    }
    private ModificarContrasenaRequest cambio(String actual, String nueva) {
        return new ModificarContrasenaRequest(usuario.getFullName(), usuario.getEmail(),
                new CambiarContrasenaRequest(actual, nueva));
    }
}
