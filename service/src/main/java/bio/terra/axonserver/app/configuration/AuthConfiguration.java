package bio.terra.axonserver.app.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "axonserver.auth")
public record AuthConfiguration(String clientId, String clientSecret) {}
