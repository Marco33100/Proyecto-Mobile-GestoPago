package com.proyecto.servicios.service;

import com.proyecto.servicios.entity.sf.SegundoFactorEntity;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.exception.auth.CodigoSegundoFactorInvalidoException;
import com.proyecto.servicios.exception.auth.LoginRateLimitException;
import com.proyecto.servicios.exception.auth.SegundoFactorNoDisponibleException;
import com.proyecto.servicios.exception.auth.UsuarioInactivoException;
import com.proyecto.servicios.model.auth.CachedUser;
import com.proyecto.servicios.model.auth.LoginDesafioResponse;
import com.proyecto.servicios.model.auth.LoginRequest;
import com.proyecto.servicios.model.auth.LoginResponse;
import com.proyecto.servicios.model.auth.VerificarLoginRequest;
import com.proyecto.servicios.repositorys.sf.SegundoFactorRepository;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import com.proyecto.servicios.service.Impl.LoginSegundoFactorServiceImpl;
import com.proyecto.servicios.service.Impl.TotpAuthorization;
import com.proyecto.servicios.service.Impl.TotpCodes;
import com.proyecto.servicios.service.Impl.TotpSecrets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LoginSegundoFactorServiceImplTest {
    private static final String JWT_SECRET = "test-secret-with-more-than-32-bytes-123456789";

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-15T12:00:00Z"));
    private final Map<UUID, SegundoFactorEntity> store = new HashMap<>();
    private AuthService auth;
    private UserRepository users;
    private TotpCodes codes;
    private JwtTokenService jwt;
    private UserEntity user;
    private LoginSegundoFactorServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());
        codes = new TotpCodes(clock);
        TotpSecrets secrets = new TotpSecrets(Base64.getEncoder().encodeToString(new byte[32]), "");
        SegundoFactorRepository factors = mock(SegundoFactorRepository.class);
        when(factors.findById(any())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.<UUID>getArgument(0))));
        when(factors.saveAndFlush(any())).thenAnswer(inv -> {
            SegundoFactorEntity factor = inv.getArgument(0);
            store.put(factor.getUsuarioId(), factor);
            return factor;
        });
        jwt = new JwtTokenService(JWT_SECRET, "test-api", Duration.ofHours(8));
        user = new UserEntity(UUID.randomUUID(), "ana@example.com", "ana", "hash", "Ana Lopez", true, Instant.now());
        auth = mock(AuthService.class);
        when(auth.verificarCredenciales(any())).thenReturn(user);
        when(auth.emitirSesion(user)).thenReturn(new LoginResponse("jwt-sesion", "Bearer",
                Instant.now().plusSeconds(3600), null));
        users = mock(UserRepository.class);
        when(users.bloquearUsuarioDeSesion(user.getId())).thenReturn(Optional.of(user));
        service = new LoginSegundoFactorServiceImpl(auth, users, factors, codes, secrets,
                new TotpAuthorization(codes, secrets, factors), jwt, mock(PlatformTransactionManager.class));
    }

    private LoginDesafioResponse iniciar() {
        return service.iniciar(new LoginRequest("ana@example.com", "Segura123!"));
    }

    private LoginResponse verificar(String desafio, String codigo) {
        return service.verificar(new VerificarLoginRequest(desafio, codigo, null));
    }

    private void avanzar() {
        now.updateAndGet(instant -> instant.plusSeconds(30));
    }

    /** Vincula el autenticador como lo haria el usuario y devuelve su clave. */
    private String vincular() {
        LoginDesafioResponse desafio = iniciar();
        String secreto = desafio.configuracion().secreto();
        verificar(desafio.desafio(), codes.code(secreto, codes.currentStep()));
        avanzar();
        return secreto;
    }

    @Test
    void sinAutenticadorEntregaConfiguracionYLoVinculaAlVerificar() {
        LoginDesafioResponse desafio = iniciar();

        assertThat(desafio.segundoFactorConfigurado()).isFalse();
        assertThat(desafio.configuracion().uri()).startsWith("otpauth://totp/Gestopago%3Aana%40example.com")
                .contains("issuer=Gestopago");
        verify(auth, never()).emitirSesion(any());

        String codigo = codes.code(desafio.configuracion().secreto(), codes.currentStep());
        LoginResponse sesion = verificar(desafio.desafio(), codigo);

        assertThat(sesion.accessToken()).isEqualTo("jwt-sesion");
        assertThat(sesion.codigosRecuperacion()).hasSize(10);
        assertThat(store.get(user.getId()).getSecretoCifrado()).isNotNull();
        assertThat(store.get(user.getId()).getPendienteCifrado()).isNull();
    }

    @Test
    void conAutenticadorNoExponeLaClaveYEmiteSesionSoloConCodigoCorrecto() {
        String secreto = vincular();
        clearInvocations(auth);

        LoginDesafioResponse desafio = iniciar();
        assertThat(desafio.segundoFactorConfigurado()).isTrue();
        assertThat(desafio.configuracion()).isNull();

        assertThatThrownBy(() -> verificar(desafio.desafio(), "000000"))
                .isInstanceOf(CodigoSegundoFactorInvalidoException.class);
        assertThat(store.get(user.getId()).getIntentos()).isEqualTo(1);
        verify(auth, never()).emitirSesion(any());

        LoginResponse sesion = verificar(desafio.desafio(), codes.code(secreto, codes.currentStep()));
        assertThat(sesion.accessToken()).isEqualTo("jwt-sesion");
        assertThat(sesion.codigosRecuperacion()).isNull();
        assertThat(store.get(user.getId()).getIntentos()).isZero();
    }

    @Test
    void elMismoCodigoNoSirveDosVeces() {
        String secreto = vincular();
        String codigo = codes.code(secreto, codes.currentStep());
        verificar(iniciar().desafio(), codigo);

        String otroDesafio = iniciar().desafio();
        assertThatThrownBy(() -> verificar(otroDesafio, codigo))
                .isInstanceOf(CodigoSegundoFactorInvalidoException.class);
    }

    @Test
    void repetirElCodigoRecienUsadoNoCuentaComoIntentoNiBloquea() {
        String secreto = vincular();
        String codigo = codes.code(secreto, codes.currentStep());
        verificar(iniciar().desafio(), codigo);

        String desafio = iniciar().desafio();
        for (int i = 0; i < 6; i++) {
            assertThatThrownBy(() -> verificar(desafio, codigo))
                    .isInstanceOf(CodigoSegundoFactorInvalidoException.class);
        }
        assertThat(store.get(user.getId()).getIntentos()).isZero();
        assertThat(store.get(user.getId()).getBloqueadoHasta()).isNull();

        // Un codigo realmente incorrecto sigue contando.
        assertThatThrownBy(() -> verificar(desafio, "000000"))
                .isInstanceOf(CodigoSegundoFactorInvalidoException.class);
        assertThat(store.get(user.getId()).getIntentos()).isEqualTo(1);

        avanzar();
        assertThat(verificar(desafio, codes.code(secreto, codes.currentStep())).accessToken()).isEqualTo("jwt-sesion");
    }

    @Test
    void unCodigoViejoFueraDeLaVentanaSiCuentaComoIntento() {
        String secreto = vincular();
        String codigo = codes.code(secreto, codes.currentStep());
        verificar(iniciar().desafio(), codigo);
        avanzar();
        avanzar();

        assertThatThrownBy(() -> verificar(iniciar().desafio(), codigo))
                .isInstanceOf(CodigoSegundoFactorInvalidoException.class);
        assertThat(store.get(user.getId()).getIntentos()).isEqualTo(1);
    }

    @Test
    void cincoCodigosIncorrectosBloqueanElInicioDeSesion() {
        String secreto = vincular();
        String desafio = iniciar().desafio();
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> verificar(desafio, "000000"))
                    .isInstanceOf(CodigoSegundoFactorInvalidoException.class);
        }
        String correcto = codes.code(secreto, codes.currentStep());
        assertThatThrownBy(() -> verificar(desafio, correcto)).isInstanceOf(LoginRateLimitException.class);
        verify(auth, times(1)).emitirSesion(any()); // solo la vinculacion inicial
    }

    @Test
    void codigoDeRecuperacionPermiteEntrarUnaSolaVez() {
        LoginDesafioResponse inicial = iniciar();
        String secreto = inicial.configuracion().secreto();
        String recuperacion = verificar(inicial.desafio(), codes.code(secreto, codes.currentStep()))
                .codigosRecuperacion().get(0);

        String desafio = iniciar().desafio();
        LoginResponse sesion = service.verificar(new VerificarLoginRequest(desafio, null, recuperacion.toLowerCase()));
        assertThat(sesion.accessToken()).isEqualTo("jwt-sesion");

        String otro = iniciar().desafio();
        assertThatThrownBy(() -> service.verificar(new VerificarLoginRequest(otro, null, recuperacion)))
                .isInstanceOf(CodigoSegundoFactorInvalidoException.class);
    }

    @Test
    void laVinculacionSoloSirveConElDesafioQueLaCreo() {
        LoginDesafioResponse primero = iniciar();
        LoginDesafioResponse segundo = iniciar(); // reemplaza la clave pendiente
        String codigoSegundo = codes.code(segundo.configuracion().secreto(), codes.currentStep());

        assertThatThrownBy(() -> verificar(primero.desafio(), codigoSegundo))
                .isInstanceOf(CodigoSegundoFactorInvalidoException.class);
        assertThat(verificar(segundo.desafio(), codigoSegundo).codigosRecuperacion()).hasSize(10);
    }

    @Test
    void desafioVencidoOFalsoNoSirve() {
        String vencido = jwt.generateLoginChallenge(user.getId(), UUID.randomUUID(), Duration.ofSeconds(-1)).value();
        String sesion = jwt.generate(new CachedUser(user.getId(), user.getEmail(), user.getIdentifier(),
                "hash", user.getFullName(), true), UUID.randomUUID()).value();
        for (String desafio : new String[]{vencido, sesion, "no-es-un-jwt"}) {
            assertThatThrownBy(() -> verificar(desafio, "123456"))
                    .isInstanceOf(CodigoSegundoFactorInvalidoException.class);
        }
        verify(auth, never()).emitirSesion(any());
    }

    @Test
    void unDesafioNuncaFuncionaComoSesion() {
        String desafio = iniciar().desafio();
        assertThatThrownBy(() -> jwt.validate(desafio)).isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void exigeExactamenteUnMetodoDeVerificacion() {
        String desafio = iniciar().desafio();
        assertThatThrownBy(() -> service.verificar(new VerificarLoginRequest(desafio, null, null)))
                .isInstanceOf(CodigoSegundoFactorInvalidoException.class);
        assertThatThrownBy(() -> service.verificar(new VerificarLoginRequest(desafio, "123456",
                "AAAAAAAA-BBBBBBBB-CCCCCCCC-DDDDDDDD")))
                .isInstanceOf(CodigoSegundoFactorInvalidoException.class);
    }

    @Test
    void usuarioDesactivadoEntrePasosNoObtieneSesion() {
        LoginDesafioResponse desafio = iniciar();
        user.desactivar();
        String codigo = codes.code(desafio.configuracion().secreto(), codes.currentStep());
        assertThatThrownBy(() -> verificar(desafio.desafio(), codigo)).isInstanceOf(UsuarioInactivoException.class);
        verify(auth, never()).emitirSesion(any());
    }

    @Test
    void sinClaveDeCifradoNoSeVerificaNadaYNadieEntra() {
        var sinClave = new LoginSegundoFactorServiceImpl(auth, users, mock(SegundoFactorRepository.class), codes,
                new TotpSecrets("", ""), mock(TotpAuthorization.class), jwt, mock(PlatformTransactionManager.class));
        assertThatThrownBy(() -> sinClave.iniciar(new LoginRequest("ana@example.com", "Segura123!")))
                .isInstanceOf(SegundoFactorNoDisponibleException.class);
        verify(auth, never()).verificarCredenciales(any());
    }
}
