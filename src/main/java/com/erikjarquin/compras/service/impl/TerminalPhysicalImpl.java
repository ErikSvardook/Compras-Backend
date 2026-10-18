package com.erikjarquin.compras.service.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.erikjarquin.compras.config.TerminalConfig;
import com.erikjarquin.compras.model.dto.Terminal.TerminalRequest;
import com.erikjarquin.compras.model.dto.Terminal.TerminalResponse;
import com.erikjarquin.compras.service.TerminalService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementación para una terminal de pagos FÍSICA conectada por Socket TCP.
 *
 * Se activa solo si {@code payment.terminal.type=PHYSICAL}
 * (@ConditionalOnProperty). Protocolo de mensajes por líneas de texto:
 *  - PAY|merchant|terminal|monto|método|transactionId  -> cobro
 *  - REV|merchant|terminal|transactionId                -> reversa
 *  - STS|merchant|terminal|transactionId                -> consulta de estado
 *
 * Todo el tráfico pasa por {@link #sendRaw(String)}, que abre el socket,
 * respeta el timeout configurado y devuelve la línea de respuesta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "payment.terminal.type", havingValue = "PHYSICAL")
public class TerminalPhysicalImpl implements TerminalService {

    private final TerminalConfig config;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    /**
     * Cobro: envía PAY|... y espera la respuesta con timeout (el executor evita
     * bloquear el hilo de la petición HTTP si la terminal no responde).
     */
    @Override
    public TerminalResponse processPayment(TerminalRequest request) {
        log.info("[TERMINAL FÍSICA] Conectando a {}:{}", config.getHost(), config.getPort());
        log.info("Monto: {}, Transacción: {}", request.getAmount(), request.getTransactionId());

        try {
            Future<String> future = executor.submit(() -> sendToTerminal(request));
            String response = future.get(config.getTimeout(), TimeUnit.SECONDS);
            return parseTerminalResponse(response);
        } catch (TimeoutException e) {
            log.error("TIMEOUT: La terminal no respondió en {} segundos", config.getTimeout());
            return TerminalResponse.builder()
                    .approved(false)
                    .responseCode("998")
                    .responseMessage("TIMEOUT")
                    .errorMessage("La terminal no respondió en el tiempo establecido")
                    .build();
        } catch (Exception e) {
            log.error("Error al comunicarse con la terminal física", e);
            return TerminalResponse.builder()
                    .approved(false)
                    .responseCode("999")
                    .responseMessage("ERROR DE COMUNICACIÓN")
                    .errorMessage("No se pudo conectar con la terminal: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Construye el mensaje PAY y lo envía por el socket.
     */
    private String sendToTerminal(TerminalRequest request) throws IOException {
        return sendRaw(buildTerminalMessage(request));
    }

    /**
     * Envía un mensaje de texto al socket de la terminal y lee la respuesta.
     *
     * Método base para PAY/REV/STS: abre conexión, configura timeout,
     * escribe una línea y espera una línea de respuesta.
     */
    private String sendRaw(String message) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(config.getHost(), config.getPort()), config.getTimeout() * 1000);
            socket.setSoTimeout(config.getTimeout() * 1000);

            try (PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                out.println(message);
                log.info("Mensaje enviado: {}", message);

                String response = in.readLine();
                if (response == null) {
                    throw new IOException("La terminal cerró la conexión sin responder");
                }

                log.info("Respuesta recibida: {}", response);
                return response;
            }
        } catch (SocketTimeoutException e) {
            throw new IOException("Timeout esperando respuesta de la terminal " + e);
        }
    }

    /**
     * Protocolo de cobro (solo datos necesarios para la transacción).
     */
    private String buildTerminalMessage(TerminalRequest request) {
        return String.format("PAY|%s|%s|%.2f|%s|%s",
                config.getMerchantId(),
                config.getTerminalId(),
                request.getAmount(),
                request.getPaymentMethod(),
                request.getTransactionId());
    }

    /**
     * Parsea la respuesta de la terminal. Formato esperado:
     * "000|AUT20231201123456|1234|VISA|DEBIT|APROBADA"
     */
    private TerminalResponse parseTerminalResponse(String response) {
        String[] parts = response.split("\\|");

        return TerminalResponse.builder()
                .approved("000".equals(parts[0]))
                .authorizationCode(parts.length > 1 ? parts[1] : null)
                .lastFourDigits(parts.length > 2 ? parts[2] : null)
                .cardBrand(parts.length > 3 ? parts[3] : null)
                .cardType(parts.length > 4 ? parts[4] : null)
                .responseCode(parts[0])
                .responseMessage(parts.length > 5 ? parts[5] : "")
                .transactionDate(LocalDateTime.now())
                .transactionId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Reversa: envía REV|... por el socket y devuelve true solo si la terminal
     * responde con el código de éxito (000).
     */
    @Override
    public boolean reversePayment(String transactionId) {
        log.info("Reversando transacción en terminal física: {}", transactionId);

        try {
            String message = String.format("REV|%s|%s|%s",
                    config.getMerchantId(),
                    config.getTerminalId(),
                    transactionId);

            String response = sendRaw(message);
            if (response == null) {
                return false;
            }

            String[] parts = response.split("\\|");
            return parts.length > 0 && "000".equals(parts[0]);
        } catch (Exception e) {
            log.error("Error en reversa de pago", e);
            return false;
        }
    }

    /**
     * Consulta de estado: envía STS|... por el socket y parsea la respuesta.
     */
    @Override
    public TerminalResponse getTransactionStatus(String transactionId) {
        try {
            String message = String.format("STS|%s|%s|%s",
                    config.getMerchantId(),
                    config.getTerminalId(),
                    transactionId);

            String response = sendRaw(message);
            return parseTerminalResponse(response);
        } catch (Exception e) {
            log.error("Error consultando estado", e);
            return TerminalResponse.builder()
                    .approved(false)
                    .transactionId(transactionId)
                    .responseMessage("ERROR")
                    .build();
        }
    }
}