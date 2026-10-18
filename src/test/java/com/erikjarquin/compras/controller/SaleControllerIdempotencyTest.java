package com.erikjarquin.compras.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.erikjarquin.compras.model.dto.Sale.SaleRequest;
import com.erikjarquin.compras.model.dto.Sale.SaleResponse;
import com.erikjarquin.compras.model.enums.PaymentMethod;
import com.erikjarquin.compras.service.SaleService;

/**
 * Tests del manejador de COBROS DUPLICADOS en el controlador.
 *
 * Cubre el caso que la comprobación previa de {@code SaleImpl} no puede
 * cubrir: dos peticiones del mismo cobro que se cruzan y las dos intentan
 * insertar. El índice UNIQUE rechaza una; aquí se comprueba que el cajero
 * recibe su venta y NO un error 500.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Controlador de ventas - cobro duplicado (V6)")
class SaleControllerIdempotencyTest {

    @Mock private SaleService service;

    @InjectMocks private SaleController controller;

    private SaleRequest requestConClave(String clave){
        SaleRequest request = new SaleRequest();
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setCashReceived(new BigDecimal("50"));
        request.setIdempotencyKey(clave);
        return request;
    }

    private SaleResponse ventaRespuesta(long saleId){
        SaleResponse response = new SaleResponse();
        response.setSaleId(saleId);
        response.setTotal(new BigDecimal("50"));
        return response;
    }

    @Test
    @DisplayName("si el indice UNIQUE rechaza el INSERT, devuelve la venta ya registrada")
    void devuelveLaVentaGanadora(){
        SaleRequest request = requestConClave("clave-carrera");

        when(service.processSale(request))
                .thenThrow(new DataIntegrityViolationException("duplicate key value"));
        when(service.findByIdempotencyKey("clave-carrera"))
                .thenReturn(Optional.of(ventaRespuesta(77L)));

        ResponseEntity<SaleResponse> respuesta = controller.processSale(request);

        //El cajero ve SU venta, con 200. No un error.
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getSaleId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("si no hay ninguna venta con esa clave, el error NO se esconde")
    void sinVentaPreviaPropagaElError(){
        SaleRequest request = requestConClave("clave-inexistente");

        when(service.processSale(request))
                .thenThrow(new DataIntegrityViolationException("duplicate key value"));
        when(service.findByIdempotencyKey("clave-inexistente"))
                .thenReturn(Optional.empty());

        //Un error de integridad que NO es un cobro duplicado (por ejemplo, una
        //restricción de stock) debe seguir propagándose: tragárselo dejaría al
        //cajero creyendo que cobro y no fue así.
        assertThatThrownBy(() -> controller.processSale(request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("un cobro normal no consulta la clave: va directo")
    void cobroNormalNoConsulta(){
        SaleRequest request = requestConClave("clave-normal");
        when(service.processSale(request)).thenReturn(ventaRespuesta(5L));

        ResponseEntity<SaleResponse> respuesta = controller.processSale(request);

        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().getSaleId()).isEqualTo(5L);

        //El camino feliz no debe pagar el costo de la busqueda de idempotencia.
        verify(service, org.mockito.Mockito.never()).findByIdempotencyKey(any());
    }

    @Test
    @DisplayName("la venta original se procesa una sola vez")
    void laVentaOriginalNoSeRepite(){
        SaleRequest request = requestConClave("clave-carrera");

        when(service.processSale(request))
                .thenThrow(new DataIntegrityViolationException("duplicate key value"));
        when(service.findByIdempotencyKey("clave-carrera"))
                .thenReturn(Optional.of(ventaRespuesta(77L)));

        controller.processSale(request);

        //En el camino de recuperacion solo se LEE. Que aqui no haya un SEGUNDO
        //processSale es justamente lo que garantiza que el stock no se descuente
        //dos veces.
        verify(service, org.mockito.Mockito.times(1)).processSale(request);
        verify(service, org.mockito.Mockito.times(1)).findByIdempotencyKey("clave-carrera");
    }
}