package edu.upb.semillero.controller.dto;

import edu.upb.semillero.repository.dto.response.NotificacionResponseDto;
import java.util.List;
import org.springframework.data.domain.Page;

public record NotificacionesPageResponse(List<NotificacionResponseDto> content, int page,
        int size, long totalElements, int totalPages, long totalNoLeidas) {
    public static NotificacionesPageResponse from(Page<NotificacionResponseDto> page, long unread) {
        return new NotificacionesPageResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), unread);
    }
}
