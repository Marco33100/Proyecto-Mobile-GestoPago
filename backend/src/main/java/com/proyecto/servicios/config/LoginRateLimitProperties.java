package com.proyecto.servicios.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Component
@Validated
@ConfigurationProperties(prefix = "auth.login-rate-limit")
public class LoginRateLimitProperties {
    @Min(1) @Max(1000)
    private int addressAttempts = 30;
    @Min(1) @Max(100)
    private int accountAttempts = 5;
    @NotNull
    private Duration addressWindow = Duration.ofMinutes(1);
    @NotNull
    private Duration accountWindow = Duration.ofMinutes(5);
    @Min(1) @Max(100000)
    private int maxTrackedKeys = 10000;
}
