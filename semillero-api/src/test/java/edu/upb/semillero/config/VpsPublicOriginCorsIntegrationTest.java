package edu.upb.semillero.config;

import edu.upb.semillero.service.seguridad.JwtService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reproduce HTTPS público con Spring recibiendo HTTP local y orígenes extra vacíos. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:vps-public-cors;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "semillero.jwt.secret=clave-solo-para-pruebas-de-cors-publico",
        "PUBLIC_WEB_ORIGIN=https://semillero.mandarina-group.cc",
        "CORS_ALLOWED_ORIGINS="
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "vps"})
class VpsPublicOriginCorsIntegrationTest {
    private static final String ORIGIN = "https://semillero.mandarina-group.cc";
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;

    @Test
    void laConsultaDesdeHttpsPublicoLlegaPorHttpLocalSinRechazoCors() throws Exception {
        mvc.perform(proxy(get("/api/v1/paises")).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @Test
    void elLoginIncompletoDevuelveValidacionEnLugarDeErrorCors() throws Exception {
        mvc.perform(proxy(post("/api/v1/auth/login")).header(HttpHeaders.ORIGIN, ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/auth/login, POST",
            "/api/v1/torneos, GET",
            "/api/v1/highlights/1, PATCH"
    })
    void elPreflightPublicoPermiteLasOperacionesSinPedirToken(String route, String method) throws Exception {
        mvc.perform(proxy(options(route)).header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @Test
    void elOrigenPublicoNoPermiteLeerUnaRutaProtegidaSinJwt() throws Exception {
        mvc.perform(proxy(get("/api/v1/torneos")).header(HttpHeaders.ORIGIN, ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @Test
    void elOrigenPublicoConservaLasRestriccionesDelRolConsulta() throws Exception {
        String token = jwt.emitir(999_999L, "cors-consulta", List.of("CONSULTA"), null);
        mvc.perform(proxy(get("/api/v1/torneos")).header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(proxy(post("/api/v1/torneos")).header(HttpHeaders.ORIGIN, ORIGIN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:3000", "https://externo.example",
            "https://semillero.mandarina-group.cc.externo.example"
    })
    void unOrigenAjenoNoSeAutorizaFalsificandoCabecerasDelProxy(String origin) throws Exception {
        mvc.perform(proxy(get("/api/v1/paises")).header(HttpHeaders.ORIGIN, origin)
                        .header("X-Forwarded-Host", origin.substring(origin.indexOf("://") + 3)))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private MockHttpServletRequestBuilder proxy(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.HOST, "semillero.mandarina-group.cc")
                .header("X-Forwarded-Proto", "https")
                .with(value -> {
                    value.setScheme("http");
                    value.setServerName("127.0.0.1");
                    value.setServerPort(18083);
                    value.setRemoteAddr("127.0.0.1");
                    return value;
                });
    }
}
