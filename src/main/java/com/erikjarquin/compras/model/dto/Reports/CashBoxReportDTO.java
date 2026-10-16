package com.erikjarquin.compras.model.dto.Reports;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.erikjarquin.compras.model.dto.Sale.SaleDetailHistoryResponse;

/**
 * Reporte de una CAJA FÍSICA y de todas sus SESIONES.
 *
 * Es el reemplazo de {@link CashReportDTO} para la pantalla de Reportes. La
 * diferencia es de fondo, no de forma: antes el selector traía turnos
 * (una fila por cada apertura), de modo que una caja abierta diez veces
 * aparecía diez veces y no se distinguía de diez cajas distintas. Ahora el
 * selector trae cajas, y cada caja trae sus turnos adentro.
 *
 * Por qué importa. "CAJA 1" es lo que el cajero reconoce. Pedirle que
 * elija entre "CAJA 1" y "CAJA 1" dos veces es pedirle que adivine. Con esta
 * estructura la pregunta "¿qué pasó en CAJA 1?" se responde en una pantalla.
 */
public class CashBoxReportDTO {

    /** Id de la caja física ({@code cash_boxes.id}). */
    private Long boxId;

    /** Número de la caja: "CAJA 1". */
    private String number;

    /** Descripción libre de la caja. */
    private String description;

    /** false = dada de baja: se puede consultar igual, pero no abrir. */
    private boolean active;

    /**
     * Todos los turnos de la caja, del más reciente al más antiguo.
     *
     * Con filtros aplicados, solo entran los turnos que tienen al menos una
     * venta que los cumple.
     */
    private List<CashBoxSessionDTO> sessions;

    /** Total de turnos de la caja, SIN contar los filtros. */
    private int totalSessions;

    public CashBoxReportDTO(){}

    public Long getBoxId(){
        return boxId;
    }

    public void setBoxId(Long boxId){
        this.boxId=boxId;
    }

    public String getNumber(){
        return number;
    }

    public void setNumber(String number){
        this.number=number;
    }

    public String getDescription(){
        return description;
    }

    public void setDescription(String description){
        this.description=description;
    }

    public boolean isActive(){
        return active;
    }

    public void setActive(boolean active){
        this.active=active;
    }

    public List<CashBoxSessionDTO> getSessions(){
        return sessions;
    }

    public void setSessions(List<CashBoxSessionDTO> sessions){
        this.sessions=sessions;
    }

    public int getTotalSessions(){
        return totalSessions;
    }

    public void setTotalSessions(int totalSessions){
        this.totalSessions=totalSessions;
    }

    /**
     * Un TURNO (apertura + cierre) de una caja.
     *
     * Los montos se calculan sobre las ventas que pasan el filtro. Es
     * lo coherente: si el usuario filtra por usuario, el total que ve es el de
     * ese usuario. Por eso, cuando hay filtros, {@code difference} viene null:
     * la diferencia del corte es un dato congelado del turno completo, y
     * compararla contra un total filtrado daría un descuadre que no es real.
     * Por eso existe el flag {@link #filtrado}: la UI sabe que debe ocultar la
     * columna en vez de mostrar un cero engañoso.
     */
    public static class CashBoxSessionDTO {

        private Long sessionId;

        /** Copia histórica del número de la caja en el momento de abrir. */
        private String number;

        private LocalDateTime openedAt;
        private LocalDateTime closedAt;

        /** true = el turno sigue abierto (no se ha cerrado). */
        private boolean active;

        /**
         * Fondo inicial. Este sí viene del corte congelado y NO se recalcula:
         * es dinero físico que se puso en el cajón, no una venta.
         */
        private BigDecimal openingAmount;

        /** Efectivo contado al cerrar. null si el turno sigue abierto. */
        private BigDecimal closingAmount;

        /** openingAmount + efectivo vendido (sobre las ventas filtradas). */
        private BigDecimal expectedAmount;

        private BigDecimal cashSales;
        private BigDecimal debitSales;
        private BigDecimal creditSales;
        private BigDecimal totalSales;

        /**
         * Diferencia del corte. <b>null cuando hay filtros activos</b>: ver la
         * nota de la clase.
         */
        private BigDecimal difference;

        /**
         * Motivo del descuadre, si el turno se cerró con diferencia.
         *
         * Viaja junto a la diferencia y no aparte porque "me sobraron 200" y
         * "me faltaron 200" son opuestos con el mismo síntoma: sin el motivo, el
         * signo de la diferencia no alcanza a explicar qué pasó.
         */
        private String differenceReason;

        private long totalTickets;

        /** Utilidad: total vendido menos el costo de los productos vendidos. */
        private BigDecimal grossProfit;

        /** Quién vendió en este turno, con sus totales. */
        private List<CashSessionSellerDTO> sellers;

        /** Las ventas del turno (las que pasan el filtro). */
        private List<SaleDetailHistoryResponse> sales;

        /** true = hay al menos un filtro aplicado a este reporte. */
        private boolean filtrado;

        public CashBoxSessionDTO(){}

        public Long getSessionId(){
            return sessionId;
        }

        public void setSessionId(Long sessionId){
            this.sessionId=sessionId;
        }

        public String getNumber(){
            return number;
        }

        public void setNumber(String number){
            this.number=number;
        }

        public LocalDateTime getOpenedAt(){
            return openedAt;
        }

        public void setOpenedAt(LocalDateTime openedAt){
            this.openedAt=openedAt;
        }

        public LocalDateTime getClosedAt(){
            return closedAt;
        }

        public void setClosedAt(LocalDateTime closedAt){
            this.closedAt=closedAt;
        }

        public boolean isActive(){
            return active;
        }

        public void setActive(boolean active){
            this.active=active;
        }

        public BigDecimal getOpeningAmount(){
            return openingAmount;
        }

        public void setOpeningAmount(BigDecimal openingAmount){
            this.openingAmount=openingAmount;
        }

        public BigDecimal getClosingAmount(){
            return closingAmount;
        }

        public void setClosingAmount(BigDecimal closingAmount){
            this.closingAmount=closingAmount;
        }

        public BigDecimal getExpectedAmount(){
            return expectedAmount;
        }

        public void setExpectedAmount(BigDecimal expectedAmount){
            this.expectedAmount=expectedAmount;
        }

        public BigDecimal getCashSales(){
            return cashSales;
        }

        public void setCashSales(BigDecimal cashSales){
            this.cashSales=cashSales;
        }

        public BigDecimal getDebitSales(){
            return debitSales;
        }

        public void setDebitSales(BigDecimal debitSales){
            this.debitSales=debitSales;
        }

        public BigDecimal getCreditSales(){
            return creditSales;
        }

        public void setCreditSales(BigDecimal creditSales){
            this.creditSales=creditSales;
        }

        public BigDecimal getTotalSales(){
            return totalSales;
        }

        public void setTotalSales(BigDecimal totalSales){
            this.totalSales=totalSales;
        }

        public BigDecimal getDifference(){
            return difference;
        }

        public void setDifference(BigDecimal difference){
            this.difference=difference;
        }

        public String getDifferenceReason(){
            return differenceReason;
        }

        public void setDifferenceReason(String differenceReason){
            this.differenceReason=differenceReason;
        }

        public long getTotalTickets(){
            return totalTickets;
        }

        public void setTotalTickets(long totalTickets){
            this.totalTickets=totalTickets;
        }

        public BigDecimal getGrossProfit(){
            return grossProfit;
        }

        public void setGrossProfit(BigDecimal grossProfit){
            this.grossProfit=grossProfit;
        }

        public List<CashSessionSellerDTO> getSellers(){
            return sellers;
        }

        public void setSellers(List<CashSessionSellerDTO> sellers){
            this.sellers=sellers;
        }

        public List<SaleDetailHistoryResponse> getSales(){
            return sales;
        }

        public void setSales(List<SaleDetailHistoryResponse> sales){
            this.sales=sales;
        }

        public boolean isFiltrado(){
            return filtrado;
        }

        public void setFiltrado(boolean filtrado){
            this.filtrado=filtrado;
        }
    }

    /**
     * Un vendedor dentro de un turno.
     *
     * Agrupa las ventas de un mismo usuario para poder responder "¿cuánto
     * vendió cada quien?" sin tener que leer la tabla venta por venta.
     *
     * {@code userId} y {@code userName} son null cuando la venta no tiene
     * usuario: son las ventas anteriores a V5, que no guardaron quién las hizo.
     * Se agrupan bajo {@code null} en vez de inventar un nombre.
     */
    public static class CashSessionSellerDTO {

        private Long userId;
        private String userName;

        /**
         * Tickets que vendió este usuario en el turno.
         *
         * <p>Se inicializan en cero por la misma razón que
         * {@link #total}: el DTO se construye "en blanco" y se va sumando con
         * {@code setTickets(getTickets() + 1)}. Con null, la primera suma
         * revienta con un {@code NullPointerException}.
         */
        private long tickets = 0;

        /**
         * Total que vendió este usuario en el turno.
         *
         * Empieza en ZERO, no en null. El service lo va rellenando con
         * {@code setTotal(getTotal().add(monto))}, y si naciera en null la
         * primera suma lanzaría un {@code NullPointerException}. Es un
         * acumulador: siempre tiene valor.
         */
        private BigDecimal total = BigDecimal.ZERO;

        public CashSessionSellerDTO(){}

        public CashSessionSellerDTO(Long userId, String userName){
            this.userId=userId;
            this.userName=userName;
        }

        public Long getUserId(){
            return userId;
        }

        public void setUserId(Long userId){
            this.userId=userId;
        }

        public String getUserName(){
            return userName;
        }

        public void setUserName(String userName){
            this.userName=userName;
        }

        public long getTickets(){
            return tickets;
        }

        public void setTickets(long tickets){
            this.tickets=tickets;
        }

        public BigDecimal getTotal(){
            return total;
        }

        public void setTotal(BigDecimal total){
            this.total=total;
        }
    }
}