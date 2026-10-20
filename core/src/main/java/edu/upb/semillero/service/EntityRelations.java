package edu.upb.semillero.service;

import edu.upb.semillero.repository.entity.BaseEntity;
import edu.upb.semillero.service.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class EntityRelations {
    private final EntityManager entityManager;

    public EntityRelations(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public <E extends BaseEntity> E find(Class<E> type, Long id) {
        if (id == null) {
            return null;
        }
        E entity = entityManager.find(type, id);
        if (entity == null) {
            throw new NotFoundException(type.getSimpleName() + " con id " + id + " no existe");
        }
        return entity;
    }

    public Long id(BaseEntity entity) {
        return entity == null ? null : entity.getId();
    }
}
