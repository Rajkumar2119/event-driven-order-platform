package com.rajkumar.orders.order.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Issues test tokens so the API can be tried locally without an identity provider.
 * Only active with the "demo" profile (set in docker-compose); never enable it in production.
 */
@RestController
@Profile("demo")
public class DevTokenController {

    private final JwtEncoder encoder;

    public DevTokenController(SecretKey jwtSigningKey) {
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
    }

    @PostMapping("/dev/token")
    public Map<String, Object> token(@RequestParam(defaultValue = "customer-1") String customerId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("order-platform-demo")
                .subject(customerId)
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .claim("scope", "orders:read orders:write")
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return Map.of("access_token", token, "token_type", "Bearer", "expires_in", 3600);
    }
}
