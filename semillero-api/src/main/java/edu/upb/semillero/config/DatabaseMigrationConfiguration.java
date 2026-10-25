package edu.upb.semillero.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.api.FlywayException;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** One-time adoption of the pre-Flyway Semillero schema, followed by normal versioned migrations. */
@Configuration(proxyBeanMethods = false)
public class DatabaseMigrationConfiguration {
    private static final Set<String> BASE_TABLES = Set.of("pais", "ciudad", "clubes", "users", "administrador",
            "usuarios_club", "usuario_trabaja", "jugadores", "posiciones", "posiciones_jugador", "fichado",
            "pruebas", "clinica_de_pago", "videos", "conversa", "partidos", "participaciones_partido");
    private static final Set<String> OPTIONAL_TABLES = Set.of("videos_partido", "analysis_jobs", "player_match_metrics");
    private static final Map<String, Set<String>> LEGACY_SIGNATURE = Map.of(
            "clubes", Set.of("id", "nombre_club", "ciudad_id"),
            "users", Set.of("id", "email", "password_hash"),
            "jugadores", Set.of("id", "users_id", "pierna_habil"),
            "partidos", Set.of("id", "club_local_id", "club_visitante_id", "fecha_inicio", "estado"),
            "participaciones_partido", Set.of("id", "partidos_id", "jugadores_id", "clubes_id", "dorsal"));

    @Bean
    FlywayMigrationStrategy semilleroMigrationStrategy() {
        return flyway -> {
            try (var connection = flyway.getConfiguration().getDataSource().getConnection()) {
                // Serialize adoption as well as migrate when several application instances start together.
                execute(connection, "SELECT pg_advisory_lock(hashtext('semillero:migrations:public')::bigint)");
                try {
                    var tables = tables(connection);
                    if (!tables.contains(flyway.getConfiguration().getTable()) && !tables.isEmpty()) {
                        requireLegacySchema(connection, tables);
                        // -1 skips no project migration: V000-V004 reconcile constraints and record checksums.
                        flyway.baseline();
                    }
                    flyway.migrate();
                } finally {
                    execute(connection, "SELECT pg_advisory_unlock(hashtext('semillero:migrations:public')::bigint)");
                }
            } catch (SQLException e) {
                throw new FlywayException("No se pudo preparar la migración de Semillero", e);
            }
        };
    }

    private static Set<String> tables(Connection connection) throws SQLException {
        var tables = new HashSet<String>();
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public'")) {
            while (rows.next()) tables.add(rows.getString(1));
        }
        return tables;
    }

    private static void requireLegacySchema(Connection connection, Set<String> tables) throws SQLException {
        var known = new HashSet<>(BASE_TABLES);
        known.addAll(OPTIONAL_TABLES);
        if (!tables.containsAll(BASE_TABLES) || !known.containsAll(tables)) throw unknownSchema();
        for (var entry : LEGACY_SIGNATURE.entrySet()) {
            var columns = new HashSet<String>();
            try (var statement = connection.prepareStatement("""
                    SELECT column_name FROM information_schema.columns
                    WHERE table_schema='public' AND table_name=?
                    """)) {
                statement.setString(1, entry.getKey());
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) columns.add(rows.getString(1));
                }
            }
            if (!columns.containsAll(entry.getValue())) throw unknownSchema();
        }
    }

    private static FlywayException unknownSchema() {
        return new FlywayException("Esquema existente no reconocido como Semillero. Se canceló la adopción; "
                + "revisa DB_URL y la estructura antes de iniciar. No se eliminan datos ni se omiten migraciones.");
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
