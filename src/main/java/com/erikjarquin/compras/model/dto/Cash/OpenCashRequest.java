package com.erikjarquin.compras.model.dto.Cash;

import java.math.BigDecimal;

/**
 * Petición de apertura de un turno ({@code POST /api/local/cash/open}).
 *
 * number es la caja FÍSICA que se va a abrir, y tiene que existir en
 * {@code cash_boxes} ({@code POST /api/local/cash/boxes}). Hay dos motivos por los
 * que el número viene aquí y no se crea en este mismo request:
 *   El número identifica a la caja en el inventario y en los reportes, así
 *       que tiene que existir antes para poder elegirse de una lista.
 *   Una caja se abre MUCHAS veces (una por día, por ejemplo). Si este
 *       endpoint creara la caja, la segunda vez daría conflicto de unicidad.
 * Ver {@code CashRegisterImpl.open} para el detalle.
 *
 * openingAmount es el fondo con el que arranca el turno. Tiene un
 * mínimo de 100 porque una caja que abre con 0 o con 50 no permite ni una venta
 * pequeña y además esconde los errores de captura: escribir "0" casi siempre
 * significa que se olvidó el campo, no que el cajón esté vacío.
 */
public class OpenCashRequest {

    /**
     * Fondo inicial del turno. Obligatorio, no negativo y <b>mínimo 100</b>
     * (regla del dueño, 2026-09-30).
     */
    private BigDecimal openingAmount;

    /**
     * Número de la caja física que se abre.
     *
     * Ya NO tiene que estar "sin usar": como una caja se abre todos los días,
     * lo único que se exige es que exista, que esté activa y que no tenga un
     * turno abierto ahora mismo.
     */
    private String number;

    public OpenCashRequest(){}

    //Getter y setter de openingAmount
    public BigDecimal getOpeningAmount(){
        return openingAmount;
    }

    public void setOpeningAmount(BigDecimal openingAmount){
        this.openingAmount = openingAmount;
    }

    //Getter y setter de number
    public String getNumber(){
        return number;
    }

    public void setNumber(String number){
        this.number = number;
    }
}
