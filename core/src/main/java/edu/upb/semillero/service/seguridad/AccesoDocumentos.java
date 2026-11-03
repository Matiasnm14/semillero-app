package edu.upb.semillero.service.seguridad;

import edu.upb.semillero.repository.repos.FichadoRepository;
import edu.upb.semillero.repository.repos.JugadorRepository;
import edu.upb.semillero.service.comun.ComunService;
import edu.upb.semillero.service.comun.Roles;
import edu.upb.semillero.service.exception.ForbiddenException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * <p>Las operaciones requieren una identidad autenticada también cuando el
 * servicio se invoca directamente, fuera de la cadena HTTP.
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

    /** Vacío si falta autenticación o el principal no representa una identidad válida. */
    public Optional<UsuarioAutenticado> actual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && auth.getPrincipal() instanceof UsuarioAutenticado usuario
                && usuario.usuarioId() != null && usuario.usuarioId() > 0) {
            return Optional.of(usuario);
        }
        return Optional.empty();
    }

    public boolean esAdministrador() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return actual().isPresent() && auth.getAuthorities().stream()
                .anyMatch(a -> ("ROLE_" + Roles.ADMIN).equals(a.getAuthority()));
    }

    /** Aprobar a VIGENTE es solo del administrador (política inicial). */
    public boolean puedeAprobar() {
        return esAdministrador();
    }

    public boolean puedeAdministrar(Long jugadorId) {
        if (jugadorId == null) {
            return false;
        }
        Optional<UsuarioAutenticado> actual = actual();
        if (actual.isEmpty()) {
            return false;
        }
        if (esAdministrador()) {
            return true;
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
        return jugadoresFichadosVigentes(actor).contains(jugadorId);
    }

    /**
     * Para acotar el listado sin filtrarlo dejaría ver documentos ajenos:
     * {@code null} significa todos (administrador); si no, el conjunto
     * de jugadores cuyos documentos puede ver el usuario.
     */
    public Set<Long> jugadoresAdministrables() {
        UsuarioAutenticado usuario = actual().orElseThrow(() -> {
            registro.rechazado("GET", "/api/v1/documentos-jugador", null,
                    "Falta una identidad autenticada para consultar documentos");
            return new ForbiddenException("Debes iniciar sesión para consultar documentos");
        });
        if (esAdministrador()) {
            return null;
        }
        Set<Long> ids = new HashSet<>();
        Long actor = usuario.usuarioId();
        if (actor == null || !comun.usuarioExiste(actor)) {
            return ids;
        }
        jugadores.findFirstByUsuarioId(actor).ifPresent(jugador -> ids.add(jugador.getId()));
        ids.addAll(jugadoresFichadosVigentes(actor));
        return ids;
    }

    private Set<Long> jugadoresFichadosVigentes(Long actor) {
        Set<Long> clubes = comun.clubesDondeTrabaja(actor);
        if (clubes.isEmpty()) return Set.of();
        LocalDate hoy = LocalDate.now();
        return fichados.findJugadorIdsVigentesEnClubes(clubes,
                hoy.atStartOfDay(), hoy.atTime(LocalTime.MAX));
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
