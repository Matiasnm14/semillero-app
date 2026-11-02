package edu.upb.semillero.service.torneos;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * Tarea S4-09: le avisa al microservicio de video que un partido se cerró,
 * con un {@code POST} a {@code semillero.torneos.partido-finalizado-url} y el
 * cuerpo {@code {"partidoId":..,"fechaCierre":".."}}.
 *
 * <ul>
 *   <li>Sale recién cuando la transacción del cierre se confirmó: si el cierre
 *       falla, no se avisa de un partido que no se cerró.</li>
 *   <li>Es asíncrono y nunca falla el cierre: si el microservicio no responde,
 *       queda en el log y él lee los partidos por {@code fecha_cierre} (punto
 *       13 del acuerdo).</li>
 *   <li>Sin URL configurada no hace nada, que es lo que pasa en desarrollo y
 *       en las pruebas.</li>
 * </ul>
 */
@Component
public class AvisoPartidoFinalizado {
    private static final Logger log = LoggerFactory.getLogger(AvisoPartidoFinalizado.class);

    private final String url;
    private final String token;
    private final ObjectMapper json;
    private final HttpClient http;

    public AvisoPartidoFinalizado(
            @Value("${semillero.torneos.partido-finalizado-url:}") String url,
            @Value("${semillero.torneos.partido-finalizado-token:}") String token,
            ObjectMapper json) {
        this.url = url == null ? "" : url.trim();
        this.token = token == null ? "" : token.trim();
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCerrarse(PartidoFinalizado evento) {
        if (url.isEmpty()) {
            return;
        }
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(evento)));
        if (!token.isEmpty()) {
            pedido.header("Authorization", "Bearer " + token);
        }
        http.sendAsync(pedido.build(), HttpResponse.BodyHandlers.discarding())
                .whenComplete((respuesta, error) -> {
                    if (error != null) {
                        log.warn("No se pudo avisar al microservicio de video que se cerró el partido"
                                + " {}: {}", evento.partidoId(), error.getMessage());
                    } else if (respuesta.statusCode() >= 300) {
                        log.warn("El microservicio de video respondió {} al cierre del partido {}",
                                respuesta.statusCode(), evento.partidoId());
                    }
                });
    }
}
