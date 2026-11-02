package edu.upb.semillero.service.seguridad;

import edu.upb.semillero.repository.entity.Partido;
import edu.upb.semillero.repository.repos.AnalysisJobRepository;
import edu.upb.semillero.repository.repos.ParticipacionPartidoRepository;
import edu.upb.semillero.repository.repos.PartidoRepository;
import edu.upb.semillero.service.comun.ComunService;
import edu.upb.semillero.service.comun.Roles;
import edu.upb.semillero.service.exception.ForbiddenException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Match ownership is checked both before multipart parsing and at the service boundary. */
@Service
@Transactional(readOnly = true)
public class AccesoVideos {
    private final ComunService comun;
    private final PartidoRepository partidos;
    private final AnalysisJobRepository analyses;
    private final ParticipacionPartidoRepository participaciones;
    private final RegistroAccesoService registro;

    public AccesoVideos(ComunService comun, PartidoRepository partidos, AnalysisJobRepository analyses,
            ParticipacionPartidoRepository participaciones, RegistroAccesoService registro) {
        this.comun = comun;
        this.partidos = partidos;
        this.analyses = analyses;
        this.participaciones = participaciones;
        this.registro = registro;
    }

    public boolean permitsMatch(Authentication authentication, String id, boolean write) {
        if (!authenticated(authentication)) return false;
        Long value = positiveId(id);
        // MVC reports malformed identifiers and missing resources using its normal 400/404 responses.
        return value == null || partidos.findById(value).map(p -> permits(authentication, p, write)).orElse(true);
    }

    public boolean permitsAnalysis(Authentication authentication, String id, boolean write) {
        if (!authenticated(authentication)) return false;
        Long value = positiveId(id);
        return value == null || analyses.findById(value)
                .map(job -> permits(authentication, job.getVideo().getPartido(), write)).orElse(true);
    }

    public void requireRead(Partido partido) { require(partido, false); }
    public void requireWrite(Partido partido) { require(partido, true); }

    public Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UsuarioAutenticado user ? user.usuarioId() : null;
    }

    private void require(Partido partido, boolean write) {
        if (permits(SecurityContextHolder.getContext().getAuthentication(), partido, write)) return;
        String path = "/api/v1/partidos/" + partido.getId() + "/videos";
        registro.rechazado(write ? "POST" : "GET", path, currentUserId(), "Sin acceso al video del partido");
        throw new ForbiddenException("No tienes permiso para acceder a los videos de este partido");
    }

    private boolean permits(Authentication auth, Partido partido, boolean write) {
        if (!authenticated(auth)) return false;
        if (auth.getPrincipal() instanceof UsuarioAutenticado user
                && (user.usuarioId() == null || !comun.usuarioExiste(user.usuarioId()))) return false;
        if (role(auth, Roles.ADMINISTRADOR_GENERAL) || role(auth, Roles.USUARIO_SISTEMA)) return true;
        if (!(auth.getPrincipal() instanceof UsuarioAutenticado user) || user.usuarioId() == null
                || !comun.usuarioExiste(user.usuarioId())) return false;
        if (role(auth, Roles.USUARIO_CLUB)) {
            var clubs = comun.clubesDondeTrabaja(user.usuarioId());
            if (clubs.contains(partido.getClubLocal().getId()) || clubs.contains(partido.getClubVisitante().getId())) return true;
        }
        if (role(auth, Roles.USUARIO_SISTEMA) && partido.getMesaControl() != null
                && user.usuarioId().equals(partido.getMesaControl().getId())) return true;
        return !write && participaciones.existsByPartidoIdAndJugadorUsuarioId(partido.getId(), user.usuarioId());
    }

    private boolean authenticated(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken);
    }

    private boolean role(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(value -> value.getAuthority().equals("ROLE_" + role));
    }

    private Long positiveId(String id) {
        try { long value = Long.parseLong(id); return value > 0 ? value : null; }
        catch (NumberFormatException | NullPointerException e) { return null; }
    }
}
