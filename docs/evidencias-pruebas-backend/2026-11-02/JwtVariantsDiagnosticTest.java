package edu.upb.semillero.config;

import edu.upb.semillero.service.seguridad.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tokens sintéticos; no utiliza secretos ni datos del VPS. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:jwt-variants-diagnostic;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "semillero.jwt.secret=clave-solo-del-diagnostico-local-de-tokens"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtVariantsDiagnosticTest {
    private static final String KEY = "clave-solo-del-diagnostico-local-de-tokens";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;

    private void rejected(String token) throws Exception {
        mvc.perform(get("/api/v1/torneos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
    private String emit(JwtService service) {
        return service.emitir(1L, "diagnostico-local", List.of("ADMIN"), null);
    }
    @Test void validSignedTokenCanReadProtectedEndpoint() throws Exception {
        mvc.perform(get("/api/v1/torneos").header("Authorization", "Bearer " + emit(jwt)))
                .andExpect(status().isOk());
    }
    @Test void expiredTokenReturns401() throws Exception {
        rejected(emit(new JwtService(KEY, -1, json)));
    }
    @Test void tokenSignedWithAnotherSecretReturns401() throws Exception {
        rejected(emit(new JwtService("otra-clave-de-prueba-completamente-distinta", 10, json)));
    }
    @Test void alteredPayloadWithoutNewSignatureReturns401() throws Exception {
        String[] parts=emit(jwt).split("\\.");
        parts[1]=Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"999\",\"roles\":[\"ADMIN\"],\"exp\":9999999999}".getBytes(StandardCharsets.UTF_8));
        rejected(String.join(".",parts));
    }
    @Test void unsupportedHeaderAlgorithmMustReturn401EvenWithValidHmac() throws Exception {
        String[] parts=emit(jwt).split("\\.");
        parts[0]=Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String unsigned=parts[0]+"."+parts[1];
        Mac mac=Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
        parts[2]=Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        rejected(String.join(".",parts));
    }
}
