package com.erikjarquin.compras.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.erikjarquin.compras.exceptions.CashException;
import com.erikjarquin.compras.mapper.CashRegisterMapper;
import com.erikjarquin.compras.model.dto.Cash.CashBoxRequest;
import com.erikjarquin.compras.model.dto.Cash.CashBoxResponse;
import com.erikjarquin.compras.model.dto.Cash.CashResponse;
import com.erikjarquin.compras.model.dto.Cash.CloseCashRequest;
import com.erikjarquin.compras.model.dto.Cash.OpenCashRequest;
import com.erikjarquin.compras.model.entity.CashBoxEntity;
import com.erikjarquin.compras.model.entity.CashRegisterEntity;
import com.erikjarquin.compras.model.enums.PaymentMethod;
import com.erikjarquin.compras.model.enums.PaymentStatus;
import com.erikjarquin.compras.model.entity.SaleEntity;
import com.erikjarquin.compras.repository.CashBoxRepository;
import com.erikjarquin.compras.repository.CashRegisterRepository;
import com.erikjarquin.compras.repository.SaleRepository;

/**
 * Tests de la caja V4: cajas REUTILIZABLES y turnos.
 *
 * Qué cambió respecto a V3 y por qué hay tantos tests nuevos. Una
 * fila de {@code cash_registers} era "una caja" y su número era UNIQUE, así que
 * una caja se podía abrir una sola vez. El dueño aclaró el modelo real: hay dos
 * cajas físicas y se abren y cierran todos los días. Se separan los dos conceptos
 * ({@code cash_boxes} = cajas, {@code cash_registers} = turnos) y por eso los
 * tests de "crear caja" y "caja ya usada" se reescribieron como tests de cajas y
 * de reapertura.
 *
 * El bloque de {@link CuadreTest} no cambió: el cuadre del efectivo es
 * exactamente igual. Se documenta aparte porque es la regla que másEXPONE dinero
 * del módulo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Caja V4 - cajas reutilizables, turnos y cuadre")
class CashRegisterV3Test {

    @Mock private CashRegisterRepository repository;
    @Mock private CashBoxRepository cashBoxRepository;
    @Mock private CashRegisterMapper mapper;
    @Mock private SaleRepository saleRepository;

    @InjectMocks private CashRegisterImpl service;

    //Helper: caja física registrada, nunca abierta
    private CashBoxEntity cajaFisica(String number){
        return cajaFisica(number, true);
    }

    /**
     * Caja física con id.
     *
     * El id importa: en una entidad real JPA lo asigna al guardar, pero en un
     * test con Mockito la entidad es un objeto en blanco. Si el id fuera null, las
     * llamadas al repositorio pasarían null y los stubs ({@code ...ById(1L)})
     * no casarían. Por eso el helper lo fija siempre.
     */
    private CashBoxEntity cajaFisica(String number, boolean active){
        return cajaFisicaId(1L, number, active);
    }

    private CashBoxEntity cajaFisicaId(Long id, String number, boolean active){
        CashBoxEntity box = new CashBoxEntity();
        box.setId(id);
        box.setNumber(number);
        box.setActive(active);
        box.setCreatedAt(LocalDateTime.now());
        return box;
    }

    //Helper: sesión de caja (un turno) ya cerrada
    private CashRegisterEntity turnoCerrado(String number, CashBoxEntity box){
        CashRegisterEntity sesion = new CashRegisterEntity();
        sesion.setNumber(number);
        sesion.setCashBox(box);
        sesion.setActive(false);
        sesion.setOpenedAt(LocalDateTime.now().minusHours(5));
        sesion.setClosedAt(LocalDateTime.now().minusHours(1));
        sesion.setTotalTickets(0);
        return sesion;
    }

    //Helper: turno abierto (el que está en uso ahora)
    private CashRegisterEntity turnoAbierto(String number, BigDecimal fondo){
        CashRegisterEntity sesion = new CashRegisterEntity();
        sesion.setNumber(number);
        sesion.setActive(true);
        sesion.setOpenedAt(LocalDateTime.now());
        sesion.setOpeningAmount(fondo);
        sesion.setTotalTickets(0);
        return sesion;
    }

    // =========================================================================
    //  CRUD de cajas físicas
    // =========================================================================
    @Nested
    @DisplayName("Registrar caja física")
    class CrearTest {

        @Test
        @DisplayName("crea la caja activa y sin turnos")
        void creaActiva(){
            CashBoxRequest request = new CashBoxRequest();
            request.setNumber("CAJA 1");
            request.setDescription("la que esta junto a la puerta");

            when(cashBoxRepository.existsByNumber("CAJA 1")).thenReturn(false);
            when(cashBoxRepository.save(any(CashBoxEntity.class)))
                    .thenAnswer(i -> {
                        CashBoxEntity saved = i.getArgument(0);
                        saved.setId(1L);
                        return saved;
                    });
            when(repository.findByCashBoxIdOrderByOpenedAtDesc(1L)).thenReturn(List.of());

            CashBoxResponse r = service.createBox(request);

            assertThat(r.getNumber()).isEqualTo("CAJA 1");
            assertThat(r.isActive()).isTrue();
            assertThat(r.getSessionsCount()).isZero();
            assertThat(r.isInUse()).isFalse();
            assertThat(r.getDescription()).isEqualTo("la que esta junto a la puerta");
        }

        @Test
        @DisplayName("número repetido -> 409, no un 500 por violar el UNIQUE")
        void numeroRepetidoDa409(){
            CashBoxRequest request = new CashBoxRequest();
            request.setNumber("CAJA 1");

            when(cashBoxRepository.existsByNumber("CAJA 1")).thenReturn(true);

            assertThatThrownBy(() -> service.createBox(request))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("Ya existe")
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);

            verify(cashBoxRepository, never()).save(any(CashBoxEntity.class));
        }

        @Test
        @DisplayName("normaliza a mayúsculas: 'caja 1' y 'CAJA 1' son la misma")
        void normalizaMayusculas(){
            CashBoxRequest request = new CashBoxRequest();
            request.setNumber("  caja 1  ");

            when(cashBoxRepository.existsByNumber("CAJA 1")).thenReturn(false);
            when(cashBoxRepository.save(any(CashBoxEntity.class)))
                    .thenAnswer(i -> {
                        CashBoxEntity saved = i.getArgument(0);
                        saved.setId(1L);
                        return saved;
                    });
            when(repository.findByCashBoxIdOrderByOpenedAtDesc(1L)).thenReturn(List.of());

            service.createBox(request);

            verify(cashBoxRepository).existsByNumber("CAJA 1");
        }

        @Test
        @DisplayName("número vacío -> 400")
        void numeroVacioDa400(){
            CashBoxRequest request = new CashBoxRequest();
            request.setNumber("  ");

            assertThatThrownBy(() -> service.createBox(request))
                    .isInstanceOf(CashException.class)
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("sugiere el siguiente número libre contando las CAJAS, no los cortes")
        void sugiereSiguienteNumero(){
            when(cashBoxRepository.findAll()).thenReturn(List.of());

            assertThat(service.getNextSuggestedNumber()).isEqualTo("CAJA 1");
        }

        @Test
        @DisplayName("sugiere el siguiente ignorando las que no siguen el patrón")
        void sugiereIgnoraNombresLibres(){
            CashBoxEntity a = cajaFisica("CAJA 1");
            CashBoxEntity b = cajaFisica("CAJA PRINCIPAL"); //no numérica
            when(cashBoxRepository.findAll()).thenReturn(List.of(a, b));

            assertThat(service.getNextSuggestedNumber()).isEqualTo("CAJA 2");
        }
    }

    @Nested
    @DisplayName("Editar caja física")
    class EditarTest {

        @Test
        @DisplayName("cambia el número y la descripción")
        void editaNumeroYDescripcion(){
            CashBoxEntity box = cajaFisica("CAJA 1");
            CashBoxRequest request = new CashBoxRequest();
            request.setNumber("CAJA PRINCIPAL");
            request.setDescription("la de la entrada");

            when(cashBoxRepository.findById(1L)).thenReturn(Optional.of(box));
            when(cashBoxRepository.existsByNumber("CAJA PRINCIPAL")).thenReturn(false);
            when(cashBoxRepository.save(any(CashBoxEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(repository.findByCashBoxIdOrderByOpenedAtDesc(anyLong())).thenReturn(List.of());

            CashBoxResponse r = service.updateBox(1L, request);

            assertThat(r.getNumber()).isEqualTo("CAJA PRINCIPAL");
            assertThat(r.getDescription()).isEqualTo("la de la entrada");
        }

        @Test
        @DisplayName("renombrar a un número que ya existe -> 409")
        void renombrarAExistenteDa409(){
            CashBoxEntity box = cajaFisica("CAJA 1");
            CashBoxRequest request = new CashBoxRequest();
            request.setNumber("CAJA 2");

            when(cashBoxRepository.findById(1L)).thenReturn(Optional.of(box));
            when(cashBoxRepository.existsByNumber("CAJA 2")).thenReturn(true);

            assertThatThrownBy(() -> service.updateBox(1L, request))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("Ya existe otra caja")
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("guardar el MISMO número no da 409 (solo se edita la descripción)")
        void mismoNumeroNoDa409(){
            CashBoxEntity box = cajaFisica("CAJA 1");
            CashBoxRequest request = new CashBoxRequest();
            request.setNumber("CAJA 1");   //sin cambios
            request.setDescription("nueva nota");

            when(cashBoxRepository.findById(1L)).thenReturn(Optional.of(box));
            when(cashBoxRepository.save(any(CashBoxEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(repository.findByCashBoxIdOrderByOpenedAtDesc(anyLong())).thenReturn(List.of());

            CashBoxResponse r = service.updateBox(1L, request);

            assertThat(r.getNumber()).isEqualTo("CAJA 1");
            assertThat(r.getDescription()).isEqualTo("nueva nota");

            //La clave: nunca se consulta si ese número ya existe, porque ES el suyo.
            verify(cashBoxRepository, never()).existsByNumber(any());
        }
    }

    @Nested
    @DisplayName("Dar de baja caja física")
    class DesactivarTest {

        @Test
        @DisplayName("una caja SIN turnos se da de baja sin problema")
        void cajaSinTurnosSeDaDeBaja(){
            CashBoxEntity box = cajaFisica("CAJA 3");
            when(cashBoxRepository.findById(1L)).thenReturn(Optional.of(box));
            when(repository.findByCashBoxIdAndActiveTrue(1L)).thenReturn(Optional.empty());

            service.desactiveBox(1L);

            assertThat(box.isActive()).isFalse();
            verify(cashBoxRepository, never()).delete(any());
        }

        @Test
        @DisplayName("una caja CON turnos NO se borra: se desactiva y conserva el historial")
        void cajaConTurnosNoSeBorra(){
            CashBoxEntity box = cajaFisica("CAJA 1");
            when(cashBoxRepository.findById(1L)).thenReturn(Optional.of(box));
            when(repository.findByCashBoxIdAndActiveTrue(1L)).thenReturn(Optional.empty());

            service.desactiveBox(1L);

            //El punto central de V4: desactivar, NUNCA borrar.
            assertThat(box.isActive()).isFalse();
            verify(cashBoxRepository).save(any(CashBoxEntity.class));
            verify(cashBoxRepository, never()).delete(any());
        }

        @Test
        @DisplayName("una caja con el turno abierto no se puede dar de baja")
        void cajaConTurnoAbiertoNoSeDaDeBaja(){
            CashBoxEntity box = cajaFisica("CAJA 1");
            when(cashBoxRepository.findById(1L)).thenReturn(Optional.of(box));
            when(repository.findByCashBoxIdAndActiveTrue(1L))
                    .thenReturn(Optional.of(turnoAbierto("CAJA 1", new BigDecimal("500"))));

            assertThatThrownBy(() -> service.desactiveBox(1L))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("turno abierto")
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("dar de baja una caja ya dada de baja -> 409")
        void cajaYaDeBajaDa409(){
            CashBoxEntity box = cajaFisica("CAJA 1", false);
            when(cashBoxRepository.findById(1L)).thenReturn(Optional.of(box));

            assertThatThrownBy(() -> service.desactiveBox(1L))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("ya esta dada de baja")
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("Borrar caja física (solo si nunca se abrió)")
    class BorrarTest {

        @Test
        @DisplayName("una caja SIN turnos sí se borra (no hay historial que perder)")
        void cajaSinTurnosSeBorra(){
            CashBoxEntity box = cajaFisica("CAJA 3");
            when(cashBoxRepository.findById(1L)).thenReturn(Optional.of(box));
            when(repository.findByCashBoxIdAndActiveTrue(1L)).thenReturn(Optional.empty());
            when(cashBoxRepository.hasSessions(1L)).thenReturn(false);

            service.deleteBox(1L);

            verify(cashBoxRepository).delete(box);
        }

        /**
         * ESTE es el test de la regla del dueño: lo que ya tuvo corte de caja no
         * se borra, se da de baja.
         */
        @Test
        @DisplayName("una caja CON cortes -> 409 y el mensaje dice 'dala de baja'")
        void cajaConCortesNoSeBorra(){
            CashBoxEntity box = cajaFisica("CAJA 1");
            when(cashBoxRepository.findById(1L)).thenReturn(Optional.of(box));
            when(repository.findByCashBoxIdAndActiveTrue(1L)).thenReturn(Optional.empty());
            when(cashBoxRepository.hasSessions(1L)).thenReturn(true);

            assertThatThrownBy(() -> service.deleteBox(1L))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("no se puede borrar")
                    .hasMessageContaining("Dala de baja")
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);

            verify(cashBoxRepository, never()).delete(any());
        }

        @Test
        @DisplayName("caja con el turno abierto -> 409 antes de mirar el historial")
        void cajaConTurnoAbiertoNoSeBorra(){
            CashBoxEntity box = cajaFisica("CAJA 1");
            when(cashBoxRepository.findById(1L)).thenReturn(Optional.of(box));
            when(repository.findByCashBoxIdAndActiveTrue(1L))
                    .thenReturn(Optional.of(turnoAbierto("CAJA 1", new BigDecimal("500"))));

            assertThatThrownBy(() -> service.deleteBox(1L))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("turno abierto")
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);

            verify(cashBoxRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("Consultar cajas")
    class ConsultarTest {

        @Test
        @DisplayName("getOpenable devuelve las cajas libres con sus datos")
        void getOpenableEnriquece(){
            CashBoxEntity box = cajaFisica("CAJA 1");
            box.setId(1L);
            CashRegisterEntity cerrado = turnoCerrado("CAJA 1", box);

            when(cashBoxRepository.findOpenable()).thenReturn(List.of(box));
            when(repository.findByCashBoxIdOrderByOpenedAtDesc(1L)).thenReturn(List.of(cerrado));

            List<CashBoxResponse> r = service.getOpenable();

            assertThat(r).hasSize(1);
            assertThat(r.get(0).getSessionsCount()).isEqualTo(1);
            assertThat(r.get(0).isInUse()).isFalse();      //el turno ya se cerró
            assertThat(r.get(0).getLastOpenedAt()).isNotNull();
        }

        @Test
        @DisplayName("inUse es true cuando la caja tiene un turno abierto")
        void inUseDetectaTurnoAbierto(){
            CashBoxEntity box = cajaFisica("CAJA 2");
            box.setId(2L);
            CashRegisterEntity abierto = turnoAbierto("CAJA 2", new BigDecimal("500"));

            when(cashBoxRepository.findOpenable()).thenReturn(List.of(box));
            when(repository.findByCashBoxIdOrderByOpenedAtDesc(2L)).thenReturn(List.of(abierto));

            List<CashBoxResponse> r = service.getOpenable();

            assertThat(r.get(0).isInUse()).isTrue();
        }

        @Test
        @DisplayName("getBoxes trae TODAS, incluidas las dadas de baja")
        void getBoxesTraeTodas(){
            CashBoxEntity activa = cajaFisica("CAJA 1");
            activa.setId(1L);
            CashBoxEntity baja = cajaFisica("CAJA 2", false);
            baja.setId(2L);

            when(cashBoxRepository.findAllByOrderByNumberAsc()).thenReturn(List.of(activa, baja));
            when(repository.findByCashBoxIdOrderByOpenedAtDesc(anyLong())).thenReturn(List.of());

            List<CashBoxResponse> r = service.getBoxes();

            assertThat(r).hasSize(2);
            assertThat(r.get(1).isActive()).isFalse();
        }

        @Test
        @DisplayName("historial de una caja devuelve un corte por turno")
        void historialDevuelveUnCortePorTurno(){
            CashBoxEntity box = cajaFisica("CAJA 1");
            CashRegisterEntity lunes = turnoCerrado("CAJA 1", box);
            CashRegisterEntity viernes = turnoCerrado("CAJA 1", box);

            when(cashBoxRepository.existsById(1L)).thenReturn(true);
            when(repository.findByCashBoxIdOrderByOpenedAtDesc(1L))
                    .thenReturn(List.of(viernes, lunes));
            when(mapper.toResponse(any(CashRegisterEntity.class))).thenAnswer(i -> new CashResponse());

            List<CashResponse> r = service.getBoxHistory(1L);

            //La misma caja abierta dos días = 2 filas. Ese es todo el punto de V4.
            assertThat(r).hasSize(2);
        }

        @Test
        @DisplayName("historial de una caja inexistente -> 404")
        void historialDeCajaInexistenteDa404(){
            when(cashBoxRepository.existsById(9L)).thenReturn(false);

            assertThatThrownBy(() -> service.getBoxHistory(9L))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("no existe")
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // =========================================================================
    //  ABRIR
    // =========================================================================
    @Nested
    @DisplayName("Abrir un turno")
    class AbrirTest {

        private OpenCashRequest request(String numero, String fondo){
            OpenCashRequest r = new OpenCashRequest();
            r.setNumber(numero);
            r.setOpeningAmount(new BigDecimal(fondo));
            return r;
        }

        @Test
        @DisplayName("abre una caja registrada y crea un turno nuevo")
        void abreCajaRegistrada(){
            CashBoxEntity box = cajaFisica("CAJA 1");
            when(repository.findByActiveTrue()).thenReturn(Optional.empty());
            when(cashBoxRepository.findByNumber("CAJA 1")).thenReturn(Optional.of(box));
            when(cashBoxRepository.save(any(CashBoxEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(repository.save(any(CashRegisterEntity.class)))
                    .thenAnswer(i -> {
                        CashRegisterEntity s = i.getArgument(0);
                        s.setId(10L);
                        return s;
                    });
            when(mapper.toResponse(any(CashRegisterEntity.class))).thenAnswer(i -> new CashResponse());

            service.open(request("CAJA 1", "500"));

            verify(repository).save(any(CashRegisterEntity.class));
        }

        /**
         * La misma caja que ya tuvo un corte el lunes se puede volver a abrir
         * el martes. Esto daba 409 y no había forma de abrir la caja que sí
         * existía.
         */
        @Test
        @DisplayName("la MISMA caja se puede reabrir en otro turno (esto V3 lo impedía)")
        void laMismaCajaSeReabre(){
            CashBoxEntity box = cajaFisica("CAJA 1");

            when(repository.findByActiveTrue()).thenReturn(Optional.empty());
            when(cashBoxRepository.findByNumber("CAJA 1")).thenReturn(Optional.of(box));
            when(cashBoxRepository.save(any(CashBoxEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(repository.save(any(CashRegisterEntity.class)))
                    .thenAnswer(i -> {
                        CashRegisterEntity s = i.getArgument(0);
                        s.setId(11L);
                        return s;
                    });
            when(mapper.toResponse(any(CashRegisterEntity.class))).thenAnswer(i -> new CashResponse());

            //No lanza: la caja existe, está activa y no hay turno abierto.
            CashResponse r = service.open(request("CAJA 1", "300"));

            assertThat(r).isNotNull();
            verify(repository).save(any(CashRegisterEntity.class));
        }

        @Test
        @DisplayName("el turno nuevo arranca con los acumulados en CERO (el fondo no es una venta)")
        void acumuladosArrancanEnCero(){
            CashBoxEntity box = cajaFisica("CAJA 1");
            when(repository.findByActiveTrue()).thenReturn(Optional.empty());
            when(cashBoxRepository.findByNumber("CAJA 1")).thenReturn(Optional.of(box));
            when(cashBoxRepository.save(any(CashBoxEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(repository.save(any(CashRegisterEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(mapper.toResponse(any(CashRegisterEntity.class))).thenAnswer(i -> new CashResponse());

            service.open(request("CAJA 1", "500"));

            //Si cashSales naciera con el fondo, el esperado contaría el fondo dos
            //veces y el cajero vería una diferencia fantasma de 500.
            verify(repository).save(org.mockito.ArgumentMatchers.argThat(sesion ->
                    sesion.getCashSales().compareTo(BigDecimal.ZERO) == 0
                    && sesion.getOpeningAmount().compareTo(new BigDecimal("500")) == 0));
        }

        @Test
        @DisplayName("el turno guarda el número como COPIA HISTÓRICA de la caja")
        void guardaCopiaHistoricaDelNumero(){
            CashBoxEntity box = cajaFisica("CAJA 1");
            when(repository.findByActiveTrue()).thenReturn(Optional.empty());
            when(cashBoxRepository.findByNumber("CAJA 1")).thenReturn(Optional.of(box));
            when(cashBoxRepository.save(any(CashBoxEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(repository.save(any(CashRegisterEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(mapper.toResponse(any(CashRegisterEntity.class))).thenAnswer(i -> new CashResponse());

            service.open(request("CAJA 1", "500"));

            verify(repository).save(org.mockito.ArgumentMatchers.argThat(sesion ->
                    "CAJA 1".equals(sesion.getNumber())
                    && box.equals(sesion.getCashBox())));
        }

        @Test
        @DisplayName("caja no registrada -> 404 con la pista de crearla primero")
        void cajaNoRegistradaDa404(){
            when(repository.findByActiveTrue()).thenReturn(Optional.empty());
            when(cashBoxRepository.findByNumber("CAJA 9")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.open(request("CAJA 9", "500")))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("Crerala primero")
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("caja dada de baja -> 409: no se puede abrir")
        void cajaDadaDeBajaDa409(){
            when(repository.findByActiveTrue()).thenReturn(Optional.empty());
            when(cashBoxRepository.findByNumber("CAJA 1"))
                    .thenReturn(Optional.of(cajaFisica("CAJA 1", false)));

            assertThatThrownBy(() -> service.open(request("CAJA 1", "500")))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("dada de baja")
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);

            verify(repository, never()).save(any(CashRegisterEntity.class));
        }

        @Test
        @DisplayName("si ya hay un turno abierto -> 409 y menciona cuál es")
        void yaHayUnTurnoAbiertoDa409(){
            when(repository.findByActiveTrue())
                    .thenReturn(Optional.of(turnoAbierto("CAJA 1", new BigDecimal("500"))));

            assertThatThrownBy(() -> service.open(request("CAJA 2", "500")))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("CAJA 1")
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("fondo menor a 100 -> 400 (regla del dueño)")
        void fondoMenorACienDa400(){
            when(repository.findByActiveTrue()).thenReturn(Optional.empty());
            when(cashBoxRepository.findByNumber("CAJA 1")).thenReturn(Optional.of(cajaFisica("CAJA 1")));

            assertThatThrownBy(() -> service.open(request("CAJA 1", "50")))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("al menos")
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("fondo negativo -> 400")
        void fondoNegativoDa400(){
            when(repository.findByActiveTrue()).thenReturn(Optional.empty());
            when(cashBoxRepository.findByNumber("CAJA 1")).thenReturn(Optional.of(cajaFisica("CAJA 1")));

            assertThatThrownBy(() -> service.open(request("CAJA 1", "-500")))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("negativo")
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("fondo exactamente 100 sí se acepta (es el mínimo, no el tope)")
        void fondoEnElMinimoSeAcepta(){
            CashBoxEntity box = cajaFisica("CAJA 1");
            when(repository.findByActiveTrue()).thenReturn(Optional.empty());
            when(cashBoxRepository.findByNumber("CAJA 1")).thenReturn(Optional.of(box));
            when(cashBoxRepository.save(any(CashBoxEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(repository.save(any(CashRegisterEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(mapper.toResponse(any(CashRegisterEntity.class))).thenAnswer(i -> new CashResponse());

            service.open(request("CAJA 1", "100"));

            verify(repository).save(org.mockito.ArgumentMatchers.argThat(sesion ->
                    sesion.getOpeningAmount().compareTo(new BigDecimal("100")) == 0));
        }
    }

    // =========================================================================
    //  EL CUADRE AL CERRAR
    // =========================================================================
    @Nested
    @DisplayName("Cuadre del efectivo al cerrar")
    class CuadreTest {

        //Una venta en efectivo de 200
        private List<SaleEntity> ventaEnEfectivoDe(BigDecimal total){
            SaleEntity sale = new SaleEntity();
            sale.setTotal(total);
            sale.setPaymentMethod(PaymentMethod.CASH);
            sale.setPaymentStatus(PaymentStatus.APPROVED);
            sale.setCancelled(false);
            sale.setSaleDate(LocalDateTime.now());
            return List.of(sale);
        }

        private CloseCashRequest request(String contado, String motivo){
            CloseCashRequest r = new CloseCashRequest();
            r.setClosingAmount(new BigDecimal(contado));
            r.setDifferenceReason(motivo);
            return r;
        }

        @Test
        @DisplayName("si cuadra exactamente, cierra y NO guarda motivo")
        void cierraSiCuadra(){
            //fondo 500 + venta 200 en efectivo = esperado 700
            CashRegisterEntity cash = turnoAbierto("CAJA 1", new BigDecimal("500"));
            when(repository.findByActiveTrue()).thenReturn(Optional.of(cash));
            when(saleRepository.findByCashRegister(cash)).thenReturn(ventaEnEfectivoDe(new BigDecimal("200")));
            when(repository.save(any(CashRegisterEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(mapper.toResponse(any(CashRegisterEntity.class))).thenAnswer(i -> new CashResponse());

            service.close(request("700", null));

            assertThat(cash.getDifference()).isEqualByComparingTo("0");
            assertThat(cash.getDifferenceReason()).isNull();
            assertThat(cash.getActive()).isFalse();
        }

        @Test
        @DisplayName("NO cuadra y sin motivo -> 409 y el turno SIGUE ABIERTO")
        void noCuadraSinMotivoBloquea(){
            CashRegisterEntity cash = turnoAbierto("CAJA 1", new BigDecimal("500"));
            when(repository.findByActiveTrue()).thenReturn(Optional.of(cash));
            when(saleRepository.findByCashRegister(cash)).thenReturn(ventaEnEfectivoDe(new BigDecimal("200")));

            assertThatThrownBy(() -> service.close(request("650", null)))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("no cuadra")
                    .hasMessageContaining("700")  //el esperado, para que compare
                    .hasMessageContaining("650")  //el contado
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);

            //Lo importante: NO se guardó nada, el turno sigue abierto
            verify(repository, never()).save(any(CashRegisterEntity.class));
            assertThat(cash.getActive()).isTrue();
        }

        @Test
        @DisplayName("NO cuadra pero con motivo -> cierra y guarda la diferencia y el motivo")
        void noCuadraConMotivoCierra(){
            CashRegisterEntity cash = turnoAbierto("CAJA 1", new BigDecimal("500"));
            when(repository.findByActiveTrue()).thenReturn(Optional.of(cash));
            when(saleRepository.findByCashRegister(cash)).thenReturn(ventaEnEfectivoDe(new BigDecimal("200")));
            when(repository.save(any(CashRegisterEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(mapper.toResponse(any(CashRegisterEntity.class))).thenAnswer(i -> new CashResponse());

            service.close(request("650", "Se mePaso un billete de 50 al contar"));

            assertThat(cash.getDifference()).isEqualByComparingTo("-50");
            assertThat(cash.getDifferenceReason()).isEqualTo("Se mePaso un billete de 50 al contar");
            assertThat(cash.getActive()).isFalse();
        }

        @Test
        @DisplayName("motivo en blanco cuenta como no enviado: también bloquea")
        void motivoEnBlancoBloquea(){
            CashRegisterEntity cash = turnoAbierto("CAJA 1", new BigDecimal("500"));
            when(repository.findByActiveTrue()).thenReturn(Optional.of(cash));
            when(saleRepository.findByCashRegister(cash)).thenReturn(ventaEnEfectivoDe(new BigDecimal("200")));

            assertThatThrownBy(() -> service.close(request("650", "   ")))
                    .isInstanceOf(CashException.class)
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("efectivo contado negativo -> 400")
        void efectivoNegativoDa400(){
            CashRegisterEntity cash = turnoAbierto("CAJA 1", new BigDecimal("500"));
            when(repository.findByActiveTrue()).thenReturn(Optional.of(cash));
            when(saleRepository.findByCashRegister(cash)).thenReturn(ventaEnEfectivoDe(new BigDecimal("200")));

            assertThatThrownBy(() -> service.close(request("-100", null)))
                    .isInstanceOf(CashException.class)
                    .hasMessageContaining("negativo")
                    .extracting(e -> ((CashException) e).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }

        /**
         * Las ventas anuladas no cuentan ni para el dinero ni para los tickets.
         *
         * Contarlas dejaría el monto de una venta anulada dentro del "esperado":
         * el cajero contaría el efectivo real, no coincidiría, y vería una
         * diferencia fantasma de su propio bolsillo.
         */
        @Test
        @DisplayName("las ventas anuladas no entran al cálculo del corte")
        void ventasAnuladasNoCuentan(){
            SaleEntity anulada = new SaleEntity();
            anulada.setTotal(new BigDecimal("200"));
            anulada.setPaymentMethod(PaymentMethod.CASH);
            anulada.setPaymentStatus(PaymentStatus.APPROVED);
            anulada.setCancelled(true);
            anulada.setSaleDate(LocalDateTime.now());

            CashRegisterEntity cash = turnoAbierto("CAJA 1", new BigDecimal("500"));
            when(repository.findByActiveTrue()).thenReturn(Optional.of(cash));
            when(saleRepository.findByCashRegister(cash)).thenReturn(List.of(anulada));
            when(repository.save(any(CashRegisterEntity.class))).thenAnswer(i -> i.getArgument(0));
            when(mapper.toResponse(any(CashRegisterEntity.class))).thenAnswer(i -> new CashResponse());

            //Si la anulada contara, el esperado sería 700. Como no cuenta, es 500.
            service.close(request("500", null));

            assertThat(cash.getExpectedAmount()).isEqualByComparingTo("500");
            assertThat(cash.getTotalTickets()).isZero();
        }
    }
}