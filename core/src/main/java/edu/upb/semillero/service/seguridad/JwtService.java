package edu.upb.semillero.service.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Tarea S2-01: emite y valida el JWT del login común.
 *
 * <p>Es un JWT firmado con HS256, armado con el JDK (HMAC y Base64) y el
 * Jackson que ya usa el proyecto, para no sumarle otra dependencia al
 * {@code pom.xml}, que es compartido entre los cuatro grupos.
 *
 * <p>El secreto se configura con {@code semillero.jwt.secret} y en producción
 * va por la variable de entorno {@code JWT_SECRET}: nunca en Git.
 */
@Service
public class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final String CABECERA = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String ALGORITMO = "HmacSHA256";

    private final byte[] clave;
    private final long duracionMinutos;
    private final ObjectMapper json;

    public JwtService(@Value("${semillero.jwt.secret}") String secreto,
            @Value("${semillero.jwt.duracion-minutos:480}") long duracionMinutos,
            ObjectMapper json) {
        this.clave = secreto.getBytes(StandardCharsets.UTF_8);
        this.duracionMinutos = duracionMinutos;
        this.json = json;
    }

    /** El token que devuelve el login: quién es, qué roles tiene y de qué club. */
    public String emitir(Long usuarioId, String name, List<String> roles, Long clubId) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("sub", String.valueOf(usuarioId));
        cuerpo.put("name", name);
        cuerpo.put("roles", roles);
        if (clubId != null) {
            cuerpo.put("clubId", clubId);
        }
        cuerpo.put("exp", Instant.now().plusSeconds(duracionMinutos * 60).getEpochSecond());

        String sinFirma = base64(CABECERA.getBytes(StandardCharsets.UTF_8))
                + "." + base64(json.writeValueAsBytes(cuerpo));
        return sinFirma + "." + base64(firmar(sinFirma));
    }

    /** Vacío si el token está mal formado, mal firmado o vencido. */
    public Optional<UsuarioAutenticado> validar(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] partes = token.split("\\.");
        if (partes.length != 3) {
            return Optional.empty();
        }
        String sinFirma = partes[0] + "." + partes[1];
        try {
            if (!MessageDigest.isEqual(Base64.getUrlDecoder().decode(partes[2]),
                    firmar(sinFirma))) {
                return Optional.empty();
            }
            JsonNode cuerpo = json.readTree(Base64.getUrlDecoder().decode(partes[1]));
            if (cuerpo.path("exp").asLong(0) < Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            List<String> roles = new ArrayList<>();
            cuerpo.path("roles").forEach(rol -> roles.add(rol.asString()));
            Long clubId = cuerpo.has("clubId") ? cuerpo.get("clubId").asLong() : null;
            return Optional.of(new UsuarioAutenticado(
                    Long.valueOf(cuerpo.path("sub").asString()),
                    cuerpo.path("name").asString(),
                    List.copyOf(roles),
                    clubId));
        } catch (RuntimeException e) {
            log.debug("Token inválido: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private byte[] firmar(String contenido) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(clave, ALGORITMO));
            return mac.doFinal(contenido.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo firmar el token", e);
        }
    }

    private String base64(byte[] contenido) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(contenido);
    }
}
