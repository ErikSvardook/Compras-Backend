package com.erikjarquin.compras.service.impl;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.erikjarquin.compras.config.TerminalConfig;
import com.erikjarquin.compras.exceptions.PaymentException;
import com.erikjarquin.compras.mapper.PaymentMapper;
import com.erikjarquin.compras.model.dto.Payment.CardPaymentRequest;
import com.erikjarquin.compras.model.dto.Payment.CardPaymentResponse;
import com.erikjarquin.compras.model.dto.Terminal.TerminalRequest;
import com.erikjarquin.compras.model.dto.Terminal.TerminalResponse;
import com.erikjarquin.compras.model.entity.PaymentEntity;
import com.erikjarquin.compras.model.entity.SaleEntity;
import com.erikjarquin.compras.model.enums.PaymentStatus;
import com.erikjarquin.compras.repository.PaymentRepository;
import com.erikjarquin.compras.repository.SaleRepository;
import com.erikjarquin.compras.service.PaymentService;
import com.erikjarquin.compras.service.TerminalService;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementación del módulo de PAGOS con tarjeta.
 *
 * Flujo: valida la venta -> construye la solicitud para la terminal
 * (SIMULADA o física) -> si la terminal aprueba, guarda el PaymentEntity
 * APPROVED y marca la venta como paga. Incluye:
 *  - Idempotencia por venta (una venta no se paga dos veces).
 *  - Reintentos de pagos rechazados (máximo 3).
 *  - Reversas de pagos aprobados.
 *  - Consulta de estado de transacciones pendientes.
 */
@Service
@Slf4j
public class PaymentImpl implements PaymentService {
    private final SaleRepository saleRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final TerminalService terminalService;
    private final TerminalConfig terminalConfig;

    public PaymentImpl(SaleRepository saleRepository,
                        PaymentRepository paymentRepository,
                        PaymentMapper paymentMapper,
                        TerminalService terminalService,
                        TerminalConfig terminalConfig){
                            this.saleRepository=saleRepository;
                            this.paymentRepository=paymentRepository;
                            this.paymentMapper=paymentMapper;
                            this.terminalService=terminalService;
                            this.terminalConfig=terminalConfig;
                        }

    //Procesar el pago
    @Override //
    @Transactional //
    public CardPaymentResponse processCardPayment(CardPaymentRequest request){ 
        log.info("Procesando pago con tarjeta para la venta ID: {}", request.getSaleId());

        //Validar que la venta exista
        SaleEntity sale = saleRepository.findById(request.getSaleId()).orElseThrow(
                        () -> new PaymentException("Venta no encontrada con ID" + request.getSaleId()));

        //Validación de idempotencia
        Optional<PaymentEntity> existingPayment = paymentRepository.findBySaleId(sale.getId());

        if(existingPayment.isPresent()){
            PaymentEntity payment = existingPayment.get();

            //Si ya está aprobado, retornar éxito
            if(payment.getStatus() == PaymentStatus.APPROVED){
                log.info("Venta ya pagada. Payment ID: {}", payment.getId());
                return paymentMapper.toCardPaymentResponse(payment);
            }

            //Si esta pendiente, verificar si debemos consultar estado
            if(payment.getStatus() == PaymentStatus.PENDING){
                throw new PaymentException("La transacción está en proceso. Por favor espere.");
            }

            if(payment.getStatus() == PaymentStatus.REJECTED){
                throw new PaymentException("Esta venta ya fue rechazada. Motivo: " + payment.getErrorMessage());
            }
        }

        //Generar transaction ID (sin guardar aún)
        String transactionId = generateTransactionId(sale.getId());

        //Construir request para la terminal
        TerminalRequest terminalRequest = TerminalRequest.builder()
                        .transactionId(transactionId)
                        .amount(sale.getTotal())
                        .paymentMethod(request.getPaymentMethod().name())
                        .merchantId(terminalConfig.getMerchantId())
                        .terminalId(terminalConfig.getTerminalId())
                        .pin(request.getPin())
                        .cardNumber(request.getCardNumber())
                        .build();
                        
        //Procesar pago con terminal
        try{
            TerminalResponse terminalResponse = terminalService.processPayment(terminalRequest);

            //Solo si la terminal aprueba, guardar el pago
            if(terminalResponse.isApproved()){
                //Crear y guardar el pago
                PaymentEntity payment = new PaymentEntity();
                payment.setSale(sale);
                payment.setPaymentMethod(request.getPaymentMethod());
                payment.setAmount(sale.getTotal());
                payment.setStatus(PaymentStatus.APPROVED);
                payment.setPaymentDate(LocalDateTime.now());
                payment.setTransactionId(transactionId);
                payment.setAuthorizationCode(terminalResponse.getAuthorizationCode());
                payment.setLastFourDigits(terminalResponse.getLastFourDigits());
                payment.setAttemptCount(1);  
                
                PaymentEntity savedPayment = paymentRepository.save(payment);

                //Actualizar la venta
                sale.setPaymentStatus(PaymentStatus.APPROVED);
                sale.setPayment(savedPayment);
                saleRepository.save(sale);

                log.info("Pago aprobado. Código: {}", terminalResponse.getAuthorizationCode());

                return paymentMapper.toCardPaymentResponse(savedPayment);
            }else{
                //Terminal rechazó -> lanzar excepción (no guarda nada)
                log.warn("Pago rechazado por la terminal: {}", terminalResponse.getErrorMessage());

                throw new PaymentException("Pago rechazado: " + terminalResponse.getErrorMessage());
            }
        } catch(Exception e){
            log.error("Error procesando pago con terminal", e);
            throw new PaymentException("Error al procesar el pago: " + e.getMessage());
        }
    }

    /**
     * Genera un transactionId único y determinista badado en el ID de la venta
     */
    private String generateTransactionId(Long saleId){
        //Formato: TXN-{saleId}-{timestamp}-{uuid_corto}
        String timestamp = String.valueOf(System.currentTimeMillis()); //currentTimeMillis():
        String shortUuid = UUID.randomUUID().toString().substring(0, 8); //substring: 
        return String.format("TXN-%d-%s-%s", saleId, timestamp, shortUuid); //format("TXN-%d-%s-%s"): 
    }

    //Revisar el estado de la venta
    @Override
    public CardPaymentResponse getPaymentStatus(String transactionId){
        log.info("Consultando estado de transacción: {}", transactionId);

        PaymentEntity payment = paymentRepository.findByTransactionId(transactionId).orElseThrow(
                                () -> new PaymentException("Transacción no encontrada"));
        
        //Si está pendiente, consultar estado actual
        if(payment.getStatus() == PaymentStatus.PENDING){
            TerminalResponse statuResponse = terminalService.getTransactionStatus(transactionId);

            payment.setStatusQueried(true);
            payment.setLastStatusQuery(LocalDateTime.now());

            if(statuResponse.isApproved()){
                payment.setStatus(PaymentStatus.APPROVED);
                payment.setAuthorizationCode(statuResponse.getAuthorizationCode());
                payment.setLastFourDigits(statuResponse.getLastFourDigits());
                paymentRepository.save(payment);

                //Actualizar venta
                SaleEntity sale = payment.getSale();
                sale.setPaymentStatus(PaymentStatus.APPROVED);
                saleRepository.save(sale);
            }
        }

        return paymentMapper.toCardPaymentResponse(payment);
    }
    
    //Reintentar pago
    @Override
    @Transactional
    public CardPaymentResponse retryPayment(Long paymentId){
        log.info("Reintentando pago ID: {}", paymentId);

        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow(
            () -> new PaymentException("Pago no encontrado con ID: " + paymentId));

        if(payment.getStatus() != PaymentStatus.REJECTED){
            throw new PaymentException("Solo se pueden reintentar pagos rechazados. Estado actual: " + payment.getStatus());
        }

        if(payment.getAttemptCount() >= 3){
            throw new PaymentException("Máximo de reintentos alcanzado (3 intentos)");
        }

        //Crear nuevo transactionId para el reintento
        String newTransactionId = generateTransactionId(payment.getSale().getId());
        payment.setTransactionId(newTransactionId);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setErrorMessage(null);
        payment.setStatusQueried(false);
        paymentRepository.save(payment);

        //Reintentar el pago
        CardPaymentRequest retryRequest = new CardPaymentRequest();
        retryRequest.setSaleId(payment.getSale().getId());
        retryRequest.setPaymentMethod(payment.getPaymentMethod());

        return processCardPayment(retryRequest);
    }

    //Regresar el pago
    @Override
    @Transactional
    public boolean reversePayment(Long paymentId){
        log.info("Reversando pago ID: {}", paymentId);

        PaymentEntity payment = paymentRepository.findById(paymentId).orElseThrow(
            () -> new PaymentException("Pago no encotrado con ID: " + paymentId));

        if(payment.getStatus() != PaymentStatus.APPROVED){
            throw new PaymentException("Solo se pueden reversar pagos aprobados. Estado actual: " + payment.getStatus());
        }

        boolean reversed = terminalService.reversePayment(payment.getTransactionId());

        if(reversed){
            payment.setStatus(PaymentStatus.REVERSED);
            payment.setReversalDate(LocalDateTime.now());
            payment.setReversalReason("Reversa manual solicitada");
            paymentRepository.save(payment);

            SaleEntity sale = payment.getSale();
            sale.setPaymentStatus(PaymentStatus.REVERSED);
            saleRepository.save(sale);

            log.info("Pago reversado exitosamente: {}", paymentId);
            return true;
        }

        log.error("Error al reversar pago: {}", paymentId);
        return false;
    }
}
