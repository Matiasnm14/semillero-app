package edu.upb.semillero.service.scouting;

import edu.upb.semillero.repository.repos.scouting.CuotaContactoRepository;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CuotaContactoService {
    private final CuotaContactoRepository cuotas;
    private final Clock clock;
    private final int usuario;
    private final int club;
    private final int jugador;

    public CuotaContactoService(CuotaContactoRepository cuotas, @Qualifier("scoutingClock") Clock clock,
            @Value("${scouting.cuotas.usuario-dia:20}") int usuario,
            @Value("${scouting.cuotas.club-dia:50}") int club,
            @Value("${scouting.cuotas.jugador-dia:5}") int jugador) {
        if (usuario < 1 || club < 1 || jugador < 1) throw new IllegalArgumentException("Las cuotas deben ser positivas");
        this.cuotas = cuotas; this.clock = clock; this.usuario = usuario; this.club = club; this.jugador = jugador;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reservar(Long actorId, Long clubId, Long jugadorId) {
        LocalDate hoy = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        // Fixed order and atomic conditional updates serialize concurrent requests
        // across all application instances. A failed contact rolls back its counters.
        reservar("USUARIO:" + actorId, hoy, usuario);
        reservar("CLUB:" + clubId, hoy, club);
        reservar("JUGADOR:" + jugadorId, hoy, jugador);
    }

    private void reservar(String titular, LocalDate hoy, int limite) {
        String id = titular + ":" + hoy;
        cuotas.inicializar(id, hoy);
        if (cuotas.reservar(id, limite) != 1) {
            long segundos = Duration.between(clock.instant(), hoy.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()).getSeconds();
            throw new LimiteContactoException(Math.max(1, segundos));
        }
    }
}
