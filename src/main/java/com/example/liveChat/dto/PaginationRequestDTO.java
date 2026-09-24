package com.example.liveChat.dto;

import com.example.liveChat.exceptions.InvalidRequestException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record PaginationRequestDTO(int page, int size) {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PaginationRequestDTO {
        if (page < 0) {
            throw new InvalidRequestException("Page must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidRequestException("Size must be between 1 and " + MAX_SIZE);
        }
    }

    public static PaginationRequestDTO from(String page, String size) {
        return new PaginationRequestDTO(parse(page, "page"), parse(size, "size"));
    }

    public Pageable toPageable() {
        return PageRequest.of(page, size);
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page, size, sort);
    }

    private static int parse(String value, String parameter) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new InvalidRequestException(parameter + " must be an integer");
        }
    }
}
