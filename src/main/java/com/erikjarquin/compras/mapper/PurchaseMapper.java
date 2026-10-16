package com.erikjarquin.compras.mapper;

import com.erikjarquin.compras.model.dto.Purchases.PurchaseDTO;
import com.erikjarquin.compras.model.dto.Purchases.PurchaseItemDTO;
import com.erikjarquin.compras.model.entity.PurchaseDetailEntity;
import com.erikjarquin.compras.model.entity.PurchaseEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper estático compra -> PurchaseDTO. Requiere que los listados vengan con
 * provider y details.product precargados (lo garantiza @EntityGraph del
 * repositorio) para leer providerName y productName sin consultas extra.
 */
public class PurchaseMapper {

    //Entity → DTO (con sus renglones)
    public static PurchaseDTO toDto(PurchaseEntity entity){
        if(entity == null) return null;

        PurchaseDTO dto = new PurchaseDTO();
        dto.setId(entity.getId());
        dto.setPurchaseDate(entity.getPurchaseDate());
        dto.setTotal(entity.getTotal());

        //Ciclo de vida de la compra: el frontend lo usa para pintar el
        //estado y para decidir si el botón "Confirmar" tiene sentido.
        dto.setConfirmed(entity.isConfirmed());
        dto.setConfirmedAt(entity.getConfirmedAt());

        if(entity.getProvider() != null){
            dto.setProviderId(entity.getProvider().getId());
            dto.setProviderName(entity.getProvider().getName());
        }

        List<PurchaseItemDTO> items = new ArrayList<>();
        if(entity.getDetails() != null){
            for(PurchaseDetailEntity detail : entity.getDetails()){
                items.add(toItemDto(detail));
            }
        }
        dto.setItems(items);
        dto.setTotalItems(items.size());

        return dto;
    }

    //Renglón → DTO
    public static PurchaseItemDTO toItemDto(PurchaseDetailEntity detail){
        PurchaseItemDTO item = new PurchaseItemDTO();
        item.setQuantity(detail.getQuantity());
        item.setUnitCost(detail.getUnitCost());
        item.setUnitPrice(detail.getUnitPrice()); //Precio de venta del renglón
        item.setSubtotal(detail.getSubtotal());

        if(detail.getProduct() != null){
            item.setProductId(detail.getProduct().getId());
            item.setProductName(detail.getProduct().getName());
        }

        return item;
    }
}