package com.proyecto.servicios.service.Impl;

import static com.proyecto.servicios.support.ClienteDatos.normalizarTexto;

import com.proyecto.servicios.exception.cliente.ClienteValidationException;
import com.proyecto.servicios.model.cliente.CatalogosRegistroResponse;
import com.proyecto.servicios.service.CatalogoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;

@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class CatalogoServiceImpl implements CatalogoService {

    private final JdbcTemplate jdbc;

    @Autowired
    public CatalogoServiceImpl(@Qualifier("sfDatasource") DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public CatalogosRegistroResponse catalogosRegistro() {
        return new CatalogosRegistroResponse(
                lista("SELECT nombre FROM cat_paises ORDER BY nombre"),
                lista("SELECT DISTINCT nacionalidad FROM cat_paises WHERE nacionalidad IS NOT NULL ORDER BY nacionalidad"),
                lista("SELECT nombre FROM cat_ocupaciones ORDER BY id"));
    }

    @Override
    public void validarSeleccion(String nacionalidad, String pais, String ocupacion) {
        if (nacionalidad != null) {
            exigir("SELECT EXISTS (SELECT 1 FROM cat_paises WHERE nacionalidad = ?)", nacionalidad,
                    "La nacionalidad debe elegirse del catalogo");
        }
        exigir("SELECT EXISTS (SELECT 1 FROM cat_paises WHERE nombre = ?)", pais,
                "El pais debe elegirse del catalogo");
        exigir("SELECT EXISTS (SELECT 1 FROM cat_ocupaciones WHERE nombre = ?)", ocupacion,
                "La ocupacion debe elegirse del catalogo");
    }

    private List<String> lista(String sql) {
        return jdbc.queryForList(sql, String.class);
    }

    private void exigir(String sql, String valor, String mensaje) {
        if (valor == null || !Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, normalizarTexto(valor)))) {
            throw new ClienteValidationException(mensaje);
        }
    }
}
