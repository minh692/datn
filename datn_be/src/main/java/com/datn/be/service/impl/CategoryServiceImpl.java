package com.datn.be.service.impl;

import com.datn.be.dto.request.category.CategoryCreateRequestDTO;
import com.datn.be.dto.request.category.CategoryUpdateRequestDTO;
import com.datn.be.dto.response.ResultPaginationResponse;
import com.datn.be.dto.response.category.CategoryResponse;
import com.datn.be.exception.InvalidDataException;
import com.datn.be.exception.ResourceNotFoundException;
import com.datn.be.model.Category;
import com.datn.be.repository.CategoryRepository;
import com.datn.be.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;

    @Override
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Override
    public CategoryResponse create(CategoryCreateRequestDTO categoryCreateRequestDTO) {
        if(categoryRepository.existsByName(categoryCreateRequestDTO.getName())) {
            throw new InvalidDataException("Category name already exist");
        }
        Category category = Category.builder()
                .name(categoryCreateRequestDTO.getName())
                .description(categoryCreateRequestDTO.getDescription())
                .thumbnail(categoryCreateRequestDTO.getThumbnail())
                .hot(categoryCreateRequestDTO.isHot())
                .active(true)
                .build();

        return CategoryResponse.fromCategoryToCategoryResponse(categoryRepository.save(category));
    }

    @Override
    public CategoryResponse update(CategoryUpdateRequestDTO categoryUpdateRequestDTO) {
        Category category = this.getCategoryById(categoryUpdateRequestDTO.getId());
        category.setName(categoryUpdateRequestDTO.getName());
        category.setThumbnail(categoryUpdateRequestDTO.getThumbnail());
        category.setDescription(categoryUpdateRequestDTO.getDescription());
        category.setHot(categoryUpdateRequestDTO.isHot());
        category.setActive(categoryUpdateRequestDTO.isActive());

        return CategoryResponse.fromCategoryToCategoryResponse(categoryRepository.save(category));
    }

    @Override
    public void delete(Long id) {
        Category category = getCategoryById(id);
        category.setActive(false);
        categoryRepository.save(category);
    }

    @Override
    public ResultPaginationResponse getAllCategories(Specification<Category> specification, Pageable pageable) {

        Page<Category> categoryPage = categoryRepository.findAll(specification, pageable);

        ResultPaginationResponse.Meta meta = ResultPaginationResponse.Meta.builder()
                .total(categoryPage.getTotalElements())
                .pages(categoryPage.getTotalPages())
                .page(pageable.getPageNumber() + 1)
                .pageSize(pageable.getPageSize())
                .build();
        List<CategoryResponse> categoryResponses = categoryPage.getContent()
                .stream().map(CategoryResponse::fromCategoryToCategoryResponse).toList();

        return ResultPaginationResponse.builder()
                .meta(meta)
                .result(categoryResponses)
                .build();
    }

    @Override
    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }
}
