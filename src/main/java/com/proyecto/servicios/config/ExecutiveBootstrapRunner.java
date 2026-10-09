package com.proyecto.servicios.config;

import com.proyecto.servicios.service.Impl.ExecutiveBootstrapService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.database.enabled", havingValue = "true")
public class ExecutiveBootstrapRunner implements ApplicationRunner {
    private final ExecutiveBootstrapService service;
    public ExecutiveBootstrapRunner(ExecutiveBootstrapService service) { this.service = service; }
    @Override public void run(ApplicationArguments args) { service.crearSiSeSolicita(); }
}
