package com.erikjarquin.compras.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.erikjarquin.compras.model.dto.Sale.SaleDetailHistoryResponse;
import com.erikjarquin.compras.model.dto.Sale.SaleDetailResponse;
import com.erikjarquin.compras.model.dto.Sale.SaleHistoryResponse;
import com.erikjarquin.compras.model.dto.Sale.SaleResponse;
import com.erikjarquin.compras.model.entity.SaleDetailEntity;
import com.erikjarquin.compras.model.entity.SaleEntity;
import com.erikjarquin.compras.model.enums.PaymentMethod;

/**
 * Mapper venta → respuestas de venta/historial/detalle (componente de Spring).
 *
 * En pagos con tarjeta expone SOLO los últimos 4 dígitos y el código de
 * autorización (nunca el número completo). toDetailResponse arma el detalle
 * con historial, manejando details nulo como lista vacía.
 */
@Component
public class SaleMapper {
    public SaleResponse toResponse(SaleEntity sale){
        SaleResponse response = new SaleResponse();

        response.setSaleId(sale.getId());
        response.setTotal(sale.getTotal());
        response.setPaymentMethod(sale.getPaymentMethod());
        response.setCashReceived(sale.getCashReceived());
        response.setChangeAmount(sale.getChangeAmount());
        response.setPaymentStatus(sale.getPaymentStatus());
        
        //Si el pago fue con tarjeta, obtener información del payment
        if(sale.getPaymentMethod() == PaymentMethod.DEBIT || 
            sale.getPaymentMethod() == PaymentMethod.CREDIT){
                //Opción 1: Si tienes relación OneToOne en SaleEntity
                if(sale.getPayment() != null) {
                    response.setLastFourDigits(sale.getPayment().getLastFourDigits());
                    response.setAuthorizationCode(sale.getPayment().getAuthorizationCode());
                    response.setErrorMessage(sale.getPayment().getErrorMessage());
                }
        }

        return response;
    }

    public SaleHistoryResponse toHistoryResponse(SaleEntity sale){
        SaleHistoryResponse response = new SaleHistoryResponse();

        response.setId(sale.getId());
        response.setSaleDate(sale.getSaleDate());
        response.setTotal(sale.getTotal());
        response.setPaymentMethod(sale.getPaymentMethod());
        response.setCashReceived(sale.getCashReceived());
        response.setChangeAmount(sale.getChangeAmount());
        response.setPaymentStatus(sale.getPaymentStatus());

        //Ciclo de vida de la venta: se copia tal cual, sin derivar un "estado".
        //El mapper es un translator, no un business rule holder: si aquí se
        //calculara p.ej. "abierta" a mano, el frontend y el backend podrían
        //discrepar. La regla vive en SaleImpl; aquí solo se transporta.
        response.setConfirmed(sale.isConfirmed());
        response.setConfirmedAt(sale.getConfirmedAt());
        response.setCancelled(sale.isCancelled());
        response.setCancelledAt(sale.getCancelledAt());

        return response;
    }

    public SaleDetailHistoryResponse toDetailResponse(SaleEntity sale){
        SaleDetailHistoryResponse response = new SaleDetailHistoryResponse();

        response.setSaleId(sale.getId());
        response.setSaleDate(sale.getSaleDate());
        response.setTotal(sale.getTotal());
        response.setPaymentMethod(sale.getPaymentMethod());
        response.setPaymentStatus(sale.getPaymentStatus());
        response.setConfirmed(sale.isConfirmed());
        response.setConfirmedAt(sale.getConfirmedAt());
        response.setCancelled(sale.isCancelled());
        response.setCancelledAt(sale.getCancelledAt());

        // Usuario de la venta (V5). Se copia el nombre al DTO en vez de dejar la
        // entidad: asi el reporte no depende de la relacion LAZY (que fuera de
        // transicion daria error) y el frontend ya recibe el texto listo.
        if(sale.getUser() != null){
            response.setUserId(sale.getUser().getId());
            response.setUserName(sale.getUser().getName());
        }

        List<SaleDetailResponse> items = sale.getDetails() == null ? List.of() : sale.getDetails().stream().map(this::toDetailItem).toList();
        response.setItems(items);

        return response;
        
    }

    private SaleDetailResponse toDetailItem(SaleDetailEntity detail){
        SaleDetailResponse item = new SaleDetailResponse();

        item.setProduct(detail.getProduct().getName());
        item.setQuantity(detail.getQuantity());
        item.setUnitPrice(detail.getUnitPrice());
        item.setUnitCost(detail.getUnitCost()); //V3: costo congelado al vender
        item.setSubtotal(detail.getSubTotal());

        return item;
    }
}
