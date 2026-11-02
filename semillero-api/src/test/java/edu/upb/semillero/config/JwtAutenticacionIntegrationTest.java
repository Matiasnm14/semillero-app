package edu.upb.semillero.config;

import edu.upb.semillero.service.seguridad.JwtService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jwt-regression;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "semillero.jwt.secret=clave-exclusiva-de-pruebas-de-autenticacion"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtAutenticacionIntegrationTest {
    private static final String KEY = "clave-exclusiva-de-pruebas-de-autenticacion";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtService jwt;

    @Test
    void unTokenEmitidoPorElBackendPermiteLeerLaRutaProtegida() throws Exception {
        mvc.perform(get("/api/v1/torneos").header("Authorization", "Bearer " + token(jwt)))
                .andExpect(status().isOk());
    }

    @Test
    void unTokenVencidoDevuelve401() throws Exception {
        rechazado(token(new JwtService(KEY, -1, json)));
    }

    @Test
    void unTokenFirmadoConOtraClaveDevuelve401() throws Exception {
        rechazado(token(new JwtService("otra-clave-solo-para-pruebas", 10, json)));
    }

    @Test
    void unPayloadAlteradoSinActualizarLaFirmaDevuelve401() throws Exception {
        String[] parts = token(jwt).split("\\.");
        parts[1] = base64("{\"sub\":\"999\",\"roles\":[\"ADMIN\"],\"exp\":9999999999}");
        rechazado(String.join(".", parts));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"alg\":\"none\"}", "{\"alg\":\"HS512\"}",
            "{\"alg\":\"RS256\"}", "{\"alg\":\"hs256\"}", "{\"alg\":256}",
            "[]", "null", "not-json"
    })
    void unaCabeceraNoPermitidaSeRechazaAunqueSuHmacSeaValido(String header) throws Exception {
        String[] parts = token(jwt).split("\\.");
        String unsigned = base64(header) + "." + parts[1];
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        rechazado(unsigned + "." + signature);
    }

    @Test
    void unSeparadorAdicionalNoConvierteElTokenEnValido() throws Exception {
        rechazado(token(jwt) + ".");
    }

    private void rechazado(String token) throws Exception {
        mvc.perform(get("/api/v1/torneos").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String token(JwtService service) {
        return service.emitir(1L, "regresion-local", List.of("ADMIN"), null);
    }

    private String base64(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
