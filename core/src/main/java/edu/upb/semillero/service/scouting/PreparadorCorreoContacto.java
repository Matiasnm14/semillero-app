package edu.upb.semillero.service.scouting;

import edu.upb.semillero.repository.entity.scouting.CorreoContacto;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.repository.repos.scouting.*;
import edu.upb.semillero.repository.enums.scouting.EstadoContacto;
import edu.upb.semillero.service.exception.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Puerto de preparación para el futuro adaptador SMTP/API. No realiza envío de red. */
@Service @Transactional(readOnly = true)
public class PreparadorCorreoContacto {
    private final CorreoContactoRepository correos;
    private final UsuarioRepository usuarios;
    private final PreferenciaContactoRepository preferencias;
    private final String remitente;
    private final String urlPublica;
    public PreparadorCorreoContacto(CorreoContactoRepository correos, UsuarioRepository usuarios,
            PreferenciaContactoRepository preferencias,
            @Value("${scouting.correo.remitente:}") String remitente,
            @Value("${scouting.correo.url-publica:}") String urlPublica) {
        this.correos = correos; this.usuarios = usuarios; this.preferencias = preferencias;
        this.remitente = remitente; this.urlPublica = urlPublica;
    }
    public record CorreoPreparado(UUID id, String de, String para, String responderA, String asunto, String texto) {}
    public CorreoPreparado preparar(UUID id) {
        if (remitente.isBlank() || remitente.contains("\r") || remitente.contains("\n")
                || !remitente.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+([.][A-Za-z0-9-]+)+"))
            throw new BusinessException("Configura el correo del dominio de la plataforma");
        URI base;
        try { base = URI.create(urlPublica); } catch (IllegalArgumentException e) {
            throw new BusinessException("Configura la URL pública HTTPS del frontend");
        }
        if (!"https".equals(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null)
            throw new BusinessException("Configura la URL pública HTTPS del frontend");
        var correo = correos.findById(id).orElseThrow(() -> new NotFoundException("Correo no disponible"));
        var s = correo.getEvento().getSolicitud();
        if (correo.getPlantilla().equals("INVITACION_JUGADOR")) {
            var p = preferencias.findById(s.getJugadorId());
            if (s.getEstado() != EstadoContacto.PENDIENTE || p.isEmpty() || !p.get().isAceptaSolicitudes() || !p.get().isVisible())
                throw new ConflictException("La invitación ya no está vigente");
        }
        var destinatario = usuarios.findById(correo.getDestinatarioId())
            .orElseThrow(() -> new ConflictException("El destinatario ya no existe"));
        String email = destinatario.getEmail();
        if (email == null || !email.matches("[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+([.][A-Za-z0-9-]+)+") || email.contains("\r") || email.contains("\n"))
            throw new ConflictException("El destinatario no tiene un correo válido");
        String titulo = switch (correo.getPlantilla()) {
            case "INVITACION_JUGADOR" -> "Un club quiere contactarte";
            case "CONSTANCIA_CLUB" -> "Solicitud de contacto registrada";
            case "RESULTADO_SOLICITUD" -> "Tu solicitud de contacto tiene una respuesta";
            default -> throw new BusinessException("Plantilla de correo desconocida");
        };
        String enlace = urlPublica.replaceAll("/+$", "") + "/contactos/solicitudes/" + s.getId();
        // Sin CC y sin texto libre ni datos privados. El enlace requiere el JWT y propiedad.
        return new CorreoPreparado(id, remitente, email, remitente, titulo,
            titulo + ". Revisa los detalles e inicia sesión en la plataforma: " + enlace
            + " . Esta notificación no confirma una incorporación deportiva.");
    }
}
