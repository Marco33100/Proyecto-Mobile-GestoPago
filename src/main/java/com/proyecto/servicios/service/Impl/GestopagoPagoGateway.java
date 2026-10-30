package com.proyecto.servicios.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.config.ProductServiceProperties;
import com.proyecto.servicios.service.PagoGateway;
import org.springframework.stereotype.Component;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.DisposableBean;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class GestopagoPagoGateway implements PagoGateway, DisposableBean {
    private final GestoPagoAccessTokenProvider tokens;
    private final ProductServiceProperties properties;
    private final PagoCrypto crypto;
    private final ObjectMapper json;
    private final CloseableHttpClient http;
    public GestopagoPagoGateway(GestoPagoAccessTokenProvider tokens, ProductServiceProperties properties,
                               PagoCrypto crypto, ObjectMapper json) {
        this.tokens = tokens; this.properties = properties; this.crypto = crypto; this.json = json;
        http = HttpClients.custom().disableAutomaticRetries().disableRedirectHandling()
                .setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(Timeout.ofSeconds(5))
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5)).setResponseTimeout(Timeout.ofSeconds(70)).build()).build();
    }
    @Override public Respuesta enviar(String payload) { return invocar("sendTx", payload, false); }
    @Override public Respuesta confirmar(String payload) {
        var respuesta = invocar("confirmTx", payload, true);
        // Failure to send a confirmation says nothing about the original payment.
        return "NOT_SENT".equals(respuesta.codigo()) ? Respuesta.incierta() : respuesta;
    }
    @Override public Referencia validarReferencia(Integer producto, Integer servicio, String referencia) {
        try {
            String payload = json.writeValueAsString(Map.of("idProducto", producto, "idServicio", servicio, "referencia", referencia));
            var respuesta = invocar("verifyReference", payload, false);
            return interpretarReferencia(respuesta.evidencia());
        } catch (Exception e) { return Referencia.incierta(); }
    }
    private Respuesta invocar(String metodo, String payload, boolean confirmacion) {
        String token;
        String signed;
        URI uri;
        try {
            URI base = URI.create(properties.getUrl());
            if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
                    || base.getFragment() != null || base.getQuery() != null) return Respuesta.noEnviada();
            uri = base.resolve("/sistema/service/" + metodo + ".do");
            signed = crypto.cifrarProveedor(payload);
            token = tokens.obtenerToken();
        } catch (Exception e) { return Respuesta.noEnviada(); }
        HttpPost request = new HttpPost(uri);
        request.setHeader("Authorization", "Bearer " + token);
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) request.setHeader("X-API-Key", properties.getApiKey());
        request.setEntity(new UrlEncodedFormEntity(List.of(new BasicNameValuePair("random", PagoCrypto.nonce()),
                new BasicNameValuePair("signed", signed)), StandardCharsets.UTF_8));
        try {
            return http.execute(request, response -> {
                byte[] bytes = response.getEntity() == null ? new byte[0] : response.getEntity().getContent().readNBytes(1_048_577);
                if (bytes.length > 1_048_576) return Respuesta.incierta();
                String body = new String(bytes, StandardCharsets.UTF_8);
                if ((response.getCode() == 401 || response.getCode() == 403) && tokenExpirado(body)) {
                    // The provider explicitly rejected authentication; refresh only for future calls. Never replay sendTx.
                    try { tokens.renovarTrasRechazo(token); } catch (Exception ignored) { }
                    return Respuesta.noEnviada();
                }
                if (response.getCode() < 200 || response.getCode() >= 300) return Respuesta.incierta();
                return interpretar(body, confirmacion);
            });
        } catch (Exception e) {
            // No exception text, request, headers, JSON, gift PINs, or token in logs.
            return Respuesta.incierta();
        }
    }
    private boolean tokenExpirado(String body) {
        try { return "EXPIRED".equals(json.readTree(body).path("token").asText()); }
        catch (Exception e) { return false; }
    }
    public static Respuesta interpretar(String xml, boolean confirmar) {
        try {
            Document doc = documento(xml);
            if (!"RESPONSE".equals(doc.getDocumentElement().getNodeName())) return Respuesta.incierta();
            String codigo = texto(doc, "CODIGO");
            String id = texto(doc, "ID_TX"), autorizacion = texto(doc, "NUM_AUTORIZACION");
            boolean aplicado = (confirmar ? "06".equals(codigo) : "01".equals(codigo))
                    && positivo(id) && positivo(autorizacion);
            Estado estado = aplicado ? Estado.APLICADO : confirmar && "70".equals(codigo) ? Estado.RECHAZADO : Estado.INCIERTO;
            return new Respuesta(estado, limitar(codigo, 40), limitar(id, 60), limitar(autorizacion, 60),
                    decimal(texto(doc, "MONTO")), decimal(texto(doc, "COMISION")),
                    limitar(texto(doc, "PIN"), 250), limitar(texto(doc, "legend"), 65600), xml);
        } catch (Exception e) { return Respuesta.incierta(); }
    }
    public static Referencia interpretarReferencia(String xml) {
        try {
            if (xml == null || xml.length() > 1_048_576) return Referencia.incierta();
            Document doc = documento(xml);
            if (!"RESPONSE".equals(doc.getDocumentElement().getNodeName())) return Referencia.incierta();
            String codigo = texto(doc, "CODIGO");
            if ("02".equals(codigo)) return new Referencia(EstadoReferencia.INVALIDA, codigo, null, xml);
            if (!"01".equals(codigo)) return Referencia.incierta();
            String valor = texto(doc, "SALDO");
            if (valor == null || valor.length() > 80) return Referencia.incierta();
            BigDecimal saldo = new BigDecimal(valor).stripTrailingZeros();
            if (saldo.precision() > 19 || saldo.scale() > 2 || saldo.scale() < -17
                    || saldo.abs().compareTo(new BigDecimal("99999999999999999.99")) > 0)
                return Referencia.incierta();
            return new Referencia(EstadoReferencia.VALIDA, codigo, saldo.setScale(2), xml);
        } catch (Exception e) { return Referencia.incierta(); }
    }
    private static Document documento(String xml) throws Exception {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler());
            return builder.parse(new InputSource(new StringReader(xml)));
    }
    private static boolean positivo(String valor) { return valor != null && valor.matches("[0-9]{1,60}") && new BigDecimal(valor).signum() > 0; }
    private static String texto(Document doc, String campo) {
        var nodes = doc.getElementsByTagName(campo);
        if (nodes.getLength() == 0 && "legend".equals(campo)) nodes = doc.getElementsByTagName("LEGEND");
        return nodes.getLength() == 1 ? nodes.item(0).getTextContent().trim() : null;
    }
    private static BigDecimal decimal(String valor) { return valor == null || valor.isBlank() ? null : new BigDecimal(valor); }
    private static String limitar(String valor, int max) { return valor == null ? null : valor.substring(0, Math.min(valor.length(), max)); }
    @Override public void destroy() throws Exception { http.close(); }
}
