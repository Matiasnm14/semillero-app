package edu.upb.semillero.config;

import edu.upb.semillero.service.comun.Roles;
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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flutter Web debe poder consultar los permisos CORS antes de enviar su JWT. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WebCorsIntegrationTest {
    private static final String TORNEOS = "/api/v1/torneos";

    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;

    @ParameterizedTest
    @CsvSource({
            "http://localhost:3000, /api/v1/auth/login, POST",
            "http://localhost:3000, /api/v1/torneos, GET",
            "http://127.0.0.1:5050, /api/v1/administradores, POST",
            "http://127.0.0.1:5050, /api/v1/usuarios/1/roles, PUT"
    })
    void elPreflightDeRutasPublicasYProtegidasNoPideToken(String origen, String ruta,
            String metodo) throws Exception {
        mvc.perform(options(ruta)
                        .header(HttpHeaders.ORIGIN, origen)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, metodo)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origen))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString(metodo)))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        "Authorization, Content-Type"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:3000", "http://127.0.0.1:5050"})
    void laPeticionRealSiguePidiendoTokenYPermiteLeerElErrorDesdeLaWeb(String origen)
            throws Exception {
        mvc.perform(get(TORNEOS).header(HttpHeaders.ORIGIN, origen))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origen));
    }

    @Test
    void unTokenInvalidoConservaElErrorDeAutenticacionYLasCabecerasCors() throws Exception {
        String origen = "http://localhost:3000";
        mvc.perform(get(TORNEOS).header(HttpHeaders.ORIGIN, origen)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer no.es.un.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origen));
    }

    @Test
    void laWebPuedeLeerConJwtPeroNoEscribirConRolConsulta() throws Exception {
        String origen = "http://localhost:3000";
        String token = jwt.emitir(999_999L, "cors-consulta", List.of(Roles.CONSULTA), null);
        mvc.perform(get(TORNEOS).header(HttpHeaders.ORIGIN, origen)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origen));
        mvc.perform(post(TORNEOS).header(HttpHeaders.ORIGIN, origen)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origen));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://externo.example",
            "http://localhost.externo.example:3000",
            "http://127.0.0.1.externo.example:3000"
    })
    void unOrigenNoPermitidoNoPuedeHacerPreflightNiPeticionesReales(String origen)
            throws Exception {
        mvc.perform(options(TORNEOS).header(HttpHeaders.ORIGIN, origen)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mvc.perform(get(TORNEOS).header(HttpHeaders.ORIGIN, origen))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
