package com.erikjarquin.compras.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests de {@link InputValidator}.
 *
 * Son tests de texto y no de números porque ahí está toda la razón de
 * ser de la clase. Al convertir a {@code BigDecimal}, "000.2" ya es 0.2 y
 * "1.875" ya es 1.875: los errores de captura se pierden antes de poder
 * detectarlos. Estos tests fijan que el texto no se transforme antes de
 * validar.
 */
@DisplayName("InputValidator - reglas de captura de los formularios")
class InputValidatorTest {

    // =========================================================================
    //  SKU: letras + números + separadores, con límite
    // =========================================================================
    @Nested
    @DisplayName("SKU")
    class SkuTest {

        @Test
        @DisplayName("pasa a mayúsculas y recorta")
        void normalizaAMayusculas(){
            assertThat(InputValidator.sku("  choc-500 ")).isEqualTo("CHOC-500");
        }

        @Test
        @DisplayName("admite letras, números y los separadores - _ . /")
        void admiteLetrasNumerosYSeparadores(){
            assertThat(InputValidator.sku("CHOC-500")).isEqualTo("CHOC-500");
            assertThat(InputValidator.sku("LALA_1")).isEqualTo("LALA_1");
            assertThat(InputValidator.sku("LECHE.1L")).isEqualTo("LECHE.1L");
            assertThat(InputValidator.sku("REF/12")).isEqualTo("REF/12");
        }

        @ParameterizedTest
        @ValueSource(strings = {"CHOC 500", "CHOC*500", "CHOC@500", "CHOC#500", "CHOC!500"})
        @DisplayName("rechaza espacios y símbolos no permitidos")
        void rechazaCaracteresNoPermitidos(String valor){
            assertThatThrownBy(() -> InputValidator.sku(valor))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rechaza más de 50 caracteres")
        void rechazaDemasiadoLargo(){
            String largo = "A".repeat(51);

            assertThatThrownBy(() -> InputValidator.sku(largo))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("50");
        }

        @Test
        @DisplayName("es obligatorio: vacío, blancos o null lanzan (V7)")
        void vacioLanza(){
            // Esto antes devolvía null y el SKU se guardaba
            // como NULL. Ahora es obligatorio, y el mensaje tiene que decir qué
            // campo falta: es lo que ve el usuario en el 400.
            assertThatThrownBy(() -> InputValidator.sku(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SKU")
                    .hasMessageContaining("obligatorio");

            // "   " es el caso que un chequeo con != null no atrapa: el
            // formulario manda la cadena vacía o con espacios cuando el usuario
            // deja el campo en blanco.
            assertThatThrownBy(() -> InputValidator.sku("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("obligatorio");

            assertThatThrownBy(() -> InputValidator.sku(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("obligatorio");
        }
    }

    // =========================================================================
    //  RFC: letras y números, máximo 13 (como el mexicano)
    // =========================================================================
    @Nested
    @DisplayName("RFC")
    class RfcTest {

        @Test
        @DisplayName("pasa a mayúsculas: un RFC se escribe en mayúsculas")
        void normalizaAMayusculas(){
            assertThat(InputValidator.rfc("gode561231gr8")).isEqualTo("GODE561231GR8");
        }

        @Test
        @DisplayName("admite 13 caracteres (persona física)")
        void admiteTreceCaracteres(){
            assertThat(InputValidator.rfc("GODE561231GR8")).hasSize(13);
        }

        @Test
        @DisplayName("rechaza más de 13 caracteres")
        void rechazaDemasiadoLargo(){
            assertThatThrownBy(() -> InputValidator.rfc("GODE561231GR89"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("13");
        }

        @Test
        @DisplayName("rechaza guiones y espacios")
        void rechazaSeparadores(){
            assertThatThrownBy(() -> InputValidator.rfc("GODE-561231-GR8"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> InputValidator.rfc("GODE 561231 GR8"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    //  Barcode: solo dígitos y se conserva como TEXTO
    // =========================================================================
    @Nested
    @DisplayName("Código de barras")
    class BarcodeTest {

        @Test
        @DisplayName("conserva los ceros a la izquierda porque es un identificador")
        void conservaCerosIniciales(){
            //Si se tratara como número, esto se guardaría como 7501234567890 y el
            //lector dejaría de encontrar el producto. Por eso es texto.
            assertThat(InputValidator.barcode("07501234567890")).isEqualTo("07501234567890");
        }

        @Test
        @DisplayName("rechaza letras")
        void rechazaLetras(){
            assertThatThrownBy(() -> InputValidator.barcode("ABC123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("números");
        }

        @Test
        @DisplayName("rechaza más de 20 dígitos")
        void rechazaDemasiadoLargo(){
            assertThatThrownBy(() -> InputValidator.barcode("1".repeat(21)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("20");
        }

        @Test
        @DisplayName("es obligatorio: vacío, blancos o null lanzan (V7)")
        void vacioLanza(){
            // Antes el código de barras vacío devolvía null y se guardaba
            // como NULL. En el POS el producto se cobra escaneando, así que un
            // producto sin código es un producto que el cajero no puede cobrar
            // sin buscarlo a mano.
            assertThatThrownBy(() -> InputValidator.barcode(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("código de barras")
                    .hasMessageContaining("obligatorio");

            assertThatThrownBy(() -> InputValidator.barcode("   "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("obligatorio");

            assertThatThrownBy(() -> InputValidator.barcode(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("obligatorio");
        }
    }

    // =========================================================================
    //  Obligatoriedad: requerido() y password()
    //
    //  Estos dos métodos son los que evitan que un campo vacío llegue al
    //  INSERT. Antes cada servicio escribía su propio `if (x == null) throw`
    //  no lo escribía, como el de usuario— y la base de datos respondía con
    //  un 409 diciendo "el dato ya existe".
    // =========================================================================
    @Nested
    @DisplayName("Obligatoriedad (V7)")
    class ObligatorioTest {

        @Test
        @DisplayName("requerido: devuelve el valor recortado")
        void requeridoRecorta(){
            assertThat(InputValidator.requerido("  Leche  ", "nombre")).isEqualTo("Leche");
        }

        @Test
        @DisplayName("requerido: null, vacío y solo espacios lanzan")
        void requeridoLanza(){
            // Los TRES casos, y el último es el que se cuela en la práctica:
            // el formulario manda "   " cuando el usuario deja el campo vacío.
            for (String vacio : new String[]{ null, "", "   ", "\t\n" }) {
                assertThatThrownBy(() -> InputValidator.requerido(vacio, "nombre"))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("nombre")
                        .hasMessageContaining("obligatorio");
            }
        }

        @Test
        @DisplayName("password: rechaza vacía y demasiado corta")
        void passwordValida(){
            assertThat(InputValidator.password("clave1234", "contraseña", 8))
                    .isEqualTo("clave1234");

            assertThatThrownBy(() -> InputValidator.password("", "contraseña", 8))
                    .isInstanceOf(IllegalArgumentException.class)
                    // 🔑 El mensaje se comprueba palabra por palabra porque es lo
                    // ÚNICO que ve el usuario. "El contraseña es obligatorio"
                    // (el error que había) hace que el sistema parezca hecho a
                    // prisas; "La contraseña es obligatoria" no.
                    .hasMessage("La contraseña es obligatoria.");

            assertThatThrownBy(() -> InputValidator.password("corta", "contraseña", 8))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("La contraseña")
                    .hasMessageContaining("8");
        }

        @Test
        @DisplayName("requerida: el género del campo se refleja en el mensaje")
        void requeridoRespetaElGenero(){
            // Masculino y femenino van por métodos distintos a propósito: un
            // parámetro boolean dejaría adivinar qué significa en la llamada.
            assertThatThrownBy(() -> InputValidator.requerido("", "nombre"))
                    .hasMessage("El nombre es obligatorio.");

            assertThatThrownBy(() -> InputValidator.requerida("", "contraseña"))
                    .hasMessage("La contraseña es obligatoria.");
        }

        @Test
        @DisplayName("password: una cadena vacía no se escapaba, y por qué importa")
        void passwordVaciaNoSeEscapaba(){
            // Este es el test que documenta el bug más caro de la tanda.
            //
            // BCryptPasswordEncoder.hashear("") NO lanza nada: devuelve un hash
            // válido. Así que antes de V7, un alta de usuario con la contraseña
            // en blanco creaba la cuenta y la fila en la base. El problema
            // aparecía al intentar entrar: matches("", hash) devuelve false
            // SIEMPRE, así que esa persona quedaba bloqueada para siempre y sin
            // explicación. Por eso password() exige contenido antes de hashear.
            assertThatThrownBy(() -> InputValidator.password("", "contraseña", 8))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    //  Enteros: stock y cantidades
    // =========================================================================
    @Nested
    @DisplayName("Enteros (stock, cantidad)")
    class EnteroTest {

        @Test
        @DisplayName("acepta enteros normales")
        void aceptaEnteros(){
            assertThat(InputValidator.entero("0", "stock")).isZero();
            assertThat(InputValidator.entero("25", "stock")).isEqualTo(25);
        }

        @Test
        @DisplayName("rechaza decimales: no existe 1.6 de jabón")
        void rechazaDecimales(){
            assertThatThrownBy(() -> InputValidator.entero("1.6", "stock"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("decimales");
            //También con coma, que es lo que escribe un teclado en México
            assertThatThrownBy(() -> InputValidator.entero("1,6", "stock"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rechaza negativos")
        void rechazaNegativos(){
            assertThatThrownBy(() -> InputValidator.entero("-5", "stock"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negativo");
        }

        @Test
        @DisplayName("rechaza la notación científica 1e5 (el hueco clásico)")
        void rechazaNotacionCientifica(){
            assertThatThrownBy(() -> InputValidator.entero("1e5", "stock"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("científica");
            assertThatThrownBy(() -> InputValidator.entero("1E5", "stock"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rechaza letras")
        void rechazaLetras(){
            assertThatThrownBy(() -> InputValidator.entero("abc", "stock"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("números");
        }

        @Test
        @DisplayName("rechaza ceros a la izquierda, pero sí un 0 solo")
        void rechazaCerosIniciales(){
            assertThatThrownBy(() -> InputValidator.entero("007", "stock"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ceros");

            //Un 0 suelto SÍ es válido: el stock puede estar en cero
            assertThatCode(() -> InputValidator.entero("0", "stock")).doesNotThrowAnyException();
        }
    }

    // =========================================================================
    //  Precios: hasta 2 decimales
    // =========================================================================
    @Nested
    @DisplayName("Precio")
    class PrecioTest {

        @Test
        @DisplayName("acepta hasta 2 decimales")
        void aceptaDosDecimales(){
            assertThat(InputValidator.precio("150", "precio")).isEqualByComparingTo("150");
            assertThat(InputValidator.precio("150.5", "precio")).isEqualByComparingTo("150.5");
            assertThat(InputValidator.precio("150.50", "precio")).isEqualByComparingTo("150.50");
        }

        @Test
        @DisplayName("rechaza 3 decimales: la columna es numeric(38,2) y redondearía en silencio")
        void rechazaTresDecimales(){
            assertThatThrownBy(() -> InputValidator.precio("1.875", "precio"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2 decimales");
        }

        @Test
        @DisplayName("rechaza 000.2: el cero a la izquierda se pierde al parsear")
        void rechazaCerosIniciales(){
            assertThatThrownBy(() -> InputValidator.precio("000.2", "precio"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ceros");
        }

        @Test
        @DisplayName("sí acepta 0 y 0.50 (el 0 suelto es un valor legítimo)")
        void aceptaCeroYDecimas(){
            assertThat(InputValidator.precio("0", "precio")).isEqualByComparingTo("0");
            assertThat(InputValidator.precio("0.50", "precio")).isEqualByComparingTo("0.50");
        }

        @Test
        @DisplayName("rechaza negativos")
        void rechazaNegativos(){
            assertThatThrownBy(() -> InputValidator.precio("-10", "precio"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negativo");
        }

        @Test
        @DisplayName("rechaza notación científica")
        void rechazaNotacionCientifica(){
            assertThatThrownBy(() -> InputValidator.precio("1e5", "precio"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("rechaza letras y dos puntos")
        void rechazaBasura(){
            assertThatThrownBy(() -> InputValidator.precio("abc", "precio"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> InputValidator.precio("1.2.3", "precio"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("acepta la coma como separador decimal y la convierte a punto")
        void aceptaComaDecimal(){
            //Un teclado en México escribe 150,50. Spring lo parsearía mal, por eso
            //se normaliza la coma a punto ANTES de construir el BigDecimal.
            assertThat(InputValidator.precio("150,50", "precio"))
                    .isEqualByComparingTo(new BigDecimal("150.50"));
        }
    }
}
