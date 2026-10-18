package com.erikjarquin.compras.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.erikjarquin.compras.model.entity.CashRegisterEntity;

/**
 * Repositorio de las SESIONES de caja (cortes).
 *
 * No se maneja "las cajas" sino "los turnos". Las cajas físicas viven en
 * {@link CashBoxRepository}: este repositorio es el historial, y por eso crece
 * todos los días.
 *
 * {@code findByActiveTrue} devuelve el turno vigente; que exista UN turno
 * abierto es la invariante del módulo (la regla la aplica
 * {@code CashRegisterImpl}, no la base de datos).
 *
 * Conviven tres estados que se distinguen por columnas, no por un enum:
 *   Sesión abierta: {@code openedAt != null}, {@code closedAt == null},
 *       {@code active == true}.
 *   Sesión cerrada: {@code closedAt != null}, {@code active == false}.
 * Ya no existe el estado "caja creada sin abrir": en V4 una caja que nunca se
 * abrió no tiene fila aquí, vive solo en {@code cash_boxes}.
 */
public interface CashRegisterRepository extends JpaRepository<CashRegisterEntity, Long> {

    /** El turno vigente (uno solo a la vez, por regla de negocio). */
    Optional<CashRegisterEntity> findByActiveTrue();

    /** Busca por número exacto. El servicio normaliza antes (recorta y capitaliza). */
    Optional<CashRegisterEntity> findByNumber(String number);

    /**
     * Historial completo, la más reciente primero.
     *
     * Alimenta el selector "filtrar por caja" de Reportes. Cada fila es un
     * TURNO, así que la misma caja aparece varias veces si se abrió varios días:
     * eso es lo correcto, porque cada turno tiene su propio corte.
     */
    List<CashRegisterEntity> findAllByOrderByOpenedAtDesc();

    /**
     * Las sesiones de una caja física, de la más reciente a la más antigua.
     *
     * Es lo que alimenta el historial por caja: la misma caja abierta el lunes
     * y el viernes devuelve DOS filas, una por turno.
     */
    List<CashRegisterEntity> findByCashBoxIdOrderByOpenedAtDesc(Long cashBoxId);

    /**
     * El turno abierto de una caja concreta, si lo hay.
     *
     * Se usa al dar de baja una caja: no se puede desactivar la caja que el
     * cajero está usando ahora mismo, porque se le desaparece el turno de
     * encima mientras cuenta el dinero.
     */
    Optional<CashRegisterEntity> findByCashBoxIdAndActiveTrue(Long cashBoxId);
}
