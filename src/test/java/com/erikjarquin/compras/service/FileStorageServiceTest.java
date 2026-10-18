package com.erikjarquin.compras.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Tests de FileStorageService (validacion de imagenes) con un directorio
 * temporal real; no requiere Spring ni base de datos.
 *
 * Puntos criticos probados: rechazo por extension, rechazo por contenido
 * (magic bytes: un ejecutable renombrado a .png no debe pasar), rechazo por
 * tamano y guardado/eliminacion reales en disco.
 */
class FileStorageServiceTest {

    private FileStorageService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new FileStorageService(tempDir.toString());
    }

    /** PNG real en miniatura (firma 89 50 4E 47 0D 0A 1A 0A + header basico). */
    private static byte[] pngBytes() {
        byte[] png = new byte[20];
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(signature, 0, png, 0, 8);
        return png;
    }

    @Test
    void store_guardaImagenValidaConNombreUUID() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.png", "image/png", pngBytes());

        String storedName = service.store(file);

        assertThat(storedName).isNotBlank();
        assertThat(Files.exists(tempDir.resolve(storedName))).isTrue();
        // El nombre guardado nunca conserva el nombre original (path traversal/colision).
        assertThat(storedName).doesNotContain("foto");
    }

    @Test
    void store_rechazaArchivoNoImagen_porContenido() {
        // Mismo .png "exitoso", pero el contenido es un script/ejecutable invalido.
        MockMultipartFile fake = new MockMultipartFile(
                "file", "malware.png", "image/png", "MZ\u0000\u0000..." .getBytes());

        assertThatThrownBy(() -> service.store(fake))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no es una imagen");
    }

    @Test
    void store_rechazaExtensionNoPermitida() {
        MockMultipartFile script = new MockMultipartFile(
                "file", "consola.sh", "text/x-script", "#!/bin/bash".getBytes());

        assertThatThrownBy(() -> service.store(script))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tipo de archivo no permitido");
    }

    @Test
    void store_rechazaArchivoDeMasDe5MB() {
        MockMultipartFile big = new MockMultipartFile(
                "file", "grande.png", "image/png", new byte[6 * 1024 * 1024]);

        assertThatThrownBy(() -> service.store(big))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void store_devuelveNullSiNoHayArchivo() {
        assertThat(service.store(null)).isNull();
        assertThat(service.store(new MockMultipartFile("file", "", "image/png",
                new byte[0]))).isNull();
    }

    @Test
    void delete_eliminaArchivoYEsInofensivoSiNoExiste() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "foto.png", "image/png", pngBytes());
        String storedName = service.store(file);

        service.delete(storedName);

        assertThat(Files.exists(tempDir.resolve(storedName))).isFalse();
        // No debe lanzar si el archivo ya no esta.
        service.delete(storedName);
        service.delete(null);
    }
}