package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.entity.ParticipacionPartido;
import edu.upb.semillero.repository.entity.torneos.EventoPartido;
import edu.upb.semillero.repository.enums.torneos.TipoEventoPartido;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tarea S3-08: cuándo entró y salió cada jugador y cuántos minutos jugó,
 * sacado de la planilla y de los eventos. Regla 6 del contexto y decisión 3.6:
 *
 * <ul>
 *   <li>el titular entra en el minuto 0;</li>
 *   <li>el suplente entra en el minuto del cambio que lo hace entrar;</li>
 *   <li>se sale en el minuto del cambio o de la roja;</li>
 *   <li>el que sigue en cancha al final sale en el minuto en que termina el
 *       partido (los dos tiempos de la categoría);</li>
 *   <li>el suplente que no entró queda con entrada 0 y salida 0.</li>
 * </ul>
 *
 * <p>Los minutos jugados se cuentan dentro del tiempo reglamentario: un cambio
 * en el minuto 92 de un partido de 90 no le suma minutos al que entra.
 *
 * <p>No tiene estado ni toca la base: la usan el registro de eventos, para
 * saber quién está en cancha, y el cierre del partido, para guardar los
 * minutos.
 */
final class MinutosJugados {

    private MinutosJugados() {
    }

    /** Cuándo entró y salió un jugador. {@code entrada} nula: no entró nunca. */
    record Tramo(Short entrada, Short salida, boolean expulsado) {

        boolean entro() {
            return entrada != null;
        }

        /** En cancha en ese minuto: ya entró y todavía no salió. */
        boolean enCanchaAl(int minuto) {
            return entro() && entrada <= minuto && (salida == null || minuto <= salida);
        }
    }

    /** El tramo de cada participación, por id, en el orden de la planilla. */
    static Map<Long, Tramo> tramos(List<ParticipacionPartido> planilla, List<EventoPartido> eventos) {
        Map<Long, Short> entradas = new LinkedHashMap<>();
        Map<Long, Short> salidas = new LinkedHashMap<>();
        Map<Long, Boolean> expulsados = new LinkedHashMap<>();
        for (ParticipacionPartido participacion : planilla) {
            entradas.put(participacion.getId(),
                    Boolean.TRUE.equals(participacion.getEsTitular()) ? (short) 0 : null);
        }
        List<EventoPartido> ordenados = eventos.stream()
                .sorted(Comparator.comparing(EventoPartido::getMinuto)
                        .thenComparing(EventoPartido::getId,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        for (EventoPartido evento : ordenados) {
            Long protagonista = evento.getParticipacion().getId();
            if (evento.getTipo() == TipoEventoPartido.CAMBIO) {
                salidas.putIfAbsent(protagonista, evento.getMinuto());
                entradas.put(evento.getParticipacionRelacionada().getId(), evento.getMinuto());
            } else if (evento.getTipo() == TipoEventoPartido.ROJA) {
                expulsados.put(protagonista, true);
                if (entradas.get(protagonista) != null) {
                    salidas.putIfAbsent(protagonista, evento.getMinuto());
                }
            }
        }
        Map<Long, Tramo> tramos = new LinkedHashMap<>();
        entradas.forEach((id, entrada) -> tramos.put(id,
                new Tramo(entrada, salidas.get(id), expulsados.getOrDefault(id, false))));
        return tramos;
    }

    /**
     * Lo que se guarda al cerrar: entrada, salida y minutos jugados. El
     * suplente que no entró queda en 0, 0 y 0.
     */
    static Tramo cerrado(Tramo tramo, int duracionMin) {
        if (!tramo.entro()) {
            return new Tramo((short) 0, (short) 0, tramo.expulsado());
        }
        // El que entró en el descuento termina en el minuto en que entró, no antes.
        short salida = tramo.salida() == null
                ? (short) Math.max(duracionMin, tramo.entrada())
                : tramo.salida();
        return new Tramo(tramo.entrada(), salida, tramo.expulsado());
    }

    /** Minutos jugados dentro del tiempo reglamentario; nunca negativos. */
    static short minutos(Tramo cerrado, int duracionMin) {
        int desde = Math.min(cerrado.entrada(), duracionMin);
        int hasta = Math.min(cerrado.salida(), duracionMin);
        return (short) Math.max(0, hasta - desde);
    }
}
