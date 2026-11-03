package edu.upb.semillero.service;

import edu.upb.semillero.repository.dto.response.NotificacionResponseDto;
import edu.upb.semillero.repository.entity.Notificacion;
import edu.upb.semillero.repository.entity.Usuario;
import edu.upb.semillero.repository.repos.NotificacionRepository;
import edu.upb.semillero.service.exception.ForbiddenException;
import edu.upb.semillero.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@Transactional(readOnly = true)
public class NotificacionService {
    private final NotificacionRepository repository;
    private final EntityRelations relations;

    public NotificacionService(NotificacionRepository repository, EntityRelations relations) {
        this.repository = repository;
        this.relations = relations;
    }

    @Transactional
    public void crear(Long usuarioId, String tipo, String titulo, String mensaje,
            Long referenciaId, String referenciaTipo) {
        if (usuarioId == null) return;
        Notificacion entity = new Notificacion();
        entity.setUsuario(relations.find(Usuario.class, usuarioId));
        entity.setModulo("RECLUTAMIENTO");
        entity.setTipo(tipo);
        entity.setTitulo(titulo);
        entity.setMensaje(mensaje);
        entity.setLeida(false);
        entity.setFecha(LocalDateTime.now());
        entity.setReferenciaId(referenciaId);
        entity.setReferenciaTipo(referenciaTipo);
        repository.save(entity);
    }

    public Page<NotificacionResponseDto> listar(Long usuarioId, Boolean leida, Pageable pageable) {
        Page<Notificacion> entities = leida == null
                ? repository.findByUsuarioIdOrderByFechaDesc(usuarioId, pageable)
                : repository.findByUsuarioIdAndLeidaOrderByFechaDesc(usuarioId, leida, pageable);
        return entities.map(this::toResponse);
    }

    public long contarNoLeidas(Long usuarioId) {
        return repository.countByUsuarioIdAndLeidaFalse(usuarioId);
    }

    @Transactional
    public void marcarLeida(Long id, Long usuarioId) {
        Notificacion entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Notificación con id " + id + " no existe"));
        if (!usuarioId.equals(entity.getUsuario().getId())) {
            throw new ForbiddenException("No puedes modificar una notificación de otra cuenta");
        }
        entity.setLeida(true);
    }

    private NotificacionResponseDto toResponse(Notificacion entity) {
        return new NotificacionResponseDto(entity.getId(), entity.getModulo(), entity.getTipo(),
                entity.getTitulo(), entity.getMensaje(), Boolean.TRUE.equals(entity.getLeida()),
                entity.getFecha(), entity.getReferenciaId(), entity.getReferenciaTipo());
    }
}
