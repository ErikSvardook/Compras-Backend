package com.erikjarquin.compras.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.erikjarquin.compras.exceptions.CashException;
import com.erikjarquin.compras.mapper.CashRegisterMapper;
import com.erikjarquin.compras.model.dto.Cash.CashBoxRequest;
import com.erikjarquin.compras.model.dto.Cash.CashBoxResponse;
import com.erikjarquin.compras.model.dto.Cash.CashResponse;
import com.erikjarquin.compras.model.dto.Cash.CashSummaryResponse;
import com.erikjarquin.compras.model.dto.Cash.CloseCashRequest;
import com.erikjarquin.compras.model.dto.Cash.OpenCashRequest;
import com.erikjarquin.compras.model.entity.CashBoxEntity;
import com.erikjarquin.compras.model.entity.CashRegisterEntity;
import com.erikjarquin.compras.model.entity.SaleEntity;
import com.erikjarquin.compras.repository.CashBoxRepository;
import com.erikjarquin.compras.repository.CashRegisterRepository;
import com.erikjarquin.compras.repository.SaleRepository;
import com.erikjarquin.compras.service.CashRegisterService;

/**
 * Implementación de la caja registradora.
 *
 * <p><b>El modelo V4 separa dos cosas que V3 confundía en una sola tabla.</b>
 * Un negocio tiene POCAS cajas físicas (CAJA 1, CAJA 2) y MUCHOS turnos. Antes
 * cada fila de {@code cash_registers} era "una caja", y como su número era
 * UNIQUE, la segunda vez que se abría "CAJA 1" daba 409: ese día no había forma
 * de volver a abrir la caja que sí existía.
 *
 * <p>La solución son dos tablas:
 * <ul>
 *   <li>{@code cash_boxes} ({@link CashBoxEntity}): las cajas FÍSICAS. Pocas
 *       filas, cambian poco. Es la lista que el cajero elige al abrir.</li>
 *   <li>{@code cash_registers} ({@link CashRegisterEntity}): las SESIONES. Una
 *       fila por cada apertura y cierre. Crece todos los días y guarda el corte
 *       congelado (qué se vendió, cuánto había, cuánto se contó).</li>
 * </ul>
 * Una caja puede tener muchas sesiones. Es el caso normal, no la excepción.
 *
 * <p>Reglas que se siguen respetando:
 * <ul>
 *   <li>Una sola caja abierta a la vez (409 si ya hay). Dos cajas abiertas al
 *       mismo tiempo harían imposible saber a cuál pertenece cada venta.</li>
 *   <li>El fondo inicial mínimo es 100 y nunca negativo.</li>
 *   <li>El cierre exige cuadrar el efectivo, con salida de emergencia que pide
 *       un motivo.</li>
 *   <li>Una caja con cortes nunca se borra: se da de baja. Ver
 *       {@link #desactiveBox(Long)}.</li>
 * </ul>
 */
@Service
public class CashRegisterImpl implements CashRegisterService {

    /**
     * Fondo mínimo con el que se puede abrir una caja (decisión del dueño).
     *
     * Además de sentido practical (una caja con 0 no alcanza ni para una
     * venta), evita que un forgot del campo se guarde como 0 y/contamine el
     * "esperado" del corte sin que nadie lo note.
     */
    private static final BigDecimal MIN_OPENING_AMOUNT = new BigDecimal("100");

    /** Longitud máxima del número, igual que el VARCHAR(50) de la columna. */
    private static final int MAX_NUMBER_LENGTH = 50;

    /**
     * Límite del motivo del descuadre, igual que el VARCHAR(255) de la columna.
     * Si se pasa, la BD lo truncaría en silencio y se perdería la mitad de la
     * explicación.
     */
    private static final int MAX_REASON_LENGTH = 255;

    private final CashRegisterRepository repository;
    private final CashBoxRepository cashBoxRepository;
    private final CashRegisterMapper mapper;
    private final SaleRepository saleRepository;

    public CashRegisterImpl(
        CashRegisterRepository repository,
        CashRegisterMapper mapper,
        SaleRepository saleRepository,
        CashBoxRepository cashBoxRepository){
        this.repository = repository;
        this.mapper = mapper;
        this.saleRepository=saleRepository;
        this.cashBoxRepository=cashBoxRepository;
    }

    // =========================================================================
    //  ABRIR y CERRAR
    // =========================================================================

    /**
     * Abre un turno con una caja física ya registrada (V4).
     *
     * La fila de {@code cash_registers}
     * era la caja, y como su número era UNIQUE no se podía volver a abrir.
     * Ahora el modelo separa dos conceptos que V3 confundía en una sola tabla:
     *   {@code cash_boxes}: las cajas FÍSICAS del local. Pocas filas.
     *   {@code cash_registers}: las SESIONES. Una fila por cada apertura y
     *       cierre. Crece todos los días.
     * Abrir es entonces: buscar la caja por número en {@code cash_boxes} y crear
     * una sesión nueva en {@code cash_registers} que apunte a ella. La misma caja
     * puede abrirse todos los días, y cada turno queda como una fila aparte.
     *
     * Se mantiene la regla de UNA caja abierta a la vez (409 si ya hay): dos
     * cajas abiertas al mismo tiempo harían imposible saber a cuál pertenece cada
     * venta.
     */
    @Override
    @Transactional
    public CashResponse open(OpenCashRequest request){
        repository.findByActiveTrue().ifPresent(c -> {
            throw new CashException(
                "Ya existe una caja abierta (" + c.getNumber()
                + "). Ciérrala antes de abrir otra.", HttpStatus.CONFLICT);
        });

        String number = normalizeNumber(request.getNumber());

        CashBoxEntity caja = cashBoxRepository.findByNumber(number)
                .orElseThrow(() -> new CashException(
                    "No existe una caja registrada con el numero \"" + number
                    + "\". Crerala primero con el boton \"Ver cajas\".",
                    HttpStatus.NOT_FOUND));

        if(!caja.isActive()){
            throw new CashException(
                "La caja \"" + number + "\" esta dada de baja y no se puede abrir.",
                HttpStatus.CONFLICT);
        }

        BigDecimal openingAmount = validateOpeningAmount(request.getOpeningAmount());

        // ===== SESIÓN NUEVA =====
        // No se reutiliza la fila anterior: cada turno es su propio corte, con su
        // propio dinero contado y su propia diferencia. Por eso se crea un
        // CashRegisterEntity nuevo en vez de actualizar el anterior.
        CashRegisterEntity sesion = new CashRegisterEntity();
        sesion.setNumber(number);          // copia historica del numero
        sesion.setCashBox(caja);           // a que caja fisica pertenece
        // IMPORTANTE: los acumulados NACEN en cero. El fondo inicial NO es una
        // venta: va en openingAmount, y expectedAmount = openingAmount + cashSales.
        // Si se sembrara cashSales con el fondo, el "esperado" del corte contaria
        // el fondo dos veces (una como openingAmount y otra como cashSales) y el
        // cajero veria una diferencia fantasma igual al doble del fondo.
        sesion.setCashSales(BigDecimal.ZERO);
        sesion.setDebitSales(BigDecimal.ZERO);
        sesion.setCreditSales(BigDecimal.ZERO);
        sesion.setTotalSales(BigDecimal.ZERO);
        sesion.setDifference(BigDecimal.ZERO);
        sesion.setDifferenceReason(null);
        sesion.setExpectedAmount(openingAmount);
        sesion.setTotalTickets(0);
        sesion.setOpenedAt(LocalDateTime.now());
        sesion.setOpeningAmount(openingAmount);
        sesion.setActive(true);

        caja.setUpdatedAt(LocalDateTime.now());
        cashBoxRepository.save(caja);

        return mapper.toResponse(repository.save(sesion));
    }

    /**
     * Valida el fondo inicial: obligatorio, no negativo y mínimo 100.
     *
     * Se hace aquí y no con {@code @Valid} en el DTO porque el mensaje puede
     * decir qué límite se rompió y por qué, que es más útil que "validation
     * failed".
     */
    private BigDecimal validateOpeningAmount(BigDecimal openingAmount){
        if(openingAmount == null){
            throw new CashException(
                "El monto inicial de la caja es obligatorio", HttpStatus.BAD_REQUEST);
        }
        if(openingAmount.signum() < 0){
            throw new CashException(
                "El monto inicial no puede ser negativo", HttpStatus.BAD_REQUEST);
        }
        if(openingAmount.compareTo(MIN_OPENING_AMOUNT) < 0){
            throw new CashException(
                "El monto inicial debe ser al menos $" + MIN_OPENING_AMOUNT.toPlainString()
                + ". Una caja no puede abrir con menos.",
                HttpStatus.BAD_REQUEST);
        }
        return openingAmount;
    }

    // =========================================================================
    //  CRUD de cajas físicas
    // =========================================================================

    /**
     * Registra una caja física nueva.
     *
     * No es abrir caja: esto solo la da de alta en el inventario del local.
     * Abrir un turno es {@link #open(OpenCashRequest)}, que crea una SESIÓN
     * nueva apuntando a esta caja.
     */
    @Override
    @Transactional
    public CashBoxResponse createBox(CashBoxRequest request){
        String number = normalizeNumber(request.getNumber());

        //Se valida aquí y no solo con el UNIQUE de la BD: PostgreSQL lanzaría una
        //violación de constraint (500, sin mensaje útil). Esto da un 409 que
        //explica el porqué.
        if(cashBoxRepository.existsByNumber(number)){
            throw new CashException(
                "Ya existe una caja con el numero \"" + number + "\".",
                HttpStatus.CONFLICT);
        }

        CashBoxEntity caja = new CashBoxEntity();
        caja.setNumber(number);
        caja.setDescription(normalizeDescription(request.getDescription()));
        caja.setActive(true);
        caja.setCreatedAt(LocalDateTime.now());

        return toBoxResponse(cashBoxRepository.save(caja));
    }

    /**
     * Todas las cajas del local, incluidas las dadas de baja.
     *
     * Incluidas a propósito: la tabla "Ver cajas" es también el lugar donde se
     * ve que una caja se dio de baja y por qué (tiene cortes). Ocultarlas
     * haría creer que desaparecieron.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CashBoxResponse> getBoxes(){
        return cashBoxRepository.findAllByOrderByNumberAsc().stream()
                .map(this::toBoxResponse)
                .toList();
    }

    /**
     * Cajas que se pueden abrir ahora: activas y sin ninguna sesión abierta.
     *
     * Alimenta el selector del modal "Abrir caja". Ya no se filtran en Java
     * con "las que nunca se abrieron": una caja que se abrió el lunes se
     * vuelve a abrir el martes, así que el criterio correcto es "¿tiene algún
     * turno abierto AHORA?".
     */
    @Override
    @Transactional(readOnly = true)
    public List<CashBoxResponse> getOpenable(){
        return cashBoxRepository.findOpenable().stream()
                .map(this::toBoxResponse)
                .toList();
    }

    /** Edita número y descripción de una caja. */
    @Override
    @Transactional
    public CashBoxResponse updateBox(Long id, CashBoxRequest request){
        CashBoxEntity caja = cashBoxRepository.findById(id)
                .orElseThrow(() -> new CashException(
                    "La caja no existe", HttpStatus.NOT_FOUND));

        //El número solo se cambia si viene informado: un PUT con number vacío no
        //debe borrar el número de una caja que ya tiene cortes.
        if(request.getNumber() != null && !request.getNumber().isBlank()){
            String number = normalizeNumber(request.getNumber());

            //409 solo si el número es OTRO: renombrar una caja a su propio número
            //no es un duplicado, y sin esta guarda un usuario que solo quiere
            //corregir la descripción recibe un error falso.
            if(!number.equals(caja.getNumber()) && cashBoxRepository.existsByNumber(number)){
                throw new CashException(
                    "Ya existe otra caja con el numero \"" + number + "\".",
                    HttpStatus.CONFLICT);
            }

            caja.setNumber(number);
        }

        if(request.getDescription() != null){
            caja.setDescription(normalizeDescription(request.getDescription()));
        }

        caja.setUpdatedAt(LocalDateTime.now());

        return toBoxResponse(cashBoxRepository.save(caja));
    }

    /**
     * Da de baja una caja: deja de ofrecerse al abrir, pero NO se borra.
     *
     * Esta es la única operación de "baja" que existe, y es a propósito. Una
     * caja que ya tuvo cortes tiene ventas apuntando a su sesión, y la sesión a
     * la caja. Borrarla dejaría ventas sin caja y el reporte "filtrar por caja"
     * no podría agruparlas: el historial de ese turno se volvería inalcanzable.
     *
     * Por eso el borrado físico solo tiene sentido para una caja que NUNCA se
     * abrió (no tiene ventas). Ese caso no se expone como endpoint: se resuelve
     * desactivando, que es una operación reversible y sin consecuencias. Una caja
     * recién creada que se creó por error se da de baja y ya no estorba.
     */
    @Override
    @Transactional
    public void desactiveBox(Long id){
        CashBoxEntity caja = cashBoxRepository.findById(id)
                .orElseThrow(() -> new CashException(
                    "La caja no existe", HttpStatus.NOT_FOUND));

        if(!caja.isActive()){
            throw new CashException(
                "La caja \"" + caja.getNumber() + "\" ya esta dada de baja.",
                HttpStatus.CONFLICT);
        }

        //No se puede dar de baja una caja con el turno abierto: el cajero está
        //contando el dinero de ella ahora mismo, y desactivarla le desaparecería
        //el corte de encima mientras lo cierra.
        if(repository.findByCashBoxIdAndActiveTrue(caja.getId()).isPresent()){
            throw new CashException(
                "La caja \"" + caja.getNumber() + "\" tiene un turno abierto. "
                + "Cierra el turno antes de darla de baja.",
                HttpStatus.CONFLICT);
        }

        caja.setActive(false);
        caja.setUpdatedAt(LocalDateTime.now());
        cashBoxRepository.save(caja);
    }

    /**
     * Da de ALTA una caja que estaba dada de baja.
     *
     * Existe para que una caja retirada siga siendo recuperable. Sin esto,
     * "dar de baja" sería una decisión irreversible y además malintencionada: si
     * se da de baja por error (o porque se estaba reparando y ya terminó), no
     * hay vuelta atrás.
     *
     * No se puede reutilizar el número para otra caja. El UNIQUE está
     * en {@code cash_boxes.number} y la fila NO se borra al dar de baja, así que
     * el número queda ocupado para siempre: la caja dada de baja y la que se
     * re-activan son la misma caja con su mismo historial. Es lo correcto:
     * sus ventas siguen apuntando a sus turnos, y perder ese enlace dejaría
     * ventas sin caja.
     */
    @Override
    @Transactional
    public void activateBox(Long id){
        CashBoxEntity caja = cashBoxRepository.findById(id)
                .orElseThrow(() -> new CashException(
                    "La caja no existe", HttpStatus.NOT_FOUND));

        if(caja.isActive()){
            throw new CashException(
                "La caja \"" + caja.getNumber() + "\" ya esta dada de alta.",
                HttpStatus.CONFLICT);
        }

        caja.setActive(true);
        caja.setUpdatedAt(LocalDateTime.now());
        cashBoxRepository.save(caja);
    }

    /**
     * BORRA una caja, pero solo si nunca se abrió. Si ya tuvo turnos, da 409.
     *
     * Esta es la contraparte de {@link #desactiveBox(Long)} y implementa
     * exactamente la regla del dueño: lo que ya tuvo corte de caja no se
     * borra, se da de baja.
     *
     * Por qué el borrado físico es aceptable SOLO en este caso: una caja que
     * nunca se abrió no tiene sesiones, y las sesiones son las que apuntan a las
     * ventas. Sin sesiones no hay nada que romperse, así que borrarla es
     * limpio. En cuanto la caja tuvo un turno, sus ventas quedan colgando de él y
     * borrar dejaría ventas sin caja: el reporte "filtrar por caja" no podría
     * agruparlas y el corte de ese día sería inalcanzable.
     *
     * Por eso el borrado es condicional y el 409 dice exactamente qué hacer
     * ("dala de baja") en vez de dejar al usuario adivinando.
     */
    @Override
    @Transactional
    public void deleteBox(Long id){
        CashBoxEntity caja = cashBoxRepository.findById(id)
                .orElseThrow(() -> new CashException(
                    "La caja no existe", HttpStatus.NOT_FOUND));

        if(repository.findByCashBoxIdAndActiveTrue(caja.getId()).isPresent()){
            throw new CashException(
                "La caja \"" + caja.getNumber() + "\" tiene un turno abierto. "
                + "Cierra el turno antes de borrarla.",
                HttpStatus.CONFLICT);
        }

        if(cashBoxRepository.hasSessions(id)){
            throw new CashException(
                "La caja \"" + caja.getNumber() + "\" ya tiene cortes y no se puede borrar. "
                + "Dala de baja: deja de ofrecerse al abrir, pero sus ventas siguen en el historial.",
                HttpStatus.CONFLICT);
        }

        cashBoxRepository.delete(caja);
    }

    /**
     * Historial de UNA caja: un corte por turno, de la más reciente a la más
     * antigua. La misma caja abierta el lunes y el viernes devuelve 2 filas.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CashResponse> getBoxHistory(Long boxId){
        if(!cashBoxRepository.existsById(boxId)){
            throw new CashException("La caja no existe", HttpStatus.NOT_FOUND);
        }

        return repository.findByCashBoxIdOrderByOpenedAtDesc(boxId).stream()
                .map(mapper::toResponse)
                .toList();
    }

    /** Descripción opcional: recortada y acotada al VARCHAR(255) de la columna. */
    private String normalizeDescription(String description){
        if(description == null || description.isBlank()){
            return null;
        }

        String trimmed = description.trim();

        return trimmed.length() > MAX_REASON_LENGTH
                ? trimmed.substring(0, MAX_REASON_LENGTH)
                : trimmed;
    }

    /**
     * Caja física → DTO, enriquecida con cuántos turnos tiene y si está en uso.
     *
     * El conteo se hace con una consulta por caja. Son pocas cajas (dos o
     * tres), así que el N+1 es irrelevante y el código queda legible.
     */
    private CashBoxResponse toBoxResponse(CashBoxEntity caja){
        List<CashRegisterEntity> sesiones =
                repository.findByCashBoxIdOrderByOpenedAtDesc(caja.getId());

        CashBoxResponse dto = new CashBoxResponse();
        dto.setId(caja.getId());
        dto.setNumber(caja.getNumber());
        dto.setDescription(caja.getDescription());
        dto.setActive(caja.isActive());
        dto.setCreatedAt(caja.getCreatedAt());
        dto.setSessionsCount(sesiones.size());
        dto.setInUse(sesiones.stream().anyMatch(CashRegisterEntity::getActive));
        dto.setLastOpenedAt(sesiones.isEmpty() ? null : sesiones.get(0).getOpenedAt());

        return dto;
    }


    /**
     * Cierra la caja exigiendo que el efectivo contado cuadre.
     *
     * Por qué bloquea y por qué tiene salida
     *
     * Si el efectivo contado no coincide con el esperado, el cierre se rechaza
     * con 409. La razón de fondo: el dinero que falta en el cajón es dinero
     * que salió del negocio, y un corte que "cierra igual" convierte un robo en
     * un descuadre invisible.
     *
     * Pero bloquear sin salida tiene un costo real: si el cajero se equivocó al
     * contar, o un cliente entregó un billete falso y le dio mal el cambio, el
     * turno quedaría encerrado y no se podría cerrar hasta que alguien investigue.
     * Por eso existe {@code differenceReason}: si el cajero está seguro del monto
     * que cuenta, escribe por qué no cuadra y el cierre proceeds. La diferencia y
     * su motivo quedan guardados juntos, y con eso el descuadre deja de ser
     * invisible.
     */
    @Override
    @Transactional
    public CashResponse close(CloseCashRequest request){
        CashRegisterEntity cash = repository.findByActiveTrue().orElseThrow(() ->
            new CashException("No existe caja abierta", HttpStatus.NOT_FOUND));

        CashSummaryResponse summary = calculateSummary(cash);

        if(request.getClosingAmount() == null){
            throw new CashException(
                "Debes indicar cuánto efectivo hay en la caja", HttpStatus.BAD_REQUEST);
        }
        if(request.getClosingAmount().signum() < 0){
            throw new CashException(
                "El efectivo contado no puede ser negativo", HttpStatus.BAD_REQUEST);
        }

        BigDecimal countedAmount = request.getClosingAmount();
        BigDecimal difference = countedAmount.subtract(summary.getExpectedAmount());

        // ===== EL CUADRE =====
        //Con diferencia 0 el motivo se ignora y se limpia: un corte que cuadró no
        //debe quedar con un motivo de descuadre colgado de una versión anterior.
        if(difference.signum() != 0){
            String reason = normalizeReason(request.getDifferenceReason());

            if(reason == null){
                //409 y NO se escribe nada: la caja sigue abierta.
                throw new CashException(
                    "El dinero no cuadra. Esperado: $" + summary.getExpectedAmount().toPlainString()
                    + ", contado: $" + countedAmount.toPlainString()
                    + ", diferencia: " + signed(difference)
                    + ". Revisa el conteo. Si estás seguro de que el monto es correcto, "
                    + "cierra con la salida de emergencia escribiendo el motivo.",
                    HttpStatus.CONFLICT);
            }
            cash.setDifferenceReason(reason);
        }else{
            cash.setDifferenceReason(null);
        }

        // ===== Guardar el corte =====
        cash.setCountedAmount(countedAmount);
        cash.setCashSales(summary.getCashSales());
        cash.setDebitSales(summary.getDebitSales());
        cash.setCreditSales(summary.getCreditSales());
        cash.setTotalSales(summary.getTotalSales());
        cash.setExpectedAmount(summary.getExpectedAmount());
        cash.setDifference(difference);
        cash.setTotalTickets(summary.getTotalTickets());
        cash.setClosedAt(LocalDateTime.now());
        cash.setActive(false);

        return mapper.toResponse(repository.save(cash));
    }

    /**
     * Motivo del descuadre: obligatorio si se avisó, y acotado al VARCHAR(255).
     * Devuelve null si no se envió nada, que es lo que dispara el 409.
     */
    private String normalizeReason(String reason){
        if(reason == null || reason.isBlank()){
            return null;
        }

        String trimmed = reason.trim();

        if(trimmed.length() > MAX_REASON_LENGTH){
            //Se recorta en vez de dejar que la BD lo trunque en silencio: mejor un
            //motivo corto y legible que uno cortado a la mitad sin avisar.
            return trimmed.substring(0, MAX_REASON_LENGTH);
        }

        return trimmed;
    }

    /** Formatea la diferencia con signo explícito (+50 / -50), que se lee mejor. */
    private String signed(BigDecimal difference){
        return (difference.signum() > 0 ? "+" : "") + difference.toPlainString();
    }

    /**
     * Normaliza el número de caja: recorta y apitaliza.
     *
     * Se capitaliza a propósito: "caja 1" y "CAJA 1" son la MISMA caja y
     * deben ser el mismo número. Sin esto el UNIQUE de PostgreSQL los dejaría
     * pasar como dos cajas distintas (compara las mayúsculas y minúsculas tal
     * cual) y el reporte de esa caja mesclaría dos cortes reales.
     */
    private String normalizeNumber(String number){
        if(number == null || number.isBlank()){
            throw new CashException("El numero de caja es obligatorio", HttpStatus.BAD_REQUEST);
        }

        String normalized = number.trim().toUpperCase();

        if(normalized.length() > MAX_NUMBER_LENGTH){
            throw new CashException(
                "El numero de caja no puede tener mas de " + MAX_NUMBER_LENGTH + " caracteres",
                HttpStatus.BAD_REQUEST);
        }

        return normalized;
    }

    // =========================================================================
    //  Consultas
    // =========================================================================

    /**
     * Sugiere el siguiente número libre con el patrón "CAJA n".
     *
     * Existe para que el alta de caja venga con "CAJA 7" ya escrito en vez de
     * obligar a contar cuántas hay. Cuenta sobre {@code cash_boxes} (las cajas
     * físicas), que es donde el número es UNIQUE: si contara sobre los cortes,
     * sugeriría un número que una caja existente ya tiene y el alta fallaría
     * con 409.
     *
     * <p>Solo sugiere: si el número ya existe, {@link #createBox} lo rechaza con
     * 409 en vez de fallar en silencio.
     */
    @Override
    @Transactional(readOnly = true)
    public String getNextSuggestedNumber(){
        int max = 0;

        for(CashBoxEntity caja : cashBoxRepository.findAll()){
            String number = caja.getNumber();
            if(number == null){
                continue;
            }
            //Solo interessan los que siguen el patrón "CAJA <número>"
            String[] parts = number.trim().split("\\s+");
            if(parts.length != 2 || !parts[0].equalsIgnoreCase("CAJA")){
                continue;
            }
            try{
                max = Math.max(max, Integer.parseInt(parts[1]));
            }catch(NumberFormatException ignored){
                //Un número escrito a mano ("CAJA PRINCIPAL") no cuenta para el
                //siguiente: no es un error, solo no sigue el patrón.
            }
        }

        return "CAJA " + (max + 1);
    }

    //Ver el turno abierto
    @Override
    @Transactional(readOnly = true)
    public CashResponse getActiveCash(){
        CashRegisterEntity cash = repository.findByActiveTrue().orElseThrow(() ->
            new CashException("No existe caja abierta"));

        return mapper.toResponse(cash);
    }

    /**
     * Historial de cajas: la más reciente primero, para elegir cuál filtrar.
     * Incluye las que se crearon pero todavía no se abrieron.
     */
    @Override
    @Transactional(readOnly = true)
    public List<CashResponse> getHistory(){
        return repository.findAllByOrderByOpenedAtDesc().stream()
                .map(mapper::toResponse)
                .toList();
    }

    //Detalle de una caja concreta por su número (V3)
    @Override
    @Transactional(readOnly = true)
    public CashResponse getByNumber(String number){
        return repository.findByNumber(normalizeNumber(number))
                .map(mapper::toResponse)
                .orElseThrow(() -> new CashException(
                    "No existe ninguna caja con el numero \"" + number + "\"", HttpStatus.NOT_FOUND));
    }

    //Ver el resumen de la venta
    @Override
    @Transactional(readOnly = true)
    public CashSummaryResponse getSummary(){
        CashRegisterEntity cash = repository.findByActiveTrue().orElseThrow(() ->
            new CashException("No existe caja abierta"));

        return calculateSummary(cash);
    }

    /**
     * Calcula el resumen del corte: totales por método de pago y el efectivo
     * esperado (fondo inicial + ventas en efectivo).
     *
     * Las ventas ANULADAS se saltan: anular una venta en efectivo no devuelve
     * el dinero al cajón, así que su monto ya no está ahí. Contarlas dejaría su
     * dinero dentro del "esperado" y el cajero vería una diferencia fantasma de
     * su propio bolsillo. Mismo criterio que el filtro
     * {@code cancelled = false} de los reportes de Ventas.
     */
    private CashSummaryResponse calculateSummary(CashRegisterEntity cash){
        List<SaleEntity> sales = saleRepository.findByCashRegister(cash);

        BigDecimal cashSales = BigDecimal.ZERO;
        BigDecimal debitSales = BigDecimal.ZERO;
        BigDecimal creditSales = BigDecimal.ZERO;

        for(SaleEntity sale : sales){
            if(sale.isCancelled()){
                continue;
            }

            switch(sale.getPaymentMethod()){
                case CASH -> cashSales = cashSales.add(sale.getTotal());

                case DEBIT -> debitSales = debitSales.add(sale.getTotal());

                case CREDIT -> creditSales = creditSales.add(sale.getTotal());
            }
        }

        BigDecimal totalSales = cashSales.add(debitSales).add(creditSales);
        BigDecimal opening = cash.getOpeningAmount() == null ? BigDecimal.ZERO : cash.getOpeningAmount();
        BigDecimal expectedAmount = opening.add(cashSales);

        CashSummaryResponse response = new CashSummaryResponse();
        response.setCashId(cash.getId());
        response.setOpeningAmount(opening);
        response.setCashSales(cashSales);
        response.setDebitSales(debitSales);
        response.setCreditSales(creditSales);
        response.setTotalSales(totalSales);
        response.setExpectedAmount(expectedAmount);
        //Ticketes contados aparte porque las anuladas no suman dinero pero sí
        //fueron tickets: si se contaran, el cajero vería más tickets que ventas.
        response.setTotalTickets(countValidTickets(sales));

        return response;
    }

    private int countValidTickets(List<SaleEntity> sales){
        int total = 0;
        for(SaleEntity sale : sales){
            if(!sale.isCancelled()){
                total++;
            }
        }
        return total;
    }
}
