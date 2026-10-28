package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.Notificacion;
import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.entity.UsuarioTrabaja;
import edu.upb.semillero.repository.entity.torneos.InscripcionEquipo;
import edu.upb.semillero.repository.entity.torneos.ListaBuenaFe;
import edu.upb.semillero.repository.repos.NotificacionRepository;
import edu.upb.semillero.repository.repos.torneos.InscripcionEquipoRepository;
import edu.upb.semillero.repository.repos.torneos.ListaBuenaFeRepository;
import edu.upb.semillero.repository.repos.torneos.VinculoClubConsultaRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Los avisos que torneos deja en {@code notificaciones} para la web y la app
 * móvil (punto 9 del acuerdo de la S1-04). Hoy lo usa la reprogramación (tarea
 * S3-19), el cierre para el resultado publicado y la HU 13 para la suspensión
 * (tarea S4-16).
 *
 * <p>"Avisar a un equipo" es avisar a:
 *
 * <ul>
 *   <li>los usuarios que trabajan en el club ({@code usuario_trabaja});</li>
 *   <li>el entrenador que inscribió al equipo en la categoría;</li>
 *   <li>los jugadores de su lista de buena fe que tienen cuenta, que es la
 *       misma cuenta con la que entran a la app (HU 14).</li>
 * </ul>
 *
 * <p>Cada usuario recibe un solo aviso aunque aparezca por varios lados.
 */
@Component
public class AvisosTorneos {
    private static final String MODULO = "TORNEOS";
    private static final int MAX_TITULO = 100;
    private static final int MAX_MENSAJE = 500;

    private final NotificacionRepository notificaciones;
    private final VinculoClubConsultaRepository vinculos;
    private final InscripcionEquipoRepository inscripciones;
    private final ListaBuenaFeRepository listas;

    public AvisosTorneos(NotificacionRepository notificaciones, VinculoClubConsultaRepository vinculos,
            InscripcionEquipoRepository inscripciones, ListaBuenaFeRepository listas) {
        this.notificaciones = notificaciones;
        this.vinculos = vinculos;
        this.inscripciones = inscripciones;
        this.listas = listas;
    }

    /** Avisa a los dos equipos del partido. Devuelve cuántos usuarios recibieron el aviso. */
    public int avisarALosEquipos(Partido partido, String titulo, String mensaje) {
        Map<Long, Usuario> destinatarios = new LinkedHashMap<>();
        for (Club club : List.of(partido.getClubLocal(), partido.getClubVisitante())) {
            destinatariosDe(partido, club).forEach(usuario ->
                    destinatarios.putIfAbsent(usuario.getId(), usuario));
        }
        LocalDateTime ahora = LocalDateTime.now();
        for (Usuario usuario : destinatarios.values()) {
            guardar(usuario, titulo, mensaje, ahora);
        }
        notificaciones.flush();
        return destinatarios.size();
    }

    /**
     * Avisa solo al jugador, si tiene cuenta: la suspensión de la HU 13, que la
     * app le muestra (HU 14). Devuelve si se avisó.
     */
    public boolean avisarAlJugador(Jugador jugador, String titulo, String mensaje) {
        if (jugador == null || jugador.getUsuario() == null) {
            return false;
        }
        guardar(jugador.getUsuario(), titulo, mensaje, LocalDateTime.now());
        notificaciones.flush();
        return true;
    }

    private void guardar(Usuario usuario, String titulo, String mensaje, LocalDateTime fecha) {
        Notificacion aviso = new Notificacion();
        aviso.setUsuario(usuario);
        aviso.setModulo(MODULO);
        aviso.setTitulo(recortar(titulo, MAX_TITULO));
        aviso.setMensaje(recortar(mensaje, MAX_MENSAJE));
        aviso.setLeida(false);
        aviso.setFecha(fecha);
        notificaciones.save(aviso);
    }

    private List<Usuario> destinatariosDe(Partido partido, Club club) {
        List<Usuario> usuarios = new ArrayList<>(vinculos.findByClubId(club.getId()).stream()
                .map(UsuarioTrabaja::getUsuarioClub)
                .filter(Objects::nonNull)
                .map(usuarioClub -> usuarioClub.getUsuario())
                .filter(Objects::nonNull)
                .toList());
        if (partido.getTorneoCategoria() == null) {
            return usuarios;
        }
        inscripciones.findByTorneoCategoriaIdAndClubId(partido.getTorneoCategoria().getId(), club.getId())
                .ifPresent(inscripcion -> {
                    usuarios.add(inscripcion.getUsuario());
                    usuarios.addAll(jugadoresConCuenta(inscripcion));
                });
        return usuarios;
    }

    private List<Usuario> jugadoresConCuenta(InscripcionEquipo inscripcion) {
        return listas.findByInscripcionEquipoIdOrderByDorsalAsc(inscripcion.getId()).stream()
                .map(ListaBuenaFe::getJugador)
                .map(jugador -> jugador.getUsuario())
                .filter(Objects::nonNull)
                .toList();
    }

    private String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo - 3) + "...";
    }
}
