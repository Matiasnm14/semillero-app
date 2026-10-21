package edu.upb.semillero;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tarea S1-05: comprueba que las 16 tablas propias de torneos y cada CHECK y
 * UNIQUE de {@code docs/torneos/bd_torneos_propuesta.sql} llegaron al esquema
 * que Hibernate genera desde las entidades.
 */
@SpringBootTest
@ActiveProfiles("test")
class EsquemaTorneosTest {
    @Autowired JdbcTemplate jdbc;

    @ParameterizedTest(name = "la tabla {0} existe")
    @ValueSource(strings = {"sedes", "canchas", "torneos", "reglamentos", "criterios_desempate",
            "reglamento_desempates", "torneo_categorias", "grupos", "inscripciones_equipo",
            "lista_buena_fe", "reprogramaciones", "planillas", "planilla_cuerpo_tecnico",
            "eventos_partido", "sanciones", "apelaciones"})
    void tablasDeTorneos(String tabla) {
        assertThat(nombres("select table_name from information_schema.tables where table_schema = 'PUBLIC'"))
                .contains(tabla.toUpperCase());
    }

    @Test
    void restriccionesCheckDelScript() {
        assertThat(constraints("CHECK")).contains(
                "CHK_TORNEO_FECHAS", "CHK_TORNEO_LIMITE_INSCRIPCION", "CHK_TORNEO_ORGANIZADOR_EXTERNO",
                "CHK_REGLAMENTO_JUGADORES", "CHK_REGLAMENTO_DESEMPATE_ORDEN",
                "CHK_TORNEO_CATEGORIA_CUPO", "CHK_TORNEO_CATEGORIA_EDADES",
                "CHK_INSCRIPCION_MOTIVO", "CHK_LISTA_BF_DORSAL",
                "CHK_EVENTO_MINUTO", "CHK_EVENTO_GOL_EXIGE_TIPO", "CHK_EVENTO_NO_GOL_SIN_TIPO",
                "CHK_EVENTO_CAMBIO_EXIGE_REL", "CHK_EVENTO_PARTICIPACIONES_DISTINTAS",
                "CHK_SANCION_FECHAS_SUSPENSION", "CHK_SANCION_FECHAS_CUMPLIDAS",
                "CHK_SANCION_MANUAL_MOTIVO");
    }

    @Test
    void restriccionesUniqueDelScript() {
        assertThat(constraints("UNIQUE")).contains(
                "UK_CANCHA_SEDE_NOMBRE", "UK_REGLAMENTO_TORNEO", "UK_CRITERIO_DESEMPATE_CODIGO",
                "UK_REGLAMENTO_DESEMPATE_ORDEN", "UK_REGLAMENTO_DESEMPATE_CRITERIO",
                "UK_TORNEO_CATEGORIA_NOMBRE", "UK_GRUPO_CATEGORIA_NOMBRE",
                "UK_INSCRIPCION_CATEGORIA_CLUB", "UK_LISTA_BF_DORSAL", "UK_LISTA_BF_JUGADOR_TORNEO",
                "UK_PLANILLA_PARTIDO");
    }

    @Test
    void tablasAcordadasSiguenSinLasColumnasPendientes() {
        assertThat(columnas("PARTIDOS")).containsExactlyInAnyOrder(
                "ID", "FECHA_INICIO", "FECHA_FIN", "CLUB_LOCAL_ID", "CLUB_VISITANTE_ID", "ESTADO");
        assertThat(columnas("PARTICIPACIONES_PARTIDO")).doesNotContain("CAPITAN", "MINUTOS_JUGADOS");
        assertThat(columnas("JUGADORES")).doesNotContain("ESTADO");
    }

    private List<String> constraints(String tipo) {
        return jdbc.queryForList("""
                select constraint_name from information_schema.table_constraints
                where table_schema = 'PUBLIC' and constraint_type = ?
                """, String.class, tipo);
    }

    private List<String> columnas(String tabla) {
        return jdbc.queryForList("""
                select column_name from information_schema.columns
                where table_schema = 'PUBLIC' and table_name = ?
                """, String.class, tabla);
    }

    private List<String> nombres(String sql) {
        return jdbc.queryForList(sql, String.class);
    }
}
