package org.masumjia.reactcartecom.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CategoryDtos {
    public static record CreateCategoryRequest(@NotBlank @Size(max = 100) String name) {}
    public static record UpdateCategoryRequest(@Size(max = 100) String name,
                                               @Size(max = 120) String slug) {}
    public static record CategoryView(String id, String name, String slug) {}
}
