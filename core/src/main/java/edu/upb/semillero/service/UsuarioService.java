package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.request.UsuarioRequestDto;
import edu.upb.semillero.repository.dto.response.UsuarioResponseDto;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.repos.UsuarioRepository;
import edu.upb.semillero.service.exception.BusinessException;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UsuarioService {
    private final UsuarioRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public Page<UsuarioResponseDto> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(this::toResponse);
    }

    public UsuarioResponseDto findById(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public UsuarioResponseDto create(UsuarioRequestDto request) {
        Usuario entity = new Usuario();
        if (request.password() == null || request.password().isBlank()) {
            throw new BusinessException("La contraseña es obligatoria");
        }
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public UsuarioResponseDto update(Long id, UsuarioRequestDto request) {
        Usuario entity = require(id);
        apply(entity, request);
        return toResponse(repository.saveAndFlush(entity));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(require(id));
        repository.flush();
    }

    private Usuario require(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuario con id " + id + " no existe"));
    }

    private void apply(Usuario entity, UsuarioRequestDto request) {
        entity.setName(request.name());
        entity.setEmail(request.email());
        entity.setTelefonoUsuario(request.telefonoUsuario());
        entity.setDocIdentidadUsuario(request.docIdentidadUsuario());
        if (request.password() != null && !request.password().isBlank()) {
            entity.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        entity.setNombres(request.nombres());
        entity.setApellidos(request.apellidos());
        entity.setFotoPerfil(request.fotoPerfil());
    }

    private UsuarioResponseDto toResponse(Usuario entity) {
        return new UsuarioResponseDto(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                entity.getTelefonoUsuario(),
                entity.getDocIdentidadUsuario(),
                entity.getNombres(),
                entity.getApellidos(),
                entity.getFotoPerfil());
    }
}
