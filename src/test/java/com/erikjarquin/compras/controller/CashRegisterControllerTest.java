package com.erikjarquin.compras.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.erikjarquin.compras.config.JwtUtil;
import com.erikjarquin.compras.config.SecurityConfig;
import com.erikjarquin.compras.config.security.SecurityAuthorityMapper;
import com.erikjarquin.compras.exceptions.CashException;
import com.erikjarquin.compras.model.dto.Cash.CashBoxResponse;
import com.erikjarquin.compras.model.dto.Cash.CashResponse;
import com.erikjarquin.compras.model.dto.Cash.CashSummaryResponse;
import com.erikjarquin.compras.repository.UserRepository;
import com.erikjarquin.compras.service.CashRegisterService;

/**
 * Tests del controlador de caja registradora.
 *
 * Cubre el CRUD de cajas físicas (V4), la apertura y cierre de turnos, el
 * resumen (hoja de corte) y la consulta de caja activa. También valida el
 * permiso específico de cada endpoint (ABRIR_CAJA / CERRAR_CAJA / CORTE_CAJA /
 * VER_CAJA / EDITAR_CAJA).
 */
@WebMvcTest(CashRegisterController.class)
@Import(SecurityConfig.class)
class CashRegisterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CashRegisterService cashService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private SecurityAuthorityMapper authorityMapper;

    private CashResponse cajaActiva() {
        CashResponse response = new CashResponse();
        response.setId(1L);
        response.setOpeningAmount(new BigDecimal("500.00"));
        response.setNumber("CAJA-1");
        response.setActive(true);
        response.setTotalSales(new BigDecimal("1230.50"));
        return response;
    }

    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void abrirCaja_devuelveCaja() throws Exception {
        when(cashService.open(any())).thenReturn(cajaActiva());

        mockMvc.perform(post("/api/local/cash/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingAmount\":500,\"number\":\"CAJA-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @WithMockUser(authorities = "CERRAR_CAJA")
    void cerrarCaja_devuelveCajaCerrada() throws Exception {
        CashResponse cerrada = cajaActiva();
        cerrada.setActive(false);
        when(cashService.close(any())).thenReturn(cerrada);

        mockMvc.perform(post("/api/local/cash/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\":1700}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(authorities = "VER_CAJA")
    void consultarCajaActiva_devuelveCaja() throws Exception {
        when(cashService.getActiveCash()).thenReturn(cajaActiva());

        mockMvc.perform(get("/api/local/cash/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @WithMockUser(authorities = "CORTE_CAJA")
    void obtenerResumen_devuelveHojaDeCorte() throws Exception {
        CashSummaryResponse summary = new CashSummaryResponse();
        summary.setCashId(1L);
        summary.setOpeningAmount(new BigDecimal("500.00"));
        summary.setTotalSales(new BigDecimal("1230.50"));
        summary.setExpectedAmount(new BigDecimal("1730.50"));
        summary.setTotalTickets(7);

        when(cashService.getSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/local/cash/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashId").value(1L))
                .andExpect(jsonPath("$.totalTickets").value(7));
    }

    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void cerrarCaja_sinPermisoDeCierre_devuelve403() throws Exception {
        // Tiene ABRIR_CAJA pero no CERRAR_CAJA → el método queda bloqueado.
        mockMvc.perform(post("/api/local/cash/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\":1700}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "VER_CAJA")
    void consultarCajaSinCajaAbierta_devuelve404() throws Exception {
        when(cashService.getActiveCash())
                .thenThrow(new CashException("No hay caja abierta", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/local/cash/active"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CASH_ERROR"));
    }

    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void abrirCajaYaAbierta_devuelve409() throws Exception {
        when(cashService.open(any()))
                .thenThrow(new CashException("Ya existe una caja abierta", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/local/cash/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingAmount\":500,\"number\":\"CAJA-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_ERROR"));

        verify(cashService).open(any());
    }

    // =========================================================================
    //  V3: número de caja + historial (el filtro por caja de Reportes)
    // =========================================================================

    /**
     * El número es OBLIGATORIO: sin él la caja no se puede identificar en
     * el historial ni en el filtro de reportes. Se declara en el servicio con un
     * 400, no por validación de bean, porque el mensaje puede explicar por qué
     * se necesita.
     */
    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void abrirCaja_sinNumero_devuelve400() throws Exception {
        when(cashService.open(any()))
                .thenThrow(new CashException("El numero de caja es obligatorio", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/local/cash/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingAmount\":500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CASH_ERROR"));
    }

    /**
     * Número repetido → 409. Dos cajas con el mismo número harían que el filtro
     * por caja de Reportes mezclara dos cortes distintos.
     */
    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void abrirCaja_numeroRepetido_devuelve409() throws Exception {
        when(cashService.open(any()))
                .thenThrow(new CashException(
                        "Ya existe una caja con el numero \"CAJA-1\"", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/local/cash/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingAmount\":500,\"number\":\"CAJA-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_ERROR"));
    }

    /**
     * El historial se lee con VER_CAJA, no con CORTE_CAJA: ver la lista de cajas
     * es consultar, no hacer un corte. Y devuelve el número para que el frontend
     * pueda armar el selector.
     */
    @Test
    @WithMockUser(authorities = "VER_CAJA")
    void historialDeCajas_devuelveListaConNumero() throws Exception {
        when(cashService.getHistory()).thenReturn(List.of(cajaActiva()));

        mockMvc.perform(get("/api/local/cash/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].number").value("CAJA-1"));
    }

    @Test
    @WithMockUser(authorities = "VER_CAJA")
    void consultarCajaPorNumero_devuelveLaCaja() throws Exception {
        when(cashService.getByNumber("CAJA-1")).thenReturn(cajaActiva());

        mockMvc.perform(get("/api/local/cash/number/CAJA-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("CAJA-1"));

        verify(cashService).getByNumber("CAJA-1");
    }

    @Test
    @WithMockUser(authorities = "VER_CAJA")
    void consultarCajaPorNumeroInexistente_devuelve404() throws Exception {
        when(cashService.getByNumber("CAJA-99"))
                .thenThrow(new CashException("No existe ninguna caja con el numero \"CAJA-99\"",
                        HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/local/cash/number/CAJA-99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CASH_ERROR"));
    }

    // =========================================================================
    //  CRUD de cajas físicas + apertura de turno
    // =========================================================================

    /**
     * Registrar caja física es un endpoint NUEVO y separado de abrir.
     *
     * Es un CRUD del inventario de cajas del local: no abre un turno ni mueve
     * dinero. Abrir un turno es {@code POST /open}, que crea una sesión nueva
     * apuntando a esta caja.
     */
    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void crearCaja_devuelve200ConElNumero() throws Exception {
        when(cashService.createBox(any())).thenReturn(caja("CAJA 1", true, 0, false));

        mockMvc.perform(post("/api/local/cash/boxes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"CAJA 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("CAJA 1"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sessionsCount").value(0));
    }

    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void crearCaja_numeroRepetido_devuelve409() throws Exception {
        when(cashService.createBox(any())).thenThrow(new CashException(
                "Ya existe una caja con el numero \"CAJA 1\"", HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/local/cash/boxes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"CAJA 1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_ERROR"));
    }

    /** La tabla "Ver cajas" trae TODAS, incluidas las dadas de baja. */
    @Test
    @WithMockUser(authorities = "VER_CAJA")
    void listarCajas_devuelveLista() throws Exception {
        when(cashService.getBoxes()).thenReturn(List.of(
                caja("CAJA 1", true, 3, false),
                caja("CAJA 2", false, 1, false)));

        mockMvc.perform(get("/api/local/cash/boxes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].number").value("CAJA 1"))
                .andExpect(jsonPath("$[0].sessionsCount").value(3))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    /** El selector de "Abrir caja" pide las cajas LIBRES, no todas. */
    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void cajasAbribles_devuelveSoloLasLibres() throws Exception {
        when(cashService.getOpenable()).thenReturn(List.of(caja("CAJA 2", true, 0, false)));

        mockMvc.perform(get("/api/local/cash/boxes/openable"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value("CAJA 2"));
    }

    /** Editar y dar de baja usan ABRIR_CAJA: mismo permiso, mismo responsable. */
    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void editarCaja_devuelve200() throws Exception {
        when(cashService.updateBox(anyLong(), any())).thenReturn(caja("CAJA PRINCIPAL", true, 2, false));

        mockMvc.perform(put("/api/local/cash/boxes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"CAJA PRINCIPAL\",\"description\":\"la de la entrada\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("CAJA PRINCIPAL"));
    }

    /** Dar de baja es PATCH, no DELETE: la caja nunca se borra. */
    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void darDeBajaCaja_devuelve204() throws Exception {
        mockMvc.perform(patch("/api/local/cash/boxes/1"))
                .andExpect(status().isNoContent());

        verify(cashService).desactiveBox(1L);
    }

    /** El historial de una caja devuelve un corte por turno. */
    @Test
    @WithMockUser(authorities = "VER_CAJA")
    void historialDeCaja_devuelveUnCortePorTurno() throws Exception {
        when(cashService.getBoxHistory(1L)).thenReturn(List.of(
                cajaConNumero("CAJA 1"), cajaConNumero("CAJA 1")));

        mockMvc.perform(get("/api/local/cash/boxes/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    /**
     * El siguiente número viene en un objeto y no como texto pelado: TypeScript
     * espera una propiedad con nombre.
     */
    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void siguienteNumero_devuelveLaSugerencia() throws Exception {
        when(cashService.getNextSuggestedNumber()).thenReturn("CAJA 3");

        mockMvc.perform(get("/api/local/cash/next-number"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestedNumber").value("CAJA 3"));
    }

    /**
     * Abrir un turno manda el número de una CAJA FÍSICA ya registrada.
     *
     * La misma caja puede abrirse otra vez en otro día: por eso el endpoint
     * sigue siendo el mismo y lo que cambia es que ahora crea una sesión nueva.
     */
    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void abrirCaja_devuelve200() throws Exception {
        when(cashService.open(any())).thenReturn(cajaConNumero("CAJA 1"));

        mockMvc.perform(post("/api/local/cash/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingAmount\":500,\"number\":\"CAJA 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value("CAJA 1"));
    }

    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void abrirCaja_fondoMenorACien_devuelve400() throws Exception {
        when(cashService.open(any())).thenThrow(new CashException(
                "El monto inicial debe ser al menos $100", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/local/cash/open")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"openingAmount\":50,\"number\":\"CAJA 1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CASH_ERROR"));
    }

    /**
     * El punto 5.1 del encargo: si el dinero no cuadra y no hay motivo, el cierre
     * se rechaza con 409 y el turno sigue abierto.
     */
    @Test
    @WithMockUser(authorities = "CERRAR_CAJA")
    void cerrarCaja_sinCuadrarYSinMotivo_devuelve409() throws Exception {
        when(cashService.close(any())).thenThrow(new CashException(
                "El dinero no cuadra. Esperado: $700, contado: $650, diferencia: -50",
                HttpStatus.CONFLICT));

        mockMvc.perform(post("/api/local/cash/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\":650}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("no cuadra")));
    }

    /** Salida de emergencia: con motivo, el cierre procede. */
    @Test
    @WithMockUser(authorities = "CERRAR_CAJA")
    void cerrarCaja_conMotivo_cierra() throws Exception {
        CashResponse cerrada = cajaConNumero("CAJA 1");
        cerrada.setDifference(new BigDecimal("-50"));
        cerrada.setDifferenceReason("Se me paso un billete de 50");
        when(cashService.close(any())).thenReturn(cerrada);

        mockMvc.perform(post("/api/local/cash/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"closingAmount\":650,\"differenceReason\":\"Se me paso un billete de 50\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.differenceReason").value("Se me paso un billete de 50"));
    }

    @Test
    @WithMockUser(authorities = "OTRO_PERMISO")
    void crearCaja_sinPermiso_devuelve403() throws Exception {
        mockMvc.perform(post("/api/local/cash/boxes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"number\":\"CAJA 9\"}"))
                .andExpect(status().isForbidden());

        verify(cashService, never()).createBox(any());
    }

    /**
     * Borrar una caja que ya tuvo cortes da 409 y el mensaje dice "dala de baja".
     *
     * Es la regla del dueño: lo que ya tuvo corte de caja no se borra nunca.
     */
    @Test
    @WithMockUser(authorities = "ABRIR_CAJA")
    void borrarCaja_conCortes_devuelve409() throws Exception {
        org.mockito.Mockito.doThrow(new CashException(
                "La caja \"CAJA 1\" ya tiene cortes y no se puede borrar. Dala de baja.",
                HttpStatus.CONFLICT))
                .when(cashService).deleteBox(1L);

        mockMvc.perform(delete("/api/local/cash/boxes/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASH_ERROR"));
    }

/** Dar de baja una caja exige ABRIR_CAJA: sin permiso, 403. */
    @Test
    @WithMockUser(authorities = "OTRO_PERMISO")
    void darDeBajaCaja_sinPermiso_devuelve403() throws Exception {
        mockMvc.perform(patch("/api/local/cash/boxes/1"))
                .andExpect(status().isForbidden());

        verify(cashService, never()).desactiveBox(anyLong());
    }

    //Helper: caja física con sus datos de tabla
    private CashBoxResponse caja(String numero, boolean active, int turnos, boolean inUse){
        CashBoxResponse b = new CashBoxResponse();
        b.setId(1L);
        b.setNumber(numero);
        b.setActive(active);
        b.setSessionsCount(turnos);
        b.setInUse(inUse);
        b.setCreatedAt(LocalDateTime.now());
        b.setLastOpenedAt(LocalDateTime.now().minusDays(1));
        return b;
    }

    //Helper: caja con número
    private CashResponse cajaConNumero(String numero){
        CashResponse r = new CashResponse();
        r.setId(1L);
        r.setNumber(numero);
        r.setActive(false);
        return r;
    }
}