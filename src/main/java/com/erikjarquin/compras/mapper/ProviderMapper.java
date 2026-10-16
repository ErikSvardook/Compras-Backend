package com.erikjarquin.compras.mapper;

import com.erikjarquin.compras.model.dto.Purchases.ProviderDto;
import com.erikjarquin.compras.model.entity.ProviderEntity;

/**
 * Mapper estático proveedor <--> DTO. El servicio usa toEntity para la parte de
 * entrada y toDto para todos los listados/respuestas.
 */
public class ProviderMapper {

    //Entity → DTO
    public static ProviderDto toDto(ProviderEntity entity){
        if(entity == null) return null;

        ProviderDto dto = new ProviderDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setRfc(entity.getRfc());
        dto.setPhone(entity.getPhone());
        dto.setEmail(entity.getEmail());
        return dto;
    }

    //DTO → Entity (los campos nulos/obligatorios los valida el servicio)
    public static ProviderEntity toEntity(ProviderDto dto){
        ProviderEntity entity = new ProviderEntity();
        entity.setName(dto.getName());
        entity.setRfc(dto.getRfc());
        entity.setPhone(dto.getPhone());
        entity.setEmail(dto.getEmail());
        return entity;
    }
}