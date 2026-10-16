package com.erikjarquin.compras.mapper;

import com.erikjarquin.compras.model.dto.Permissions.PermissionResponse;
import com.erikjarquin.compras.model.entity.PermissionEntity;

/**
 * Mapper estático permiso -> DTO. El constructor privado evita instanciarlo
 * (solo expone el método estático toDto).
 */
public class PermissionMapper {
    private PermissionMapper(){}

    public static PermissionResponse toDto(PermissionEntity entity){
        if(entity == null){
            return null;
        }

        return new PermissionResponse(
            entity.getId(),
            entity.getName().name());
    }
}
