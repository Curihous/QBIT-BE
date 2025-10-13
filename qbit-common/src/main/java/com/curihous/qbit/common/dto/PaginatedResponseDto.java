package com.curihous.qbit.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PaginatedResponseDto<T>(
    int currentPage,
    
    int pageSize,
    
    long totalElements,
    
    int totalPages,
    
    boolean hasNext,
    
    List<T> content
) {
    public static <T> PaginatedResponseDto<T> from(Page<T> page) {
        return new PaginatedResponseDto<>(
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext(),
            page.getContent()
        );
    }
}

