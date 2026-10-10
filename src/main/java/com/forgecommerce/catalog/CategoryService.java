package com.forgecommerce.catalog;

import com.forgecommerce.catalog.dto.CategoryRequest;
import com.forgecommerce.catalog.dto.CategoryResponse;
import com.forgecommerce.common.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

  private final CategoryRepository categories;

  public CategoryService(CategoryRepository categories) {
    this.categories = categories;
  }

  @Transactional(readOnly = true)
  public List<CategoryResponse> list() {
    return categories.findAll().stream().map(c -> new CategoryResponse(c.getId(), c.getName())).toList();
  }

  @Transactional
  public CategoryResponse create(CategoryRequest req) {
    String name = req.name().trim();
    if (categories.existsByNameIgnoreCase(name)) {
      throw new IllegalStateException("Category already exists");
    }
    Category saved = categories.save(new Category(name));
    return new CategoryResponse(saved.getId(), saved.getName());
  }

  @Transactional
  public CategoryResponse rename(Long id, CategoryRequest req) {
    Category category =
        categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    String name = req.name().trim();
    if (!category.getName().equalsIgnoreCase(name) && categories.existsByNameIgnoreCase(name)) {
      throw new IllegalStateException("Category already exists");
    }
    category.rename(name);
    return new CategoryResponse(category.getId(), category.getName());
  }

  @Transactional
  public void delete(Long id) {
    if (!categories.existsById(id)) {
      throw new ResourceNotFoundException("Category not found");
    }
    categories.deleteById(id);
  }
}
