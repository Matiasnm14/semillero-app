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
                "CHK_SANCION_MANUAL_MOTIVO",
                "CHK_PARTIDO_TORNEO_FASE", "CHK_WALKOVER_CLUB", "CHK_PARTIDO_FECHAS",
                "CHK_PARTIDO_GOLES_LOCAL", "CHK_PARTIDO_GOLES_VISITANTE",
                "CHK_PARTIDO_PENALES_LOCAL", "CHK_PARTIDO_PENALES_VISITANTE",
                "CHK_PARTICIPACION_MINUTOS");
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

    /**
     * Puntos 8 y 9 del acuerdo de la S1-04: las dos tablas compartidas que crea
     * torneos. Llevan columna {@code modulo} para que sirvan a los cuatro.
     */
    @Test
    void lasTablasCompartidasQueCreaTorneos() {
        assertThat(nombres("select table_name from information_schema.tables where table_schema = 'PUBLIC'"))
                .contains("AUDITORIA", "NOTIFICACIONES");
        assertThat(columnas("AUDITORIA")).contains(
                "MODULO", "TABLA", "REGISTRO_ID", "ACCION", "VALOR_ANTERIOR", "VALOR_NUEVO",
                "MOTIVO", "USERS_ID", "FECHA");
        assertThat(columnas("NOTIFICACIONES")).contains(
                "USERS_ID", "MODULO", "TITULO", "MENSAJE", "LEIDA", "FECHA");
        assertThat(nullables("NOTIFICACIONES")).doesNotContain("LEIDA", "FECHA");
    }

    /** Tarea S1-06: las columnas que torneos agrega a las tablas acordadas. */
    @Test
    void lasColumnasDeTorneosLlegaronALasTablasAcordadas() {
        assertThat(columnas("PARTIDOS")).contains(
                "TORNEO_CATEGORIAS_ID", "GRUPOS_ID", "FASE", "JORNADA", "CANCHAS_ID", "ARBITRO",
                "MESA_CONTROL_ID", "GOLES_LOCAL", "GOLES_VISITANTE", "PENALES_LOCAL",
                "PENALES_VISITANTE", "GANADOR_WALKOVER_ID", "PARTIDO_SIGUIENTE_ID",
                "FECHA_CIERRE", "CERRADO_POR");
        assertThat(columnas("PARTICIPACIONES_PARTIDO")).contains("CAPITAN", "MINUTOS_JUGADOS");
        assertThat(columnas("JUGADORES")).contains("ESTADO");
    }

    /**
     * Las columnas nuevas no pueden romper el CRUD que ya existía: o aceptan
     * NULL, o son NOT NULL con valor por defecto.
     */
    @Test
    void lasColumnasNuevasNoRompenElCrudExistente() {
        assertThat(nullables("PARTIDOS")).contains(
                "TORNEO_CATEGORIAS_ID", "GRUPOS_ID", "FASE", "JORNADA", "CANCHAS_ID", "ARBITRO",
                "MESA_CONTROL_ID", "GOLES_LOCAL", "GOLES_VISITANTE", "PENALES_LOCAL",
                "PENALES_VISITANTE", "GANADOR_WALKOVER_ID", "PARTIDO_SIGUIENTE_ID",
                "FECHA_CIERRE", "CERRADO_POR");
        assertThat(nullables("PARTICIPACIONES_PARTIDO")).contains("MINUTOS_JUGADOS");
        assertThat(nullables("PARTICIPACIONES_PARTIDO")).doesNotContain("CAPITAN");
        assertThat(nullables("JUGADORES")).doesNotContain("ESTADO");
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

    private List<String> nullables(String tabla) {
        return jdbc.queryForList("""
                select column_name from information_schema.columns
                where table_schema = 'PUBLIC' and table_name = ? and is_nullable = 'YES'
                """, String.class, tabla);
    }

    private List<String> nombres(String sql) {
        return jdbc.queryForList(sql, String.class);
    }
}
