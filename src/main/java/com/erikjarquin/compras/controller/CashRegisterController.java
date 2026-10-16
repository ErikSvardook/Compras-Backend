package com.erikjarquin.compras.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.erikjarquin.compras.model.dto.Cash.CashBoxRequest;
import com.erikjarquin.compras.model.dto.Cash.CashBoxResponse;
import com.erikjarquin.compras.model.dto.Cash.CashResponse;
import com.erikjarquin.compras.model.dto.Cash.CashSummaryResponse;
import com.erikjarquin.compras.model.dto.Cash.CloseCashRequest;
import com.erikjarquin.compras.model.dto.Cash.OpenCashRequest;
import com.erikjarquin.compras.service.CashRegisterService;

/**
 * Caja registradora. Cubre DOS recursos distintos:
 *
 * Cajas físicas ({@code /boxes}): el inventario de cajas del local.
 *       CRUD: listar, crear, editar, dar de baja y borrar (solo si nunca se
 *       abrió).
 * Turnos ({@code /open}, {@code /close}, {@code /active},
 *       {@code /summary}, {@code /history}): la apertura y el cierre. Cada
 *       apertura crea un turno nuevo, y una misma caja puede tener muchos.
 *
 * Permisos: ABRIR_CAJA (registrar, editar, dar de baja y abrir cajas),
 * CERRAR_CAJA, VER_CAJA, CORTE_CAJA.
 * CORS global (${CORS_ALLOWED_ORIGINS}) en SecurityConfig.
 */
@RestController
@RequestMapping("/api/local/cash")
public class CashRegisterController {
    private final CashRegisterService service;

    public CashRegisterController(CashRegisterService service){
        this.service=service;
    }

    //Ver resumen de las ventas
    @PreAuthorize("hasAuthority('CORTE_CAJA')")
    @GetMapping("/summary")
    public CashSummaryResponse getSummary(){
        return service.getSummary();
    }

    /**
     * Registra una caja física del local.
     *
     * Es un CRUD, no abrir caja: esto solo la da de alta en el inventario.
     * Abrir un turno es {@code POST /open}.
     */
    @PreAuthorize("hasAuthority('ABRIR_CAJA')")
    @PostMapping("/boxes")
    public CashBoxResponse createBox(@RequestBody CashBoxRequest request){
        return service.createBox(request);
    }

    /** Tabla "Ver cajas": todas, incluidas las dadas de baja. */
    @PreAuthorize("hasAuthority('VER_CAJA')")
    @GetMapping("/boxes")
    public List<CashBoxResponse> getBoxes(){
        return service.getBoxes();
    }

    /**
     * Cajas que se pueden abrir ahora: activas y sin turno abierto.
     *
     * Usa {@code ABRIR_CAJA} porque es el insumo directo de "abrir caja": quien
     * puede abrir necesita saber qué cajas hay libres.
     */
    @PreAuthorize("hasAuthority('ABRIR_CAJA')")
    @GetMapping("/boxes/openable")
    public List<CashBoxResponse> getOpenable(){
        return service.getOpenable();
    }

    /**
     * Edita una caja (número y descripción).
     *
     * Usa {@code ABRIR_CAJA} y no un {@code EDITAR_CAJA} nuevo a propósito: el
     * inventario de cajas del local es una lista corta y estático que administra
     * la misma persona que abre el turno. No justificaba un permiso aparte, y
     * agregarlo habríaobligado a tocar el bootstrap de roles y permisos.
     */
    @PreAuthorize("hasAuthority('ABRIR_CAJA')")
    @PutMapping("/boxes/{id}")
    public CashBoxResponse updateBox(@PathVariable Long id, @RequestBody CashBoxRequest request){
        return service.updateBox(id, request);
    }

/**
     * Da de ALTA una caja que estaba dada de baja.
     *
     * La caja conserva su número y todo su historial: reactivarla no crea una
     * caja nueva, rehabilita la misma. Por eso el número no se puede volver a
     * usar en otra caja.
     */
    @PreAuthorize("hasAuthority('ABRIR_CAJA')")
    @PatchMapping("/boxes/{id}/active")
    public ResponseEntity<Void> activateBox(@PathVariable Long id){
        service.activateBox(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * BORRA una caja física. Solo funciona si NUNCA se abrió.
     *
     * Con turnos devuelve 409 y el mensaje dice "dala de baja", que es la
     * salida correcta: sus ventas siguen en el historial y no se pueden perder.
     */
    @PreAuthorize("hasAuthority('ABRIR_CAJA')")
    @DeleteMapping("/boxes/{id}")
    public ResponseEntity<Void> deleteBox(@PathVariable Long id){
        service.deleteBox(id);
        return ResponseEntity.noContent().build();
    }

    /** Dar de baja una caja: deja de ofrecerse al abrir, pero sus ventas siguen
     * en el historial. */
    @PreAuthorize("hasAuthority('ABRIR_CAJA')")
    @PatchMapping("/boxes/{id}")
    public ResponseEntity<Void> desactiveBox(@PathVariable Long id){
        service.desactiveBox(id);
        return ResponseEntity.noContent().build();
    }

    //Historial de la caja
    @PreAuthorize("hasAuthority('VER_CAJA')")
    @GetMapping("/boxes/{id}/history")
    public List<CashResponse> getBoxHistory(@PathVariable Long id){
        return service.getBoxHistory(id);
    }

    /**
     * Sugiere el siguiente número libre ("CAJA n") para prellenar el alta de caja.
     *
     * Es solo una sugerencia: el usuario puede escribir otro. Ya no cuenta
     * sobre los cortes (V3) sino sobre las cajas físicas, porque el número ahora
     * es único en {@code cash_boxes} y no en {@code cash_registers}.
     */
    @PreAuthorize("hasAuthority('ABRIR_CAJA')")
    @GetMapping("/next-number")
    public NextNumberResponse getNextNumber(){
        return new NextNumberResponse(service.getNextSuggestedNumber());
    }

    //Abrir una caja ya registrada
    @PreAuthorize("hasAuthority('ABRIR_CAJA')")
    @PostMapping("/open")
    public CashResponse open(@RequestBody OpenCashRequest request){
        return service.open(request);
    }

    //Cerrar la caja
    @PreAuthorize("hasAuthority('CERRAR_CAJA')")
    @PostMapping("/close")
    public CashResponse close(@RequestBody CloseCashRequest request){
        return service.close(request);
    }

    //Ver si la caja esta activa
    @PreAuthorize("hasAuthority('VER_CAJA')")
    @GetMapping("/active")
    public CashResponse getActiveCash(){
        return service.getActiveCash();
    }

    /**
     * Historial de cajas: la mas reciente primero. Es lo que alimenta el
     * selector "filtrar por caja" de Reportes, asi que se cuelga de
     * {@code VER_CAJA} y no de {@code CORTE_CAJA}: ver una lista de cajas es
     * consultar, no hacer un corte.
     */
    @PreAuthorize("hasAuthority('VER_CAJA')")
    @GetMapping("/history")
    public List<CashResponse> getHistory(){
        return service.getHistory();
    }

    //Detalle de una caja por su numero (V3)
    @PreAuthorize("hasAuthority('VER_CAJA')")
    @GetMapping("/number/{number}")
    public CashResponse getByNumber(@PathVariable String number){
        return service.getByNumber(number);
    }

    /**
     * Envoltorio de {@code GET /cash/next-number}: un endpoint que devuelve un
     * String pelado es raro de consumir desde TypeScript, que espera un objeto
     * con la propiedad nombrada.
     */
    public record NextNumberResponse(String suggestedNumber){}

}
