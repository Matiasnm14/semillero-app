package edu.upb.semillero.service.scouting;

import edu.upb.semillero.repository.dto.response.scouting.*;
import edu.upb.semillero.repository.repos.scouting.DirectorioScoutingRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import java.time.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Transactional(readOnly = true)
public class DirectorioScoutingService {
    private final DirectorioScoutingRepository directorio;
    private final AccesoScouting acceso;
    private final Clock clock;
    public DirectorioScoutingService(DirectorioScoutingRepository directorio, AccesoScouting acceso,
            @Qualifier("scoutingClock") Clock clock) {
        this.directorio = directorio; this.acceso = acceso; this.clock = clock;
    }
    public Page<JugadorDirectorioDto> jugadores(String texto, Long posicionId, Long clubId, int page, int size) {
        acceso.usuario();
        return buscar(null, texto, posicionId, clubId, page, size);
    }
    public JugadorDirectorioDto jugador(Long id) {
        acceso.usuario();
        return buscar(id, "", null, null, 0, 1).stream().findFirst()
            .orElseThrow(() -> new NotFoundException("Perfil no disponible"));
    }
    private Page<JugadorDirectorioDto> buscar(Long id, String texto, Long posicionId, Long clubId, int page, int size) {
        return directorio.jugadores(id, patron(texto), posicionId, clubId, LocalDate.now(clock).minusYears(18),
                LocalDateTime.now(clock), pagina(page, size, "id"));
    }
    public Page<ClubDirectorioDto> clubes(String texto, int page, int size) {
        acceso.usuario(); return directorio.clubes(patron(texto), pagina(page, size, "id"));
    }
    static String patron(String texto) {
        return "%" + texto.strip().toLowerCase(java.util.Locale.ROOT)
            .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
    public static Pageable pagina(int page, int size, String campo) {
        var sort = Sort.by(campo).descending();
        return PageRequest.of(page, size, campo.equals("id") ? sort : sort.and(Sort.by("id")));
    }
}
