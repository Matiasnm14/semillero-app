package edu.upb.semillero.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * HU 15 - Tarea S4-08: el contrato de la API de torneos en OpenAPI, para la
 * web, la app móvil y el microservicio de video.
 *
 * <ul>
 *   <li>Declara el esquema de seguridad {@code bearerAuth} (el JWT de
 *       {@code POST /api/v1/auth/login}) y lo pide solo en las rutas de
 *       {@code /api/v1/torneos}: las de los otros módulos siguen como las
 *       dejó cada grupo.</li>
 *   <li>Publica el esquema {@code PartidoFinalizado}, que no sale de ningún
 *       endpoint porque es el cuerpo que torneos le manda al microservicio
 *       de video al cerrar un partido (tarea S4-09).</li>
 * </ul>
 *
 * <p>Los endpoints se agrupan por HU con las etiquetas de cada controlador.
 * El documento queda en {@code /v3/api-docs} y la interfaz en
 * {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiTorneosConfiguration {
    static final String SEGURIDAD = "bearerAuth";
    private static final String TORNEOS = "/api/v1/torneos";

    @Bean
    OpenAPI semilleroOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SEMILLERO API")
                        .version("v1")
                        .description("Sistema de gestión para escuelas de fútbol. Las rutas de "
                                + TORNEOS + " piden el JWT de POST /api/v1/auth/login en "
                                + "Authorization: Bearer; un token que falta, no es válido o "
                                + "venció da 401, y un rol sin permiso da 403. Las respuestas "
                                + "llevan IDs, nunca datos personales."))
                .components(new Components()
                        .addSecuritySchemes(SEGURIDAD, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("El token de POST /api/v1/auth/login")));
    }

    /**
     * Solo las operaciones de torneos llevan el candado del token. Acá también
     * va el esquema de {@code PartidoFinalizado}: springdoc arma los esquemas
     * de nuevo con los DTO de los endpoints, así que uno agregado en el bean
     * de arriba se pierde.
     */
    @Bean
    OpenApiCustomizer seguridadDeTorneos() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }
            openApi.getComponents().addSchemas("PartidoFinalizado", new ObjectSchema()
                    .description("Lo que torneos le manda por POST al microservicio de video al"
                            + " cerrar un partido (punto 13 del acuerdo). Si no le llega, lee GET "
                            + TORNEOS + "/partidos/finalizados?desde=")
                    .addProperty("partidoId", new IntegerSchema().format("int64"))
                    .addProperty("fechaCierre", new DateTimeSchema())
                    .required(List.of("partidoId", "fechaCierre")));
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((ruta, item) -> {
                if (ruta.startsWith(TORNEOS)) {
                    operaciones(item).forEach(operacion ->
                            operacion.addSecurityItem(new SecurityRequirement().addList(SEGURIDAD)));
                }
            });
        };
    }

    private List<Operation> operaciones(PathItem item) {
        return Stream.of(item.getGet(), item.getPost(), item.getPut(),
                        item.getDelete(), item.getPatch())
                .filter(Objects::nonNull)
                .toList();
    }
}
