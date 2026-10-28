package edu.upb.semillero.service.seguridad;

import edu.upb.semillero.repository.entity.Auditoria;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.repos.AuditoriaRepository;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tarea S2-04: cada acceso rechazado queda registrado en {@code auditoria}, que
 * es lo que pide la HU 16 ("las acciones no permitidas se rechazan y se
 * registran") y la regla 9 del contexto.
 *
 * <p>Se guarda el método y la ruta, el usuario si el token era válido, y el
 * motivo: sin token (401) o sin permiso (403). No se guarda el token.
 */
@Service
public class RegistroAccesoService {
    private static final String TABLA = "api";
    private static final String ACCION = "ACCESO_DENEGADO";

    private final AuditoriaRepository auditoria;
    private final UsuarioRepository usuarios;

    public RegistroAccesoService(AuditoriaRepository auditoria, UsuarioRepository usuarios) {
        this.auditoria = auditoria;
        this.usuarios = usuarios;
    }

    /**
     * En su propia transacción: cuando el rechazo lo levanta un servicio, la
     * transacción de ese servicio se deshace, y el registro del acceso tiene que
     * quedar igual.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rechazado(String metodo, String ruta, Long usuarioId, String motivo) {
        Auditoria fila = new Auditoria();
        fila.setModulo(ruta != null && ruta.startsWith("/api/v1/torneos") ? "TORNEOS" : "COMUN");
        fila.setTabla(TABLA);
        fila.setAccion(ACCION);
        fila.setValorNuevo("{\"metodo\":\"%s\",\"ruta\":\"%s\"}".formatted(metodo, ruta));
        fila.setMotivo(motivo);
        fila.setUsuario(usuarioId == null ? null : usuarios.findById(usuarioId).orElse(null));
        fila.setFecha(LocalDateTime.now());
        auditoria.saveAndFlush(fila);
    }

    /** Para que el handler no tenga que cargar el usuario. */
    public Usuario usuario(Long usuarioId) {
        return usuarioId == null ? null : usuarios.findById(usuarioId).orElse(null);
    }
}
