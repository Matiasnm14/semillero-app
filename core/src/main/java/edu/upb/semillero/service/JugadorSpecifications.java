package edu.upb.semillero.service;

import edu.upb.semillero.repository.entity.Jugador;
import edu.upb.semillero.repository.entity.PosicionJugador;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

final class JugadorSpecifications {
    private JugadorSpecifications() {}

    static Specification<Jugador> filtrar(String posicion, String piernaHabil,
            Integer edadMin, Integer edadMax, String nombre) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (nombre != null && !nombre.isBlank()) {
                String pattern = "%" + nombre.trim().toLowerCase(Locale.ROOT) + "%";
                var user = root.join("usuario", jakarta.persistence.criteria.JoinType.LEFT);
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(user.get("nombres"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(user.get("apellidos"), "")), pattern),
                        cb.like(cb.lower(cb.concat(cb.concat(cb.coalesce(user.get("nombres"), ""), " "),
                                cb.coalesce(user.get("apellidos"), ""))), pattern)));
            }
            if (piernaHabil != null && !piernaHabil.isBlank()) {
                String code = switch (piernaHabil.trim().toLowerCase(Locale.ROOT)) {
                    case "derecho", "right" -> "D";
                    case "izquierdo", "left" -> "I";
                    case "ambidiestro", "ambidextrous" -> "A";
                    default -> piernaHabil.trim().substring(0, 1).toUpperCase(Locale.ROOT);
                };
                predicates.add(cb.equal(cb.upper(root.get("piernaHabil")), code));
            }
            LocalDate today = LocalDate.now();
            if (edadMin != null) predicates.add(cb.lessThanOrEqualTo(root.get("fechaNacimiento"), today.minusYears(edadMin)));
            if (edadMax != null) predicates.add(cb.greaterThan(root.get("fechaNacimiento"), today.minusYears(edadMax + 1L)));
            if (posicion != null && !posicion.isBlank()) {
                var subquery = query.subquery(Long.class);
                var pj = subquery.from(PosicionJugador.class);
                var p = pj.join("posicion");
                subquery.select(pj.get("id")).where(cb.equal(pj.get("jugador"), root),
                        cb.like(cb.lower(p.get("posicion")), "%" + posicion.trim().toLowerCase(Locale.ROOT) + "%"));
                predicates.add(cb.exists(subquery));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
