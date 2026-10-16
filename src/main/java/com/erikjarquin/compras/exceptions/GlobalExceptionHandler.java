package com.erikjarquin.compras.exceptions;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * Manejador GLOBAL de excepciones (@RestControllerAdvice).
 * Centraliza la conversión de cada error en una respuesta JSON uniforme:
 *
 * { "timestamp": ..., "status": ..., "code": "...", "message": "..." }
 *
 * Reglas aplicadas:
 *  - Los mensajes internos/reales NUNCA se filtran al cliente en errores 500
 *    (se loguean en el servidor y se responde un mensaje genérico).
 *  - Los errores de validación de {@code @Valid} devuelven 400 con los detalles.
 *  - Errores de Spring Security (401/403) tienen su propio handler para que
 *    el frontend siempre reciba el mismo formato de error.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ------------------- Excepciones de negocio del proyecto -------------------

    /**
     * Caja: errores de apertura/cierre/consulta de caja.
     * Nota: se mapea con el estado que traiga la excepción (por defecto 404).
     */
    @ExceptionHandler(CashException.class)
    public ResponseEntity<ErrorResponse> handleCashException(CashException ex) {
        return buildErrorResponse(ex.getStatus(), "CASH_ERROR", ex.getMessage());
    }

    /**
     * Usuarios: errores de negocio del módulo de usuarios.
     */
    @ExceptionHandler(UserException.class)
    public ResponseEntity<ErrorResponse> handleUserException(UserException ex) {
        return buildErrorResponse(ex.getStatus(), "USER_ERROR", ex.getMessage());
    }

    /**
     * Roles: errores de negocio del módulo de roles (no encontrado, nombre
     * duplicado, rol con usuarios asignados).
     */
    @ExceptionHandler(RoleException.class)
    public ResponseEntity<ErrorResponse> handleRoleException(RoleException ex) {
        log.warn("Error de rol: {}", ex.getMessage());
        return buildErrorResponse(ex.getStatus(), "ROLE_ERROR", ex.getMessage());
    }

    /**
     * Categorías: errores de negocio del módulo de categorías (no encontrada,
     * nombre duplicado, categoría con productos asociados).
     */
    @ExceptionHandler(CategoryException.class)
    public ResponseEntity<ErrorResponse> handleCategoryException(CategoryException ex) {
        log.warn("Error de categoría: {}", ex.getMessage());
        return buildErrorResponse(ex.getStatus(), "CATEGORY_ERROR", ex.getMessage());
    }

    /**
     * Productos: errores de negocio del módulo de productos (no encontrado,
     * producto con ventas/compras asociadas que impide borrarlo).
     */
    @ExceptionHandler(ProductException.class)
    public ResponseEntity<ErrorResponse> handleProductException(ProductException ex) {
        log.warn("Error de producto: {}", ex.getMessage());
        return buildErrorResponse(ex.getStatus(), "PRODUCT_ERROR", ex.getMessage());
    }

    /**
     * Proveedores: errores de negocio del módulo de proveedores (no encontrado,
     * RFC duplicado, proveedor con compras asociadas).
     */
    @ExceptionHandler(ProviderException.class)
    public ResponseEntity<ErrorResponse> handleProviderException(ProviderException ex) {
        log.warn("Error de proveedor: {}", ex.getMessage());
        return buildErrorResponse(ex.getStatus(), "PROVIDER_ERROR", ex.getMessage());
    }

    /**
     * Compras: errores de negocio del módulo de compras (no encontrada, items
     * inválidos, proveedor inexistente, inconsistencia al cancelar).
     */
    @ExceptionHandler(PurchaseException.class)
    public ResponseEntity<ErrorResponse> handlePurchaseException(PurchaseException ex) {
        log.warn("Error de compra: {}", ex.getMessage());
        return buildErrorResponse(ex.getStatus(), "PURCHASE_ERROR", ex.getMessage());
    }

    /**
     * Ventas: errores de negocio del módulo de ventas.
     *
     * Se usa {@code ex.getStatus()} en vez de un 400 fijo: SaleException es
     * 400 por defecto (stock insuficiente, caja cerrada, método de pago
     * inválido) pero el ciclo de vida de la venta necesita 409 CONFLICT
     * (anular una venta confirmada, anular dos veces, anular con tarjeta) y
     * 404 (venta inexistente). El servicio declara el código; el handler solo
     * lo traduce, sin saber nada de reglas de ventas.
     */
    @ExceptionHandler(SaleException.class)
    public ResponseEntity<ErrorResponse> handleSaleException(SaleException e) {
        log.warn("Error de venta: {}", e.getMessage());
        return buildErrorResponse(e.getStatus(), "SALE_ERROR", e.getMessage());
    }

    /**
     * Reportes: 404 cuando se pide el detalle de una caja que no existe.
     * El código lo declara el servicio (ReportException) y el handler solo lo
     * traduce, como con ventas y compras.
     */
    @ExceptionHandler(ReportException.class)
    public ResponseEntity<ErrorResponse> handleReportException(ReportException e) {
        log.warn("Error de reporte: {}", e.getMessage());
        return buildErrorResponse(e.getStatus(), "REPORT_ERROR", e.getMessage());
    }

    /**
     * Pagos: se intenta deducir el código HTTP según el tipo de fallo
     * (timeout, problema de terminal o rechazo). También respeta el estado
     * que venga explícito en la excepción.
     */
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<ErrorResponse> handlePaymentException(PaymentException e) {
        log.warn("Error de pago: {}", e.getMessage());

        if (e.getStatus() != null) {
            return buildErrorResponse(e.getStatus(), "PAYMENT_ERROR", e.getMessage());
        }

        if (e.getMessage() != null && (e.getMessage().contains("timeout") || e.getMessage().contains("TIMEOUT"))) {
            return buildErrorResponse(HttpStatus.REQUEST_TIMEOUT, "PAYMENT_TIMEOUT", e.getMessage());
        }

        if (e.getMessage() != null && (e.getMessage().contains("comunicación") || e.getMessage().contains("terminal"))) {
            return buildErrorResponse(HttpStatus.BAD_GATEWAY, "TERMINAL_ERROR", e.getMessage());
        }

        if (e.getMessage() != null && e.getMessage().contains("rechazado")) {
            return buildErrorResponse(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_REJECTED", e.getMessage());
        }

        return buildErrorResponse(HttpStatus.BAD_REQUEST, "PAYMENT_ERROR", e.getMessage());
    }

    // ------------------- Seguridad (401 / 403) -------------------

    /**
     * Usuario autenticado sin el permiso requerido a 403 FORBIDDEN.
     * Se dispara cuando un @PreAuthorize no se cumple (o AccessDeniedException
     * llega hasta aquí). Confirma que el JSON de error es uniforme para el front.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "No tienes permisos para realizar esta acción");
    }

    /**
     * Request sin autenticación válida a 401 UNAUTHORIZED.
     * (BadCredentialsException, AuthenticationCredentialsNotFoundException, etc.)
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "No autenticado");
    }

    // ------------------- Errores de validación y de request -------------------

    /**
     * Falla la validación de un DTO con anotaciones tipo @NotBlank/@Size/@Min a 400.
     * Devuelve la lista de campos con su mensaje para que el frontend pueda
     * mostrarlos por campo. Antes caía al 500 genérico.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                details.isEmpty() ? "Datos de entrada inválidos" : details);
    }

    /**
     * Parámetro de path/query con tipo incorrecto, p. ej. /api/local/sales/{id} no
     * numérico a 400.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST",
                "El parámetro '" + ex.getName() + "' no tiene el tipo esperado");
    }

    /**
     * JSON mal formado o sin el body esperado a 400 (no un 500 interno).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Cuerpo de la petición inválido");
    }

    /**
     * Recurso estático inexistente (p. ej. una imagen de producto cuyo archivo
     * ya no está en uploads/) a 404, NO 500. Evita que el frontend reciba un
     * "Error interno" cuando el navegador pide una imagen huérfana.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", "El recurso solicitado no existe");
    }

    /**
     * Entrada del usuario inválida que no es de negocio (p. ej. archivo de imagen
     * con formato no permitido, lanzada por FileStorageService) a 400.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Solicitud inválida: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
    }

    // ------------------- Error genérico -------------------

    /**
     * Cualquier excepción no contemplada a 500 con mensaje genérico.
     * El detalle real se loguea en el servidor (no se filtra al cliente).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDateIntegrityViolation(DataIntegrityViolationException ex){
        String raw = String.valueOf(ex.getMostSpecificCause().getMessage()).toLowerCase();

        log.warn("Dato duplicado en BD: {}", raw);

        String message;
        if(raw.contains("sku")){
            message = "Ya existe un producto con ese SKU";
        } else if(raw.contains("barcode")){
            message = "Ya existe un producto con ese código de barras";
        } else {
            message = "No se pudo guardar: el dato ya existe";
        }

        return buildErrorResponse(HttpStatus.CONFLICT, "PRODUCT_ERROR", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("Error interno del servidor", e);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocurrió un error interno en el servidor");
    }

    // ------------------- Helpers -------------------

    /**
     * Construye la respuesta JSON de error con el mismo formato en todos los casos.
     */
    private ResponseEntity<ErrorResponse> buildErrorResponse(HttpStatus status, String code, String message) {
        ErrorResponse error = new ErrorResponse(LocalDateTime.now(), status.value(), code, message);
        return ResponseEntity.status(status).body(error);
    }

    /**
     * Formato estándar de respuesta de error que consume el frontend.
     */
    public static class ErrorResponse {
        private final LocalDateTime timestamp;
        private final int status;
        private final String code;
        private final String message;

        public ErrorResponse(LocalDateTime timestamp, int status, String code, String message) {
            this.timestamp = timestamp;
            this.status = status;
            this.code = code;
            this.message = message;
        }

        public LocalDateTime getTimestamp() { return timestamp; }
        public int getStatus() { return status; }
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }

    /**
     * Fallo al entregar el correo (SMTP o API HTTPS). 503: lo que no está
     * disponible es el servicio de correo, no el backend entero
     */
    @ExceptionHandler(EmailDeliveryException.class)
    public ResponseEntity<ErrorResponse> handleEmailDelivery(EmailDeliveryException e){
        log.error("No se pudo entregar el correo de recuperación: {}", e.getMessage());
        return buildErrorResponse(e.getStatus(), "EMAIL_ERROR", e.getMessage());
    }

}