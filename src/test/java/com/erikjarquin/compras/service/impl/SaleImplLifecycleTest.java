package com.erikjarquin.compras.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.erikjarquin.compras.exceptions.SaleException;
import com.erikjarquin.compras.mapper.SaleMapper;
import com.erikjarquin.compras.model.dto.Sale.SaleHistoryResponse;
import com.erikjarquin.compras.model.entity.ProductEntity;
import com.erikjarquin.compras.model.entity.SaleDetailEntity;
import com.erikjarquin.compras.model.entity.SaleEntity;
import com.erikjarquin.compras.model.enums.PaymentMethod;
import com.erikjarquin.compras.model.enums.PaymentStatus;
import com.erikjarquin.compras.repository.CashRegisterRepository;
import com.erikjarquin.compras.repository.ProductRepository;
import com.erikjarquin.compras.repository.SaleRepository;
import com.erikjarquin.compras.service.PaymentService;

/**
 * Tests del CICLO DE VIDA de la venta en {@code SaleImpl} (Mockito, sin BD).
 *
 * Por qué estos tests son la mitad del arreglo
 *
 * El síntoma que reportó el usuario fue: "compré 10, vendí 5, y al cancelar
 * la compra el stock volvió a 0". Ese dato corrupto no lo producía solo
 * {@code PurchaseImpl.cancel}: cualquiera que devuelva stock sin validar puede
 * dejar el inventario incoherentente. Estos tests fijan la regla del lado de la
 * venta, que es la que hace el inventario coherente:
 *
 *   Una venta confirmada NO se anula nunca (es el congelamiento).
 *   Una venta anulada no se anula dos veces (el stock no sube doble).
 *   Una venta con tarjeta no se anula desde aquí (el dinero está
 *       capturado: necesita reversa real).
 *   Confirmar es IDEMPOTENTE: reintentar tras un corte de red no falla.
 *   Una venta anulada no se "revive" confirmándola.
 *
 * Por qué el mapper real y no un mock: {@code SaleImpl} necesita el
 * mapper para devolver DTOs, y aquí se usa el {@code SaleMapper} de verdad
 * (es un bean sin dependencias). Es a propósito: si el mapper se olvidara de
 * copiar {@code confirmed}, estos tests lo detectarían, porque el
 * {@code assertThat(resultado.isConfirmed())} leería {@code false} del DTO. Con
 * un mapper mockeado, ese error pasaría desapercibido hasta que la UI no
 * refrescara el botón.
 *
 * Por qué {@code productRepository.save()} nunca se verifica en el camino
 * de éxito de {@code cancel()}: porque el mapper y el producto son
 * mutables en memoria, la aserción útil es directamente sobre
 * {@code producto.getStock()}. Mockito {@code verify} se usa en cambio para
 * comprobar las NO escrituras, que es donde está el peligro.
 */
@ExtendWith(MockitoExtension.class)
class SaleImplLifecycleTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SaleRepository saleRepository;

    @Mock
    private CashRegisterRepository cashRepository;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private SaleImpl saleService;

    /**
     * Mapper real, envuelto en {@code @Spy}.
     *
     * El {@code @Spy} NO es decorativo: {@code SaleImpl} se construye por
     * constructor y recibe el mapper por ahí. Si este campo fuera una simple
     * instancia sin {@code @Spy}, Mockito no la registraría como test double y
     * le inyectaría {@code null} al servicio, revolviendo con
     * {@code NullPointerException} dentro de {@code cancel()}. Al declararlo
     * {@code @Spy}, Mockito lo ve, lo registra con su tipo y lo inyecta.
     *
     * Además, usar el mapper de verdad (y no uno mockeado que devuelve
     * {@code null}) es lo que hace que {@code assertThat(resultado.isConfirmed())}
     * sea una comprobación real: si mañana el mapper se olvidara de copiar
     * {@code confirmed}, el DTO devolvería {@code false} y el test fallaría. Con
     * un mapper mockeado ese error pasaría inadvertido.
     */
    @Spy
    private SaleMapper mapper = new SaleMapper();

    //===== Helpers de construcción =====

    /**
     * Producto de prueba. El id es {@code long} porque así es en
     * {@code ProductEntity} ({@code Long}, clave primaria autogenerada): si el
     * helper tomara {@code int}, los {@code 10L} de los tests no compilarían y
     * el compilador avisaría del desajuste de tipos en vez de dejar que pase
     * un truncamiento silencioso.
     */
    private ProductEntity producto(long id, String nombre, int stock){
        ProductEntity p = new ProductEntity();
        p.setId(id);
        p.setName(nombre);
        p.setPrice(new BigDecimal("10.00"));
        p.setStock(stock);
        return p;
    }

    private SaleDetailEntity renglon(ProductEntity producto, int cantidad){
        SaleDetailEntity d = new SaleDetailEntity();
        d.setProduct(producto);
        d.setQuantity(cantidad);
        d.setUnitPrice(producto.getPrice());
        d.setSubTotal(producto.getPrice().multiply(BigDecimal.valueOf(cantidad)));
        return d;
    }

    /** Venta en efectivo, abierta (ni confirmada ni anulada). */
    private SaleEntity ventaAbierta(Long id, PaymentMethod metodo, List<SaleDetailEntity> detalles){
        SaleEntity s = new SaleEntity();
        s.setId(id);
        s.setPaymentMethod(metodo);
        s.setPaymentStatus(PaymentStatus.APPROVED);
        s.setTotal(new BigDecimal("50.00"));
        s.setDetails(detalles);
        return s;
    }

    // ===================== CONFIRMAR =====================

    @Test
void confirmar_marcaConfirmadaYPonFecha(){
   SaleEntity venta = ventaAbierta(1L, PaymentMethod.CASH, List.of());
   when(saleRepository.findById(1L)).thenReturn(Optional.of(venta));
   //1 fila afectada = esta petición ganó la carrera y confirma de verdad.
   when(saleRepository.markConfirmedIfPending(eq(1L), any())).thenReturn(1);
   //El repositorio devuelve la venta YA marcada: el flag lo escribió el UPDATE
   //condicional, no el servicio sobre la entidad en memoria.
   when(saleRepository.findById(1L)).thenAnswer(i -> {
      venta.setConfirmed(true);
      venta.setConfirmedAt(java.time.LocalDateTime.now());
      return Optional.of(venta);
   });

   SaleHistoryResponse r = saleService.confirm(1L);

   assertThat(r.isConfirmed()).isTrue();
   assertThat(r.getConfirmedAt())
   .as("debe registrar el momento de la confirmación")
   .isNotNull();
}

   /**
    * Carrera entre dos confirmaciones simultáneas.
    *
    * La segunda petición ejecuta su UPDATE y obtiene 0 filas porque la primera
    * ya confirmó. Debe devolver 200 con la venta confirmada (idempotencia) y NO
    * escribir nada, aunque su copia en memoria diga que sigue abierta.
    */
   @Test
   void confirmar_carreraConOtraPeticion_noEscribeNada(){
   SaleEntity venta = ventaAbierta(1L, PaymentMethod.CASH, List.of());
   when(saleRepository.findById(1L)).thenReturn(Optional.of(venta));
   when(saleRepository.markConfirmedIfPending(eq(1L), any())).thenReturn(0);

   saleService.confirm(1L);

   verify(saleRepository, never()).save(any(SaleEntity.class));
   }

    /**
     * Idempotencia: el usuario Confirmar dos veces porque el primero no le llegó
     * la respuesta. Debe devolver 200 y NO pisar la fecha original.
     *
     * Si se reescribiera la fecha, el historial mostraría el último reintento
     * como si fuera la hora real de la confirmación, y eso ya es pérdida de
     * información de auditoría.
     */
    @Test
    void confirmar_yaConfirmada_esIdempotenteYNoPisaLaFecha(){
        SaleEntity venta = ventaAbierta(1L, PaymentMethod.CASH, List.of());
        venta.setConfirmed(true);
        var fechaOriginal = java.time.LocalDateTime.of(2026, 1, 1, 10, 0);
        venta.setConfirmedAt(fechaOriginal);

        when(saleRepository.findById(1L)).thenReturn(Optional.of(venta));

        SaleHistoryResponse r = saleService.confirm(1L);

        assertThat(r.isConfirmed()).isTrue();
        assertThat(r.getConfirmedAt())
                .as("una segunda confirmación no debe cambiar la fecha original")
                .isEqualTo(fechaOriginal);

        // Ni siquiera se guarda: no hay nada que cambiar.
        verify(saleRepository, never()).save(any(SaleEntity.class));
    }

    /**
     * Una venta anulada es terminal. Confirmarla sería devolverle vida a una
     * venta que ya devolvió el stock: el stock volvería a descontarse sin que
     * exista una venta real detrás.
     */
    @Test
    void confirmar_ventaAnulada_devuelve409(){
        SaleEntity venta = ventaAbierta(1L, PaymentMethod.CASH, List.of());
        venta.setCancelled(true);

        when(saleRepository.findById(1L)).thenReturn(Optional.of(venta));

        assertThatThrownBy(() -> saleService.confirm(1L))
                .isInstanceOf(SaleException.class)
                .hasMessageContaining("ya fue anulada")
                .extracting(e -> ((SaleException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(saleRepository, never()).save(any(SaleEntity.class));
    }

    @Test
    void confirmar_inexistente_devuelve404(){
        when(saleRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saleService.confirm(404L))
                .isInstanceOf(SaleException.class)
                .hasMessageContaining("no encontrada")
                .extracting(e -> ((SaleException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ===================== ANULAR =====================

    /**
     * Caso feliz: se devuelve exactamente la cantidad vendida en cada renglón.
     *
     * Se usa la cantidad del HISTÓRICO ({@code detail.getQuantity()}), no la
     * del request: en una anulación no hay request. Este es el test que
     * documenta que "deshacer" significa deshacer exactamente lo que se hizo.
     */
    @Test
    void anular_devuelveElStockDeCadaRenglon(){
        ProductEntity cafe = producto(10L, "Café", 8);
        ProductEntity pan = producto(11L, "Pan", 20);

        SaleEntity venta = ventaAbierta(1L, PaymentMethod.CASH,
                List.of(renglon(cafe, 3), renglon(pan, 5)));

        when(saleRepository.findById(1L)).thenReturn(Optional.of(venta));
        when(productRepository.findById(10L)).thenReturn(Optional.of(cafe));
        when(productRepository.findById(11L)).thenReturn(Optional.of(pan));
        when(saleRepository.save(any(SaleEntity.class)))
                .thenAnswer(i -> i.getArgument(0));

        SaleHistoryResponse r = saleService.cancel(1L);

        assertThat(cafe.getStock()).as("8 - 3").isEqualTo(11);
        assertThat(pan.getStock()).as("20 - 5").isEqualTo(25);

        assertThat(r.isCancelled()).isTrue();
        assertThat(r.getCancelledAt()).isNotNull();

        // Ni confirmada ni "revivida": sigue abierta en el eje de confirmación.
        assertThat(r.isConfirmed()).isFalse();
    }

    /**
     * EL CASO CENTRAL: una venta confirmada ya no se puede anular.
     *
     * Esta es la regla que cierra el agujero del bug reportado. Con ella, el
     * recorrido "vender 5 -> cancelar la compra que los trajo" queda cortado en el
     * primer paso, porque la venta de esas 5 unidades se confirma al salir el
     * pedido y desde ahí es intocable.
     */
    @Test
    void anular_ventaConfirmada_devuelve409YNiTocaElStock(){
        ProductEntity cafe = producto(10L, "Café", 5);
        SaleEntity venta = ventaAbierta(1L, PaymentMethod.CASH, List.of(renglon(cafe, 5)));
        venta.setConfirmed(true);

        when(saleRepository.findById(1L)).thenReturn(Optional.of(venta));

        assertThatThrownBy(() -> saleService.cancel(1L))
                .isInstanceOf(SaleException.class)
                .hasMessageContaining("ya confirmada")
                .extracting(e -> ((SaleException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(cafe.getStock()).isEqualTo(5);
        verify(productRepository, never()).save(any(ProductEntity.class));
        verify(saleRepository, never()).save(any(SaleEntity.class));
    }

    /**
     * Anular dos veces SUBIRÍA el stock dos veces: 8 -> 11 -> 14 por la misma
     * unidad vendida. Por eso es 409 y no un "no-op" silencioso.
     */
    @Test
    void anular_dosVeces_devuelve409YNiTocaElStock(){
        ProductEntity cafe = producto(10L, "Café", 8);
        SaleEntity venta = ventaAbierta(1L, PaymentMethod.CASH, List.of(renglon(cafe, 3)));
        venta.setCancelled(true);

        when(saleRepository.findById(1L)).thenReturn(Optional.of(venta));

        assertThatThrownBy(() -> saleService.cancel(1L))
                .isInstanceOf(SaleException.class)
                .hasMessageContaining("ya estaba anulada")
                .extracting(e -> ((SaleException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(cafe.getStock()).isEqualTo(8);
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    /**
     * Venta con tarjeta: NO se anula, porque el dinero ya está capturado.
     *
     * Anular aquí solo mentiría el historial; el dinero hay que devolverlo con
     * la reversa real del módulo de pagos. Es además una separación de
     * responsabilidades: el estado de la venta y el del pago son ejes distintos.
     */
    @Test
    void anular_ventaConTarjeta_devuelve409(){
        ProductEntity cafe = producto(10L, "Café", 8);
        SaleEntity venta = ventaAbierta(1L, PaymentMethod.CREDIT, List.of(renglon(cafe, 3)));

        when(saleRepository.findById(1L)).thenReturn(Optional.of(venta));

        assertThatThrownBy(() -> saleService.cancel(1L))
                .isInstanceOf(SaleException.class)
                .hasMessageContaining("tarjeta")
                .hasMessageContaining("reversa")
                .extracting(e -> ((SaleException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(cafe.getStock()).isEqualTo(8);
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    /**
     * Venta sin renglones: se anula igual (marca el estado) y no rompe.
     *
     * Cubre el {@code if (sale.getDetails() != null)}: una venta legacy sin
     * detalles no debe reventar con NPE al anularla.
     */
    @Test
    void anular_sinRenglones_marcaAnuladaSinFallar(){
        SaleEntity venta = ventaAbierta(1L, PaymentMethod.CASH, null);
        venta.setDetails(null);

        when(saleRepository.findById(1L)).thenReturn(Optional.of(venta));
        when(saleRepository.save(any(SaleEntity.class)))
                .thenAnswer(i -> i.getArgument(0));

        SaleHistoryResponse r = saleService.cancel(1L);

        assertThat(r.isCancelled()).isTrue();
        verify(productRepository, never()).save(any(ProductEntity.class));
    }

    @Test
    void anular_inexistente_devuelve404(){
        when(saleRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> saleService.cancel(404L))
                .isInstanceOf(SaleException.class)
                .hasMessageContaining("no encontrada")
                .extracting(e -> ((SaleException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    /**
     * El DTO que sale del mapper trae el estado de vida completo, para que el
     * frontend no tenga que deducirlo.
     */
    @Test
    void elMapperExponeElEstadoDeVidaEnElDto(){
        ProductEntity cafe = producto(10L, "Café", 8);
        SaleEntity venta = ventaAbierta(1L, PaymentMethod.CASH, List.of(renglon(cafe, 1)));
        venta.setConfirmed(true);
        venta.setConfirmedAt(java.time.LocalDateTime.now());

        SaleHistoryResponse r = mapper.toHistoryResponse(venta);

        assertThat(r.isConfirmed()).isTrue();
        assertThat(r.getConfirmedAt()).isNotNull();
        assertThat(r.isCancelled()).isFalse();
    }
}
