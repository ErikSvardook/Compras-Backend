package com.erikjarquin.compras.service;

import java.util.List;

import com.erikjarquin.compras.model.dto.Cash.CashBoxRequest;
import com.erikjarquin.compras.model.dto.Cash.CashBoxResponse;
import com.erikjarquin.compras.model.dto.Cash.CashResponse;
import com.erikjarquin.compras.model.dto.Cash.CashSummaryResponse;
import com.erikjarquin.compras.model.dto.Cash.CloseCashRequest;
import com.erikjarquin.compras.model.dto.Cash.OpenCashRequest;

/**
 * Contrato de la caja registradora.
 *
 * Cajas y turnos son dos cosas. Un negocio tiene pocas cajas
 * físicas y muchos turnos, así que el modelo separa:
 * 
 * {@code cash_boxes}: las cajas FÍSICAS del local. Se registran con
 * {@link #createBox} y se editan con {@link #updateBox}. Se dan de baja
 * con {@link #desactiveBox}, nunca se borran.
 * {@code cash_registers}: los TURNOS. Se crean con {@link #open} y se
 * cierran con {@link #close}. Una misma caja puede tener muchos.
 *
 * Antes estas dos ideas vivían en una sola tabla y por eso una caja se
 * podía abrir una sola vez: su número era UNIQUE. Ese error de modelo es
 * precisely lo que V4 corrige.
 *
 * @see com.erikjarquin.compras.service.impl.CashRegisterImpl
 */
public interface CashRegisterService {

    // ===== Turnos (cash_registers) =====

    //Abre un turno con una caja física ya registrada (fondo inicial mínimo 100)
    CashResponse open(OpenCashRequest request);

    //Cierra el turno exigiendo que el efectivo cuadre (salida de emergencia con motivo)
    CashResponse close(CloseCashRequest request);

    //El turno abierto ahora mismo
    CashResponse getActiveCash();

    //Resumen del corte del turno abierto
    CashSummaryResponse getSummary();

    //Historial de turnos (la más reciente primero) y detalle por número
    List<CashResponse> getHistory();
    CashResponse getByNumber(String number);

    // ===== Cajas físicas (cash_boxes) =====

    //Registra una caja física nueva
    CashBoxResponse createBox(CashBoxRequest request);

    //Todas las cajas del local, incluidas las dadas de baja
    List<CashBoxResponse> getBoxes();

    //Cajas que se pueden abrir ahora (activas y sin turno abierto)
    List<CashBoxResponse> getOpenable();

    //Edita número y descripción de una caja
    CashBoxResponse updateBox(Long id, CashBoxRequest request);

    //Da de baja una caja (deja de ofrecerse, pero sus ventas siguen en el historial)
    void desactiveBox(Long id);

    //Da de ALTA una caja que estaba dada de baja (mismo numero, mismo historial)
    void activateBox(Long id);

    //Borra una caja SOLO si nunca se abrió. Con turnos: 409 y hay que darla de baja.
    void deleteBox(Long id);

    //Historial de una caja: un corte por turno
    List<CashResponse> getBoxHistory(Long boxId);

    //Sugiere el siguiente número libre con el patrón "CAJA n"
    String getNextSuggestedNumber();
}
