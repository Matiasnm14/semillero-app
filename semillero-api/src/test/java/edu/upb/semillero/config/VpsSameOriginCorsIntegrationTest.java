package edu.upb.semillero.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:semillero-vps-same-origin;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
        "semillero.jwt.secret=secreto-solo-para-pruebas-del-perfil-vps",
        "semillero.cors.allowed-origin-patterns="
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "vps"})
class VpsSameOriginCorsIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void laApiRespondeSinNecesitarUnOrigenExterno() throws Exception {
        mvc.perform(get("/api/v1/paises")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://web.semillero.example", "http://localhost:3000", "https://externo.example"})
    void unaListaVaciaNoHabilitaOrigenesConComodin(String origin) throws Exception {
        mvc.perform(options("/api/v1/torneos")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
