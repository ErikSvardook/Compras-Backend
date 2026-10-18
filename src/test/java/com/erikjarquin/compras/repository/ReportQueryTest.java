package com.erikjarquin.compras.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import com.erikjarquin.compras.model.entity.CategoryEntity;
import com.erikjarquin.compras.model.entity.ProductEntity;
import com.erikjarquin.compras.model.entity.SaleDetailEntity;
import com.erikjarquin.compras.model.entity.SaleEntity;
import com.erikjarquin.compras.model.enums.PaymentMethod;
import com.erikjarquin.compras.model.enums.PaymentStatus;

/**
 * Tests de las consultas agregadas de REPORTES sobre H2 en memoria.
 * Valida que las queries JPQL (CAST AS date, year/month, GROUP BY, LEFT JOIN,
 * Pageable) funcionen sobre el dialecto real que ejecutarán en producción
 * (PostgreSQL usa las mismas funciones SQL estándar).
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class ReportQueryTest {

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private LocalDateTime from = LocalDateTime.of(2026, 9, 1, 0, 0);
    private LocalDateTime to = LocalDateTime.of(2026, 10, 1, 0, 0);

    @BeforeEach
    void seed() {
        //Categorías y productos
        CategoryEntity bebidas = new CategoryEntity();
        bebidas.setName("Bebidas");
        categoryRepository.save(bebidas);

        ProductEntity gaseosa = new ProductEntity();
        gaseosa.setName("Gaseosa");
        gaseosa.setPrice(new BigDecimal("5.00"));
        gaseosa.setStock(3);
        gaseosa.setSku("SKU-1");
        gaseosa.setBarcode("1111");
        gaseosa.setCategory(bebidas);
        productRepository.save(gaseosa);

        //Producto SIN categoría (valida el LEFT JOIN en categoryPerformance)
        ProductEntity cafe = new ProductEntity();
        cafe.setName("Café");
        cafe.setPrice(new BigDecimal("2.50"));
        cafe.setStock(20);
        cafe.setSku("SKU-2");
        cafe.setBarcode("2222");
        productRepository.save(cafe);

        //Venta aprobada en efectivo
        saveSale(LocalDateTime.of(2026, 9, 10, 10, 0), new BigDecimal("10.00"),
                PaymentMethod.CASH, gaseosa, 2);

        //Venta aprobada débito — mismo producto
        saveSale(LocalDateTime.of(2026, 9, 20, 10, 0), new BigDecimal("15.00"),
                PaymentMethod.DEBIT, gaseosa, 3);

        //Venta RECHAZADA en crédito — se debe EXCLUIR de los agregados
        saveSale(LocalDateTime.of(2026, 9, 25, 10, 0), new BigDecimal("99.00"),
                PaymentMethod.CREDIT, cafe, 1);

        //Venta ANULADA: es la que destapó un bug. Anular deja
        //la fila con cancelled=true a propósito (evidencia contable) pero NO
        //cambia el paymentStatus, que sigue APPROVED. Si los agregados solo
        //filtraran por APPROVED, esta venta de 500.00 seguiría sumando ingresos.
        //Todos los tests de este archivo siguen esperando
        //2 ventas y 25.00 en total, así que son también regresión de eso.
        saveSale(LocalDateTime.of(2026, 9, 28, 10, 0), new BigDecimal("500.00"),
                PaymentMethod.CASH, cafe, 1, true);
    }

    private void saveSale(LocalDateTime date, BigDecimal total, PaymentMethod method,
                          ProductEntity product, int quantity){
        saveSale(date, total, method, product, quantity, false);
    }

    /**
     * @param unitCost costo congelado del producto al vender. Si viene
     *                null se deja NULL a propósito: simula un renglón sin costo
     *                conocido.
     * @param cancelled la venta fue anulada. No cambia el paymentStatus.
     */
    private void saveSale(LocalDateTime date, BigDecimal total, PaymentMethod method,
                          ProductEntity product, int quantity, boolean cancelled){
        saveSale(date, total, method, product, quantity, cancelled, null);
    }

    private void saveSale(LocalDateTime date, BigDecimal total, PaymentMethod method,
                          ProductEntity product, int quantity, boolean cancelled,
                          BigDecimal unitCost){
        SaleEntity sale = new SaleEntity();
        sale.setSaleDate(date);
        sale.setTotal(total);
        sale.setPaymentMethod(method);
        sale.setPaymentStatus(method == PaymentMethod.CREDIT
                ? PaymentStatus.REJECTED : PaymentStatus.APPROVED);
        sale.setCancelled(cancelled);
        if(cancelled){
            sale.setCancelledAt(date);
        }

        SaleDetailEntity detail = new SaleDetailEntity();
        detail.setSale(sale);
        detail.setProduct(product);
        detail.setQuantity(quantity);
        //REVISAR ESTA LÍNEA: el total de la venta se reparte entre las unidades vendidas
        detail.setUnitPrice(total.divide(BigDecimal.valueOf(quantity), 2));
        detail.setUnitCost(unitCost);
        detail.setSubTotal(total);
        sale.setDetails(List.of(detail));

        saleRepository.save(sale);
    }

    @Test
    void sumSalesByDay_agrupaPorDiaSoloAprobadas() {
        List<Object[]> rows = saleRepository.sumSalesByDay(from, to, PaymentStatus.APPROVED);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)[1]).isEqualTo(new BigDecimal("10.00"));
        assertThat(rows.get(1)[1]).isEqualTo(new BigDecimal("15.00"));

        //Ninguna fila debe contener la venta rechazada (7.50 en total si incluyera)
        BigDecimal sum = rows.stream()
                .map(r -> new BigDecimal(r[1].toString()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualTo(new BigDecimal("25.00"));
    }

    @Test
    void sumSalesByMonth_agrupaPorMes() {
        List<Object[]> rows = saleRepository.sumSalesByMonth(from, to, PaymentStatus.APPROVED);

        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0)[0]).intValue()).isEqualTo(2026);
        assertThat(((Number) rows.get(0)[1]).intValue()).isEqualTo(9);
        assertThat(rows.get(0)[2]).isEqualTo(new BigDecimal("25.00"));
        assertThat(((Number) rows.get(0)[3]).longValue()).isEqualTo(2);
    }

    @Test
    void sumSalesByYear_agrupaPorAnio() {
        List<Object[]> rows = saleRepository.sumSalesByYear(from, to, PaymentStatus.APPROVED);

        assertThat(rows).hasSize(1);
        assertThat(((Number) rows.get(0)[0]).intValue()).isEqualTo(2026);
        assertThat(rows.get(0)[1]).isEqualTo(new BigDecimal("25.00"));
    }

    @Test
    void findTopSellingProducts_ordenaPorCantidadYRigeLimite() {
        List<Object[]> rows = saleRepository.findTopSellingProducts(
                from, to, PaymentStatus.APPROVED, PageRequest.of(0, 1));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[1]).isEqualTo("Gaseosa");
        assertThat(((Number) rows.get(0)[2]).longValue()).isEqualTo(5);
        assertThat(new BigDecimal(rows.get(0)[3].toString()))
                .isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    void findPaymentMethodDistribution_agrupaPorMetodo() {
        List<Object[]> rows = saleRepository.findPaymentMethodDistribution(
                from, to, PaymentStatus.APPROVED);

        assertThat(rows).hasSize(2); //CASH y DEBIT (no CREDIT rechazado)
        assertThat(((PaymentMethod) rows.get(0)[0])).isEqualTo(PaymentMethod.CASH);
        assertThat(((PaymentMethod) rows.get(1)[0])).isEqualTo(PaymentMethod.DEBIT);
    }

    @Test
    void findCategoryPerformance_incluyeProductosSinCategoria() {
        //Venta APROBADA del café (que no tiene categoría) dentro de este test
        saveSale(LocalDateTime.of(2026, 9, 15, 10, 0), new BigDecimal("5.00"),
                PaymentMethod.CASH, productRepository.findByBarcode("2222").orElseThrow(), 2);

        List<Object[]> rows = saleRepository.findCategoryPerformance(from, to, PaymentStatus.APPROVED);

        //Bebidas (25.00) y la fila null (Café sin categoría, 5.00)
        assertThat(rows).hasSize(2);

        Object[] bebidas = rows.get(0);
        assertThat(bebidas[1]).isEqualTo("Bebidas");
        assertThat(new BigDecimal(bebidas[3].toString()))
                .isEqualByComparingTo(new BigDecimal("25.00"));

        Object[] sinCategoria = rows.get(1);
        assertThat(sinCategoria[1]).isNull();
        assertThat(new BigDecimal(sinCategoria[3].toString()))
                .isEqualByComparingTo(new BigDecimal("5.00"));
    }

    @Test
    void findLowStock_filtraYOrdena() {
        List<ProductEntity> rows = productRepository.findLowStock(5);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getName()).isEqualTo("Gaseosa");
        assertThat(rows.get(0).getStock()).isEqualTo(3);
    }

    @Test
    void sumSalesTotals_devuelveConteoYTotal() {
        List<Object[]> rows = saleRepository.sumSalesTotals(from, to, PaymentStatus.APPROVED);

        assertThat(((Number) rows.get(0)[0]).longValue()).isEqualTo(2);
        assertThat(new BigDecimal(rows.get(0)[1].toString()))
                .isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(new BigDecimal(rows.get(0)[2].toString()))
                .isEqualByComparingTo(new BigDecimal("12.50"));
    }

    /**
     * Una venta ANULADA (cancelled=true) NO cuenta.
     *
     * El seed incluye una venta anulada de 500.00 con paymentStatus APPROVED.
     * Si el filtro {@code cancelled = false} desapareciera de cualquier consulta,
     * el total saltaría de 25.00 a 525.00 y los tests de arriba fallarían.
     */
    @Test
    void ventaAnulada_noSumaIngresosAunqueElPagoEsteAprobado() {
        List<Object[]> totals = saleRepository.sumSalesTotals(from, to, PaymentStatus.APPROVED);

        assertThat(new BigDecimal(totals.get(0)[1].toString()))
                .as("la venta anulada no debe sumar ingresos")
                .isEqualByComparingTo(new BigDecimal("25.00"));

        // Tampoco debe aparecer en la tendencia por día
        List<Object[]> byDay = saleRepository.sumSalesByDay(from, to, PaymentStatus.APPROVED);
        assertThat(byDay).hasSize(2);

        // Ni en productos más vendidos: el Café solo aparece en ventas RECHAZADA y
        // ANULADA, así que no debe existir como producto vendido
        List<Object[]> top = saleRepository.findTopSellingProducts(
                from, to, PaymentStatus.APPROVED, PageRequest.of(0, 10));
        assertThat(top).hasSize(1);
        assertThat(top.get(0)[1]).isEqualTo("Gaseosa");
    }

    // =========================================================================
    //  COSTO DE LO VENDIDO — la base de la utilidad
    // =========================================================================

    /**
     * El costo de lo vendido sale del unitCost CONGELADO en el renglón, y solo de
     * las ventas válidas. Aquí las dos ventas aprobadas no tienen costo (NULL),
     * así que el total es 0 y el contador de renglones sin costo es 2: el
     * reporte debe poder decir "no sé" en vez de inventar un 0 que finge una
     * utilidad de 25.00.
     */
    @Test
    void sumCostOfGoodsSold_sinCostosDevuelveCeroYCuentaLosDesconocidos() {
        List<Object[]> rows = saleRepository.sumCostOfGoodsSold(from, to, PaymentStatus.APPROVED);

        assertThat(new BigDecimal(rows.get(0)[0].toString()))
                .as("sin costo conocido, el costo de lo vendido es 0")
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(((Number) rows.get(0)[1]).longValue())
                .as("2 renglones sin costo (los de las 2 ventas aprobadas)")
                .isEqualTo(2);

        assertThat(((Number) rows.get(0)[2]).longValue())
                .as("unidades vendidas: 2 + 3")
                .isEqualTo(5);
    }

    @Test
    void sumCostOfGoodsSold_usaElCostoCongeladoYExcluyeAnuladas() {
        //Gaseosa a costo 3.00 (costo congelado al vender)
        ProductEntity gaseosa = productRepository.findAll().stream()
                .filter(p -> "Gaseosa".equals(p.getName()))
                .findFirst()
                .orElseThrow();

        //Se agrega una venta con costo conocido, fuera del rango: debe ignorarse
        ProductEntity cafe = productRepository.findAll().stream()
                .filter(p -> "Café".equals(p.getName()))
                .findFirst()
                .orElseThrow();
        cafe.setCost(new BigDecimal("2.00"));
        productRepository.save(cafe);

        //Venta en el rango: 2 gaseosas × 3.00 = 6.00
        saleRepository.save(saleConCosto(
                LocalDateTime.of(2026, 9, 15, 10, 0), gaseosa, 2,
                new BigDecimal("3.00"), false));

        List<Object[]> rows = saleRepository.sumCostOfGoodsSold(from, to, PaymentStatus.APPROVED);

        assertThat(new BigDecimal(rows.get(0)[0].toString()))
                .as("solo cuenta la venta con costo conocido del rango")
                .isEqualByComparingTo(new BigDecimal("6.00"));

        assertThat(((Number) rows.get(0)[1]).longValue())
                .as("siguen siendo 2 los renglones sin costo: los 2 del seed")
                .isEqualTo(2);
    }

    /**
     * Una venta anulada no aporta costo de lo vendido: si lo hiciera, anular una
     * venta bajaría la utilidad cuando en realidad la utilidad tampoco se
     * contabilizó (su ingreso tampoco suma).
     */
    @Test
    void ventaAnulada_noAportaCostoDeLoVendido() {
        ProductEntity cafe = productRepository.findAll().stream()
                .filter(p -> "Café".equals(p.getName()))
                .findFirst()
                .orElseThrow();

        SaleEntity anulada = saleConCosto(
                LocalDateTime.of(2026, 9, 20, 10, 0), cafe, 4,
                new BigDecimal("2.00"), true);
        saleRepository.save(anulada);

        List<Object[]> rows = saleRepository.sumCostOfGoodsSold(from, to, PaymentStatus.APPROVED);

        assertThat(new BigDecimal(rows.get(0)[0].toString()))
                .as("la venta anulada no suma costo")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    //Helper: venta con costo congelado en el renglón
    private SaleEntity saleConCosto(LocalDateTime date, ProductEntity product, int quantity,
                                    BigDecimal unitCost, boolean cancelled){
        SaleEntity sale = new SaleEntity();
        sale.setSaleDate(date);
        sale.setTotal(new BigDecimal("50.00"));
        sale.setPaymentMethod(PaymentMethod.CASH);
        sale.setPaymentStatus(PaymentStatus.APPROVED);
        sale.setCancelled(cancelled);

        SaleDetailEntity detail = new SaleDetailEntity();
        detail.setSale(sale);
        detail.setProduct(product);
        detail.setQuantity(quantity);
        detail.setUnitCost(unitCost);
        detail.setSubTotal(new BigDecimal("50.00"));
        sale.setDetails(List.of(detail));

        return sale;
    }
}