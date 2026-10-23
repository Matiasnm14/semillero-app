package edu.upb.semillero.service.torneos;

import edu.upb.semillero.repository.dto.response.torneos.CriterioDesempateResponseDto;
import edu.upb.semillero.repository.entity.torneos.CriterioDesempate;
import edu.upb.semillero.repository.repos.torneos.CriterioDesempateRepository;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogo de criterios de desempate (tarea S1-13). Es de solo lectura: los
 * cinco criterios del script los siembra {@link CriteriosDesempateSeeder}.
 */
@Service
@Transactional(readOnly = true)
public class CriterioDesempateService {
    private final CriterioDesempateRepository repository;

    public CriterioDesempateService(CriterioDesempateRepository repository) {
        this.repository = repository;
    }

    public Page<CriterioDesempateResponseDto> findAll(Pageable pageable) {
        return repository.findAllByOrderByCodigoAsc(pageable).map(this::toResponse);
    }

    public CriterioDesempateResponseDto findById(Long id) {
        return toResponse(repository.findById(id)
                .orElseThrow(() -> new NotFoundException("CriterioDesempate con id " + id + " no existe")));
    }

    private CriterioDesempateResponseDto toResponse(CriterioDesempate entity) {
        return new CriterioDesempateResponseDto(entity.getId(), entity.getCodigo(), entity.getNombre());
    }
}
