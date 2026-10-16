package com.erikjarquin.compras.mapper;

import com.erikjarquin.compras.model.dto.User.UserDto;
import com.erikjarquin.compras.model.entity.UserEntity;

/**
 * Mapper estático usuario -> DTO.
 * NUNCA expone el password (ni siquiera hasheado) fuera de la capa de servicio.
 */
public class UserMapper {
    public static UserDto toDto(UserEntity entity){
        if(entity == null) return null;

        UserDto dto = new UserDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setEmail(entity.getEmail());
        dto.setActive(entity.isActive());
        // Fechas de alta y baja: sin esto el frontend no puede filtrar la
        // tabla de dados de baja por rango de fechas.
        dto.setActivatedAt(entity.getActivatedAt());
        dto.setDeactivatedAt(entity.getDeactivatedAt());

        if (entity.getRole() != null) {
            dto.setRoleId(entity.getRole().getId());
            dto.setRoleName(entity.getRole().getName());
        }
        
        return dto;
    }
}
