package com.proyecto.servicios.config;

import com.proyecto.servicios.model.cliente.ContrasenaSeguraValidator;
import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "auth.bootstrap-executive")
public class ExecutiveBootstrapProperties {
    private boolean enabled;
    private String email = "";
    private String password = "";

    @AssertTrue(message = "El ejecutivo inicial requiere correo valido y contrasena segura")
    public boolean isValidConfiguration() {
        return !enabled || (email != null && email.length() <= 100
                && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
                && ContrasenaSeguraValidator.esValida(password));
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
