package edu.upb.semillero.service;

import edu.upb.semillero.config.AnalysisProperties;
import edu.upb.semillero.repository.dto.response.AnalysisJobResponseDto;
import edu.upb.semillero.repository.entity.AnalysisJob;
import edu.upb.semillero.repository.entity.VideoPartido;
import edu.upb.semillero.repository.enums.EstadoAnalisis;
import edu.upb.semillero.repository.repos.AnalysisJobRepository;
import edu.upb.semillero.repository.repos.PartidoRepository;
import edu.upb.semillero.repository.repos.VideoPartidoRepository;
import edu.upb.semillero.service.exception.AnalysisConflictException;
import edu.upb.semillero.service.exception.NotFoundException;
import edu.upb.semillero.service.exception.StorageUnavailableException;
import edu.upb.semillero.service.video.LocalVideoStorage;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalysisJobService {
    private final AnalysisJobRepository jobs;
    private final VideoPartidoRepository videos;
    private final PartidoRepository partidos;
    private final LocalVideoStorage storage;
    private final AnalysisProperties properties;
    private final Clock clock;

    public AnalysisJobService(AnalysisJobRepository jobs, VideoPartidoRepository videos,
            PartidoRepository partidos, LocalVideoStorage storage, AnalysisProperties properties,
            @Qualifier("analysisClock") Clock clock) {
        this.jobs = jobs;
        this.videos = videos;
        this.partidos = partidos;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AnalysisJobResponseDto create(Long partidoId, Long videoId) {
        requirePartido(partidoId);
        // Serialize even the first request, when there is no job row to lock yet.
        VideoPartido video = videos.findLockedById(videoId)
                .filter(v -> v.getPartido().getId().equals(partidoId)).orElseThrow(this::videoNotFound);
        jobs.findFirstByVideoIdOrderByIdDesc(videoId).ifPresent(existing -> {
            throw new AnalysisConflictException(existing.getId(), !existing.getEstado().isTerminal());
        });
        if (!properties.isEnabled() || !properties.isEndpointValid()) {
            throw new StorageUnavailableException("El servicio de análisis no está habilitado o configurado");
        }
        if (!storage.exists(video.getStorageId())) {
            throw new StorageUnavailableException("El archivo original no está disponible para analizar");
        }
        var now = clock.instant();
        var job = new AnalysisJob();
        job.setVideo(video);
        job.setEstado(EstadoAnalisis.PENDIENTE);
        job.setFechaSolicitud(now);
        job.setFechaActualizacion(now);
        job.setProximaEjecucion(now);
        return toResponse(jobs.saveAndFlush(job));
    }

    public AnalysisJobResponseDto findById(Long id) {
        return toResponse(jobs.findById(id)
                .orElseThrow(() -> new NotFoundException("Análisis con id " + id + " no existe")));
    }

    public Page<AnalysisJobResponseDto> findAll(Long partidoId, Long videoId, Pageable pageable) {
        requirePartido(partidoId);
        videos.findByIdAndPartidoId(videoId, partidoId).orElseThrow(this::videoNotFound);
        return jobs.findByVideoId(videoId, pageable).map(this::toResponse);
    }

    private void requirePartido(Long id) {
        if (!partidos.existsById(id)) throw new NotFoundException("Partido con id " + id + " no existe");
    }

    private NotFoundException videoNotFound() {
        return new NotFoundException("Video no encontrado para el partido indicado");
    }

    private AnalysisJobResponseDto toResponse(AnalysisJob job) {
        return new AnalysisJobResponseDto(job.getId(), job.getVideo().getPartido().getId(), job.getVideo().getId(),
                job.getEstado(), job.getPorcentaje(), job.getFotogramasProcesados(), job.getFotogramasTotales(),
                job.getFechaSolicitud(), job.getFechaInicio(), job.getFechaFin(), job.getFechaActualizacion(),
                job.isResultadosDisponibles(), job.getCodigoError(), job.getMensajeError());
    }
}
