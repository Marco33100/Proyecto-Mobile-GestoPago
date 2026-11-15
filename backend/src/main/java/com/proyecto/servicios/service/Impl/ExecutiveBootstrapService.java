package com.proyecto.servicios.service.Impl;

import com.proyecto.servicios.config.ExecutiveBootstrapProperties;
import com.proyecto.servicios.entity.sf.RolUsuario;
import com.proyecto.servicios.entity.sf.UserEntity;
import com.proyecto.servicios.repositorys.sf.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class ExecutiveBootstrapService {
    private final UserRepository usuarios;
    private final PasswordEncoder encoder;
    private final ExecutiveBootstrapProperties properties;

    @Autowired
    public ExecutiveBootstrapService(UserRepository usuarios, PasswordEncoder encoder,
                                     ExecutiveBootstrapProperties properties) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.properties = properties;
    }

    @Transactional(transactionManager = "sfTransactionManager")
    public void crearSiSeSolicita() {
        if (!properties.isEnabled()) return;
        if (!properties.isValidConfiguration()) {
            throw new IllegalStateException("Configuracion invalida del ejecutivo inicial");
        }
        try {
            if (usuarios.existsByRolAndEnabledTrue(RolUsuario.EJECUTIVO)) return;
            String correo = properties.getEmail().toLowerCase(Locale.ROOT);
            if (usuarios.existsByEmailOrIdentifier(correo)) {
                throw new IllegalStateException("Use un correo nuevo para el ejecutivo inicial; no se elevan usuarios existentes");
            }
            usuarios.saveAndFlush(UserEntity.crearEjecutivo(correo,
                    encoder.encode(properties.getPassword()), Instant.now()));
            log.info("Ejecutivo inicial creado sin modificar usuarios existentes");
        } catch (DataAccessException exception) {
            log.error("Error al crear ejecutivo inicial: {}", exception.getClass().getSimpleName());
            throw new IllegalStateException("No fue posible crear el ejecutivo inicial");
        }
    }
}
