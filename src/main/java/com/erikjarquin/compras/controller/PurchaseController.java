package com.erikjarquin.compras.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.erikjarquin.compras.model.dto.Purchases.PurchaseDTO;
import com.erikjarquin.compras.model.dto.Purchases.PurchaseRequest;
import com.erikjarquin.compras.service.PurchaseService;

/**
 * Módulo de COMPRAS: registro de mercancía comprada a proveedores.
 *
 * Público dentro del sistema (autenticado), protegido por permiso:
 *  - VER_COMPRAS        -> GET (todos, por proveedor y detalle)
 *  - CREAR_COMPRAS      -> POST (registra la compra PENDIENTE, sin efecto en stock)
 *  - CONFIRMAR_COMPRAS  -> PATCH /{id}/confirm (suma stock y guarda el costo real)
 *  - CANCELAR_COMPRAS   -> DELETE /{id} (borra la compra; 409 si está confirmada)
 */
@RestController
@RequestMapping("/api/local/purchases")
public class PurchaseController {
    private final PurchaseService purchaseService;

    public PurchaseController(PurchaseService purchaseService){
        this.purchaseService = purchaseService;
    }

    //Listado de todas las compras (más recientes primero)
    @PreAuthorize("hasAuthority('VER_COMPRAS')")
    @GetMapping
    public List<PurchaseDTO> getAll(){
        return purchaseService.getAll();
    }

    //Detalle de una compra (renglones + producto)
    @PreAuthorize("hasAuthority('VER_COMPRAS')")
    @GetMapping("/{id}")
    public PurchaseDTO getById(@PathVariable Long id){
        return purchaseService.getById(id);
    }

    //Compras de un proveedor concreto (?providerId=)
    @PreAuthorize("hasAuthority('VER_COMPRAS')")
    @GetMapping("/provider/{providerId}")
    public List<PurchaseDTO> getByProvider(@PathVariable Long providerId){
        return purchaseService.getByProvider(providerId);
    }

    //Registrar una compra (nace PENDIENTE: todavía no suma stock)
    @PreAuthorize("hasAuthority('CREAR_COMPRAS')")
    @PostMapping
    public ResponseEntity<PurchaseDTO> create(@RequestBody PurchaseRequest request){
        return ResponseEntity.status(HttpStatus.CREATED).body(purchaseService.create(request));
    }

    /**
     * CONFIRMAR una compra: la mercancía entró al almacén, así que ahora sí
     * suma el stock de sus productos y guarda el costo real de cada uno.
     *
     * Se usa PATCH y no PUT porque es un cambio parcial de
     * estado: un PUT reenviaría la compra entera y podría pisar el total o los
     * renglones. Mismo criterio que {@code PATCH /api/local/sales/{id}/confirm}.
     *
     * Es idempotente: si ya estaba confirmada devuelve 200 sin volver a
     * sumar el stock (doble clic o reintento tras corte de red).
     */
    @PreAuthorize("hasAuthority('CONFIRMAR_COMPRAS')")
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<PurchaseDTO> confirm(@PathVariable Long id){
        return ResponseEntity.ok(purchaseService.confirm(id));
    }

    //Cancelar una compra PENDIENTE (la borra; 409 si ya está confirmada)
    @PreAuthorize("hasAuthority('CANCELAR_COMPRAS')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id){
        purchaseService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}