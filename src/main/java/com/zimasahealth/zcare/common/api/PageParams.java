package com.zimasahealth.zcare.common.api;

import com.zimasahealth.zcare.common.error.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** 1-based paging parameters (04B section 8), validated into per-field business exceptions. */
public record PageParams(int page, int pageSize) {

    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 200;

    public static PageParams of(Integer page, Integer pageSize) {
        int p = page == null ? 1 : page;
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (p < 1) {
            throw BusinessException.invalid("page", "page is 1-based and must be at least 1");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw BusinessException.invalid("pageSize", "pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        return new PageParams(p, size);
    }

    public Pageable toPageable(Sort sort) {
        return PageRequest.of(page - 1, pageSize, sort);
    }
}
