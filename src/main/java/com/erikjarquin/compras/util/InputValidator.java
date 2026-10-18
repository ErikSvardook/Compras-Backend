package com.erikjarquin.compras.util;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Validaciones de entrada compartidas por los módulos de productos, proveedores y
 * compras.
 *
 * Por qué una clase aparte y no repetir el código en cada servicio. Las
 * reglas nacieron de errores reales de captura ("1.875" en un stock, letras en un
 * precio, "CAJA 1" y "caja 1" contadas como dos cajas). Si cada servicio tiene
 * su versión, un día uno acepta y el otro no, y el que acepta es el que rompe los
 * datos. Aquí hay una sola definición de "qué es un barcode válido".
 *
 * Por qué se valida el TEXTO y no el número ya parseado. Es la clave de
 * este diseño. Al convertir a {@code BigDecimal}, {@code "000.2"} se vuelve
 * {@code 0.2} y {@code "1.875"} se vuelve {@code 1.875}: si validáramos después
 * de parsear, el cero a la izquierda ya se perdió y no se puede detectar. Por eso
 * las reglas mira primero la cadena, y solo después convierte.
 *
 * Los errores se lanzan como {@link IllegalArgumentException}, que
 * {@code GlobalExceptionHandler} traduce a 400. Es el mismo patrón que ya
 * usaba {@code PurchaseImpl.validateRequest}, y se prefirió aBean Validation
 * porque los mensajes pueden explicar por qué ("el stock no admite
 * decimales: no existe 1.6 de jabón") en vez de un "validation failed" genérico.
 */
public final class InputValidator {

    // ===== Límites de longitud =====

    /** SKU: corto y legible. 50 es holgado para cualquier convención interna. */
    public static final int MAX_SKU_LENGTH = 50;

    /**
     * RFC mexicano: 13 caracteres para persona física (4 letras + 6 de fecha +
     * 3 de homoclave) y 12 para persona moral. 13 es el máximo real.
     */
    public static final int MAX_RFC_LENGTH = 13;

    /**
     * Código de barras: hasta 20 dígitos. El EAN-13 tiene 13 y el UPC-A 12, así
     * que 20 deja margen para códigos internos de tienda larga sin quedarse
     * corto.
     */
    public static final int MAX_BARCODE_LENGTH = 20;

    /** Número de caja: igual que el VARCHAR(50) de la columna. */
    public static final int MAX_CASH_NUMBER_LENGTH = 50;

    // ===== Patrones =====

    /**
     * SKU: letras, dígitos y los separadores de uso común ({@code - _ . /}).
     *
     * Se permiten letras porque un SKU no es un número: "CHOC-500" es un SKU
     * perfectamente válido. Por eso el SKU y el barcode tienen reglas distintas.
     */
    private static final Pattern SKU_PATTERN = Pattern.compile("^[A-Z0-9\\-_./]+$");

    /** RFC: solo letras y dígitos. Sin guiones ni espacios. */
    private static final Pattern RFC_PATTERN = Pattern.compile("^[A-Z0-9]+$");

    /**
     * Barcode: solo dígitos, y se guarda como texto.
     *
     * Decisión importante: el código de barras es un identificador, no
     * una cantidad. Si se tratara como número, {@code 0001234567895} se guardaría
     * como {@code 1234567895} y el lector dejaría de funcionar, porque los ceros
     * a la izquierda son parte del código. Por eso la columna es texto y aquí solo
     * se comprueba que sean dígitos.
     */
    private static final Pattern BARCODE_PATTERN = Pattern.compile("^[0-9]+$");

    // =========================================================================
    //  Códigos
    // =========================================================================

    /**
     * Normaliza un código: recorta y pasa a MAYÚSCULAS.
     *
     * Las mayúsculas son solo para <b>campos de código (SKU, RFC, barcode,
     * número de caja, nombre de proveedor y categoría). No se aplican a los
     * nombres de producto ni a las descripciones: "Tacos de chicharrón" en
     * mayúsculas se lee peor y la búsqueda ya es insensible a mayúsculas.
     *
     * Además de la estética, la mayúscula fija evita duplicados: "cafe" y
     * "CAFE" son el mismo producto escrito de dos formas, y sin normalizar el
     * UNIQUE del SKU dejaría pasar dos productos que en realidad son uno.
     */
    public static String normalizarCodigo(String valor){
        if(valor == null){
            return null;
        }
        return valor.trim().toUpperCase();
    }

    /**
     * Valida el SKU. Obligatorio.
     *
     * Cambia una decisión anterior. El SKU era
     * opcional: si venía vacío devolvía {@code null} y la columna lo
     * admitía. Se cambió a obligatorio por dos razones:
     *
     *   Un producto sin SKU no se puede localizar ni integrar. El SKU es la
     *       clave con la que el negocio habla del producto; si falta, el
     *       inventario tiene un agujero.
     *   En PostgreSQL un {@code UNIQUE} admite varios NULL: con el
     *       SKU opcional se acumulaban productos sin SKU sin que nada lo
     *       indicara, y un producto nuevo heredaba esa ambigüedad.
     *
     * @throws IllegalArgumentException si viene vacío, si trae caracteres no
     *                                  permitidos o si excede
     *                                  {@link #MAX_SKU_LENGTH}
     */
    public static String sku(String valor){
        String codigo = requerido(normalizarCodigo(valor), "SKU");

        if(codigo.length() > MAX_SKU_LENGTH){
            throw new IllegalArgumentException(
                "El SKU no puede tener más de " + MAX_SKU_LENGTH + " caracteres (lleva "
                + codigo.length() + ")");
        }

        if(!SKU_PATTERN.matcher(codigo).matches()){
            throw new IllegalArgumentException(
                "El SKU solo admite letras, números y los separadores - _ . / . "
                + "El valor \"" + valor + "\" tiene caracteres no permitidos.");
        }

        return codigo;
    }

    /**
     * Valida el RFC. Opcional, pero si viene debe tener solo letras y dígitos.
     *
     * Se normaliza a mayúsculas porque un RFC se escribe con letras mayúsculas
     * y compararlo en minúsculas crearía duplicados falsos.
     */
    public static String rfc(String valor){
        String codigo = normalizarCodigo(valor);

        if(codigo == null || codigo.isEmpty()){
            return null;
        }

        if(codigo.length() > MAX_RFC_LENGTH){
            throw new IllegalArgumentException(
                "El RFC no puede tener más de " + MAX_RFC_LENGTH + " caracteres (lleva "
                + codigo.length() + "). Un RFC mexicano tiene 12 o 13.");
        }

        if(!RFC_PATTERN.matcher(codigo).matches()){
            throw new IllegalArgumentException(
                "El RFC solo admite letras y números, sin guiones ni espacios.");
        }

        return codigo;
    }

    /**
     * Valida el código de barras. Obligatorio.
     *
     * Opcional. El motivo es operativo: en el POS el producto se cobra escaneando su código, así que un
     * producto sin código es un producto que el cajero no puede cobrar sin
     * buscarlo a mano. Y como en el SKU, un {@code UNIQUE} admite varios NULL,
     * de modo que "sin código" se acumulaba en silencio.
     *
     * Solo dígitos, máximo {@link #MAX_BARCODE_LENGTH}, y se guarda como
     * texto: es un identificador, y por eso no se convierte a número
     * (perdería los ceros a la izquierda y rompería el lector).
     *
     * @throws IllegalArgumentException si viene vacío, si trae letras o si
     *                                  excede {@link #MAX_BARCODE_LENGTH}
     */
    public static String barcode(String valor){
        String codigo = requerido(normalizarCodigo(valor), "código de barras");

        if(!BARCODE_PATTERN.matcher(codigo).matches()){
            throw new IllegalArgumentException(
                "El código de barras solo admite números.");
        }

        if(codigo.length() > MAX_BARCODE_LENGTH){
            throw new IllegalArgumentException(
                "El código de barras no puede tener más de " + MAX_BARCODE_LENGTH + " dígitos.");
        }

        return codigo;
    }

    // =========================================================================
    //  Números
    // =========================================================================

    /**
     * Valida un entero no negativo (stock, cantidad).
     *
     * Tres reglas que vienen de errores de captura reales:
     * 
     *   Nada de decimales. No existe 1.6 de jabón: se mide en piezas o
     *       en kilos, no en 1.6 unidades. El campo de stock es entero; para peso
     *       hay que agregar el kilo como unidad, no inventar el decimal.
     *   Nada de negativos. Un stock negativo es una contradicción
     *       (no se puede tener menos de cero cosas), y si aparece es un error de
     *       captura o una venta que no se descontó.
     *   Nada de notación científica. {@code 1e5} es un atajo para
     *       escribir 100000: el {@code <input type="number">} del navegador lo
     *       acepta en muchos casos y convertiría un error de dedo en 100,000
     *       unidades.
     * 
     */
    public static Integer entero(String valor, String campo){
        String limpio = limpiar(valor);

        if(limpio.isEmpty()){
            return null;
        }

        if(contieneExponente(limpio)){
            throw new IllegalArgumentException(
                "El " + campo + " solo admite números enteros, no notación científica "
                + "(por ejemplo 1e5).");
        }

        //Punto o coma decimal: es un decimal, y aquí no se admiten.
        if(limpio.indexOf('.') >= 0 || limpio.indexOf(',') >= 0){
            throw new IllegalArgumentException(
                "El " + campo + " no admite decimales: solo números enteros.");
        }

        //El menos solo puede ser un guion suelto; si aparece en medio es basura.
        if(limpio.indexOf('-') >= 0){
            throw new IllegalArgumentException(
                "El " + campo + " no puede ser negativo.");
        }

        if(!limpio.matches("[0-9]+")){
            throw new IllegalArgumentException(
                "El " + campo + " solo admite números.");
        }

        validarSinCerosIniciales(limpio, campo);

        try{
            return Integer.valueOf(limpio);
        }catch(NumberFormatException e){
            throw new IllegalArgumentException(
                "El " + campo + " es un número demasiado grande: el máximo es "
                + Integer.MAX_VALUE + ".");
        }
    }

    /**
     * Valida un precio: no negativo y con máximo 2 decimales.
     *
     * El límite de 2 decimales no es un capricho: la columna es
     * {@code numeric(38,2)} en PostgreSQL, así que "1.875" se guardaría
     * redondeado a 1.88 en silencio. El usuario escribiría 1.875, el
     * sistema cobraría 1.88 y nadie vería el redondeo. Rechazarlo es más honesto.
     *
     * Y se rechazan los ceros a la izquierda ("000.2"), porque un usuario que
     * escribe eso casi siempre se equivocó de dedo y el dato guardado no es el que
     * quiso escribir.
     */
    public static BigDecimal precio(String valor, String campo){
        return decimal(valor, campo, 2);
    }

    /**
     * Valida un decimal no negativo con un máximo de decimales dado.
     *
     * @param maxDecimales cuántos decimales se admiten a la derecha del punto
     */
    public static BigDecimal decimal(String valor, String campo, int maxDecimales){
        String limpio = limpiar(valor);

        if(limpio.isEmpty()){
            return null;
        }

        if(contieneExponente(limpio)){
            throw new IllegalArgumentException(
                "El " + campo + " no admite notación científica (por ejemplo 1e5).");
        }

        if(limpio.indexOf('-') >= 0){
            throw new IllegalArgumentException(
                "El " + campo + " no puede ser negativo.");
        }

        //Se separa en parte entera y decimal por el punto. Si hay más de un punto
        //es basura ("1.2.3") y la regex de abajo lo rechaza.
        int punto = limpio.indexOf('.');
        String entero = punto < 0 ? limpio : limpio.substring(0, punto);
        String decimal = punto < 0 ? "" : limpio.substring(punto + 1);

        if(entero.isEmpty() || !entero.matches("[0-9]+")){
            throw new IllegalArgumentException(
                "El " + campo + " solo admite números.");
        }

        if(!decimal.isEmpty() && !decimal.matches("[0-9]+")){
            throw new IllegalArgumentException(
                "El " + campo + " solo admite números, con un punto decimal.");
        }

        if(decimal.length() > maxDecimales){
            throw new IllegalArgumentException(
                "El " + campo + " admite máximo " + maxDecimales + " decimales "
                + "(por ejemplo 150.50). Lleva " + decimal.length() + ".");
        }

        validarSinCerosIniciales(entero, campo);

        try{
            return new BigDecimal(limpio);
        }catch(NumberFormatException e){
            throw new IllegalArgumentException("El " + campo + " no es un número válido.");
        }
    }

    /**
     * Email de forma básica, 120 caracteres es el tope práctico de una columna de correo
     */
    public static String email(String valor){
        String correo = valor == null ? null : valor.trim().toLowerCase();

        if (correo == null || correo.isEmpty()) return null;

        if (correo.length() > 120) {
            throw new IllegalArgumentException("El correo no puede tener más de 120 caracteres.");
        }

        if (!correo.matches("^[^\\s@]+@[^\\s@]+\\.[a-z]{2,}$")) {
            throw new IllegalArgumentException("El correo no es válido");
        }

        return correo;
    }

    /**
     * Texto libre con límite, sin saltos de línea.
     * Para nombre de categoría y de rol: un "Bebidas\nRicas" descoloca la fila
     *
     * Este NO exige que el texto exista. Si viene vacío devuelve
     * {@code null}, y quien lo use TIENE que comprobarlo aparte. Es la razón de
     * que existiera {@link #requerido}: tres servicios (categoría, rol y
     * proveedor) acaban de escribir su propio {@code if (x == null) throw}, y
     * el de usuario se olvidó, que es exactamente el bug que se corrigió en V7.
     */
   public static String texto(String valor, String campo, int max){
    String limpio = valor == null ? null : valor.trim();

    if (limpio == null || limpio.isEmpty()) return null;
    
    if (limpio.length() > max) {
        throw new IllegalArgumentException("El " + campo + " no puede tener más de " + max + " caracteres (llevas " + limpio.length() + ").");
    }

    if (limpio.matches(".*[\\n\\r\\t].*")) {
        throw new IllegalArgumentException("El " + campo + " no puede tener saltos de línea ni tabulaciones.");
    }

    return limpio.toUpperCase();
   }

    // =========================================================================
    // Obligatoriedad
    //
    //  Los métodos de arriba validan el FORMATO de algo que ya existe. Faltaba
    //  el que responde "¿viene algo?". Sin él, cada servicio se inventaba su
    //  propio chequeo, y el resultado era desigual: categoría, rol y proveedor
    //  rechazaban el nombre vacío, pero el de usuario se lo pasaba a la base de
    //  datos, que respondía con un 409 diciendo "el dato ya existe".
    // =========================================================================

    /**
     * Exige que el campo venga con contenido y lo devuelve recortado.
     *
     * Un "campo obligatorio" que se cumple solo con {@code != null} no sirve:
     * el formulario manda {@code ""} o cinco espacios cuando el usuario lo
     * dejó en blanco, y eso pasa el chequeo. Por eso la comparación es
     * contra {@code isBlank()}, que cubre las tres formas de "vacío".
     *
     * El mensaje dice QUÉ campo está mal y no "validation failed", que es la
     * razón de que esta clase exista en vez de anotaciones de Bean Validation
     * (ver el comentario de la cabecera de la clase).
     *
     * Para campos femeninos usa {@link #requerida}, que dice "La
     * contraseña es obligatoria" en vez de "El contraseña es obligatorio".
     *
     * @param valor  lo que viene del cliente
     * @param campo  cómo se le llama al campo en la respuesta, p. ej. "nombre de
     *               la categoría" (con artículo, para que el mensaje se lea)
     * @return el valor recortado, listo para guardarse
     * @throws IllegalArgumentException si viene {@code null}, vacío o solo
     *                                  espacios
     */
    public static String requerido(String valor, String campo){
        return exigir(valor, campo, "El", "obligatorio");
    }

    /**
     * Igual que {@link #requerido}, pero para campos femeninos.
     *
     * Existe como método aparte y no como un parámetro {@code boolean} porque
     * en la llamada {@link #requerida}(p, "contraseña") se lee solo, mientras
     * que {@code requerido(p, "contraseña", true)} deja adivinar qué significa
     * ese {@code true}. El mensaje es lo que ve el usuario final, y "La
     * contraseña es obligatoria" es la diferencia entre que parezca un sistema
     * cuidado y uno hecho a prisas.
     */
    public static String requerida(String valor, String campo){
        return exigir(valor, campo, "La", "obligatoria");
    }

    /** Núcleo de los dos de arriba: el único que decide si falta. */
    private static String exigir(String valor, String campo, String articulo, String terminacion){
        String limpio = valor == null ? null : valor.trim();

        if (limpio == null || limpio.isEmpty()) {
            throw new IllegalArgumentException(articulo + " " + campo + " es " + terminacion + ".");
        }

        return limpio;
    }

    /**
     * Exige que la contraseña venga y tenga una longitud mínima.
     *
     * Por qué esto importa más de lo que parece. Sin este método, una
     * contraseña vacía sí se guardaba: {@code BCryptPasswordEncoder}
     * hashea la cadena vacía sin quejarse, así que el usuario quedaba creado y
     * con una fila en la base de datos. El problema aparecía después, al
     * iniciar sesión: {@code matches} devuelve {@code false} ante una contraseña
     * vacía, siempre, así que esa persona quedaba bloqueada para siempre
     * sin que nadie supiera por qué. Un dato basura en la base y un soporte que
     * no tiene explicación.
     *
     * La longitud mínima es de 8 porque es la que ya exigen los formularios
     * del frontend (login, reset y perfil). Si algún día se cambia ahí, hay que
     * cambiarla aquí también: el backend es el que de verdad protege el dato, y
     * si estas dos reglas se separan alguien siempre se olvida de una.
     *
     * @param valor  la contraseña en texto plano (se hashea después)
     * @param campo  nombre del campo para el mensaje
     * @param min    longitud mínima
     * @return la contraseña tal cual, para que el llamador la hashee
     * @throws IllegalArgumentException si viene vacía o es demasiado corta
     */
    public static String password(String valor, String campo, int min){
        String limpia = requerida(valor, campo);

        if (limpia.length() < min) {
            throw new IllegalArgumentException(
                "La " + campo + " debe tener al menos " + min + " caracteres.");
        }

        return limpia;
    }

   /** 
    * Teléfono: dígitos, espacios, guiones, paréntesis y más.
   */
  public static String telefono(String valor){
    String tel = valor == null ? null : valor.trim();

    if (tel == null || tel.isEmpty()) return null;

    if (!tel.matches("^[0-9\\s()+-]+$")) {
        throw new IllegalArgumentException("El teléfono solo admite números, espacios, guions y paréntesis");
    }

    return tel;
  }

    /**
     * Rechaza ceros a la izquierda: "000.2" y "007" no son números válidos de
     * captura, son errores de dedo.
     *
     * Excepción deliberada: un solo "0" sí se acepta, porque "0" es un valor
     * legítimo (stock 0, precio 0) y no un cero de relleno.
     */
    private static void validarSinCerosIniciales(String entero, String campo){
        if(entero.length() > 1 && entero.charAt(0) == '0'){
            throw new IllegalArgumentException(
                "El " + campo + " no puede empezar con ceros: escribe "
                + entero.replaceFirst("^0+(?=.)", "") + " en vez de " + entero + ".");
        }
    }

    /** Detecta 'e' o 'E': la notación científica que rompe las validaciones. */
    private static boolean contieneExponente(String valor){
        return valor.indexOf('e') >= 0 || valor.indexOf('E') >= 0;
    }

    /** Quita espacios (incluidos los que pega el pegado) y el separador de millar. */
    private static String limpiar(String valor){
        if(valor == null){
            return "";
        }
        return valor.trim().replaceAll("\\s", "").replace(",", ".");
    }

    /** Constructor privado: es una clase de utilidades, no se instancia. */
    private InputValidator(){
    }
}
