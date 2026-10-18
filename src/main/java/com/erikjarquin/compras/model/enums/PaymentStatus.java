package com.erikjarquin.compras.model.enums;

/**
 * Ciclo de vida de un pago con tarjeta:
 *
 * PENDING (creado) -> PROCESSING (enviado a la terminal) -> APPROVED o
 * REJECTED. Un APPROVED puede ir a REVERSAL_PENDING -> REVERSED, o
 * REVERSAL_FAILED si la reversa no pudo completarse. El monitor (job) marca
 * REJECTED los PENDING que nunca resolvieron en el tiempo límite.
 */
public enum PaymentStatus {
    PENDING("Pendiente"),
    PROCESSING("Procesando"), //Enviado a terminal
    APPROVED("Aprobado"),
    REJECTED("Rechazado"),
    REVERSED("Reversado"), //Cancelado después de aprobar
    REVERSAL_PENDING("Reversa Pendiente"), //Reversa en proceso
    REVERSAL_FAILED("Reversa Fallida"); //No se pudo reversar

    private String description;

    PaymentStatus(String description){
        this.description=description;
    }

    public String getDescription(){
        return description;
    }
}
