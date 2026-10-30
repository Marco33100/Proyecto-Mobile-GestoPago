package com.proyecto.servicios.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.*;
import com.proyecto.servicios.entity.sf.*;
import com.proyecto.servicios.entity.gestopago.ProductEntity;
import com.proyecto.servicios.exception.PagoException;
import com.proyecto.servicios.model.auth.AuthenticatedUser;
import com.proyecto.servicios.model.pago.PagoDtos.*;
import com.proyecto.servicios.repositorys.sf.*;
import com.proyecto.servicios.repositorys.gestopago.ProductRepository;
import com.proyecto.servicios.service.*;
import com.proyecto.servicios.service.Impl.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.math.BigDecimal;
import java.time.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PagoFlowPersistenceTest {
    private SessionFactory factory;
    private TransactionTemplate tx;
    private JpaTransactionManager manager;
    private PagoRepository pagos;
    private PagoEvidenciaRepository evidencias;
    private CuentaRepository cuentas;
    private UserRepository usuarios;
    private SegundoFactorRepository claves;
    private ProductRepository productos;
    private ConciliacionPagoRepository propuestas;
    private PagoLedger ledger;
    private PagoCrypto crypto;
    private PagoProperties properties;
    private UUID actor;
    private UUID cuentaId;
    private final String secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private TotpCodes totpCodes;
    private TotpSecrets totpSecrets;
    private TotpAuthorization totp;
    private Clock codeClock;
    private Instant codeTime;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    @BeforeEach void prepararBase() throws Exception {
        var configuracion = new Configuration().addAnnotatedClass(ClienteEntity.class).addAnnotatedClass(CuentaEntity.class)
                .addAnnotatedClass(UserEntity.class).addAnnotatedClass(ProductEntity.class)
                .addAnnotatedClass(PagoEntity.class).addAnnotatedClass(SegundoFactorEntity.class).addAnnotatedClass(PagoEvidenciaEntity.class)
                .addAnnotatedClass(ConciliacionPagoEntity.class);
        configuracionPersistencia().forEach(configuracion::setProperty);
        factory = configuracion.buildSessionFactory();
        manager = new JpaTransactionManager(factory); tx = new TransactionTemplate(manager);
        var repositories = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory));
        pagos = repositories.getRepository(PagoRepository.class); cuentas = repositories.getRepository(CuentaRepository.class);
        usuarios = repositories.getRepository(UserRepository.class); claves = repositories.getRepository(SegundoFactorRepository.class);
        productos = repositories.getRepository(ProductRepository.class); evidencias = repositories.getRepository(PagoEvidenciaRepository.class);
        propuestas = repositories.getRepository(ConciliacionPagoRepository.class);
        var clientes = repositories.getRepository(ClienteRepository.class);
        crypto = new PagoCrypto(Base64.getEncoder().encodeToString(new byte[32]), Base64.getEncoder().encodeToString(new byte[16]));
        properties = new PagoProperties(); properties.setEnabled(true);
        // Synthetic fixture limits, not provider production values.
        properties.getLimitesPorProducto().put(1, new PagoProperties.LimiteMonto(2, new BigDecimal("0.01"), new BigDecimal("100")));
        codeTime = Instant.now(); codeClock = mock(Clock.class);
        when(codeClock.instant()).thenAnswer(invocation -> codeTime);
        totpCodes = new TotpCodes(codeClock);
        totpSecrets = new TotpSecrets(Base64.getEncoder().encodeToString(new byte[32]), "");
        totp = new TotpAuthorization(totpCodes, totpSecrets, claves);
        ledger = new PagoLedger(pagos, evidencias, cuentas, usuarios, claves, productos, properties, crypto, json, totp, manager);
        actor = UUID.randomUUID(); cuentaId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            var cliente = new ClienteEntity("Juan", null, "Lopez", "Perez", LocalDate.of(1990, 1, 1),
                    "LOPE900101HDFRRR01", "LOPE900101AB1", Sexo.MASCULINO, "Mexicana", EstadoCivil.SOLTERO,
                    null, "juan@example.com", "5512345678", null, "Empleado", "Empresa", new BigDecimal("1000"), Instant.now());
            clientes.saveAndFlush(cliente);
            usuarios.saveAndFlush(new UserEntity(actor, cliente.getCorreo(), "hash", cliente.getNombreCompleto(), cliente, Instant.now()));
            cuentas.saveAndFlush(new CuentaEntity(cuentaId, cliente, "00000000000000000001", new BigDecimal("100.00"), Instant.now()));
            var clave = new SegundoFactorEntity(); clave.setUsuarioId(actor);
            clave.setSecretoCifrado(totpSecrets.encrypt(actor, secret)); clave.setHuella(UUID.randomUUID().toString());
            claves.saveAndFlush(clave);
            var producto = new ProductEntity(); producto.setProductId(1); producto.setServiceId(2); producto.setFrontType(2);
            producto.setProductName("Servicio de prueba"); producto.setCategoryServiceTypeId(5); producto.setPrice(new BigDecimal("6.00"));
            producto.setUpdatedAt(LocalDateTime.now()); productos.saveAndFlush(producto);
        });
    }
    protected Map<String,String> configuracionPersistencia() throws Exception {
        return Map.of("hibernate.connection.driver_class","org.h2.Driver", "hibernate.connection.url",
                "jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000", "hibernate.hbm2ddl.auto","create-drop");
    }
    protected void limpiarPersistencia() throws Exception { }
    @AfterEach void cerrar() throws Exception {
        SecurityContextHolder.clearContext();
        try { if (factory != null) factory.close(); } finally { limpiarPersistencia(); }
    }
    private PrepararRequest request(String monto) { return request(monto, "REF-123"); }
    private PrepararRequest request(String monto, String referencia) { return new PrepararRequest(UUID.randomUUID(), "00000000000000000001", 2, 1, 2, new BigDecimal(monto), null, referencia); }
    private AutorizarRequest codigoPara(PagoResponse pago) {
        codeTime = codeTime.plusSeconds(30);
        return new AutorizarRequest(pago.pagoId(), totpCodes.code(secret, totpCodes.currentStep()));
    }
    private void saldo(String contable, String retenido) {
        var cuenta = cuentas.findById(cuentaId).orElseThrow();
        assertThat(cuenta.getSaldo()).isEqualByComparingTo(contable);
        assertThat(cuenta.getSaldoRetenido()).isEqualByComparingTo(retenido);
        assertThat(cuenta.getSaldoDisponible()).isEqualByComparingTo(new BigDecimal(contable).subtract(new BigDecimal(retenido)));
    }
    private PagoGateway.Respuesta aplicado(String codigo, String monto) {
        return new PagoGateway.Respuesta(PagoGateway.Estado.APLICADO, codigo, "123", "456", monto == null ? null : new BigDecimal(monto),
                new BigDecimal("6.00"), null, null, "<RESPONSE><CODIGO>" + codigo + "</CODIGO></RESPONSE>");
    }
    private PagoLedger.Confirmacion reclamarAhora(UUID id) {
        tx.executeWithoutResult(status -> {
            var p = pagos.findById(id).orElseThrow();
            p.setEnviadoEn(Instant.now().minusSeconds(80)); p.setProximaConfirmacion(Instant.now().minusSeconds(1));
            pagos.saveAndFlush(p);
        });
        return ledger.reclamarConfirmacion(id);
    }
    private PagoResponse confirmar(UUID id, PagoGateway.Respuesta resultado) {
        var p = pagos.findById(id).orElseThrow();
        if (p.getEstado() != PagoEntity.Estado.ENVIADO && p.getEstado() != PagoEntity.Estado.PENDIENTE)
            return ledger.resolverConfirmacion(new PagoLedger.Confirmacion(id, "", UUID.randomUUID()), resultado);
        return ledger.resolverConfirmacion(reclamarAhora(id), resultado);
    }
    @Test void retieneSinCobrarYConfirmaUnaSolaVezSinComisionAdicional() throws Exception {
        var pago = ledger.preparar(request("20.00"), actor).pago(); saldo("100", "0");
        assertThat(pago.comisionCliente()).isZero();
        assertThat(pago.referencia()).isEqualTo("REF-123");
        ledger.autorizar(codigoPara(pago), actor); saldo("100", "20");
        ledger.registrarResultadoEnvio(pago.pagoId(), PagoGateway.Respuesta.incierta()); saldo("100", "20");
        assertThat(confirmar(pago.pagoId(), aplicado("06", null)).estado()).isEqualTo("APLICADO");
        saldo("80", "0"); confirmar(pago.pagoId(), aplicado("06", null)); saldo("80", "0");
    }
    @Test void confirmacionNoAplicadaLiberaYFallaDeConfirmacionNoLibera() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(pago), actor);
        confirmar(pago.pagoId(), PagoGateway.Respuesta.noEnviada()); saldo("100", "20");
        confirmar(pago.pagoId(), new PagoGateway.Respuesta(PagoGateway.Estado.RECHAZADO, "70", null, null, null, null, null, null, ""));
        saldo("100", "0");
    }
    @Test void mismaSolicitudNoCambiaUpcNiCargaYNoAdmiteOtroImporte() {
        var request = request("20"); var a = ledger.preparar(request, actor); var b = ledger.preparar(request, actor);
        assertThat(a.pago().upc()).isEqualTo(b.pago().upc()); assertThat(a.json()).isEqualTo(b.json());
        var alterada = new PrepararRequest(request.solicitudId(), request.numeroCuenta(), 2, 1, 2, new BigDecimal("30"), null, "REF-123");
        assertThatThrownBy(() -> ledger.preparar(alterada, actor)).isInstanceOf(PagoException.class);
        saldo("100", "0");
    }
    @Test void confirmacionNegativaMientrasEnvioSigueSinCerrarNoLiberaDinero() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(pago), actor);
        var negativa = new PagoGateway.Respuesta(PagoGateway.Estado.RECHAZADO, "70", null, null, null, null, null, null, "");
        assertThat(confirmar(pago.pagoId(), negativa).estado()).isEqualTo("REVISION");
        saldo("100", "20");
    }
    @Test void unaTransaccionDelProveedorNoSeDescuentaEnDosOrdenesDistintas() throws Exception {
        var a = ledger.preparar(request("20"), actor).pago();
        var b = ledger.preparar(request("20", "OTRA-FACTURA"), actor).pago();
        ledger.autorizar(codigoPara(a), actor); ledger.autorizar(codigoPara(b), actor);
        ledger.registrarResultadoEnvio(a.pagoId(), aplicado("01", "20"));
        assertThat(ledger.registrarResultadoEnvio(b.pagoId(), aplicado("01", "20")).estado()).isEqualTo("REVISION");
        saldo("80", "20");
    }
    @Test void tarjetaDeRegaloFijaPrecioYTelefonoDelProveedor() throws Exception {
        tx.executeWithoutResult(status -> {
            var producto = productos.findById(1).orElseThrow();
            producto.setFrontType(1); producto.setCategoryServiceTypeId(10); producto.setPrice(new BigDecimal("25"));
            productos.saveAndFlush(producto);
        });
        var request = new PrepararRequest(UUID.randomUUID(), "00000000000000000001", 2, 1, 1, new BigDecimal("25"), null, null);
        var preparada = ledger.preparar(request, actor);
        assertThat(json.readTree(preparada.json()).path("telefono").asText()).isEqualTo("#1111111111");
        assertThat(json.readTree(preparada.json()).has("montoPago")).isFalse();
        ledger.autorizar(codigoPara(preparada.pago()), actor);
        ledger.registrarResultadoEnvio(preparada.pago().pagoId(), aplicado("01", "25"));
        saldo("75", "0");
        assertThat(ledger.consultar(preparada.pago().pagoId(), actor).aviso()).contains("PIN");
    }
    @Test void codigoInvalidoCuentaAjenaYMontoInsuficienteNoRetienen() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago();
        assertThatThrownBy(() -> ledger.autorizar(new AutorizarRequest(pago.pagoId(), "true"), actor)).isInstanceOf(PagoException.class);
        UUID ajeno = UUID.randomUUID();
        assertThatThrownBy(() -> ledger.consultar(pago.pagoId(), ajeno)).isInstanceOf(PagoException.class);
        var firma = codigoPara(pago);
        assertThatThrownBy(() -> ledger.autorizar(firma, ajeno)).isInstanceOf(PagoException.class);
        assertThatThrownBy(() -> ledger.preparar(request("101"), actor)).isInstanceOf(PagoException.class); saldo("100", "0");
    }
    @Test void cancelarPreparadoImpideAutorizacionTardiaPeroNoRevierteUnEnvio() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); var firma = codigoPara(pago);
        ledger.cancelar(pago.pagoId(), actor); assertThat(ledger.autorizar(firma, actor)).isNull(); saldo("100", "0");
        var otro = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(otro), actor);
        assertThat(ledger.cancelar(otro.pagoId(), actor).estado()).isEqualTo("ENVIADO"); saldo("100", "20");
    }
    @Test void confirmaDespuesDe62SegundosConLeaseYConservaRetencionAlVencer24Horas() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(pago), actor);
        assertThat(ledger.reclamarConfirmacion(pago.pagoId())).isNull();
        tx.executeWithoutResult(status -> { var p = pagos.findById(pago.pagoId()).orElseThrow(); p.setEnviadoEn(Instant.now().minusSeconds(80)); p.setProximaConfirmacion(Instant.now().minusSeconds(1)); pagos.saveAndFlush(p); });
        var claim = ledger.reclamarConfirmacion(pago.pagoId()); assertThat(claim).isNotNull();
        assertThat(json.readTree(claim.json()).path("upc").asText()).isEqualTo(pago.upc());
        assertThat(ledger.reclamarConfirmacion(pago.pagoId())).isNull();
        tx.executeWithoutResult(status -> { var p = pagos.findById(pago.pagoId()).orElseThrow(); p.setEnviadoEn(Instant.now().minusSeconds(86401)); p.setProximaConfirmacion(Instant.now().minusSeconds(1)); pagos.saveAndFlush(p); });
        assertThat(ledger.reclamarConfirmacion(pago.pagoId())).isNull();
        assertThat(ledger.consultar(pago.pagoId(), actor).estado()).isEqualTo("REVISION"); saldo("100", "20");
    }
    @Test void dosAutorizacionesConcurrentesSoloReservanUnaVez() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); var firma = codigoPara(pago);
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { start.await(); return ledger.autorizar(firma, actor); });
            var b = pool.submit(() -> { start.await(); return ledger.autorizar(firma, actor); }); start.countDown();
            var resultados = Arrays.asList(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertThat(resultados.stream().filter(Objects::nonNull).count()).isEqualTo(1); saldo("100", "20");
        } finally { pool.shutdownNow(); }
    }
    @Test void dosPagosConcurrentesNoGastanElMismoSaldoDisponible() throws Exception {
        var a = ledger.preparar(request("80"), actor).pago(); var b = ledger.preparar(request("80", "OTRA-FACTURA"), actor).pago();
        var firmaA = codigoPara(a); var firmaB = codigoPara(b); var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var primera = pool.submit(() -> { start.await(); try { ledger.autorizar(firmaA, actor); return true; } catch (PagoException e) { return false; } });
            var segunda = pool.submit(() -> { start.await(); try { ledger.autorizar(firmaB, actor); return true; } catch (PagoException e) { return false; } }); start.countDown();
            assertThat(List.of(primera.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false); saldo("100", "80");
        } finally { pool.shutdownNow(); }
    }
    @Test void montoContradictorioSeEscalaSinDebitoNiLiberacion() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(pago), actor);
        assertThat(ledger.registrarResultadoEnvio(pago.pagoId(), aplicado("01", "30")).estado()).isEqualTo("REVISION"); saldo("100", "20");
    }
    @Test void redFueraDeTransaccionYRepeticionNoReenviaSendTx() throws Exception {
        var gateway = mock(PagoGateway.class);
        when(gateway.enviar(any())).thenAnswer(invocation -> { assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse(); return PagoGateway.Respuesta.incierta(); });
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(actor, UUID.randomUUID(), "juan@example.com", "juan@example.com"), null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
        var service = new PagoServiceImpl(ledger, gateway, properties, crypto, json, new LoginAttemptLimiter(new LoginRateLimitProperties()));
        var pago = service.preparar(request("20")); var firma = codigoPara(pago);
        service.autorizar(firma); service.autorizar(firma);
        verify(gateway, times(1)).enviar(any()); saldo("100", "20");
    }
    @Test void cargaDelProveedorYCifradoNoAceptanUnidadOTipoAlterados() throws Exception {
        var pago = ledger.preparar(request("20"), actor);
        var payload = json.readTree(pago.json());
        assertThat(payload.path("unidad").asText()).isEqualTo("083");
        assertThat(payload.path("telefono").asText()).isEqualTo("1111111111");
        assertThat(payload.path("horaLocal").asText()).matches("[0-9]{14}");
        assertThat(payload.has("numeroCuenta")).isFalse();
        assertThat(pagos.findById(pago.pago().pagoId()).orElseThrow().getPayloadCifrado()).doesNotContain("REF-123");
        assertThatThrownBy(() -> ledger.preparar(new PrepararRequest(UUID.randomUUID(), "00000000000000000001", 99, 1, 2, new BigDecimal("20"), null, "R"), actor)).isInstanceOf(PagoException.class);
    }
    @Test void montoVariableSinLimitesOConfiguracionInvalidaNoCreaPagoNiRetiene() {
        properties.getLimitesPorProducto().clear();
        assertThatThrownBy(() -> ledger.preparar(request("20"), actor)).isInstanceOf(PagoException.class);
        properties.getLimitesPorProducto().put(1, new PagoProperties.LimiteMonto(2, new BigDecimal("30"), new BigDecimal("10")));
        assertThatThrownBy(() -> ledger.preparar(request("20"), actor)).isInstanceOf(PagoException.class);
        properties.getLimitesPorProducto().put(1, new PagoProperties.LimiteMonto(99, new BigDecimal("1"), new BigDecimal("100")));
        assertThatThrownBy(() -> ledger.preparar(request("20"), actor)).isInstanceOf(PagoException.class);
        assertThat(pagos.count()).isZero(); saldo("100", "0");
    }
    @Test void rangoDeMontoEsInclusivoYRechazaValoresFueraDeLimites() {
        properties.getLimitesPorProducto().put(1, new PagoProperties.LimiteMonto(2, new BigDecimal("10"), new BigDecimal("30")));
        assertThat(ledger.preparar(request("10"), actor)).isNotNull();
        assertThat(ledger.preparar(request("30"), actor)).isNotNull();
        assertThatThrownBy(() -> ledger.preparar(request("9.99"), actor)).isInstanceOf(PagoException.class);
        assertThatThrownBy(() -> ledger.preparar(request("30.01"), actor)).isInstanceOf(PagoException.class); saldo("100", "0");
    }
    @Test void limitesSeRevalidanAntesDeRetenerAunquePreparacionFueraValida() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); var firma = codigoPara(pago);
        properties.getLimitesPorProducto().put(1, new PagoProperties.LimiteMonto(2, new BigDecimal("1"), new BigDecimal("10")));
        assertThat(ledger.autorizar(firma, actor)).isNull();
        assertThat(ledger.consultar(pago.pagoId(), actor).estado()).isEqualTo("EXPIRADO"); saldo("100", "0");
    }
    @Test void cambioDeCatalogoExigeNuevaPreparacionSinCambiarImporteFirmado() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago();
        tx.executeWithoutResult(status -> { var p = productos.findById(1).orElseThrow(); p.setPrice(new BigDecimal("7")); productos.saveAndFlush(p); });
        assertThat(ledger.autorizar(codigoPara(pago), actor)).isNull();
        assertThat(ledger.consultar(pago.pagoId(), actor).estado()).isEqualTo("EXPIRADO");
        assertThat(ledger.consultar(pago.pagoId(), actor).monto()).isEqualByComparingTo("20"); saldo("100", "0");
        assertThat(ledger.preparar(request("20"), actor).pago().pagoId()).isNotEqualTo(pago.pagoId());
    }
    @Test void productoEliminadoNoPermiteAutorizarUnPagoPreparado() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago();
        tx.executeWithoutResult(status -> productos.deleteById(1));
        assertThat(ledger.autorizar(codigoPara(pago), actor)).isNull();
        assertThat(ledger.consultar(pago.pagoId(), actor).estado()).isEqualTo("EXPIRADO"); saldo("100", "0");
    }
    @Test void refrescoDeCatalogoSinCambioDeReglasNoInvalidaLaFirma() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago();
        tx.executeWithoutResult(status -> { var p = productos.findById(1).orElseThrow(); p.setUpdatedAt(LocalDateTime.now().plusMinutes(1)); p.setPrice(new BigDecimal("6.0000")); productos.saveAndFlush(p); });
        assertThat(ledger.autorizar(codigoPara(pago), actor)).isNotNull(); saldo("100", "20");
    }
    @Test void solicitudNuevaNoDuplicaPagoEquivalenteAunqueCambieCuenta() {
        ledger.preparar(request("20", " REF-123 "), actor);
        assertThatThrownBy(() -> ledger.preparar(request("20.00"), actor)).isInstanceOf(PagoException.class);
        tx.executeWithoutResult(status -> cuentas.saveAndFlush(new CuentaEntity(UUID.randomUUID(), cuentas.findById(cuentaId).orElseThrow().getCliente(),
                "00000000000000000002", new BigDecimal("100"), Instant.now())));
        var otraCuenta = new PrepararRequest(UUID.randomUUID(), "00000000000000000002", 2, 1, 2, new BigDecimal("20"), null, "REF-123");
        assertThatThrownBy(() -> ledger.preparar(otraCuenta, actor)).isInstanceOf(PagoException.class);
        assertThat(pagos.count()).isEqualTo(1); saldo("100", "0");
    }
    @Test void preparacionesConcurrentesEquivalentesSoloCreanUnaIntencion() throws Exception {
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<Boolean> preparar = () -> { start.await(); try { ledger.preparar(request("20"), actor); return true; } catch (PagoException e) { return false; } };
            var a = pool.submit(preparar); var b = pool.submit(preparar); start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(pagos.count()).isEqualTo(1); saldo("100", "0");
        } finally { pool.shutdownNow(); }
    }
    @Test void pendienteORevisionBloqueanNuevaIntencionSinVentanaArbitraria() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(pago), actor);
        assertThatThrownBy(() -> ledger.preparar(request("20"), actor)).isInstanceOf(PagoException.class);
        ledger.registrarResultadoEnvio(pago.pagoId(), PagoGateway.Respuesta.incierta());
        assertThatThrownBy(() -> ledger.preparar(request("20"), actor)).isInstanceOf(PagoException.class);
        confirmar(pago.pagoId(), aplicado("06", "99"));
        assertThatThrownBy(() -> ledger.preparar(request("20"), actor)).isInstanceOf(PagoException.class); saldo("100", "20");
    }
    @Test void preparacionVencidaNoBloqueaNuevaIntencion() {
        var pago = ledger.preparar(request("20"), actor).pago();
        tx.executeWithoutResult(status -> { var p = pagos.findById(pago.pagoId()).orElseThrow(); p.setVenceEn(Instant.now().minusSeconds(1)); pagos.saveAndFlush(p); });
        assertThat(ledger.preparar(request("20"), actor).pago().pagoId()).isNotEqualTo(pago.pagoId());
    }
    @Test void duplicadoDeOperacionAplicadaConocidaNoDescuentaDosVeces() throws Exception {
        var anterior = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(anterior), actor);
        ledger.registrarResultadoEnvio(anterior.pagoId(), aplicado("01", "20")); saldo("80", "0");
        // Successful purchases are not blocked for an invented period; the provider remains authoritative.
        var nueva = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(nueva), actor); saldo("80", "20");
        var duplicada = GestopagoPagoGateway.interpretar("<RESPONSE><CODIGO>06</CODIGO><ID_TX>123</ID_TX><NUM_AUTORIZACION>456</NUM_AUTORIZACION><MONTO>20</MONTO></RESPONSE>", false);
        assertThat(ledger.registrarResultadoEnvio(nueva.pagoId(), duplicada).estado()).isEqualTo("RECHAZADO"); saldo("80", "0");
        assertThat(ledger.consultar(anterior.pagoId(), actor).estado()).isEqualTo("APLICADO");
        assertThat(ledger.consultar(nueva.pagoId(), actor).aviso()).contains("no se realizo un nuevo cargo");
    }
    @Test void duplicadoExternoDesconocidoNoSeConvierteEnUnNuevoDebitoAlConcodigoPara() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(pago), actor);
        var duplicada = GestopagoPagoGateway.interpretar("<RESPONSE><CODIGO>06</CODIGO><ID_TX>123</ID_TX><NUM_AUTORIZACION>456</NUM_AUTORIZACION><MONTO>20</MONTO></RESPONSE>", false);
        assertThat(ledger.registrarResultadoEnvio(pago.pagoId(), duplicada).estado()).isEqualTo("PENDIENTE");
        assertThat(confirmar(pago.pagoId(), aplicado("06", null)).estado()).isEqualTo("REVISION"); saldo("100", "20");
        assertThat(pagos.findById(pago.pagoId()).orElseThrow().isDuplicadoProveedor()).isTrue();
    }
    @Test void duplicadoNoAplicadoConfirmadoLiberaSinCargo() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(pago), actor);
        var duplicada = GestopagoPagoGateway.interpretar("<RESPONSE><CODIGO>06</CODIGO><ID_TX>123</ID_TX><NUM_AUTORIZACION>456</NUM_AUTORIZACION></RESPONSE>", false);
        ledger.registrarResultadoEnvio(pago.pagoId(), duplicada);
        var noAplicado = new PagoGateway.Respuesta(PagoGateway.Estado.RECHAZADO, "70", null, null, null, null, null, null, "");
        assertThat(confirmar(pago.pagoId(), noAplicado).estado()).isEqualTo("RECHAZADO"); saldo("100", "0");
    }
    @Test void configuracionPorProductoSeEnlazaConSpringSinValoresPredeterminados() {
        var source = new org.springframework.boot.context.properties.source.MapConfigurationPropertySource(Map.of(
                "gestopago.pagos.limites-por-producto[205].id-servicio", "76",
                "gestopago.pagos.limites-por-producto[205].minimo", "10.00",
                "gestopago.pagos.limites-por-producto[205].maximo", "100.00"));
        var configurada = new org.springframework.boot.context.properties.bind.Binder(source)
                .bind("gestopago.pagos", org.springframework.boot.context.properties.bind.Bindable.of(PagoProperties.class)).get();
        assertThat(configurada.getLimitesPorProducto().get(205).idServicio()).isEqualTo(76);
        assertThat(configurada.getLimitesPorProducto().get(205).minimo()).isEqualByComparingTo("10");
        assertThat(new PagoProperties().getLimitesPorProducto()).isEmpty();
    }
    @Test void autorizacionRechazaPreparacionAnteriorSinHuellaDeCatalogo() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago();
        tx.executeWithoutResult(status -> { var p = pagos.findById(pago.pagoId()).orElseThrow(); p.setHuellaCatalogo(null); pagos.saveAndFlush(p); });
        assertThat(ledger.autorizar(codigoPara(pago), actor)).isNull(); saldo("100", "0");
        assertThat(ledger.consultar(pago.pagoId(), actor).estado()).isEqualTo("EXPIRADO");
    }
    @Test void idDeTransaccionAnteriorSinMismoDestinoNoLiberaUnaRetencion() throws Exception {
        var anterior = ledger.preparar(request("20", "OTRA-FACTURA"), actor).pago(); ledger.autorizar(codigoPara(anterior), actor);
        ledger.registrarResultadoEnvio(anterior.pagoId(), aplicado("01", "20"));
        var nueva = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(nueva), actor);
        var duplicada = GestopagoPagoGateway.interpretar("<RESPONSE><CODIGO>06</CODIGO><ID_TX>123</ID_TX><NUM_AUTORIZACION>456</NUM_AUTORIZACION><MONTO>20</MONTO></RESPONSE>", false);
        ledger.registrarResultadoEnvio(nueva.pagoId(), duplicada);
        assertThat(confirmar(nueva.pagoId(), aplicado("06", null)).estado()).isEqualTo("REVISION"); saldo("80", "20");
    }
    @Test void respuestaDeConfirmacionAnteriorNoSobrescribeIntentoMasReciente() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(pago), actor);
        ledger.registrarResultadoEnvio(pago.pagoId(), PagoGateway.Respuesta.incierta());
        var primera = reclamarAhora(pago.pagoId()); var segunda = reclamarAhora(pago.pagoId());
        assertThat(primera.intento()).isNotEqualTo(segunda.intento());
        var negativa = new PagoGateway.Respuesta(PagoGateway.Estado.RECHAZADO, "70", null, null, null, null, null, null, "<RESPONSE><CODIGO>70</CODIGO></RESPONSE>");
        assertThat(ledger.resolverConfirmacion(primera, negativa).estado()).isEqualTo("PENDIENTE"); saldo("100", "20");
        assertThat(pagos.findById(pago.pagoId()).orElseThrow().getIntentoConfirmacion()).isEqualTo(segunda.intento());
        ledger.resolverConfirmacion(segunda, aplicado("06", null)); saldo("80", "0");
        ledger.resolverConfirmacion(primera, negativa); saldo("80", "0");
        assertThat(evidencias.count()).isEqualTo(4);
        assertThat(evidencias.findAll()).anyMatch(e -> crypto.revelar(e.getContenido()).contains(primera.intento().toString()));
    }
    @Test void confirmacionFallidaProgramaEsperaCrecienteSinReenviarPago() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(pago), actor);
        ledger.registrarResultadoEnvio(pago.pagoId(), PagoGateway.Respuesta.incierta());
        for (long espera : new long[]{60, 120, 240, 480, 900, 900}) {
            var confirmacion = reclamarAhora(pago.pagoId()); Instant antes = Instant.now();
            ledger.resolverConfirmacion(confirmacion, PagoGateway.Respuesta.incierta());
            var guardado = pagos.findById(pago.pagoId()).orElseThrow();
            assertThat(guardado.getIntentoConfirmacion()).isNull();
            assertThat(guardado.getProximaConfirmacion()).isBetween(antes.plusSeconds(espera - 1), Instant.now().plusSeconds(espera + 1));
            assertThat(ledger.reclamarConfirmacion(pago.pagoId())).isNull(); saldo("100", "20");
        }
        assertThat(pagos.findById(pago.pagoId()).orElseThrow().getNumeroConfirmaciones()).isEqualTo(6);
    }
    @Test void timeoutRealDelGatewayConservaDineroYRepetirAutorizarNoReenvia() throws Exception {
        var gateway = mock(PagoGateway.class);
        when(gateway.enviar(any())).thenAnswer(invocation -> { throw new java.net.SocketTimeoutException("dato sensible no debe registrarse"); });
        autenticar();
        var service = new PagoServiceImpl(ledger, gateway, properties, crypto, json, new LoginAttemptLimiter(new LoginRateLimitProperties()));
        var pago = service.preparar(request("20")); var firma = codigoPara(pago);
        assertThat(service.autorizar(firma).estado()).isEqualTo("PENDIENTE"); saldo("100", "20");
        assertThat(service.autorizar(firma).estado()).isEqualTo("PENDIENTE");
        verify(gateway, times(1)).enviar(any());
    }
    @Test void falloDeBaseDuranteLiquidacionRevierteSaldoEstadoYEvidencia() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(pago), actor);
        var cuentasConFallo = mock(CuentaRepository.class, org.mockito.AdditionalAnswers.delegatesTo(cuentas));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("fallo simulado"))
                .when(cuentasConFallo).saveAndFlush(any(CuentaEntity.class));
        var ledgerConFallo = new PagoLedger(pagos, evidencias, cuentasConFallo, usuarios, claves, productos, properties, crypto, json, totp, manager);
        assertThatThrownBy(() -> ledgerConFallo.registrarResultadoEnvio(pago.pagoId(), aplicado("01", "20")))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        saldo("100", "20"); assertThat(evidencias.count()).isZero();
        assertThat(ledger.consultar(pago.pagoId(), actor).estado()).isEqualTo("ENVIADO");
        ledger.registrarResultadoEnvio(pago.pagoId(), aplicado("01", "20")); saldo("80", "0");
    }
    @Test void resultadoNoGuardadoTrasEnvioSeRecuperaConConfirmacionSinRepetirSendTx() throws Exception {
        var gateway = mock(PagoGateway.class); when(gateway.enviar(any())).thenReturn(aplicado("01", "20"));
        var ledgerConFallo = spy(ledger);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("base temporalmente no disponible"))
                .when(ledgerConFallo).registrarResultadoEnvio(any(UUID.class), any(PagoGateway.Respuesta.class));
        autenticar();
        var service = new PagoServiceImpl(ledgerConFallo, gateway, properties, crypto, json, new LoginAttemptLimiter(new LoginRateLimitProperties()));
        var pago = service.preparar(request("20")); var firma = codigoPara(pago);
        assertThatThrownBy(() -> service.autorizar(firma)).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        saldo("100", "20");
        var recuperado = new PagoLedger(pagos, evidencias, cuentas, usuarios, claves, productos, properties, crypto, json, totp, manager);
        var nuevoService = new PagoServiceImpl(recuperado, gateway, properties, crypto, json, new LoginAttemptLimiter(new LoginRateLimitProperties()));
        assertThat(nuevoService.autorizar(firma).estado()).isEqualTo("ENVIADO");
        tx.executeWithoutResult(status -> { var p = pagos.findById(pago.pagoId()).orElseThrow(); p.setEnviadoEn(Instant.now().minusSeconds(80)); p.setProximaConfirmacion(Instant.now().minusSeconds(1)); pagos.saveAndFlush(p); });
        when(gateway.confirmar(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return aplicado("06", null);
        });
        new PagoReconciliador(recuperado, gateway, properties).reconciliar();
        saldo("80", "0"); verify(gateway, times(1)).enviar(any()); verify(gateway, times(1)).confirmar(any());
    }
    @Test void dosProcesosNoReclamanSimultaneamenteLaMismaConfirmacion() throws Exception {
        var pago = ledger.preparar(request("20"), actor).pago(); ledger.autorizar(codigoPara(pago), actor);
        tx.executeWithoutResult(status -> { var p = pagos.findById(pago.pagoId()).orElseThrow(); p.setEnviadoEn(Instant.now().minusSeconds(80)); p.setProximaConfirmacion(Instant.now().minusSeconds(1)); pagos.saveAndFlush(p); });
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<PagoLedger.Confirmacion> reclamar = () -> { start.await(); return ledger.reclamarConfirmacion(pago.pagoId()); };
            var a = pool.submit(reclamar); var b = pool.submit(reclamar); start.countDown();
            assertThat(Arrays.asList(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).filteredOn(Objects::nonNull).hasSize(1);
            assertThat(pagos.findById(pago.pagoId()).orElseThrow().getNumeroConfirmaciones()).isEqualTo(1); saldo("100", "20");
        } finally { pool.shutdownNow(); }
    }
    @Test void codigoConsumidoNoAutorizaOtroPagoYFalloNoRetiene() {
        var a = ledger.preparar(request("20"), actor).pago();
        var b = ledger.preparar(request("20", "OTRO-RECIBO"), actor).pago();
        var first = codigoPara(a);
        ledger.autorizar(first, actor);
        assertThatThrownBy(() -> ledger.autorizar(new AutorizarRequest(b.pagoId(), first.codigo()), actor))
                .isInstanceOf(PagoException.class);
        saldo("100", "20");
        assertThat(ledger.consultar(b.pagoId(), actor).estado()).isEqualTo("PREPARADO");
        assertThat(claves.findById(actor).orElseThrow().getIntentos()).isEqualTo(1);
    }
    @Test void cincoCodigosIncorrectosPersistenBloqueoAunqueLaRespuestaSeaError() {
        var pago = ledger.preparar(request("20"), actor).pago();
        for (int i = 0; i < 5; i++) assertThatThrownBy(() ->
                ledger.autorizar(new AutorizarRequest(pago.pagoId(), "invalid"), actor)).isInstanceOf(PagoException.class);
        assertThat(claves.findById(actor).orElseThrow().getIntentos()).isEqualTo(5);
        assertThatThrownBy(() -> ledger.autorizar(codigoPara(pago), actor))
                .isInstanceOfSatisfying(PagoException.class, error -> assertThat(error.getStatus().value()).isEqualTo(429));
        saldo("100", "0");
    }
    @Test void activacionExigeContrasenaYCodigoYEntregaRecuperacionUnaSolaVez() {
        tx.executeWithoutResult(status -> claves.deleteById(actor)); autenticar();
        var service = secondFactorService();
        assertThat(service.estado().activo()).isFalse();
        assertThatThrownBy(() -> service.preparar(new PrepararFactorRequest("wrong", null, null)))
                .isInstanceOf(PagoException.class);
        var setup = service.preparar(new PrepararFactorRequest("fixture-password", null, null));
        assertThat(setup.uri()).contains("otpauth://totp/", "digits=6", "period=30");
        assertThat(claves.findById(actor).orElseThrow().getPendienteCifrado()).doesNotContain(setup.secreto());
        var result = service.activar(new ActivarFactorRequest(totpCodes.code(setup.secreto(), totpCodes.currentStep())));
        assertThat(result.activo()).isTrue(); assertThat(result.codigosRecuperacion()).hasSize(10).doesNotHaveDuplicates();
        var stored = claves.findById(actor).orElseThrow();
        assertThat(stored.getSecretoCifrado()).doesNotContain(setup.secreto());
        assertThat(stored.getPendienteCifrado()).isNull();
        result.codigosRecuperacion().forEach(code -> assertThat(stored.getRecuperacionHashes()).doesNotContain(code));
        assertThatThrownBy(() -> service.activar(new ActivarFactorRequest("123456"))).isInstanceOf(PagoException.class);
        assertThat(result.toString()).doesNotContain(result.codigosRecuperacion().get(0));
        assertThat(setup.toString()).doesNotContain(setup.secreto());
    }
    @Test void cambioDeAutenticadorExigeFactorAnteriorYRecuperacionEsDeUnUso() {
        tx.executeWithoutResult(status -> claves.deleteById(actor)); autenticar();
        var service = secondFactorService();
        var initial = service.preparar(new PrepararFactorRequest("fixture-password", null, null));
        var activated = service.activar(new ActivarFactorRequest(totpCodes.code(initial.secreto(), totpCodes.currentStep())));
        String fingerprint = claves.findById(actor).orElseThrow().getHuella();
        assertThatThrownBy(() -> service.preparar(new PrepararFactorRequest("fixture-password", null, null)))
                .isInstanceOf(PagoException.class);
        String recovery = activated.codigosRecuperacion().get(0);
        var setup = service.preparar(new PrepararFactorRequest("fixture-password", null, recovery));
        assertThat(claves.findById(actor).orElseThrow().getHuella()).isEqualTo(fingerprint);
        assertThat(service.estado().activo()).isTrue();
        assertThatThrownBy(() -> service.preparar(new PrepararFactorRequest("fixture-password", null, recovery)))
                .isInstanceOf(PagoException.class);
        service.activar(new ActivarFactorRequest(totpCodes.code(setup.secreto(), totpCodes.currentStep())));
        assertThat(claves.findById(actor).orElseThrow().getHuella()).isNotEqualTo(fingerprint);
    }
    @Test void activacionVencidaODesdeOtraSesionNoReemplazaSegundoFactor() {
        autenticar(); var service = secondFactorService();
        var setup = service.preparar(new PrepararFactorRequest("fixture-password",
                totpCodes.code(secret, totpCodes.currentStep()), null));
        String fingerprint = claves.findById(actor).orElseThrow().getHuella();
        autenticar(); // Nueva sesion del mismo usuario.
        assertThatThrownBy(() -> service.activar(new ActivarFactorRequest(totpCodes.code(setup.secreto(), totpCodes.currentStep()))))
                .isInstanceOf(PagoException.class);
        tx.executeWithoutResult(status -> { var f = claves.findById(actor).orElseThrow();
            f.setVenceEn(Instant.now().minusSeconds(1)); claves.saveAndFlush(f); });
        assertThatThrownBy(() -> service.activar(new ActivarFactorRequest("123456"))).isInstanceOf(PagoException.class);
        assertThat(claves.findById(actor).orElseThrow().getHuella()).isEqualTo(fingerprint);
    }
    @Test void activarNuevoFactorInvalidaPreparacionAnteriorSinRetenerSaldo() {
        var pago = ledger.preparar(request("20"), actor).pago(); autenticar(); var service = secondFactorService();
        var setup = service.preparar(new PrepararFactorRequest("fixture-password",
                totpCodes.code(secret, totpCodes.currentStep()), null));
        service.activar(new ActivarFactorRequest(totpCodes.code(setup.secreto(), totpCodes.currentStep())));
        assertThatThrownBy(() -> ledger.autorizar(codigoPara(pago), actor)).isInstanceOf(PagoException.class);
        saldo("100", "0");
    }
    private SegundoFactorService secondFactorService() {
        var passwords = mock(org.springframework.security.crypto.password.PasswordEncoder.class);
        when(passwords.matches("fixture-password", "hash")).thenReturn(true);
        return new SegundoFactorServiceImpl(claves, usuarios, passwords, totpCodes, totpSecrets, totp, manager);
    }
    @Test void falloAlRetenerRevierteTambienElConsumoDelCodigoTotp() {
        var pago = ledger.preparar(request("20"), actor).pago(); var codigo = codigoPara(pago);
        var cuentasConFallo = mock(CuentaRepository.class, org.mockito.AdditionalAnswers.delegatesTo(cuentas));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("fallo simulado"))
                .when(cuentasConFallo).saveAndFlush(any(CuentaEntity.class));
        var ledgerConFallo = new PagoLedger(pagos, evidencias, cuentasConFallo, usuarios, claves, productos,
                properties, crypto, json, totp, manager);
        assertThatThrownBy(() -> ledgerConFallo.autorizar(codigo, actor))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        saldo("100", "0");
        assertThat(claves.findById(actor).orElseThrow().getUltimoPaso()).isEqualTo(-1);
        assertThat(ledger.consultar(pago.pagoId(), actor).estado()).isEqualTo("PREPARADO");
        assertThat(ledger.autorizar(codigo, actor)).isNotNull(); saldo("100", "20");
    }
    @Test void mismoTotpConcurrenteEnPagosDistintosSoloPuedeReservarUno() throws Exception {
        var a = ledger.preparar(request("20"), actor).pago();
        var b = ledger.preparar(request("20", "OTRO-DESTINO"), actor).pago();
        var codigo = codigoPara(a);
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<Boolean> first = () -> { start.await(); try { ledger.autorizar(codigo, actor); return true; }
                catch (PagoException error) { return false; } };
            Callable<Boolean> second = () -> { start.await(); try { ledger.autorizar(new AutorizarRequest(b.pagoId(), codigo.codigo()), actor); return true; }
                catch (PagoException error) { return false; } };
            var x = pool.submit(first); var y = pool.submit(second); start.countDown();
            assertThat(List.of(x.get(15, TimeUnit.SECONDS), y.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            saldo("100", "20");
        } finally { pool.shutdownNow(); }
    }
    private PrepararRequest requestConDeuda(String monto) {
        tx.executeWithoutResult(status -> {
            var producto = productos.findById(1).orElseThrow(); producto.setFrontType(4); productos.saveAndFlush(producto);
        });
        return new PrepararRequest(UUID.randomUUID(), "00000000000000000001", 2, 1, 4, new BigDecimal(monto), null, "REF-123");
    }
    private PagoResponse referencia(PagoResponse pago, String deuda) {
        return ledger.referenciaValidada(pago.pagoId(), actor, new PagoGateway.Referencia(
                PagoGateway.EstadoReferencia.VALIDA, "01", new BigDecimal(deuda), "<RESPONSE>fixture</RESPONSE>"));
    }
    private void regla(PagoProperties.ModalidadReferencia modalidad) {
        properties.getReglasReferenciaPorProducto().put(1, new PagoProperties.ReglaReferencia(2, modalidad));
    }
    @Test void deudaSeConservaCifradaYNoSeAutorizaSinReglaConfirmada() {
        var pago = ledger.preparar(requestConDeuda("20"), actor).pago();
        assertThat(pago.autorizacionDisponible()).isFalse();
        var consultada = referencia(pago, "20.00");
        assertThat(consultada.saldoReferencia()).isEqualByComparingTo("20.00");
        assertThat(consultada.consultaReferenciaEn()).isNotNull();
        assertThat(consultada.reglaImporteReferencia()).isEqualTo("SIN_DEFINIR");
        assertThat(consultada.autorizacionDisponible()).isFalse();
        var guardado = pagos.findById(pago.pagoId()).orElseThrow();
        assertThat(guardado.getConsultaReferenciaCifrada()).doesNotContain("fixture", "20.00");
        assertThatThrownBy(() -> ledger.autorizar(codigoPara(pago), actor)).isInstanceOf(PagoException.class);
        saldo("100", "0");
    }
    @Test void importeExactoExigeCoincidenciaYSeRevalidaAlAutorizar() {
        regla(PagoProperties.ModalidadReferencia.IMPORTE_EXACTO);
        var pago = ledger.preparar(requestConDeuda("20"), actor).pago();
        assertThat(referencia(pago, "25").autorizacionDisponible()).isFalse();
        assertThatThrownBy(() -> ledger.autorizar(codigoPara(pago), actor)).isInstanceOf(PagoException.class);
        // Cambiar politica despues de preparar tampoco permite sobrepagar.
        regla(PagoProperties.ModalidadReferencia.PARCIAL_HASTA_SALDO);
        assertThat(ledger.consultar(pago.pagoId(), actor).autorizacionDisponible()).isFalse();
        properties.getReglasReferenciaPorProducto().put(1, new PagoProperties.ReglaReferencia(99, PagoProperties.ModalidadReferencia.PARCIAL_HASTA_SALDO));
        assertThat(ledger.autorizar(codigoPara(pago), actor)).isNull();
        assertThat(ledger.consultar(pago.pagoId(), actor).estado()).isEqualTo("EXPIRADO");
        saldo("100", "0");
    }
    @Test void exactoValidoRetieneSoloElImporteRevisadoYSnapshotNoSeSobrescribe() {
        regla(PagoProperties.ModalidadReferencia.IMPORTE_EXACTO);
        var pago = ledger.preparar(requestConDeuda("20"), actor).pago();
        var primera = referencia(pago, "20");
        var segunda = referencia(pago, "30");
        assertThat(segunda.saldoReferencia()).isEqualByComparingTo("20");
        assertThat(java.time.Duration.between(primera.consultaReferenciaEn(), segunda.consultaReferenciaEn()).abs()).isLessThan(java.time.Duration.ofMillis(1));
        assertThat(segunda.autorizacionDisponible()).isTrue();
        assertThat(ledger.autorizar(codigoPara(pago), actor)).isNotNull();
        saldo("100", "20");
    }
    @Test void abonoParcialSoloConPoliticaConfirmadaYNoPermiteSobrepago() {
        regla(PagoProperties.ModalidadReferencia.PARCIAL_HASTA_SALDO);
        var pago = ledger.preparar(requestConDeuda("20"), actor).pago();
        assertThat(referencia(pago, "15").autorizacionDisponible()).isFalse();
        assertThatThrownBy(() -> ledger.autorizar(codigoPara(pago), actor)).isInstanceOf(PagoException.class);
        saldo("100", "0");
    }
    @Test void abonoPermitidoSeAutorizaSinCambiarMontoYRetieneSoloElAbono() {
        regla(PagoProperties.ModalidadReferencia.PARCIAL_HASTA_SALDO);
        var pago = ledger.preparar(requestConDeuda("20"), actor).pago();
        assertThat(referencia(pago, "30").autorizacionDisponible()).isTrue();
        var envio = ledger.autorizar(codigoPara(pago), actor);
        assertThat(envio).isNotNull();
        assertThat(ledger.consultar(pago.pagoId(), actor).monto()).isEqualByComparingTo("20");
        saldo("100", "20");
    }
    @Test void respuestaTardiaDeReferenciaNoReactivaPagoCanceladoNiCuentaAjena() {
        var pago = ledger.preparar(requestConDeuda("20"), actor).pago();
        assertThatThrownBy(() -> ledger.referenciaValidada(pago.pagoId(), UUID.randomUUID(),
                new PagoGateway.Referencia(PagoGateway.EstadoReferencia.VALIDA, "01", new BigDecimal("20"), "fixture")))
                .isInstanceOf(PagoException.class);
        ledger.cancelar(pago.pagoId(), actor);
        assertThat(referencia(pago, "20").estado()).isEqualTo("EXPIRADO");
        assertThat(pagos.findById(pago.pagoId()).orElseThrow().getConsultaReferenciaCifrada()).isNull();
        saldo("100", "0");
    }
    @Test void ceroOSaldoAFavorNoPermitenCobroPeroSeMuestran() {
        regla(PagoProperties.ModalidadReferencia.PARCIAL_HASTA_SALDO);
        for (String deuda : java.util.List.of("0", "-15")) {
            var pago = ledger.preparar(requestConDeuda("20"), actor).pago();
            var respuesta = referencia(pago, deuda);
            assertThat(respuesta.saldoReferencia()).isEqualByComparingTo(deuda);
            assertThat(respuesta.autorizacionDisponible()).isFalse();
            assertThatThrownBy(() -> ledger.autorizar(codigoPara(pago), actor)).isInstanceOf(PagoException.class);
            ledger.cancelar(pago.pagoId(), actor);
        }
        saldo("100", "0");
    }
    @Test void referenciaInciertaNoRechazaPagoYReintentoConsultaSinEnviarVenta() {
        autenticar();
        var gateway = mock(PagoGateway.class);
        var servicio = new PagoServiceImpl(ledger, gateway, properties, crypto, json, mock(LoginAttemptLimiter.class));
        var solicitud = requestConDeuda("20");
        when(gateway.validarReferencia(1, 2, "REF-123")).thenReturn(PagoGateway.Referencia.incierta());
        assertThatThrownBy(() -> servicio.preparar(solicitud)).isInstanceOf(PagoException.class);
        assertThat(ledger.consultar(solicitud.solicitudId(), actor).estado()).isEqualTo("PREPARADO");
        when(gateway.validarReferencia(1, 2, "REF-123")).thenReturn(new PagoGateway.Referencia(PagoGateway.EstadoReferencia.VALIDA, "01", new BigDecimal("30"), "fixture"));
        assertThat(servicio.preparar(solicitud).saldoReferencia()).isEqualByComparingTo("30");
        verify(gateway, never()).enviar(anyString()); saldo("100", "0");
    }
    @Test void referenciaInvalidaRechazaSinRetenerYSinExponerRespuestaProveedor() {
        var pago = ledger.preparar(requestConDeuda("20"), actor).pago();
        var respuesta = ledger.referenciaValidada(pago.pagoId(), actor,
                new PagoGateway.Referencia(PagoGateway.EstadoReferencia.INVALIDA, "02", null, "detalle privado"));
        assertThat(respuesta.estado()).isEqualTo("RECHAZADO");
        assertThat(respuesta.saldoReferencia()).isNull();
        assertThat(respuesta.autorizacionDisponible()).isFalse(); saldo("100", "0");
    }
    private UUID[] operadores() {
        var a=UserEntity.crearEjecutivo("operador1@example.com","hash",Instant.now());
        var b=UserEntity.crearEjecutivo("operador2@example.com","hash",Instant.now());
        tx.executeWithoutResult(status -> { usuarios.saveAndFlush(a); usuarios.saveAndFlush(b); });
        properties.getConciliacion().setEnabled(true);
        properties.getConciliacion().setOperadores(Set.of(a.getId(),b.getId()));
        return new UUID[]{a.getId(),b.getId()};
    }
    private void ejecutivo(UUID id) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(id,UUID.randomUUID(),"operador@example.com","operador@example.com"),null,
                List.of(new SimpleGrantedAuthority("ROLE_EJECUTIVO"))));
    }
    private ConciliacionPagoServiceImpl conciliacion() {
        return new ConciliacionPagoServiceImpl(properties,pagos,propuestas,cuentas,usuarios,evidencias,crypto,json,manager);
    }
    private PagoResponse enRevision(String referencia) {
        var pago=ledger.preparar(request("20",referencia),actor).pago(); ledger.autorizar(codigoPara(pago),actor);
        return ledger.registrarResultadoEnvio(pago.pagoId(),aplicado("01","99"));
    }
    private com.proyecto.servicios.model.pago.ConciliacionDtos.ProponerRequest propuesta(PagoResponse pago,
            com.proyecto.servicios.model.pago.ConciliacionDtos.Resultado resultado) {
        return new com.proyecto.servicios.model.pago.ConciliacionDtos.ProponerRequest(UUID.randomUUID(),pago.pagoId(),
                pagos.findById(pago.pagoId()).orElseThrow().getVersion(),pago.upc(),pago.monto(),resultado,
                resultado==com.proyecto.servicios.model.pago.ConciliacionDtos.Resultado.NO_APLICADO_DEFINITIVO?null:"123",
                resultado==com.proyecto.servicios.model.pago.ConciliacionDtos.Resultado.NO_APLICADO_DEFINITIVO?null:"456",
                "FOLIO-PRUEBA-01","Evidencia sintetica del proveedor para UPC "+pago.upc(),"Revision de evidencia y cierre definitivo de prueba");
    }
    private com.proyecto.servicios.model.pago.ConciliacionDtos.DecisionRequest decision(UUID id) {
        return new com.proyecto.servicios.model.pago.ConciliacionDtos.DecisionRequest(id,"Evidencia revisada independientemente para esta prueba");
    }
    @Test void reglasSeGuardanCifradasYCambiosInclusoPermisivosExigenNuevaPreparacion() {
        var pago=ledger.preparar(request("20"),actor).pago();
        var entity=pagos.findById(pago.pagoId()).orElseThrow();
        assertThat(entity.getHuellaReglas()).matches("[a-f0-9]{64}");
        assertThat(entity.getReglasCifradas()).doesNotContain("0.01");
        properties.getLimitesPorProducto().put(1,new PagoProperties.LimiteMonto(2,new BigDecimal("0.01"),new BigDecimal("200")));
        assertThat(ledger.consultar(pago.pagoId(),actor).autorizacionDisponible()).isFalse();
        assertThat(ledger.autorizar(codigoPara(pago),actor)).isNull();
        assertThat(ledger.consultar(pago.pagoId(),actor).estado()).isEqualTo("EXPIRADO"); saldo("100","0");
    }
    @Test void representacionDecimalEquivalenteDeReglasNoInvalidaPreparacion() {
        var pago=ledger.preparar(request("20"),actor).pago();
        properties.getLimitesPorProducto().put(1,new PagoProperties.LimiteMonto(2,new BigDecimal("0.010"),new BigDecimal("100.00")));
        assertThat(ledger.autorizar(codigoPara(pago),actor)).isNotNull(); saldo("100","20");
    }
    @Test void presupuestoPorOperacionYConfiguracionInvalidaNoCreanNiRetienen() {
        properties.setMaximoPorOperacion(new BigDecimal("10"));
        assertThatThrownBy(() -> ledger.preparar(request("20"),actor)).isInstanceOf(PagoException.class);
        properties.setMaximoPorOperacion(new BigDecimal("-1"));
        assertThatThrownBy(() -> ledger.preparar(request("1"),actor)).isInstanceOf(PagoException.class);
        assertThat(pagos.count()).isZero(); saldo("100","0");
    }
    @Test void presupuestoDiarioSeRevalidaYCuentaPagosPendientesYEnRevision() {
        properties.setMaximoDiarioPorCliente(new BigDecimal("30"));
        var a=ledger.preparar(request("20","A"),actor).pago();
        var b=ledger.preparar(request("20","B"),actor).pago();
        ledger.autorizar(codigoPara(a),actor); ledger.registrarResultadoEnvio(a.pagoId(),aplicado("01","99"));
        assertThatThrownBy(() -> ledger.autorizar(codigoPara(b),actor)).isInstanceOf(PagoException.class);
        assertThatThrownBy(() -> ledger.preparar(request("20","C"),actor)).isInstanceOf(PagoException.class);
        saldo("100","20");
    }
    @Test void presupuestoDiarioConcurrenteNoSeSuperaAunqueAmbasPreparacionesFueranValidas() throws Exception {
        properties.setMaximoDiarioPorCliente(new BigDecimal("30"));
        var a=ledger.preparar(request("20","A"),actor).pago(); var b=ledger.preparar(request("20","B"),actor).pago();
        var ra=codigoPara(a); var rb=codigoPara(b); var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures=new ArrayList<>();
            for(var r:List.of(ra,rb)) futures.add(pool.submit(() -> {start.await();try{return ledger.autorizar(r,actor)!=null;}catch(PagoException e){return false;}}));
            start.countDown(); int aceptadas=0; for(var f:futures) if(f.get(20,TimeUnit.SECONDS)) aceptadas++;
            assertThat(aceptadas).isEqualTo(1); saldo("100","20");
        } finally {pool.shutdownNow();}
    }
    @Test void clienteNoPuedeConciliarAunqueSuUuidEsteEnLaListaDeOperadores() {
        var ops=operadores(); properties.getConciliacion().setOperadores(Set.of(actor,ops[0])); autenticar();
        assertThatThrownBy(() -> conciliacion().pendientes(new com.proyecto.servicios.model.cliente.PaginacionRequest()))
                .isInstanceOf(PagoException.class); saldo("100","0");
    }
    @Test void ejecutivoNoHabilitadoYTareaDesactivadaNoAccedenAConciliacion() {
        var ops=operadores(); ejecutivo(ops[0]); properties.getConciliacion().setEnabled(false);
        assertThatThrownBy(() -> conciliacion().pendientes(new com.proyecto.servicios.model.cliente.PaginacionRequest())).isInstanceOf(PagoException.class);
        properties.getConciliacion().setEnabled(true); ejecutivo(UUID.randomUUID());
        assertThatThrownBy(() -> conciliacion().pendientes(new com.proyecto.servicios.model.cliente.PaginacionRequest())).isInstanceOf(PagoException.class);
    }
    @Test void propuestaNoModificaSaldoGuardaEvidenciaCifradaYNoPermiteAutoAprobacion() {
        var pago=enRevision("A"); var ops=operadores(); ejecutivo(ops[0]);
        var solicitud=propuesta(pago,com.proyecto.servicios.model.pago.ConciliacionDtos.Resultado.APLICADO);
        var creada=conciliacion().proponer(solicitud);
        assertThat(creada.estado()).isEqualTo("PROPUESTA");
        assertThat(propuestas.findById(creada.propuestaId()).orElseThrow().getContenidoCifrado()).doesNotContain("Evidencia","FOLIO");
        assertThat(conciliacion().proponer(solicitud).propuestaId()).isEqualTo(creada.propuestaId());
        assertThatThrownBy(() -> conciliacion().aprobar(decision(creada.propuestaId()))).isInstanceOf(PagoException.class);
        saldo("100","20");
    }
    @Test void segundaAprobacionAplicaExactamenteUnaVezYConservaAuditoria() {
        var pago=enRevision("A"); var ops=operadores(); ejecutivo(ops[0]);
        var creada=conciliacion().proponer(propuesta(pago,com.proyecto.servicios.model.pago.ConciliacionDtos.Resultado.APLICADO));
        ejecutivo(ops[1]); assertThat(conciliacion().aprobar(decision(creada.propuestaId())).estado()).isEqualTo("APROBADA");
        saldo("80","0"); conciliacion().aprobar(decision(creada.propuestaId())); saldo("80","0");
        assertThat(ledger.consultar(pago.pagoId(),actor).estado()).isEqualTo("APLICADO");
        assertThat(evidencias.findAll().stream().filter(e -> "manual-aprobar".equals(e.getMetodo())).count()).isEqualTo(1);
    }
    @Test void noAplicacionDefinitivaLiberaSoloLaRetencionTrasDobleAprobacion() {
        var pago=enRevision("A"); var ops=operadores(); ejecutivo(ops[0]);
        var creada=conciliacion().proponer(propuesta(pago,com.proyecto.servicios.model.pago.ConciliacionDtos.Resultado.NO_APLICADO_DEFINITIVO));
        saldo("100","20"); ejecutivo(ops[1]); conciliacion().aprobar(decision(creada.propuestaId()));
        saldo("100","0"); assertThat(ledger.consultar(pago.pagoId(),actor).estado()).isEqualTo("RECHAZADO");
    }
    @Test void duplicadoYaContabilizadoNoVuelveADescontar() {
        var anterior=ledger.preparar(request("20","A"),actor).pago(); ledger.autorizar(codigoPara(anterior),actor);
        ledger.registrarResultadoEnvio(anterior.pagoId(),aplicado("01","20"));
        var pago=enRevision("A"); var ops=operadores(); ejecutivo(ops[0]);
        var creada=conciliacion().proponer(propuesta(pago,com.proyecto.servicios.model.pago.ConciliacionDtos.Resultado.DUPLICADO_YA_CONTABILIZADO));
        ejecutivo(ops[1]); conciliacion().aprobar(decision(creada.propuestaId())); saldo("80","0");
    }
    @Test void transaccionYaContabilizadaNoPuedeAplicarseAOtraOrden() {
        var anterior=ledger.preparar(request("20","B"),actor).pago(); ledger.autorizar(codigoPara(anterior),actor);
        ledger.registrarResultadoEnvio(anterior.pagoId(),aplicado("01","20"));
        var pago=enRevision("A"); var ops=operadores(); ejecutivo(ops[0]);
        var creada=conciliacion().proponer(propuesta(pago,com.proyecto.servicios.model.pago.ConciliacionDtos.Resultado.APLICADO));
        ejecutivo(ops[1]); assertThatThrownBy(() -> conciliacion().aprobar(decision(creada.propuestaId()))).isInstanceOf(PagoException.class);
        saldo("80","20");
    }
    @Test void propuestaVencidaOCambioDeVersionNoLiberaDinero() {
        var pago=enRevision("A"); var ops=operadores(); ejecutivo(ops[0]);
        var creada=conciliacion().proponer(propuesta(pago,com.proyecto.servicios.model.pago.ConciliacionDtos.Resultado.NO_APLICADO_DEFINITIVO));
        tx.executeWithoutResult(status -> {var c=propuestas.findById(creada.propuestaId()).orElseThrow();
            c.setCreadoEn(Instant.now().minusSeconds(7200)); c.setVenceEn(Instant.now().minusSeconds(60)); propuestas.saveAndFlush(c);});
        ejecutivo(ops[1]); assertThatThrownBy(() -> conciliacion().aprobar(decision(creada.propuestaId()))).isInstanceOf(PagoException.class);
        conciliacion().rechazar(decision(creada.propuestaId()));
        ejecutivo(ops[0]); var otra=conciliacion().proponer(propuesta(pago,com.proyecto.servicios.model.pago.ConciliacionDtos.Resultado.NO_APLICADO_DEFINITIVO));
        tx.executeWithoutResult(status -> {var p=pagos.findById(pago.pagoId()).orElseThrow(); p.setMotivoRevision("NUEVA_EVIDENCIA"); pagos.saveAndFlush(p);});
        ejecutivo(ops[1]); assertThatThrownBy(() -> conciliacion().aprobar(decision(otra.propuestaId()))).isInstanceOf(PagoException.class);
        saldo("100","20");
    }
    @Test void proponenteRevocadoNoPermiteAplicarUnaPropuestaAnterior() {
        var pago=enRevision("A"); var ops=operadores(); ejecutivo(ops[0]);
        var creada=conciliacion().proponer(propuesta(pago,com.proyecto.servicios.model.pago.ConciliacionDtos.Resultado.APLICADO));
        tx.executeWithoutResult(status -> {var u=usuarios.findById(ops[0]).orElseThrow(); u.desactivar(); usuarios.saveAndFlush(u);});
        ejecutivo(ops[1]); assertThatThrownBy(() -> conciliacion().aprobar(decision(creada.propuestaId()))).isInstanceOf(PagoException.class); saldo("100","20");
    }
    @Test void falloAlGuardarAuditoriaRevierteSaldoPagoYPropuesta() {
        var pago=enRevision("A"); var ops=operadores(); ejecutivo(ops[0]);
        var creada=conciliacion().proponer(propuesta(pago,com.proyecto.servicios.model.pago.ConciliacionDtos.Resultado.APLICADO));
        var repoConFallo=mock(PagoEvidenciaRepository.class); doThrow(new IllegalStateException("fixture")).when(repoConFallo).saveAndFlush(any());
        var servicio=new ConciliacionPagoServiceImpl(properties,pagos,propuestas,cuentas,usuarios,repoConFallo,crypto,json,manager);
        ejecutivo(ops[1]); assertThatThrownBy(() -> servicio.aprobar(decision(creada.propuestaId()))).isInstanceOf(IllegalStateException.class);
        saldo("100","20"); assertThat(pagos.findById(pago.pagoId()).orElseThrow().getEstado()).isEqualTo(PagoEntity.Estado.REVISION);
        assertThat(propuestas.findById(creada.propuestaId()).orElseThrow().getEstado()).isEqualTo(ConciliacionPagoEntity.Estado.PROPUESTA);
    }
    @Test void aprobacionesConcurrentesLiquidanUnaSolaVez() throws Exception {
        var pago=enRevision("A"); var ops=operadores(); ejecutivo(ops[0]);
        var creada=conciliacion().proponer(propuesta(pago,com.proyecto.servicios.model.pago.ConciliacionDtos.Resultado.APLICADO));
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            List<Future<String>> futures=new ArrayList<>();
            for(int i=0;i<2;i++) futures.add(pool.submit(() -> {ejecutivo(ops[1]);try{start.await();return conciliacion().aprobar(decision(creada.propuestaId())).estado();}
                finally{SecurityContextHolder.clearContext();}}));
            start.countDown(); for(var f:futures) assertThat(f.get(20,TimeUnit.SECONDS)).isEqualTo("APROBADA"); saldo("80","0");
        } finally {pool.shutdownNow();}
    }
    private void autenticar() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUser(actor, UUID.randomUUID(), "juan@example.com", "juan@example.com"), null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"))));
    }
}
