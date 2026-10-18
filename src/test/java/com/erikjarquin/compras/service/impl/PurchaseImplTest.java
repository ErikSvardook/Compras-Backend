package com.erikjarquin.compras.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.erikjarquin.compras.exceptions.PurchaseException;
import com.erikjarquin.compras.model.dto.Purchases.PurchaseDTO;
import com.erikjarquin.compras.model.dto.Purchases.PurchaseItemRequest;
import com.erikjarquin.compras.model.dto.Purchases.PurchaseRequest;
import com.erikjarquin.compras.model.entity.ProductEntity;
import com.erikjarquin.compras.model.entity.ProviderEntity;
import com.erikjarquin.compras.model.entity.PurchaseDetailEntity;
import com.erikjarquin.compras.model.entity.PurchaseEntity;
import com.erikjarquin.compras.repository.ProductRepository;
import com.erikjarquin.compras.repository.ProviderRepository;
import com.erikjarquin.compras.repository.PurchaseRepository;

/**
 * Tests unitarios de PurchaseImpl (Mockito, sin BD).
 *
 * Verifican la REGLA DE NEGOCIO del módulo en su diseño: registrar una
 * compra no toca el inventario, {@code confirm} es lo que suma stock y
 * guarda el costo, y una compra confirmada ya no se puede cancelar.
 *
 * Por qué estos tests parecen "repetir" lo obvio
 *
 * La tentación es pensar que "crear no suma stock" no necesita un test. Sí
 * lo necesita, y mucho: {@code create()} SÍ sumaba stock, y ese
 * {@code verify(productRepository, never()).save(...)} es lo que impide que
 * alguien reintroduzca el efecto "de paso" cuando agregue un campo nuevo.
 *
 * El bug del {@code Math.max}, ahora estructuralmente imposible
 *
 * (comprar 10 -> vender 5 -> cancelar escribía un
 * {@code Math.max(5 - 10, 0)} = 0 falso con 5 unidades ya vendidas y
 * devolvía 200 OK) se cubría con una validación de stock en dos fases. Ese escenario ya no se puede representar: la compra pendiente nunca tocó el
 * inventario, así que no hay nada que deshacer. Los tests lo fijan desde el otro
 * lado: {@code cancelarCompra_pendiente_nuncaLeeLosProductos} exige que cancelar
 * ni siquiera consulte los productos, y
 * {@code confirmarCompra_pendiente_sumaStockYGuardaCosto} exige que el stock se
 * aplique una sola vez.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseImplTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private ProviderRepository providerRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private PurchaseImpl purchaseService;

    //Helper: request de compra mínima (1 renglón)
    private PurchaseRequest request(Long providerId, Long productId, int qty, String unitCost){
        PurchaseItemRequest item = new PurchaseItemRequest();
        item.setProductId(productId);
        item.setQuantity(qty);
        item.setUnitCost(new BigDecimal(unitCost));

        PurchaseRequest request = new PurchaseRequest();
        request.setProviderId(providerId);
        request.setItems(List.of(item));
        return request;
    }

    //Helper: proveedor con id
    private ProviderEntity provider(Long id, String name){
        ProviderEntity provider = new ProviderEntity();
        provider.setId(id);
        provider.setName(name);
        return provider;
    }

    //Helper: producto con id
    private ProductEntity product(Long id, String name, int stock){
        ProductEntity product = new ProductEntity();
        product.setId(id);
        product.setName(name);
        product.setStock(stock);
        return product;
    }

    // =========================================================================
    //  REGISTRAR: nace PENDIENTE y NO toca el inventario
    // =========================================================================

    @Test
    void crearCompra_nacePendienteYNoTocaElInventario(){
        ProviderEntity provider = provider(1L, "Proveedor Alfa");
        ProductEntity cafe = product(10L, "Café 1kg", 5);

        when(providerRepository.findById(1L)).thenReturn(Optional.of(provider));
        when(productRepository.findById(10L)).thenReturn(Optional.of(cafe));
        when(purchaseRepository.save(any(PurchaseEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseDTO dto = purchaseService.create(request(1L, 10L, 3, "40"));

        // Nace PENDIENTE: sin confirmar y sin fecha de confirmación
        assertThat(dto.isConfirmed()).isFalse();
        assertThat(dto.getConfirmedAt()).isNull();

        // El inventario NO se tocó: ni stock ni costo
        assertThat(cafe.getStock())
                .as("registrar una compra no debe sumar stock: eso es lo de confirmar")
                .isEqualTo(5);
        assertThat(cafe.getCost())
                .as("registrar una compra no debe escribir el costo: eso es lo de confirmar")
                .isEqualByComparingTo(BigDecimal.ZERO);

        // LO IMPORTANTE: no se escribió el producto en absoluto
        verify(productRepository, never()).save(any(ProductEntity.class));

        // El total sí se calcula al registrar (es el dato del documento)
        assertThat(dto.getTotal()).isEqualByComparingTo("120.00");
        assertThat(dto.getItems()).hasSize(1);
        assertThat(dto.getProviderName()).isEqualTo("Proveedor Alfa");
    }

    @Test
    void crearCompra_proveedorInexistenteLanza409(){
        when(providerRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseService.create(request(99L, 10L, 2, "50")))
                .isInstanceOf(PurchaseException.class)
                .hasMessageContaining("proveedor");
    }

    @Test
    void crearCompra_sinItemsLanzaIllegalArgumentException(){
        PurchaseRequest request = new PurchaseRequest();
        request.setProviderId(1L);
        request.setItems(List.of());

        assertThatThrownBy(() -> purchaseService.create(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("artículo");
    }

    // =========================================================================
    //  CONFIRMAR: aquí sí entran la mercancía y el costo
    // =========================================================================

    @Test
    void confirmarCompra_pendiente_sumaStockYGuardaCosto(){
        ProductEntity cafe = product(10L, "Café 1kg", 5);

        PurchaseDetailEntity detail = new PurchaseDetailEntity();
        detail.setProduct(cafe);
        detail.setQuantity(3);
        detail.setUnitCost(new BigDecimal("40"));

        PurchaseEntity purchase = new PurchaseEntity();
        purchase.setId(5L);
        purchase.setDetails(List.of(detail));

        when(purchaseRepository.findDetailedById(5L)).thenReturn(Optional.of(purchase));
        when(productRepository.findById(10L)).thenReturn(Optional.of(cafe));
        //1 fila afectada = esta petición ganó la carrera y SÍ aplica el stock.
        when(purchaseRepository.markConfirmedIfPending(eq(5L), any())).thenReturn(1);

        PurchaseDTO dto = purchaseService.confirm(5L);

        // Stock + costo: el efecto por el que existe el módulo de compras
        assertThat(cafe.getStock()).isEqualTo(8);
        assertThat(cafe.getCost()).isEqualByComparingTo("40");

        assertThat(dto.isConfirmed()).isTrue();
        assertThat(dto.getConfirmedAt()).isNotNull();

        verify(productRepository).save(cafe);
    }

    /**
     * LA CARRERA DE STOCK DUPLICADO. Este es el test que blinda el
     * arreglo de concurrencia.
     *
     * Simula exactamente lo que pasa con dos peticiones simultáneas:
     *   La petición A llega primero: su {@code markConfirmedIfPending} actualiza
     *       1 fila, porque la compra estaba pendiente.
     *   La petición B llega con la compra todavía cacheada como pendiente
     *       (leída antes de que A escribiera), pero al ejecutar su UPDATE la
     *       condición {@code confirmed = false} ya no se cumple y devuelve
     *       0 filas.
     *
     * El detalle que hace el test valioso: la entidad en memoria de B todavía
     * dice {@code confirmed = false}, o sea que un {@code if (entity.isConfirmed())}
     * la habría dejado sumar el stock y la mercancía quedaría duplicada. Lo
     * que la salva es que el código decide por el número de filas afectadas, no
     * por el estado que leyó.
     */
    @Test
    void confirmarCompra_carreraConOtraPeticion_NoSumaStockDosVeces(){
        ProductEntity cafe = product(10L, "Café 1kg", 8);

        PurchaseDetailEntity detail = new PurchaseDetailEntity();
        detail.setProduct(cafe);
        detail.setQuantity(3);
        detail.setUnitCost(new BigDecimal("40"));

        //La compra que ve la SEGUNDA petición: aún marcada como pendiente en
        //memoria, porque la leyó antes de que la primera escribiera.
        PurchaseEntity vistaPorLaSegunda = new PurchaseEntity();
        vistaPorLaSegunda.setId(5L);
        vistaPorLaSegunda.setConfirmed(false);
        vistaPorLaSegunda.setDetails(List.of(detail));

        when(purchaseRepository.findDetailedById(5L)).thenReturn(Optional.of(vistaPorLaSegunda));
        //0 filas = alguien más confirmó primero. ESTE es el valor que manda.
        when(purchaseRepository.markConfirmedIfPending(eq(5L), any())).thenReturn(0);

        PurchaseDTO dto = purchaseService.confirm(5L);

        assertThat(dto.isConfirmed()).isFalse();

        assertThat(cafe.getStock())
                .as("la segunda petición NO debe sumar stock aunque su copia diga pendiente")
                .isEqualTo(8);

        //Ni el producto ni la compra se escriben
        verify(productRepository, never()).save(any(ProductEntity.class));
        verify(productRepository, never()).findById(any());
    }

    @Test
    void confirmarCompra_inexistenteLanza404(){
        when(purchaseRepository.findDetailedById(404L)).thenReturn(Optional.empty());
        when(purchaseRepository.markConfirmedIfPending(eq(404L), any())).thenReturn(0);

        assertThatThrownBy(() -> purchaseService.confirm(404L))
                .isInstanceOf(PurchaseException.class)
                .hasMessageContaining("no encontrada");
    }

    @Test
    void confirmarCompra_dosRenglones_sumaTodosLosStocks(){
        ProductEntity leche = product(10L, "Leche", 20);
        ProductEntity cafe = product(11L, "Café 1kg", 2);

        PurchaseDetailEntity detLeche = new PurchaseDetailEntity();
        detLeche.setProduct(leche);
        detLeche.setQuantity(10);
        detLeche.setUnitCost(new BigDecimal("25"));

        PurchaseDetailEntity detCafe = new PurchaseDetailEntity();
        detCafe.setProduct(cafe);
        detCafe.setQuantity(4);
        detCafe.setUnitCost(new BigDecimal("40"));

        PurchaseEntity purchase = new PurchaseEntity();
        purchase.setId(9L);
        purchase.setDetails(List.of(detLeche, detCafe));

        when(purchaseRepository.findDetailedById(9L)).thenReturn(Optional.of(purchase));
        when(productRepository.findById(10L)).thenReturn(Optional.of(leche));
        when(productRepository.findById(11L)).thenReturn(Optional.of(cafe));
        when(purchaseRepository.markConfirmedIfPending(eq(9L), any())).thenReturn(1);

        purchaseService.confirm(9L);

        assertThat(leche.getStock()).isEqualTo(30);
        assertThat(cafe.getStock()).isEqualTo(6);
        assertThat(leche.getCost()).isEqualByComparingTo("25");
        assertThat(cafe.getCost()).isEqualByComparingTo("40");
        verify(productRepository, times(2)).save(any(ProductEntity.class));
    }

    // =========================================================================
    //  El precio de VENTA del renglón — se escribe al CONFIRMAR
    // =========================================================================

    /**
     * Confirmar actualiza las dos columnas del producto: el stock y el
     * precio de venta. Es el cambio de regla que pidió el dueño: al reponer stock
     * se escribe también a qué precio se va a vender ese lote.
     */
    @Test
    void confirmarCompra_actualizaStockCostoYPrecioDeVenta(){
        ProductEntity cafe = product(10L, "Café 1kg", 5);
        cafe.setCost(new BigDecimal("35")); //costo anterior
        cafe.setPrice(new BigDecimal("150")); //precio anterior

        PurchaseDetailEntity detail = new PurchaseDetailEntity();
        detail.setProduct(cafe);
        detail.setQuantity(3);
        detail.setUnitCost(new BigDecimal("40"));  //nuevo costo
        detail.setUnitPrice(new BigDecimal("165")); //nuevo precio de venta

        PurchaseEntity purchase = new PurchaseEntity();
        purchase.setId(5L);
        purchase.setDetails(List.of(detail));

        when(purchaseRepository.findDetailedById(5L)).thenReturn(Optional.of(purchase));
        when(productRepository.findById(10L)).thenReturn(Optional.of(cafe));
        when(purchaseRepository.markConfirmedIfPending(eq(5L), any())).thenReturn(1);

        purchaseService.confirm(5L);

        assertThat(cafe.getStock()).isEqualTo(8);
        assertThat(cafe.getCost()).isEqualByComparingTo("40");
        assertThat(cafe.getPrice()).isEqualByComparingTo("165");
    }

    /**
     * Si el renglón NO trae precio de venta (null), el producto conserva el que
     * ya tenía.
     *
     * Es lo que hace que el campo sea opcional de verdad: un cliente que no
     * llena la caja no debe ver el producto con precio 0.
     */
    @Test
    void confirmarCompra_sinPrecioDeVenta_conservaElQueTenia(){
        ProductEntity cafe = product(10L, "Café 1kg", 5);
        cafe.setPrice(new BigDecimal("150"));

        PurchaseDetailEntity detail = new PurchaseDetailEntity();
        detail.setProduct(cafe);
        detail.setQuantity(3);
        detail.setUnitCost(new BigDecimal("40"));
        detail.setUnitPrice(null); //el usuario no lo llenó

        PurchaseEntity purchase = new PurchaseEntity();
        purchase.setId(5L);
        purchase.setDetails(List.of(detail));

        when(purchaseRepository.findDetailedById(5L)).thenReturn(Optional.of(purchase));
        when(productRepository.findById(10L)).thenReturn(Optional.of(cafe));
        when(purchaseRepository.markConfirmedIfPending(eq(5L), any())).thenReturn(1);

        purchaseService.confirm(5L);

        assertThat(cafe.getPrice())
                .as("null significa 'no opina sobre el precio': se conserva el actual")
                .isEqualByComparingTo("150");
    }

    // =========================================================================
    //  CANCELAR: solo pendientes, y sin tocar NADA
    // =========================================================================

    @Test
    void cancelarCompra_pendiente_laBorraYNoEscribe(){
        ProductEntity cafe = product(10L, "Café 1kg", 8);

        PurchaseDetailEntity detail = new PurchaseDetailEntity();
        detail.setProduct(cafe);
        detail.setQuantity(3);

        PurchaseEntity purchase = new PurchaseEntity();
        purchase.setId(5L);
        purchase.setConfirmed(false);
        purchase.setDetails(List.of(detail));

        when(purchaseRepository.findDetailedById(5L)).thenReturn(Optional.of(purchase));

        purchaseService.cancel(5L);

        verify(purchaseRepository).delete(purchase);

        // La garantía del diseño: una compra pendiente nunca tocó el
        // inventario, así que cancelar no consulta NI ESCRIBE los productos.
        verify(productRepository, never()).findById(any());
        verify(productRepository, never()).save(any(ProductEntity.class));
        assertThat(cafe.getStock()).isEqualTo(8);
    }

    @Test
    void cancelarCompra_inexistenteLanza404(){
        when(purchaseRepository.findDetailedById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> purchaseService.cancel(404L))
                .isInstanceOf(PurchaseException.class)
                .hasMessageContaining("no encontrada");
    }

    /**
     * El escenario reportado por el usuario: comprar 10, vender 5 (quedan 5) y
     * luego cancelar la compra.
     *
     * El código viejo hacía {@code 5 - 10 = -5} y luego
     * {@code Math.max(-5, 0) = 0}: reportaba "0 en almacén" con 5 unidades ya
     * vendidas y devolvía 200 OK.
     *
     * Hoy, con el stock en 5 y la compra en estado PENDIENTE, ese escenario
     * es imposible por construcción: una compra pendiente no suman stock. Y si
     * la compra está CONFIRMADA, se rechaza con 409. Este test fija el segundo
     * caso, que es el que el usuario podría alcanzar: no se escribe nada.
     */
    @Test
    void cancelarCompra_confirmada_Lanza409YNoEscribe(){
        // Compramos 10, se vendió 5 -> quedan 5, y la compra está confirmada.
        ProductEntity cafe = product(10L, "Café 1kg", 5);

        PurchaseDetailEntity detail = new PurchaseDetailEntity();
        detail.setProduct(cafe);
        detail.setQuantity(10);

        PurchaseEntity purchase = new PurchaseEntity();
        purchase.setId(5L);
        purchase.setConfirmed(true);
        purchase.setDetails(List.of(detail));

        when(purchaseRepository.findDetailedById(5L)).thenReturn(Optional.of(purchase));

        assertThatThrownBy(() -> purchaseService.cancel(5L))
                .isInstanceOf(PurchaseException.class)
                .hasMessageContaining("confirmada")
                .extracting(e -> ((PurchaseException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        // LO MÁS IMPORTANTE: ni el stock ni la fila se tocaron
        assertThat(cafe.getStock())
                .as("el stock NO debe tocarse si la operación se rechaza")
                .isEqualTo(5);

        verify(productRepository, never()).save(any(ProductEntity.class));
        verify(purchaseRepository, never()).delete(any(PurchaseEntity.class));
    }

    /**
     * Cancelar una compra confirmada da 409 aunque el stock de sobra.
     *
     * Este es el test que mejor explica por qué el estado es lo que manda y
     * no el stock: con 50 unidades en almacén la validación antigua habría
     * permitido "deshacer" la compra y restado 10, dejando el inventario
     * inconsistente con lo que realmente llegó. El costo y el stock ya son
     * hechos; la compra es evidencia de una entrega real.
     */
    @Test
    void cancelarCompra_confirmadaConStockDeSobra_Lanza409(){
        ProductEntity pan = product(20L, "Pan", 50);

        PurchaseDetailEntity detail = new PurchaseDetailEntity();
        detail.setProduct(pan);
        detail.setQuantity(10);

        PurchaseEntity purchase = new PurchaseEntity();
        purchase.setId(6L);
        purchase.setConfirmed(true);
        purchase.setDetails(List.of(detail));

        when(purchaseRepository.findDetailedById(6L)).thenReturn(Optional.of(purchase));

        assertThatThrownBy(() -> purchaseService.cancel(6L))
                .isInstanceOf(PurchaseException.class)
                .extracting(e -> ((PurchaseException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        assertThat(pan.getStock()).isEqualTo(50);
        verify(productRepository, never()).save(any(ProductEntity.class));
        verify(purchaseRepository, never()).delete(any(PurchaseEntity.class));
    }
}
