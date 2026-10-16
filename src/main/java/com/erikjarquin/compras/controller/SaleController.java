package com.erikjarquin.compras.controller;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.erikjarquin.compras.model.dto.Sale.SaleDetailHistoryResponse;
import com.erikjarquin.compras.model.dto.Sale.SaleHistoryResponse;
import com.erikjarquin.compras.model.dto.Sale.SaleRequest;
import com.erikjarquin.compras.model.dto.Sale.SaleResponse;
import com.erikjarquin.compras.service.SaleService;

import lombok.extern.slf4j.Slf4j;

/**
 * Ventas: registrar una venta (efectivo o tarjeta), consultar el historial y
 * gestionar su ciclo de vida (confirmar / anular).
 *
 * Permisos por operación:
 *   {@code CREAR_VENTAS}    → {@code POST} (cobrar)
 *   {@code VER_VENTAS}      → {@code GET} lista y detalle
 *   {@code CONFIRMAR_VENTAS}→ {@code PATCH /{id}/confirm} (cerrar, congela)
 *   {@code CANCELAR_VENTAS} → {@code PATCH /{id}/cancel} (anular, devuelve stock)
 *
 * No existe {@code DELETE /{id}} a propósito: las ventas no se borran
 * nunca. Se anulan (quedan con {@code cancelled = true}) para que el historial
 * siga siendo auditable.
 *
 * CORS global en SecurityConfig (${CORS_ALLOWED_ORIGINS}), sin
 * {@code @CrossOrigin} aquí.
 */
@Slf4j
@RestController
@RequestMapping("/api/local/sales")
public class SaleController {
    private final SaleService service;

    public SaleController(SaleService service){
        this.service=service;
    }

    //Realizar la venta
    @PreAuthorize("hasAuthority('CREAR_VENTAS')")
    @PostMapping
    public ResponseEntity<SaleResponse> processSale(@RequestBody SaleRequest request){
        try {
            SaleResponse response = service.processSale(request);
            return ResponseEntity.ok(response);
        }
        catch(DataIntegrityViolationException e){
            /*====== IDEMPOTENCIA: DOS PETICIONES DEL MISMO COBRO A LA VEZ ======

              Este es el ÚNICO caso que la comprobación previa de SaleImpl no
              puede cubrir, y es importante entender por qué.

              `processSale` primero busca la clave y, si no la encuentra, inserta.
              El problema: si las dos peticiones llegan juntas, las dos buscan,
              las dos no encuentran nada, y las dos intentan insertar. El índice
              UNIQUE hace su trabajo y a una la rechaza...

              ...pero aquí está el detalle no obvio que hace que esto funcione:
              en PostgreSQL, el INSERT perdedor se BLOQUEA dentro del índice hasta
              que la transacción ganadora resuelve. El error de clave duplicada
              solo se emite cuando la ganadora ya hizo COMMIT. Por eso la venta
              que buscamos aquí YA EXISTE y es visible: no hay carrera al
              releerla.

              Sin este catch, el perdedor devolvería 500 y el cajero vería un
              error aunque su venta estuviera cobrada y registrada.
            */
            String clave = request.getIdempotencyKey();
            log.warn("Intento de cobro duplicado detectado (carrera simultanea). "
                    + "Se devuelve la venta ya registrada.");

            return service.findByIdempotencyKey(clave)
                    .map(ResponseEntity::ok)
                    .orElseThrow(() -> e);
        }
    }

    //Ver los detalles de la venta
    @PreAuthorize("hasAuthority('VER_VENTAS')")
    @GetMapping
    public List<SaleHistoryResponse> getSales(){
        return service.getSales();
    }

    //Ver venta por ID
    @PreAuthorize("hasAuthority('VER_VENTAS')")
    @GetMapping("/{id}")
    public SaleDetailHistoryResponse getSaleById(@PathVariable Long id){
        return service.getSaleById(id);
    }

    /**
     * CONFIRMAR una venta (la congela para siempre).
     *
     * Se ejecuta cuando el pedido ya salió del mostrador. A partir de aquí
     * la venta no se puede anular ni borrar jamás.
     *
     * Por qué PATCH y no POST ni PUT: PATCH significa "cambio
     * parcial": no se reenvía la venta entera, solo se le pide mover su estado
     * a confirmado. Es la semántica correcta y además evita el problema clásico
     * de un PUT que reenvía campos viejos y pisa datos que otro usuario cambió.
     * Tampoco se usa POST porque la venta ya existe: no se crea nada.
     *
     * Permiso propio ({@code CONFIRMAR_VENTAS}) y no {@code CREAR_VENTAS}:
     * cobrar y "dar por salida la comida" son responsabilidades distintas. En un
     * POS real quien cobra (caja) y quien entrega (cocina/mostrador) suelen ser
     * personas distintas; con un permiso único cualquiera podría cerrar la
     * venta de otro y dejarlo sin poder corregirla.
     *
     * Devuelve 200 con la venta actualizada. Si ya estaba confirmada también
     * devuelve 200 (idempotencia); los 409 son para el caso incompatible de
     * verdad, que es intentar confirmar una venta ya anulada.
     */
    @PreAuthorize("hasAuthority('CONFIRMAR_VENTAS')")
    @PatchMapping("/{id}/confirm")
    public ResponseEntity<SaleHistoryResponse> confirm(@PathVariable Long id){
        return ResponseEntity.ok(service.confirm(id));
    }

    /**
     * ANULAR una venta: devuelve el stock al inventario y conserva el registro
     * con {@code cancelled = true}.
     *
     * No borra la fila. En un POS el historial es evidencia contable: una
     * venta anulada que desaparece deja el mismo hueco en los números que una
     * venta que nunca existió, y no hay forma de auditar quién la quitó.
     *
     * Rechazos (409) que devuelve el servicio, no el controller:
     *  la venta ya está confirmada (congelada),
     *  la venta ya estaba anulada (el stock ya volvió una vez),
     *  la venta fue con tarjeta: el pago está capturado y necesita una
     *       reversa real vía {@code POST /api/local/payments/reverse/{id}}.
     *
     * Permiso {@code CANCELAR_VENTAS} para que un cajero con
     * {@code CREAR_VENTAS} no pueda deshacer ventas por su cuenta.
     */
    @PreAuthorize("hasAuthority('CANCELAR_VENTAS')")
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<SaleHistoryResponse> cancel(@PathVariable Long id){
        return ResponseEntity.ok(service.cancel(id));
    }
}
