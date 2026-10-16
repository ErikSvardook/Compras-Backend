package com.erikjarquin.compras.mapper;

import org.springframework.stereotype.Component;

import com.erikjarquin.compras.model.dto.Payment.CardPaymentResponse;
import com.erikjarquin.compras.model.entity.PaymentEntity;
import com.erikjarquin.compras.model.enums.PaymentStatus;

/**
 * Mapper pago -> CardPaymentResponse (componente de Spring).
 * Construye el mensaje de cara al usuario según el estado final del pago.
 */
@Component
public class PaymentMapper {
    public CardPaymentResponse toCardPaymentResponse(PaymentEntity payment){
        CardPaymentResponse response = new CardPaymentResponse();
        response.setPaymentId(payment.getId());
        response.setSaleId(payment.getSale().getId());
        response.setStatus(payment.getStatus());
        response.setAuthorizationCode(payment.getAuthorizationCode());
        response.setAmount(payment.getAmount());
        response.setPaymentDate(payment.getPaymentDate());
        response.setTransactionId(payment.getTransactionId());

        //Mensaje según el estado
        if(payment.getStatus() == PaymentStatus.APPROVED){
            response.setMessage("Pago aprobado con éxito");
        } else if(payment.getStatus() == PaymentStatus.REJECTED){
            response.setMessage(payment.getErrorMessage());
        } else{
            response.setMessage("Pago en proceso");
        }

        return response;
    }
}
