package edu.upb.semillero.service.torneos;

import java.time.LocalDateTime;

/**
 * HU 15 - El evento que torneos publica al cerrar un partido (tarea S4-09).
 *
 * <p>Es el contrato mínimo del punto 13 del acuerdo de la S1-04: solo el ID del
 * partido y la fecha de cierre. Con eso el microservicio de video busca lo que
 * necesite (la planilla está en {@code participaciones_partido}).
 *
 * <p>Se publica como evento de Spring dentro de la aplicación, para que
 * cualquier módulo del monolito lo escuche, y {@code AvisoPartidoFinalizado} lo
 * reenvía por REST al microservicio cuando la transacción del cierre ya se
 * confirmó. Si no llega, el microservicio lee los partidos FINALIZADO por
 * {@code fecha_cierre} en {@code GET /api/v1/torneos/partidos/finalizados}.
 */
public record PartidoFinalizado(Long partidoId, LocalDateTime fechaCierre) {}
