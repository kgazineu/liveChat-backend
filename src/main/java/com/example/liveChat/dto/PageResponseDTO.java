package com.example.liveChat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "Página estável de resultados")
public record PageResponseDTO<T>(
        @Schema(description = "Itens da página atual") List<T> content,
        @Schema(description = "Índice da página atual, iniciado em zero", example = "0") int page,
        @Schema(description = "Quantidade máxima de itens na página", example = "20") int size,
        @Schema(description = "Quantidade total de itens", example = "42") long totalElements,
        @Schema(description = "Quantidade total de páginas", example = "3") int totalPages,
        @Schema(description = "Indica se esta é a última página", example = "false") boolean last) {

    public static <T> PageResponseDTO<T> from(Page<T> page) {
        return new PageResponseDTO<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isLast());
    }
}
