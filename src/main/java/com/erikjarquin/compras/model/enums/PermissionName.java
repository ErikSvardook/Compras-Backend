package com.erikjarquin.compras.model.enums;

/**
 * Catálogo de permisos. Cada nombre se usa en entidades PermissionEntity
 * (columna name) y en los @PreAuthorize("hasAuthority('...')") de los
 * controllers: el string debe coincidir EXACTAMENTE con el nombre del enum.
 *
 * Organizados por módulo. Al agregar/quitar un valor (3 pasos, en orden):
 *  1. Agregar el valor aquí.
 *  2. Actualizar el CHECK {@code permissions_name_check} en una migración
 *     Flyway nueva. Es DDL puro: ni Hibernate con {@code ddl-auto=update} ni una
 *     entidad JPA pueden ampliarlo, y sin esto el INSERT del bootstrap revienta
 *     con "violates check constraint". Ver {@code V2__venta_confirmada.sql} como
 *     ejemplo del patrón DROP + ADD.
 *  3. PermissionBootstrap lo inserta en BD en el siguiente arranque (solo añade
 *     los que faltan; nunca borra ni toca los existentes).
 *  4. Asignarlo a los roles. OJO: RolePermissionBootstrap NO es self-healing:
 *     solo siembra permisos a los roles base cuando NO
 *     tienen ninguno. Si creas un permiso que ADMIN/CAJERO/ALMACENISTA deban
 *     tener y la BD ya está sembrada, asígnalo desde la pantalla de Roles
 *     (o inclúyelo en la migración Flyway, como se hizo en V2).
 *  5. Verificar que el frontend tenga la ruta/guard correspondiente si es nuevo.
 *
 * NOTA: los módulos de clientes y facturas se descartaron,
 * por eso ya  no existen VER_CLIENTES / VER_FACTURAS. La caja sigue activa para
 * el POS (VER_CAJA, ABRIR_CAJA, CERRAR_CAJA, CORTE_CAJA).
 */
public enum PermissionName {
    //Compras (módulo completo: ver, registrar y cancelar compras)
    VER_COMPRAS,
    CREAR_COMPRAS,
    CONFIRMAR_COMPRAS,
    CANCELAR_COMPRAS,

    //Proveedores (CRUD de proveedores que alimenta el módulo de compras)
    VER_PROVEEDORES,
    CREAR_PROVEEDORES,
    EDITAR_PROVEEDORES,
    ELIMINAR_PROVEEDORES,

    //Productos
    VER_PRODUCTOS,
    CREAR_PRODUCTOS,
    EDITAR_PRODUCTOS,
    ELIMINAR_PRODUCTOS,
    //Borrado lógico de productos: quitarlo del catálogo sin perder el histórico
    DESACTIVAR_PRODUCTOS,
    //Volver a poner activo un producto dado de baja
    ACTIVAR_PRODUCTOS,

    //Usuarios
    VER_USUARIOS,
    CREAR_USUARIOS,
    EDITAR_USUARIOS,
    ACTIVAR_USUARIOS,
    DESACTIVAR_USUARIOS,

    //Categorías
    VER_CATEGORIAS,
    CREAR_CATEGORIAS,
    EDITAR_CATEGORIAS,
    ELIMINAR_CATEGORIAS,

    //Ventas
    VER_VENTAS,
    CREAR_VENTAS,
    //Cerrar la venta en el POS: la congela para siempre (el cliente ya se
    //llevó el pedido). Es la acción que ejecuta cocina/mostrador.
    CONFIRMAR_VENTAS,
    //Anular una venta NO confirmada (error de caja). Devuelve el stock.
    //Prohibido sobre ventas confirmadas y sobre ventas con tarjeta
    //(esas necesitan una reversa real del pago, no un borrado).
    CANCELAR_VENTAS,

    //Caja
    VER_CAJA,
    ABRIR_CAJA,
    CERRAR_CAJA,
    CORTE_CAJA,

    //Roles
    VER_ROLES,
    CREAR_ROLES,
    ELIMINAR_ROLES,
    EDITAR_ROLES,

    //Reportes
    VER_REPORTES,

    //Pagos
    PROCESAR_PAGOS
}