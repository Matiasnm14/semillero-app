package edu.upb.semillero.service.scouting;

import edu.upb.semillero.service.comun.ComunService;
import edu.upb.semillero.service.exception.ForbiddenException;
import edu.upb.semillero.service.seguridad.UsuarioAutenticado;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class AccesoScouting {
    private final ComunService comun;
    public Long usuario() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UsuarioAutenticado u)
                || !comun.usuarioExiste(u.usuarioId())) {
            throw new ForbiddenException("La operación requiere una cuenta válida");
        }
        return u.usuarioId();
    }
    public Set<Long> clubes() { return comun.clubesDondeTrabaja(usuario()); }
    public void exigirClub(Long clubId) {
        if (!clubes().contains(clubId)) throw new ForbiddenException("No puedes actuar por ese club");
    }
}
