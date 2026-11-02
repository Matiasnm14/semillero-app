package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.entity.ParticipacionPartido;
import edu.upb.semillero.repository.entity.torneos.EventoPartido;
import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea S3-08 - Minutos jugados con titularidad, cambios y expulsiones, en un
 * partido de dos tiempos de 35 minutos.
 */
class MinutosJugadosTest {
    private static final int DURACION = 70;

    private final ParticipacionPartido titular = participacion(1L, true);
    private final ParticipacionPartido otroTitular = participacion(2L, true);
    private final ParticipacionPartido suplente = participacion(3L, false);
    private final ParticipacionPartido suplenteQueNoEntra = participacion(4L, false);
    private final List<EventoPartido> eventos = new ArrayList<>();
    private long siguienteEvento = 1;

    @Test
    void elTitularQueNoSaleJuegaElPartidoEntero() {
        assertThat(minutosDe(titular)).isEqualTo(70);
        assertThat(cerrado(titular)).isEqualTo(new MinutosJugados.Tramo((short) 0, (short) 70, false));
    }

    @Test
    void elSuplenteQueNoEntraQuedaEnCero() {
        assertThat(cerrado(suplenteQueNoEntra))
                .isEqualTo(new MinutosJugados.Tramo((short) 0, (short) 0, false));
        assertThat(minutosDe(suplenteQueNoEntra)).isZero();
    }

    @Test
    void elCambioRepartenLosMinutosEntreElQueSaleYElQueEntra() {
        cambio(50, titular, suplente);

        assertThat(minutosDe(titular)).isEqualTo(50);
        assertThat(minutosDe(suplente)).isEqualTo(20);
        assertThat(cerrado(suplente).entrada()).isEqualTo((short) 50);
        assertThat(cerrado(suplente).salida()).isEqualTo((short) 70);
    }

    @Test
    void elExpulsadoSaleEnElMinutoDeLaRoja() {
        evento(TipoEventoPartido.ROJA, 33, otroTitular, null);

        MinutosJugados.Tramo tramo = cerrado(otroTitular);
        assertThat(tramo.salida()).isEqualTo((short) 33);
        assertThat(tramo.expulsado()).isTrue();
        assertThat(minutosDe(otroTitular)).isEqualTo(33);
    }

    @Test
    void elQueEntraYDespuesEsExpulsadoCuentaSoloSuTramo() {
        cambio(40, titular, suplente);
        evento(TipoEventoPartido.ROJA, 55, suplente, null);

        assertThat(minutosDe(suplente)).isEqualTo(15);
    }

    @Test
    void laRojaAUnSuplenteQueNoEntroNoLeSumaMinutos() {
        evento(TipoEventoPartido.ROJA, 20, suplenteQueNoEntra, null);

        assertThat(cerrado(suplenteQueNoEntra).salida()).isZero();
        assertThat(minutosDe(suplenteQueNoEntra)).isZero();
    }

    /** Un cambio en el descuento no le suma minutos reglamentarios al que entra. */
    @Test
    void losMinutosSeCuentanDentroDelTiempoReglamentario() {
        cambio(73, titular, suplente);

        assertThat(minutosDe(titular)).isEqualTo(70);
        assertThat(minutosDe(suplente)).isZero();
        // La salida nunca queda antes de la entrada.
        assertThat(cerrado(suplente).salida()).isEqualTo((short) 73);
    }

    /** Sirve también para saber quién está en cancha al registrar un evento. */
    @Test
    void sabeQuienEstaEnCanchaEnCadaMinuto() {
        cambio(50, titular, suplente);
        Map<Long, MinutosJugados.Tramo> tramos = MinutosJugados.tramos(planilla(), eventos);

        assertThat(tramos.get(1L).enCanchaAl(49)).isTrue();
        assertThat(tramos.get(1L).enCanchaAl(51)).isFalse();
        assertThat(tramos.get(3L).enCanchaAl(49)).isFalse();
        assertThat(tramos.get(3L).enCanchaAl(60)).isTrue();
        assertThat(tramos.get(4L).entro()).isFalse();
    }

    private void cambio(int minuto, ParticipacionPartido sale, ParticipacionPartido entra) {
        evento(TipoEventoPartido.CAMBIO, minuto, sale, entra);
    }

    private void evento(TipoEventoPartido tipo, int minuto, ParticipacionPartido protagonista,
            ParticipacionPartido relacionado) {
        EventoPartido evento = new EventoPartido();
        evento.setId(siguienteEvento++);
        evento.setTipo(tipo);
        evento.setMinuto((short) minuto);
        evento.setParticipacion(protagonista);
        evento.setParticipacionRelacionada(relacionado);
        eventos.add(evento);
    }

    private MinutosJugados.Tramo cerrado(ParticipacionPartido participacion) {
        return MinutosJugados.cerrado(MinutosJugados.tramos(planilla(), eventos)
                .get(participacion.getId()), DURACION);
    }

    private int minutosDe(ParticipacionPartido participacion) {
        return MinutosJugados.minutos(cerrado(participacion), DURACION);
    }

    private List<ParticipacionPartido> planilla() {
        return List.of(titular, otroTitular, suplente, suplenteQueNoEntra);
    }

    private static ParticipacionPartido participacion(Long id, boolean titular) {
        ParticipacionPartido participacion = new ParticipacionPartido();
        participacion.setId(id);
        participacion.setEsTitular(titular);
        return participacion;
    }
}
