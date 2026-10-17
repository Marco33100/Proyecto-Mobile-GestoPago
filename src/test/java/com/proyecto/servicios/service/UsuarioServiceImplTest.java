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
        assertThatThrownBy(() -> service.consultar(
                new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail()), null))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(usuarios, sesiones, events);
    }

    @Test
    void noDevuelveUnUsuarioAjenoAunqueElRepositorioLoEntreguePorError() {
        UUID actor = UUID.randomUUID();
        when(usuarios.consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", actor))
                .thenReturn(java.util.Optional.of(usuario));

        assertThatThrownBy(() -> service.consultar(
                new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail()), actor))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void falloEnConsultaCompuestaNoSeDisfrazaComoUsuarioInexistente() {
        when(usuarios.consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", usuario.getId()))
                .thenThrow(new DataAccessResourceFailureException("sql privado"));

        assertThatThrownBy(() -> service.consultar(
                new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail()), usuario.getId()))
                .isInstanceOf(AuthPersistenceException.class);
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void consultaCompuestaNoPermiteAccesoAlUsuarioInactivo() {
        usuario.desactivar();
        when(usuarios.consultarPropioPorNombreYCorreo("marco martinez", "uno@example.com", usuario.getId()))
                .thenReturn(java.util.Optional.of(usuario));

        assertThatThrownBy(() -> service.consultar(
                new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail()), usuario.getId()))
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

        assertThatThrownBy(() -> service.buscar(new UsuarioBusquedaRequest())).isInstanceOf(AuthPersistenceException.class);
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void noPuedeConsultarNiCambiarOtroUsuario() {
        UUID actor = UUID.randomUUID();
        assertThatThrownBy(() -> service.consultar(
                new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail()), actor))
                .isInstanceOf(UsuarioNoEncontradoException.class);
        assertThatThrownBy(() -> service.cambiarContrasena(cambio("Actual123!", "Nueva456!"), actor)).isInstanceOf(UsuarioNoEncontradoException.class);
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
        assertThatThrownBy(() -> service.cambiarContrasena(cambio("Incorrecta1!", "Nueva456!"), usuario.getId()))
                .isInstanceOf(ContrasenaInvalidaException.class);
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void nuevaContrasenaSinEspecialSeRechaza() {
        assertThatThrownBy(() -> service.cambiarContrasena(cambio("Actual123!", "Nueva456"), usuario.getId()))
                .isInstanceOf(ContrasenaInvalidaException.class);
        verifyNoInteractions(usuarios, sesiones, events);
    }

    @Test
    void usuarioNoEncontradoTieneExcepcionEspecifica() {
        assertThatThrownBy(() -> service.consultar(new UsuarioConsultaRequest(usuario.getFullName(), usuario.getEmail()), usuario.getId()))
                .isInstanceOf(UsuarioNoEncontradoException.class);
    }

    @Test
    void usuarioInactivoNoPuedeCambiarContrasena() {
        usuario.desactivar();
        when(usuarios.consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", usuario.getId())).thenReturn(java.util.Optional.of(usuario));
        assertThatThrownBy(() -> service.cambiarContrasena(cambio("Actual123!", "Nueva456!"), usuario.getId()))
                .isInstanceOf(UsuarioInactivoException.class);
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void falloPostgresNoSeOcultaComoExito() {
        when(usuarios.consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", usuario.getId()))
                .thenThrow(new DataAccessResourceFailureException("fallo"));
        assertThatThrownBy(() -> service.cambiarContrasena(cambio("Actual123!", "Nueva456!"), usuario.getId()))
                .isInstanceOf(AuthPersistenceException.class);
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void cambioSinIdentidadAutenticadaNoConsultaNiEscribe() {
        assertThatThrownBy(() -> service.cambiarContrasena(cambio("Actual123!", "Nueva456!"), null))
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
        assertThatThrownBy(() -> service.cambiarContrasena(cambio("Actual123!", "Nueva456!"), actor))
                .isInstanceOf(AccessDeniedException.class);
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(sesiones, events);
    }

    @Test
    void contrasenaNuevaIgualALaActualNoEscribeNiRevocaSesion() {
        when(usuarios.consultarPropioPorNombreYCorreoParaActualizar("marco martinez", "uno@example.com", usuario.getId()))
                .thenReturn(java.util.Optional.of(usuario));
        assertThatThrownBy(() -> service.cambiarContrasena(cambio("Actual123!", "Actual123!"), usuario.getId()))
                .isInstanceOf(ContrasenaInvalidaException.class);
        verify(usuarios, never()).saveAndFlush(any());
        verifyNoInteractions(sesiones, events);
    }
    private ModificarContrasenaRequest cambio(String actual, String nueva) {
        return new ModificarContrasenaRequest(usuario.getFullName(), usuario.getEmail(),
                new CambiarContrasenaRequest(actual, nueva));
    }
}
