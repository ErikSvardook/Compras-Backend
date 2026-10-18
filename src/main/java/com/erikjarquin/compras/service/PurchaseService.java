package com.erikjarquin.compras.service;

import java.util.List;

import com.erikjarquin.compras.model.dto.Purchases.PurchaseDTO;
import com.erikjarquin.compras.model.dto.Purchases.PurchaseRequest;

/**
 * Contrato del módulo de COMPRAS (registro de mercancía y su detalle).
 * Ver {@code service/impl/PurchaseImpl}.
 */
public interface PurchaseService {
    //Registrar una compra. Nace PENDIENTE: no suma stock ni cambia costo
    PurchaseDTO create(PurchaseRequest request);

    //Listado completo de compras (más recientes primero)
    List<PurchaseDTO> getAll();

    //Compras de un proveedor concreto
    List<PurchaseDTO> getByProvider(Long providerId);

    //Detalle de una compra (con sus renglones)
    PurchaseDTO getById(Long id);

    //Cancelar una compra PENDIENTE: la borra (nunca tocó el inventario).
    //409 si ya está confirmada (su stock y su costo son hechos reales)
    void cancel(Long id);

    //Confirmar una compra: suma el stock y guarda el costo real de cada producto.
    //Idempotente: confirmar dos veces NO suma el stock dos veces
    PurchaseDTO confirm(Long id);
}