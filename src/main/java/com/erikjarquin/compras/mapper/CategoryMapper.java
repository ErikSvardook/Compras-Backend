package com.erikjarquin.compras.mapper;

import com.erikjarquin.compras.model.dto.Categories.CategoryDto;
import com.erikjarquin.compras.model.entity.CategoryEntity;
/**
 * Mapper estático categoría <--> DTO (solo id+name).
 */
public class CategoryMapper {
    
    public static CategoryDto toDto(CategoryEntity entity){
        if(entity == null){
            return null;
        }

        CategoryDto dto = new CategoryDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());

        return dto;
    }

    public static CategoryEntity toEntity(CategoryDto dto){
        if(dto == null){
            return null;
        }

        CategoryEntity entity = new CategoryEntity();
        entity.setId(dto.getId());
        entity.setName(dto.getName());

        return entity;
    }
}
