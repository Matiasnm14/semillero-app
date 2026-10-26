package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.enums.EstadoJugador;
import edu.upb.semillero.service.comun.ComunService;
import edu.upb.semillero.service.fichatecnica.FichaTecnicaService;
import edu.upb.semillero.service.fichatecnica.JugadorParaTorneosDto;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stubs de las interfaces que torneos consume pero no implementa, para no
 * quedar bloqueado mientras ficha técnica escribe las suyas (puntos 10 y 11 del
 * acuerdo de la S1-04).
 *
 * <p>Cada stub se registra solo si no hay otro bean de su tipo, así que el día
 * que ficha técnica agregue su {@code @Service} real, estos se apagan sin que
 * nadie toque nada. Igual conviene borrar esta clase cuando las dos estén
 * implementadas.
 *
 * <p><strong>Lo que estos stubs falsean</strong>, para que nadie lo confunda con
 * algo que funciona:
 *
 * <ul>
 *   <li>{@code tieneRol} responde siempre {@code true}: <strong>no hay
 *       autorización real</strong>. La HU 16 no está cubierta hasta que exista
 *       la implementación con las tablas {@code roles} y {@code usuarios_roles}.</li>
 *   <li>{@code datosParaTorneos} responde que el jugador existe, está ACTIVO y
 *       tiene los documentos al día. Sirve para desarrollar la HU 5, no para
 *       validar de verdad.</li>
 *   <li>{@code clubesDondeEstaFichado} y {@code jugadoresFichadosEn} responden
 *       vacío porque un stub no puede saberlo. Las validaciones de la lista de
 *       buena fe que dependen del fichaje <strong>no se pueden probar</strong>
 *       con estos stubs.</li>
 * </ul>
 */
@Configuration
public class MocksExternosConfiguration {

    @Bean
    @ConditionalOnMissingBean(FichaTecnicaService.class)
    FichaTecnicaService fichaTecnicaServiceStub() {
        return new FichaTecnicaStub();
    }

    @Bean
    @ConditionalOnMissingBean(ComunService.class)
    ComunService comunServiceStub() {
        return new ComunStub();
    }

    /** Edad que devuelve el stub: entra en los rangos típicos de las categorías. */
    private static final int EDAD_STUB = 12;

    private static class FichaTecnicaStub implements FichaTecnicaService {
        private static final Logger log = LoggerFactory.getLogger(FichaTecnicaStub.class);

        @Override
        public Optional<JugadorParaTorneosDto> datosParaTorneos(Long jugadorId, LocalDate fechaCorte) {
            log.warn("FichaTecnicaService es un stub: los datos del jugador {} no son reales", jugadorId);
            return Optional.of(new JugadorParaTorneosDto(
                    jugadorId, EDAD_STUB, EstadoJugador.ACTIVO, true, true, Set.of()));
        }

        @Override
        public List<Long> jugadoresFichadosEn(Long clubId, LocalDate fecha) {
            log.warn("FichaTecnicaService es un stub: no sabe quién está fichado en el club {}", clubId);
            return List.of();
        }
    }

    private static class ComunStub implements ComunService {
        private static final Logger log = LoggerFactory.getLogger(ComunStub.class);

        @Override
        public boolean usuarioExiste(Long usuarioId) {
            return usuarioId != null;
        }

        @Override
        public boolean clubExiste(Long clubId) {
            return clubId != null;
        }

        @Override
        public boolean tieneRol(Long usuarioId, String codigoRol) {
            log.warn("ComunService es un stub: se concede el rol {} al usuario {} sin verificar nada",
                    codigoRol, usuarioId);
            return true;
        }

        @Override
        public Set<Long> clubesDondeTrabaja(Long usuarioId) {
            log.warn("ComunService es un stub: no sabe en qué clubes trabaja el usuario {}", usuarioId);
            return Set.of();
        }
    }
}
