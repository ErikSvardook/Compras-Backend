package com.erikjarquin.compras.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erikjarquin.compras.model.entity.CashRegisterEntity;
import com.erikjarquin.compras.model.entity.SaleEntity;
import com.erikjarquin.compras.model.enums.PaymentStatus;

/**
 * Repositorio de ventas. Además del corte de caja
 * ({@code findByCashRegister}) contiene las consultas de AGRAGADOS del módulo
 * de reportes ({@code ReportsImpl}), todas acotadas por rango de fechas y
 * estado APPROVED para no contabilizar ventas canceladas/reversadas.
 *
 * Todas las consultas filtran además {@code cancelled = false}. Anular una venta la BORRABA, así que nunca aparecía en
 * un reporte. Dejó en la tabla con {@code cancelled = true} a propósito,
 * porque es evidencia contable y eso destapó este bug: {@code cancel()} no
 * cambia el {@code paymentStatus}, que sigue en APPROVED. Resultado: una venta
 * anulada seguía sumando ingresos, y con V3 también sumaría costo, inflando la
 * utilidad. Filtrar por APPROVED no excluye las anuladas; hacen falta las
 * dos condiciones, porque el estado del pago y el de la venta son ejes
 * ortogonales a propósito.
 */
public interface SaleRepository extends JpaRepository<SaleEntity, Long> {
    List<SaleEntity> findByCashRegister(CashRegisterEntity cashRegister);

    /**
     * Venta ya creada con esta clave de idempotencia.
     *
     * Es lo que permite que un doble "Enter" en el modal de cobro NO haga
     * dos ventas: la segunda petición encuentra aquí la primera y devuelve esa
     * misma venta en vez de crear otra.
     *
     * Es un nombre DERIVADO y por eso funciona: {@code findBy} +
     * {@code IdempotencyKey} resuelve a la propiedad {@code idempotencyKey} de
     * {@code SaleEntity}, que existe. (En cambio {@code existsByCashRegistersId}
     * en {@code CashBoxRepository} NO funcionaba, porque buscaba una propiedad
     * que no existe en ese lado de la relación; ver el javadoc de V4.)
     */
    Optional<SaleEntity> findByIdempotencyKey(String idempotencyKey);

    //Tendencia por día
    @Query("""
        SELECT CAST(s.saleDate AS date), SUM(s.total), COUNT(s)
        FROM SaleEntity s
        WHERE s.paymentStatus = :status AND s.cancelled = false AND s.saleDate >= :from AND s.saleDate < :to
        GROUP BY CAST(s.saleDate AS date)
        ORDER BY CAST(s.saleDate AS date)
    """)
    List<Object[]> sumSalesByDay(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") PaymentStatus status);

    //Tendencia por mes
    @Query("""
        SELECT year(CAST(s.saleDate AS date)), month(CAST(s.saleDate AS date)), SUM(s.total), COUNT(s)
        FROM SaleEntity s
        WHERE s.paymentStatus = :status AND s.cancelled = false AND s.saleDate >= :from AND s.saleDate < :to
        GROUP BY year(CAST(s.saleDate AS date)), month(CAST(s.saleDate AS date))
        ORDER BY year(CAST(s.saleDate AS date)), month(CAST(s.saleDate AS date))
    """)
    List<Object[]> sumSalesByMonth(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") PaymentStatus status);

    //Tendencia por año
    @Query("""
        SELECT year(CAST(s.saleDate AS date)), SUM(s.total), COUNT(s)
        FROM SaleEntity s
        WHERE s.paymentStatus = :status AND s.cancelled = false AND s.saleDate >= :from AND s.saleDate < :to
        GROUP BY year(CAST(s.saleDate AS date))
        ORDER BY year(CAST(s.saleDate AS date))
    """)
    List<Object[]> sumSalesByYear(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") PaymentStatus status);

    //Productos más vendidos (por unidades)
    @Query("""
        SELECT d.product.id, d.product.name, SUM(d.quantity), SUM(d.subtotal)
        FROM SaleDetailEntity d JOIN d.sale s
        WHERE s.paymentStatus = :status AND s.cancelled = false AND s.saleDate >= :from AND s.saleDate < :to
        GROUP BY d.product.id, d.product.name
        ORDER BY SUM(d.quantity) DESC
    """)
    List<Object[]> findTopSellingProducts(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") PaymentStatus status,
            Pageable pageable);

    //Distribución por método de pago
    @Query("""
        SELECT s.paymentMethod, SUM(s.total), COUNT(s)
        FROM SaleEntity s
        WHERE s.paymentStatus = :status AND s.cancelled = false AND s.saleDate >= :from AND s.saleDate < :to
        GROUP BY s.paymentMethod
        ORDER BY s.paymentMethod
    """)
    List<Object[]> findPaymentMethodDistribution(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") PaymentStatus status);

    //Rendimiento por categoría
    @Query("""
        SELECT c.id, c.name, SUM(d.quantity), SUM(d.subtotal)
        FROM SaleDetailEntity d JOIN d.sale s LEFT JOIN d.product p LEFT JOIN p.category c
        WHERE s.paymentStatus = :status AND s.cancelled = false AND s.saleDate >= :from AND s.saleDate < :to
        GROUP BY c.id, c.name
        ORDER BY SUM(d.subtotal) DESC
    """)
    List<Object[]> findCategoryPerformance(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") PaymentStatus status);

    //Totales del rango (conteo, suma y ticket promedio)
    @Query("""
        SELECT COUNT(s), SUM(s.total), AVG(s.total)
        FROM SaleEntity s
        WHERE s.paymentStatus = :status AND s.cancelled = false AND s.saleDate >= :from AND s.saleDate < :to
    """)
    List<Object[]> sumSalesTotals(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") PaymentStatus status);

    /**
     * UTILIDAD: costo real de lo vendido en el rango.
     *
     * Suma {@code quantity * unitCost} de los renglones de las ventas válidas,
     * usando el costo CONGELADO en el renglón y no {@code product.cost}. Razón:
     * {@code product.cost} es el costo del último purchase de hoy, así que
     * comprar algo más barato mañana reescribiría la utilidad de ayer. El
     * costo se congela al vender justamente para esto.
     *
     * Los renglones con {@code unitCost} NULL se cuentan como 0 (COALESCE) y
     * el segundo valor del SELECT cuenta cuántos hay, para que el reporte pueda
     * avisar "N renglones sin costo conocido" en vez de mentir con una utilidad
     * inflada. Los renglones anteriores a V3 se rellenaron con el costo actual
     * del producto como mejor aproximación (ver la migración).
     */
    @Query("""
        SELECT COALESCE(SUM(d.quantity * COALESCE(d.unitCost, 0)), 0),
               COUNT(CASE WHEN d.unitCost IS NULL THEN 1 END),
               COALESCE(SUM(d.quantity), 0)
        FROM SaleDetailEntity d JOIN d.sale s
        WHERE s.paymentStatus = :status AND s.cancelled = false
              AND s.saleDate >= :from AND s.saleDate < :to
    """)
    List<Object[]> sumCostOfGoodsSold(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("status") PaymentStatus status);

    /**
     * Ventas de una caja concreta, para el detalle "que se vendio en esta
     * caja". Filtra las anuladas igual que el resto de reportes.
     */
    @Query("""
        SELECT s
        FROM SaleEntity s
        WHERE s.cashRegister.id = :cashId AND s.cancelled = false
        ORDER BY s.saleDate DESC
    """)
    List<SaleEntity> findValidSalesByCashId(@Param("cashId") Long cashId);

    /**
     * Marca la venta como CONFIRMADA de forma atómica y condicional.
     *
     * Devuelve el número de filas afectadas: 1 si esta petición ganó la
     * carrera, 0 si otra se adelantó. Mismo mecanismo que
     * {@code PurchaseRepository.markConfirmedIfPending}, y por el mismo motivo:
     * un {@code if (sale.isConfirmed())} lee la fila y decide en Java, así que
     * dos peticiones simultáneas leen {@code false} antes de que ninguna escriba.
     *
     * Aquí el daño de la carrera es menor que en las compras (no se duplica
     * stock, solo se duplica una marca de tiempo), pero se corrige igual: un
     * {@code confirmed_at} distinto del real sería evidencia contable falsa.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE SaleEntity s
        SET s.confirmed = true,
            s.confirmedAt = :when
        WHERE s.id = :id AND s.confirmed = false AND s.cancelled = false
    """)
    int markConfirmedIfPending(@Param("id") Long id, @Param("when") LocalDateTime when);
}