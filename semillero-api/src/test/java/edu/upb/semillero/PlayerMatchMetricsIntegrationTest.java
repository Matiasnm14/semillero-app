package edu.upb.semillero;

import edu.upb.semillero.repository.entity.ParticipacionPartido;
import edu.upb.semillero.repository.entity.PlayerMatchMetrics;
import edu.upb.semillero.repository.enums.EstadoAnalisis;
import edu.upb.semillero.repository.repos.*;
import edu.upb.semillero.service.PlayerMatchMetricsIntegrityService;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.NotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:metrics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "analysis.enabled=false", "analysis.worker-enabled=false", "video.youtube.enabled=false",
        "video.youtube.worker-enabled=false"
})
@ActiveProfiles("test")
class PlayerMatchMetricsIntegrationTest {
    @Autowired ApplicationContext context;
    @Autowired PlayerMatchMetricsRepository metrics;
    @Autowired AnalysisJobRepository analyses;
    @Autowired ParticipacionPartidoRepository participaciones;
    @Autowired PlayerMatchMetricsIntegrityService integrity;
    @Autowired JdbcTemplate jdbc;
    private MetricsFixtures.Fixture fixture;

    @BeforeEach
    void setup() {
        metrics.deleteAll();
        analyses.deleteAll();
        context.getBean(VideoPartidoRepository.class).deleteAll();
        participaciones.deleteAll();
        context.getBean(JugadorRepository.class).deleteAll();
        context.getBean(PartidoRepository.class).deleteAll();
        context.getBean(ClubRepository.class).deleteAll();
        fixture = MetricsFixtures.create(context);
    }

    @Test
    void storesAllRequiredFieldsAndRecoversExactDecimalsAndReferences() {
        var refs = integrity.validate(analysisId(), partidoId(), jugadorId());
        var input = MetricsFixtures.metric(fixture);
        var saved = metrics.saveAndFlush(input);
        var recovered = metrics.findById(saved.getId()).orElseThrow();
        assertThat(recovered.getId()).isPositive();
        assertThat(recovered.getAnalysis().getId()).isEqualTo(refs.analysis().getId());
        assertThat(recovered.getPartido().getId()).isEqualTo(refs.partido().getId());
        assertThat(recovered.getJugador().getId()).isEqualTo(refs.jugador().getId());
        assertThat(recovered.getTotalDistance()).isEqualByComparingTo("1234.567890");
        assertThat(recovered.getAvgSpeed()).isEqualByComparingTo("3.000001");
        assertThat(recovered.getMaxSpeed()).isEqualByComparingTo("9.123456");
        assertThat(recovered.getPeriodStart()).isEqualByComparingTo("0.123456");
        assertThat(recovered.getPeriodEnd()).isEqualByComparingTo("60.123456");
        assertThat(recovered.getVersion()).isEqualTo(1);
        assertThat(recovered.isCurrent()).isFalse();
        assertThat(recovered.getCreatedAt()).isEqualTo(saved.getCreatedAt());
        assertThat(metrics.findByAnalysisIdOrderById(analysisId())).extracting(PlayerMatchMetrics::getId)
                .containsExactly(saved.getId());
        assertThat(metrics.findByAnalysisIdAndJugadorIdAndPeriodStartAndPeriodEnd(
                analysisId(), jugadorId(), new BigDecimal("0.123456"), new BigDecimal("60.123456"))).isPresent();
    }

    @Test
    void cannotDuplicateTheSameAnalysisPlayerAndIntervalEvenWithAnotherVersion() {
        metrics.saveAndFlush(MetricsFixtures.metric(fixture));
        var duplicate = new PlayerMatchMetrics(fixture.analysis(), fixture.partido(), fixture.jugador(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.123456"), new BigDecimal("60.123456"), 2, false);
        assertThatThrownBy(() -> metrics.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(metrics.count()).isEqualTo(1);
    }

    @Test
    void differentIntervalsOfOneAnalysisRemainDistinct() {
        metrics.saveAndFlush(MetricsFixtures.metric(fixture));
        metrics.saveAndFlush(new PlayerMatchMetrics(fixture.analysis(), fixture.partido(), fixture.jugador(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("60.123456"), new BigDecimal("90"), 1, false));
        assertThat(metrics.findByAnalysisIdOrderById(analysisId())).hasSize(2);
    }

    @Test
    void rejectsExcessDecimalPrecisionBeforeTheDatabaseRoundsIt() {
        var invalid = new PlayerMatchMetrics(fixture.analysis(), fixture.partido(), fixture.jugador(),
                new BigDecimal("1.1234567"), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ONE, 1, false);
        assertThatThrownBy(() -> metrics.saveAndFlush(invalid)).isInstanceOf(ConstraintViolationException.class);
        assertThat(metrics.count()).isZero();
    }

    @Test
    void checksRejectInvertedIntervalsAndAverageSpeedGreaterThanMaximum() {
        var inverted = new PlayerMatchMetrics(fixture.analysis(), fixture.partido(), fixture.jugador(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.TEN, BigDecimal.ONE, 1, false);
        assertThatThrownBy(() -> metrics.saveAndFlush(inverted)).isInstanceOf(DataIntegrityViolationException.class);
        var invalidSpeed = new PlayerMatchMetrics(fixture.analysis(), fixture.partido(), fixture.jugador(),
                BigDecimal.ZERO, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ONE, 1, false);
        assertThatThrownBy(() -> metrics.saveAndFlush(invalidSpeed)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(metrics.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidIds")
    void validatesIdsBeforeResolvingReferences(int field, Long value) {
        Long[] ids = {analysisId(), partidoId(), jugadorId()};
        ids[field] = value;
        assertThatThrownBy(() -> integrity.validate(ids[0], ids[1], ids[2]))
                .isInstanceOf(BusinessException.class).hasMessageContaining("identificador positivo");
        assertThat(metrics.count()).isZero();
    }

    static Stream<Arguments> invalidIds() {
        return Stream.of(0, 1, 2).flatMap(index -> Stream.of(null, 0L, -1L).map(value -> Arguments.of(index, value)));
    }

    @Test
    void rejectsEachMissingReferenceWithItsOwnMessage() {
        assertThatThrownBy(() -> integrity.validate(Long.MAX_VALUE, partidoId(), jugadorId()))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Análisis");
        assertThatThrownBy(() -> integrity.validate(analysisId(), Long.MAX_VALUE, jugadorId()))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Partido");
        assertThatThrownBy(() -> integrity.validate(analysisId(), partidoId(), Long.MAX_VALUE))
                .isInstanceOf(NotFoundException.class).hasMessageContaining("Jugador");
        assertThat(metrics.count()).isZero();
    }

    @Test
    void analysisCannotBeAssociatedWithAnotherExistingMatch() {
        assertThatThrownBy(() -> integrity.validate(analysisId(), fixture.otroPartido().getId(), jugadorId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("análisis no corresponde");
        assertThat(metrics.count()).isZero();
    }

    @Test
    void existingPlayerRegisteredOnlyInAnotherMatchIsRejected() {
        ParticipacionPartido other = new ParticipacionPartido();
        other.setPartido(fixture.otroPartido()); other.setJugador(fixture.otroJugador());
        other.setClub(fixture.participacion().getClub()); other.setEsTitular(true); other.setMinutoEntrada((short) 0);
        participaciones.saveAndFlush(other);
        assertThatThrownBy(() -> integrity.validate(analysisId(), partidoId(), fixture.otroJugador().getId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("no está registrado");
        assertThat(metrics.count()).isZero();
    }

    @Test
    void rejectsParticipationWithAClubOutsideTheMatch() {
        var participation = fixture.participacion();
        participation.setClub(fixture.tercero());
        participaciones.saveAndFlush(participation);
        assertThatThrownBy(() -> integrity.validate(analysisId(), partidoId(), jugadorId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("club del partido");
    }

    @Test
    void structuralValidationDoesNotPublishOrChangeAnalysisStatus() {
        var analysis = fixture.analysis();
        analysis.setEstado(EstadoAnalisis.PROCESANDO); analysis.setFechaFin(null);
        analysis.setProximaEjecucion(Instant.now());
        analyses.saveAndFlush(analysis);
        integrity.validate(analysisId(), partidoId(), jugadorId());
        assertThat(analyses.findById(analysisId()).orElseThrow().getEstado()).isEqualTo(EstadoAnalisis.PROCESANDO);
        assertThat(analyses.findById(analysisId()).orElseThrow().isResultadosDisponibles()).isFalse();
        assertThat(metrics.count()).isZero();
    }

    @Test
    void storedMetricPreventsDeletingItsAnalysisPlayerOrMatch() {
        metrics.saveAndFlush(MetricsFixtures.metric(fixture));
        assertThatThrownBy(() -> jdbc.update("delete from analysis_jobs where id = ?", analysisId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from jugadores where id = ?", jugadorId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("delete from partidos where id = ?", partidoId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long analysisId() { return fixture.analysis().getId(); }
    private Long partidoId() { return fixture.partido().getId(); }
    private Long jugadorId() { return fixture.jugador().getId(); }
}
