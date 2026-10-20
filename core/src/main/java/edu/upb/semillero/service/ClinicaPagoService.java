package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.ClinicaPagoRequestDto;
import edu.upb.semillero.repository.dto.response.ClinicaPagoResponseDto;
import edu.upb.semillero.repository.entity.ClinicaPago;
import edu.upb.semillero.repository.entity.Club;
import edu.upb.semillero.repository.entity.UsuarioClub;
import edu.upb.semillero.repository.repos.ClinicaPagoRepository;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ClinicaPagoService {
    private final ClinicaPagoRepository repository;
    private final EntityRelations relations;

    public ClinicaPagoService(ClinicaPagoRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    public Page<ClinicaPagoResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public ClinicaPagoResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public ClinicaPagoResponseDto create(ClinicaPagoRequestDto request) {
        ClinicaPago entity = new ClinicaPago();
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public ClinicaPagoResponseDto update(Long id, ClinicaPagoRequestDto request) {
        ClinicaPago entity = require(id);
        apply(entity, request);
        validate(entity);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private ClinicaPago require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("ClinicaPago con id " + id + " no existe"));
    }

    private void apply(ClinicaPago entity, ClinicaPagoRequestDto request) {
        entity.setFechaInicioClinica(request.fechaInicioClinica());
        entity.setFechaFinClinica(request.fechaFinClinica());
        entity.setClub(relations.find(Club.class, request.clubId()));
        entity.setUsuarioClub(relations.find(UsuarioClub.class, request.usuarioClubId()));
    }

    private ClinicaPagoResponseDto toResponse(ClinicaPago entity) {
        return new ClinicaPagoResponseDto(
                entity.getId(),
                entity.getFechaInicioClinica(),
                entity.getFechaFinClinica(),
                relations.id(entity.getClub()),
                relations.id(entity.getUsuarioClub()));
    }

    private void validate(ClinicaPago entity) {
        if (entity.getFechaInicioClinica() != null && entity.getFechaFinClinica() != null
                && entity.getFechaFinClinica().isBefore(entity.getFechaInicioClinica())) {
            throw new BusinessException("La fecha final no puede ser anterior a la fecha inicial");
        }
    }
}
