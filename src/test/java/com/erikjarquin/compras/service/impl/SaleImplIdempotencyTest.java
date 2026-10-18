package com.erikjarquin.compras.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.erikjarquin.compras.mapper.SaleMapper;
import com.erikjarquin.compras.model.dto.Sale.SaleItemRequest;
import com.erikjarquin.compras.model.dto.Sale.SaleRequest;
import com.erikjarquin.compras.model.entity.CashRegisterEntity;
import com.erikjarquin.compras.model.entity.ProductEntity;
import com.erikjarquin.compras.model.entity.SaleEntity;
import com.erikjarquin.compras.model.enums.PaymentMethod;
import com.erikjarquin.compras.repository.CashRegisterRepository;
import com.erikjarquin.compras.repository.ProductRepository;
import com.erikjarquin.compras.repository.SaleRepository;
import com.erikjarquin.compras.repository.UserRepository;
import com.erikjarquin.compras.service.PaymentService;

/**
 * Tests de IDEMPOTENCIA del cobro.
 *
 * El bug que motivó esto. El modal de confirmar venta es un
 * {@code <form>}. El cajero paga y aprieta Enter; si lo aprieta dos veces, el
 * frontend lanza dos peticiones a {@code POST /sales} y se hacen DOS ventas: el
 * stock se descuenta dos veces y el reporte muestra dos tickets por una sola
 * compra.
 *
 * Un boton deshabilitado en el frontend NO lo arregla, y esa es la parte que
 * costó entender: el boton evita el segundo clic, pero no el caso real,
 * que es que las dos peticiones ya estén viajando por la red al mismo tiempo.
 * La unica proteccion que funciona de verdad es una clave de idempotencia que
 * viaja con la intencion de cobro y se comprueba en el servidor.
 *
 * Estos tests fijan ese comportamiento en el backend, que es donde se
 * resuelve. El flag del frontend ({@code isProcessing}) es la primera barrera y
 * se documenta en el codigo, pero no es lo que garantiza que no se cobre dos
 * veces.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Venta - idempotencia del cobro (V6)")
class SaleImplIdempotencyTest {

    @Mock private ProductRepository productRepository;
    @Mock private SaleRepository saleRepository;
    @Mock private CashRegisterRepository cashRepository;
    @Mock private UserRepository userRepository;
    @Mock private PaymentService paymentService;

    @Spy private SaleMapper mapper = new SaleMapper();

    @InjectMocks private SaleImpl saleService;

    //===== Helpers =====

    private SaleRequest requestConClave(String clave){
        SaleRequest request = new SaleRequest();
        request.setPaymentMethod(PaymentMethod.CASH);
        request.setCashReceived(new BigDecimal("50"));
        request.setIdempotencyKey(clave);

        SaleItemRequest item = new SaleItemRequest();
        item.setProductId(1L);
        item.setQuantity(1);
        request.setItems(List.of(item));

        return request;
    }

    private SaleEntity ventaRegistrada(long id, String clave){
        SaleEntity sale = new SaleEntity();
        sale.setId(id);
        sale.setTotal(new BigDecimal("50"));
        sale.setPaymentMethod(PaymentMethod.CASH);
        sale.setIdempotencyKey(clave);
        return sale;
    }

    private CashRegisterEntity cajaAbierta(){
        CashRegisterEntity cash = new CashRegisterEntity();
        cash.setId(1L);
        cash.setNumber("CAJA 1");
        cash.setActive(true);
        cash.setTotalTickets(0);
        return cash;
    }

    private ProductEntity producto(){
        ProductEntity p = new ProductEntity();
        p.setId(1L);
        p.setName("Café");
        p.setPrice(new BigDecimal("50"));
        p.setStock(100);
        return p;
    }

    private void prepararVentaNueva(){
        when(cashRepository.findByActiveTrue()).thenReturn(Optional.of(cajaAbierta()));
        when(productRepository.findById(1L)).thenReturn(Optional.of(producto()));
        when(saleRepository.save(any(SaleEntity.class))).thenAnswer(i -> {
            SaleEntity s = i.getArgument(0);
            s.setId(99L);
            return s;
        });
    }

    //===== Tests =====

    @Nested
    @DisplayName("Cuando la clave ya se procesó")
    class ClaveYaProcesada {

        @Test
        @DisplayName("devuelve la venta existente y NO crea otra")
        void noCreaUnaVentaNueva(){
            SaleEntity previa = ventaRegistrada(42L, "clave-abc");

            //El backend ya tiene una venta con esa clave.
            when(saleRepository.findByIdempotencyKey("clave-abc"))
                    .thenReturn(Optional.of(previa));

            var resultado = saleService.processSale(requestConClave("clave-abc"));

            //Devuelve la MISMA venta, con su id.
            assertThat(resultado.getSaleId()).isEqualTo(42L);
            assertThat(resultado.getTotal()).isEqualByComparingTo("50");

            //Y lo importante: no se guardó ninguna venta nueva. Este es el assert
            //que evita la DOBLE VENTA.
            verify(saleRepository, never()).save(any(SaleEntity.class));
        }

        @Test
        @DisplayName("tampoco toca el stock ni abre la caja")
        void noLlegaNiAValidarElCarrito(){
            SaleEntity previa = ventaRegistrada(42L, "clave-abc");
            when(saleRepository.findByIdempotencyKey("clave-abc"))
                    .thenReturn(Optional.of(previa));

            saleService.processSale(requestConClave("clave-abc"));

            //Si el atajo se hiciera más tarde (después de processar productos),
            //el stock ya se habría descontado. Por eso la comprobación va
            // PRIMERO, antes de validar y antes de crear la venta.
            verify(productRepository, never()).findById(1L);
        }
    }

    @Nested
    @DisplayName("Cuando la clave es nueva")
    class ClaveNueva {

        @Test
        @DisplayName("crea la venta y guarda la clave")
        void creaLaVentaConLaClave(){
            when(saleRepository.findByIdempotencyKey("clave-nueva"))
                    .thenReturn(Optional.empty());
            prepararVentaNueva();

            saleService.processSale(requestConClave("clave-nueva"));

            ArgumentCaptor<SaleEntity> captor = ArgumentCaptor.forClass(SaleEntity.class);
            verify(saleRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());

            SaleEntity guardada = captor.getAllValues()
                    .get(captor.getAllValues().size() - 1);

            assertThat(guardada.getIdempotencyKey()).isEqualTo("clave-nueva");
        }

        @Test
        @DisplayName("sin clave (cliente viejo) la venta se crea normal")
        void sinClaveFuncionaNormal(){
            //OJO: aquí NO se hace stub de findByIdempotencyKey a propósito.
            prepararVentaNueva();

            SaleRequest sinClave = requestConClave(null);

            var resultado = saleService.processSale(sinClave);

            assertThat(resultado.getSaleId()).isEqualTo(99L);

            //Sin clave no se consulta el repo: buscar por null o por "" sería
            //inútil y peligroso (una venta sin clave colisionaría con todas las
            // demás que tampoco la tienen). La clave ausente es una venta normal.
            verify(saleRepository, never()).findByIdempotencyKey(any());
        }
    }

    @Nested
    @DisplayName("Normalización de la clave")
    class Normalizacion {

        @Test
        @DisplayName("recorta los espacios: la misma intención no genera dos claves")
        void recortaEspacios(){
            when(saleRepository.findByIdempotencyKey("CAJA 1"))
                    .thenReturn(Optional.empty());
            prepararVentaNueva();

            saleService.processSale(requestConClave("  CAJA 1  "));

            //Busca por la clave ya recortada: si no lo hiciera, "  x  " y "x"
            //serían dos intenciones distintas y la idempotencia fallaría.
            verify(saleRepository).findByIdempotencyKey("CAJA 1");
        }

        @Test
        @DisplayName("una clave en blanco se trata como si no hubiera clave")
        void claveEnBlancoEsAusente(){
            //Sin stub a propósito: la clave en blanco se normaliza a null y el
            //código corta ANTES de tocar el repositorio.
            prepararVentaNueva();

            saleService.processSale(requestConClave("   "));

            //No busca por cadena vacía: colisionaría con todas las ventas que
            //tampoco tienen clave.
            verify(saleRepository, never()).findByIdempotencyKey(any());
        }

        @Test
        @DisplayName("una clave enorme se acota al VARCHAR(64) de la columna")
        void acotaLaClave(){
            String enorme = "x".repeat(200);

            when(saleRepository.findByIdempotencyKey(any()))
                    .thenReturn(Optional.empty());
            prepararVentaNueva();

            saleService.processSale(requestConClave(enorme));

            ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
            verify(saleRepository).findByIdempotencyKey(captor.capture());

            assertThat(captor.getValue()).hasSize(64);
        }
    }
}