package edu.upb.semillero.service.seguridad;

import edu.upb.semillero.service.comun.ComunService;
import edu.upb.semillero.service.comun.Roles;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.ForbiddenException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Tarea S2-03: restricciones por propiedad. El rol dice qué operaciones puede
 * hacer alguien (eso lo resuelve {@code SeguridadConfiguration}); esto dice
 * sobre qué registros.
 *
 * <ul>
 *   <li>El entrenador solo maneja los equipos de un club donde trabaja.</li>
 *   <li>La mesa de control solo los partidos que tiene asignados.</li>
 * </ul>
 *
 * <p>El organizador y el administrador pasan siempre: la HU 16 les da el torneo
 * completo.
 *
 * <p>Cada rechazo queda en {@code auditoria} con {@code ACCESO_DENEGADO}, igual
 * que los 401 y 403 que corta el filtro (tarea S2-04).
 */
@Component
public class AccesoTorneos {
    private final ComunService comun;
    private final RegistroAccesoService registro;

    public AccesoTorneos(ComunService comun, RegistroAccesoService registro) {
        this.comun = comun;
        this.registro = registro;
    }

    /** Vacío cuando no hay nadie autenticado, por ejemplo en una prueba o un job. */
    public Optional<UsuarioAutenticado> actual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null
                && autenticacion.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }

    /**
     * El entrenador solo sobre su club. Si el usuario no es entrenador, o manda
     * el organizador, no se mete.
     */
    public void requireClubPropio(Long clubId, String queIntento) {
        Optional<UsuarioAutenticado> actual = actual();
        if (actual.isEmpty() || mandaEnTodo(actual.get()) || !actual.get().tieneRol(Roles.ENTRENADOR)) {
            return;
        }
        UsuarioAutenticado usuario = actual.get();
        if (!comun.clubesDondeTrabaja(usuario.usuarioId()).contains(clubId)) {
            registro.rechazado("", queIntento, usuario.usuarioId(),
                    "El entrenador no trabaja en el club " + clubId);
            throw new ForbiddenException("Sos entrenador de otro club: solo podés manejar los"
                    + " equipos del club donde trabajás");
        }
    }

    /**
     * La mesa de control solo sobre los partidos que tiene asignados. La usa la
     * planilla de la HU 9 (tarea S3-02), que es del sprint siguiente.
     */
    public void requirePartidoAsignado(Long partidoId, Long mesaControlId) {
        Optional<UsuarioAutenticado> actual = actual();
        if (actual.isEmpty() || mandaEnTodo(actual.get())
                || !actual.get().tieneRol(Roles.MESA_CONTROL)) {
            return;
        }
        UsuarioAutenticado usuario = actual.get();
        if (mesaControlId == null || !mesaControlId.equals(usuario.usuarioId())) {
            registro.rechazado("", "/api/v1/torneos/partidos/" + partidoId, usuario.usuarioId(),
                    "La mesa de control no tiene asignado el partido " + partidoId);
            throw new ForbiddenException("El partido " + partidoId
                    + " no está asignado a tu mesa de control");
        }
    }

    /**
     * Tarea S4-10: de quién son los datos que se piden (notificaciones, "mi
     * cuenta"). Con token, del dueño del token: pedir los de otro da 403, salvo
     * al administrador. Sin token, que solo pasa en las pruebas con usuario de
     * mentira, del {@code usuarioId} que se manda.
     */
    public Long usuarioDeLaConsulta(Long pedido, String queIntento) {
        Optional<UsuarioAutenticado> actual = actual();
        if (actual.isEmpty()) {
            if (pedido == null) {
                throw new BusinessException("Falta usuarioId: sin token no se sabe de quién son los datos");
            }
            return pedido;
        }
        UsuarioAutenticado usuario = actual.get();
        if (pedido == null || pedido.equals(usuario.usuarioId()) || usuario.tieneRol(Roles.ADMIN)) {
            return pedido == null ? usuario.usuarioId() : pedido;
        }
        registro.rechazado("", queIntento, usuario.usuarioId(),
                "Pidió los datos del usuario " + pedido);
        throw new ForbiddenException("Solo podés ver tus propios datos");
    }

    private boolean mandaEnTodo(UsuarioAutenticado usuario) {
        return usuario.tieneRol(Roles.ORGANIZADOR) || usuario.tieneRol(Roles.ADMIN);
    }
}
