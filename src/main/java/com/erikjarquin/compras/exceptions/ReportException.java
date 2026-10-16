package com.erikjarquin.compras.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Excepción de negocio del módulo de reportes.
 *
 * Los reportes solo validaban rangos de fechas y lanzaban
 * {@code IllegalArgumentException} (400 vía {@code GlobalExceptionHandler}).
 * El detalle por caja necesita un 404 real: pedir el reporte de una caja que no
 * existe no es "pedí algo mal formado", es "ese recurso no está".
 *
 * Mismo patrón que {@link SaleException} / {@link PurchaseException}: el
 * servicio lanza la excepción con el código desired y el handler solo la
 * traduce, para que el servicio no sepa de HTTP.
 */
public class ReportException extends RuntimeException {

    private final HttpStatus status;

    /** 400 BAD_REQUEST: el comportamiento por defecto (rango inválido, etc.). */
    public ReportException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    /** Código explícito, p. ej. NOT_FOUND (404) para una caja inexistente. */
    public ReportException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    /**
     * Estado HTTP que debe devolverse al cliente (lo usa GlobalExceptionHandler).
     */
    public HttpStatus getStatus() {
        return status;
    }
}
