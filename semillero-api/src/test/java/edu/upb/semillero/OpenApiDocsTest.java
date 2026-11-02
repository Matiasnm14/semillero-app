package edu.upb.semillero;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tarea S1-02: comprueba que springdoc publica el documento OpenAPI y que los
 * endpoints quedan documentados, que es lo que pide la definición de terminado.
 *
 * <p>springdoc 2.8.6 apunta a Spring Boot 3 y acá corre sobre Spring Boot 4.1.1:
 * no hay versión para Boot 4 todavía. Esta prueba es la que avisa si una
 * actualización de Spring Boot lo rompe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {
    @Autowired MockMvc mvc;

    @Test
    void publicaElDocumentoOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths").exists());
    }

    @Test
    void documentaLosEndpointsDeTorneos() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/torneos']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/{id}/abrir-inscripciones']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/{torneoId}/reglamento/desempates']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/sedes']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/canchas']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/inscripciones']").exists());
    }

    @Test
    void documentaTambienLosEndpointsDeLosOtrosGrupos() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/partidos']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/jugadores']").exists());
    }

    /** Tarea S4-08: los contratos que consumen el video, la web y la app (HU 15). */
    @Test
    void documentaLosContratosDeLosSprints3Y4() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/{torneoId}/categorias/{categoriaId}/tabla']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/{torneoId}/categorias/{categoriaId}/estadisticas']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/{torneoId}/jugadores/{jugadorId}/estadisticas']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/{torneoId}/sanciones']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/jugadores/{jugadorId}/sanciones']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/partidos/{partidoId}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/partidos/finalizados']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/partidos/{partidoId}/cerrar']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/mi-cuenta']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/torneos/notificaciones']").exists());
    }

    /** Tarea S4-08: el token se documenta como esquema y lo piden solo las rutas de torneos. */
    @Test
    void documentaElTokenSoloEnLasRutasDeTorneos() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/torneos'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/partidos'].get.security").doesNotExist())
                .andExpect(jsonPath("$.info.title").value("SEMILLERO API"));
    }

    /** Tarea S4-09: el cuerpo de PartidoFinalizado queda en el contrato aunque no salga de un endpoint. */
    @Test
    void documentaElEventoPartidoFinalizado() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.PartidoFinalizado.properties.partidoId").exists())
                .andExpect(jsonPath("$.components.schemas.PartidoFinalizado.properties.fechaCierre").exists())
                .andExpect(jsonPath("$.components.schemas.PartidoFinalizado.required.length()").value(2));
    }
}
