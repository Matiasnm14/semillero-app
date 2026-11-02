package edu.upb.semillero.service.seguridad;

import edu.upb.semillero.repository.repos.FichadoRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.service.comun.ComunService;
import edu.upb.semillero.service.comun.Roles;
import edu.upb.semillero.service.exception.ForbiddenException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * HU 16: quién puede administrar los documentos de un jugador. El rol dice que
 * puede entrar al recurso (lo exige {@code SeguridadConfiguration}); esto dice
 * sobre qué jugador.
 *
 * <ul>
 *   <li>un administrador (rol ADMIN) con los de cualquier jugador;</li>
 *   <li>el propio jugador con los suyos;</li>
 *   <li>el personal de un club con los jugadores que tiene fichados
 *       actualmente.</li>
 * </ul>
 *
 * <p>Aprobar un documento a VIGENTE queda reservado al administrador: subir un
 * documento no lo aprueba. Es la política inicial conservadora hasta que los
 * equipos definan otros validadores.
 *
 * <p>Igual que {@link AccesoTorneos}, la propiedad solo se comprueba cuando hay
 * un {@link UsuarioAutenticado} real (producción y pruebas con token). Sin él
 * —solo en las pruebas con usuario de mentira— no se restringe.
 */
@Service
@Transactional(readOnly = true)
public class AccesoDocumentos {
    private final ComunService comun;
    private final JugadorRepository jugadores;
    private final FichadoRepository fichados;
    private final RegistroAccesoService registro;

    public AccesoDocumentos(ComunService comun, JugadorRepository jugadores,
            FichadoRepository fichados, RegistroAccesoService registro) {
        this.comun = comun;
        this.jugadores = jugadores;
        this.fichados = fichados;
        this.registro = registro;
    }

    /** Vacío cuando no hay un usuario real autenticado (por ejemplo en una prueba). */
    public Optional<UsuarioAutenticado> actual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UsuarioAutenticado usuario) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }

    public boolean esAdministrador() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + Roles.ADMIN).equals(a.getAuthority()));
    }

    /** Aprobar a VIGENTE es solo del administrador (política inicial). */
    public boolean puedeAprobar() {
        return esAdministrador() || actual().isEmpty();
    }

    public boolean puedeAdministrar(Long jugadorId) {
        if (jugadorId == null) {
            return false;
        }
        if (esAdministrador()) {
            return true;
        }
        Optional<UsuarioAutenticado> actual = actual();
        if (actual.isEmpty()) {
            return true;                       // pruebas con usuario de mentira
        }
        Long actor = actual.get().usuarioId();
        if (actor == null || !comun.usuarioExiste(actor)) {
            return false;
        }
        // El propio jugador maneja sus documentos.
        if (jugadores.findFirstByUsuarioId(actor)
                .map(jugador -> jugadorId.equals(jugador.getId())).orElse(false)) {
            return true;
        }
        // El personal de un club maneja los de sus jugadores con fichaje vigente.
        for (Long clubId : comun.clubesDondeTrabaja(actor)) {
            if (fichados.existsByJugadorIdAndClubIdAndFechaFinalFichajeIsNull(jugadorId, clubId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Para acotar el listado sin filtrarlo dejaría ver documentos ajenos:
     * {@code null} significa todos (administrador o prueba); si no, el conjunto
     * de jugadores cuyos documentos puede ver el usuario.
     */
    public Set<Long> jugadoresAdministrables() {
        if (esAdministrador() || actual().isEmpty()) {
            return null;
        }
        Set<Long> ids = new HashSet<>();
        Long actor = actual().get().usuarioId();
        if (actor == null || !comun.usuarioExiste(actor)) {
            return ids;
        }
        jugadores.findFirstByUsuarioId(actor).ifPresent(jugador -> ids.add(jugador.getId()));
        Set<Long> clubes = comun.clubesDondeTrabaja(actor);
        if (!clubes.isEmpty()) {
            fichados.findByClubIdInAndFechaFinalFichajeIsNull(clubes)
                    .forEach(fichado -> ids.add(fichado.getJugador().getId()));
        }
        return ids;
    }

    public void requireAdministrar(String metodo, String ruta, Long jugadorId) {
        if (puedeAdministrar(jugadorId)) {
            return;
        }
        Long actor = actual().map(UsuarioAutenticado::usuarioId).orElse(null);
        registro.rechazado(metodo, ruta, actor,
                "Sin acceso a los documentos del jugador " + jugadorId);
        throw new ForbiddenException(
                "No tienes permiso para administrar los documentos de este jugador");
    }

    public void requireAprobacion(String metodo, String ruta) {
        if (puedeAprobar()) {
            return;
        }
        Long actor = actual().map(UsuarioAutenticado::usuarioId).orElse(null);
        registro.rechazado(metodo, ruta, actor,
                "Sin permiso para aprobar un documento a VIGENTE");
        throw new ForbiddenException(
                "Solo un administrador puede aprobar un documento como VIGENTE");
    }
}
