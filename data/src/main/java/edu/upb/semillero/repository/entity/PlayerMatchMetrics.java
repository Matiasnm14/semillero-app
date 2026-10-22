package edu.upb.semillero.repository.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One player's measurements for one analysis and video interval; publication is handled in core. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@AttributeOverride(name = "id", column = @Column(name = "metric_id"))
@Table(name = "player_match_metrics",
        uniqueConstraints = @UniqueConstraint(name = "uk_pmm_analysis_player_period",
                columnNames = {"analysis_id", "player_id", "period_start", "period_end"}),
        indexes = {
                @Index(name = "idx_pmm_match_player", columnList = "match_id, player_id"),
                @Index(name = "idx_pmm_player", columnList = "player_id")
        }, check = {
                @CheckConstraint(name = "chk_pmm_distance",
                        constraint = "total_distance between 0 and 9999999999999.999999"),
                @CheckConstraint(name = "chk_pmm_speed",
                        constraint = "avg_speed between 0 and 9999999999999.999999 and max_speed between avg_speed and 9999999999999.999999"),
                @CheckConstraint(name = "chk_pmm_period",
                        constraint = "period_start between 0 and 9999999999999.999999 and period_end > period_start and period_end <= 9999999999999.999999"),
                @CheckConstraint(name = "chk_pmm_version", constraint = "version >= 1")
        })
public class PlayerMatchMetrics extends BaseEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "analysis_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_pmm_analysis"))
    private AnalysisJob analysis;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_pmm_match"))
    private Partido partido;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_pmm_player"))
    private Jugador jugador;

    @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 6)
    @Column(name = "total_distance", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal totalDistance;

    @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 6)
    @Column(name = "avg_speed", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal avgSpeed;

    @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 6)
    @Column(name = "max_speed", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal maxSpeed;

    @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 6)
    @Column(name = "period_start", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal periodStart;

    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 6)
    @Column(name = "period_end", nullable = false, precision = 19, scale = 6, updatable = false)
    private BigDecimal periodEnd;

    @Min(1)
    @Column(nullable = false, updatable = false)
    private int version;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @NotNull
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public PlayerMatchMetrics(AnalysisJob analysis, Partido partido, Jugador jugador,
            BigDecimal totalDistance, BigDecimal avgSpeed, BigDecimal maxSpeed,
            BigDecimal periodStart, BigDecimal periodEnd, int version, boolean current) {
        this.analysis = analysis;
        this.partido = partido;
        this.jugador = jugador;
        this.totalDistance = totalDistance;
        this.avgSpeed = avgSpeed;
        this.maxSpeed = maxSpeed;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.version = version;
        this.current = current;
    }

    @PrePersist
    private void recordCreation() {
        createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
