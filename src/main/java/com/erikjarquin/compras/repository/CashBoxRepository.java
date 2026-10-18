package com.erikjarquin.compras.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.erikjarquin.compras.model.entity.CashBoxEntity;

/**
 * Repositorio de las cajas FÍSICAS del local (V4).
 *
 * Es distinto de {@code CashRegisterRepository}: este maneja las cajas del
 * local (pocas, casi fijas: "CAJA 1", "CAJA 2") y aquel los turnos (uno por
 * apertura/cierre, que crece todos los días).
 *
 * La separación es lo que permite que una caja se abra muchas veces: el
 * UNIQUE está aquí, en las cajas, y no en los cortes.
 */
public interface CashBoxRepository extends JpaRepository<CashBoxEntity, Long> {

    /** Cajas activas, ordenadas por número. */
    List<CashBoxEntity> findByActiveTrueOrderByNumberAsc();

    /**
     * Todas, incluidas las dadas de baja, ordenadas por número.
     *
     * Alimenta la tabla "Ver cajas". Las dadas de baja se incluyen a
     * propósito: se ven atenuadas y con su contador de turnos, que es la
     * información que justifica por qué no se pueden borrar.
     */
    List<CashBoxEntity> findAllByOrderByNumberAsc();

    Optional<CashBoxEntity> findByNumber(String number);

    /** ¿Existe una caja con este número? Da un 409 con mensaje, no un 500 por constraint. */
    boolean existsByNumber(String number);

    /**
     * ¿La caja tiene algún turno registrado?
     *
     * Se responde con un {@code @Query} explícito y no con un
     * {@code existsByCashRegistersId} derivado, porque la relación NO está en
     * este lado: {@code CashBoxEntity} no tiene una colección de sesiones, la
     * referencia apunta al revés ({@code CashRegisterEntity.cashBox}). Un
     * "existsBy..." derivado intentaría resolver la propiedad
     * {@code cashRegisters} dentro de {@code CashBoxEntity}, no la encuentra, y
     * la aplicación NO ARRANCA con un {@code PropertyReferenceException}.
     *
     * Sirve para decidir si una caja se puede borrar en vez de solo deactivate.
     */
    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
            FROM CashRegisterEntity r
            WHERE r.cashBox.id = :cashBoxId
            """)
    boolean hasSessions(@Param("cashBoxId") Long cashBoxId);

    /**
     * Cajas que se pueden abrir AHORA: activas y sin ningún turno abierto.
     *
     * Alimenta el selector del modal "Abrir caja".
     *
     * El criterio es "¿tiene algún turno abierto ahora mismo?", y no "¿nunca se
     * abrió?" (que era el de V3). La diferencia es el bug que motivatoría todo el
     * cambio: como una caja ahora se abre muchos días, filtrar por "nunca
     * abierta" la excluiría para siempre después de su primer turno.
     */
    @Query("""
            SELECT b FROM CashBoxEntity b
            WHERE b.active = true
              AND NOT EXISTS (
                SELECT 1 FROM CashRegisterEntity r
                WHERE r.cashBox.id = b.id AND r.active = true
              )
            ORDER BY b.number ASC
            """)
    List<CashBoxEntity> findOpenable();
}