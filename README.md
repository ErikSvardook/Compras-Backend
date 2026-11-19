# Compras-Backend

API REST del sistema de punto de venta: inventario, compras a proveedores, cobros, caja y reportes.
Spring Boot 4.0.5, Java 21, PostgreSQL, Spring Data JPA, Spring Security con JWT y Flyway.

Da servicio al frontend POS `Compras-Frontend-Local` (Angular 21 standalone) en `http://localhost:4200`.
La API corre en `http://localhost:8081/api/local` y las imágenes se sirven en `/api/uploads/**` (público).

## Stack

| Capa | Tecnología |
|---|---|
| Framework | Spring Boot 4.0.5 (Java 21, Maven) |
| Persistencia | PostgreSQL con Hibernate 7, esquema gestionado por Flyway (V1-V7), `JPA_DDL_AUTO=validate` |
| Seguridad | Spring Security con JWT (jjwt, HS256, 5 horas) y `@PreAuthorize` |
| Correo | SMTP de Gmail o Resend por HTTPS, según `MAIL_PROVIDER` |
| Terminal de pagos | `SIMULATED` por defecto o `PHYSICAL` (Socket TCP) |
| Imágenes | Multipart en `./uploads` (máximo 20 MB), servidas en `/api/uploads/**` |
| Tests | JUnit 5, Mockito y H2 (`@DataJpaTest`, `@WebMvcTest`, MockMvc) |

## Módulos

- **Productos y categorías**: CRUD multipart, búsqueda por barcode o texto, filtro por categoría y borrado lógico (dar de baja y reactivar). Un producto con histórico de ventas o compras no se borra (409).
- **Compras y proveedores**: la compra nace PENDIENTE. Al confirmar se suma stock y se guardan el costo y el precio de venta del renglón. Cancelar solo borra compras pendientes. Los proveedores tienen RFC único.
- **Ventas y POS**: efectivo o tarjeta de débito o crédito, clave de idempotencia para evitar cobros duplicados, confirmación que congela la venta y anulación que devuelve el stock (solo en efectivo y mientras no esté confirmada). Cada venta registra quién la hizo y el costo congelado de cada renglón.
- **Pagos**: estados PENDING, PROCESSING, APPROVED, REJECTED y REVERSED. Incluye reintentos (máximo 3), idempotencia, reversas y un monitor que revisa los pagos PENDING cada 5 minutos.
- **Caja**: cajas numeradas y reutilizables. Apertura y cierre, cuadre obligatorio (si hay diferencia se exige un motivo), historial por turno y por caja.
- **Reportes**: agregación en SQL de tendencia, productos más vendidos, métodos de pago, categorías, stock bajo, márgenes, resumen, utilidad y cortes por turno o caja.
- **Usuarios, roles, permisos y Auth**: 37 permisos, contraseñas BCrypt, login y recuperación o cambio de contraseña.

## Arquitectura

- Capas principales: `controller/`, `service/` (interfaz), `service/impl/`, `repository/` y `model/entity/`.
- Apoyo: `mapper/`, DTOs, `config/`, `exceptions/` y `jobs/`.
- 12 controllers y 73 endpoints bajo `/api/local/**`, más `/api/uploads/**` (público).
- Paquete raíz `com.erikjarquin.compras`. Los bootstraps de seed (`APP_SEED_BOOTSTRAPS`) son `RoleBootstrap`, `AdminBootstrap`, `PermissionBootstrap` y `RolePermissionBootstrap`. El administrador inicial se crea desde `ADMIN_EMAIL` y `ADMIN_PASSWORD`.

## API

Bases bajo `/api/local`:

| Módulo | Base | Notas |
|---|---|---|
| Auth | `/auth` | login, forgot-password, reset-password y change-password (público) |
| Users | `/users` | CRUD y `PATCH /{id}/active` |
| Roles y Permissions | `/roles`, `/permissions` | CRUD y listado |
| Products | `/products` | CRUD multipart, `/barcode/{bc}`, `/search`, `/inactive`, `PATCH /{id}/deactivate` y `PATCH /{id}/active` |
| Categories | `/categories` | CRUD |
| Sales | `/sales` | POST, GET, `PATCH /{id}/confirm` y `PATCH /{id}/cancel` |
| Payments | `/payments` | `/card`, `/status/{tx}`, `/retry/{id}` y `/reverse/{id}` |
| Purchases | `/purchases` | GET (todos, por id y por proveedor), POST, `PATCH /{id}/confirm` y DELETE |
| Providers | `/providers` | CRUD |
| Cash | `/cash` | `/boxes`, `/open`, `/close`, `/summary`, `/active`, `/history`, `/next-number` y `/number/{number}` |
| Reports | `/reports` | trend, top-products, payment-methods, categories, low-stock, margins, summary, profit, cash y cash-box |

## Seguridad y configuración

- JWT HS256 con vigencia de 5 horas y secreto en `JWT_SECRET`. Las rutas públicas son `OPTIONS /**`, `/api/local/auth/**` y `/api/uploads/**`.
- CORS configurado desde `CORS_ALLOWED_ORIGINS` (lista separada por comas). Los demás endpoints exigen token y permiso.
- Los valores sensibles se inyectan por entorno: en local, desde `src/main/resources/application-local.yaml` (ignorado por git); en producción, desde las variables del servicio.

Obligatorias, sin valor por defecto: `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `PAYMENT_MERCHANT_ID`, `PAYMENT_TERMINAL_ID` y `PAYMENT_KEYSTORE_PASSWORD`.

Con valor por defecto: `DB_HOST=localhost`, `DB_PORT=5432`, `DB_NAME=compras_db`, `PORT=8081`, `JPA_DDL_AUTO=validate`, `SHOW_SQL=false`, `APP_SEED_BOOTSTRAPS=true`, `CORS_ALLOWED_ORIGINS`, `FRONTEND_URL`, `UPLOAD_DIR=./uploads`, `UPLOAD_URL`, `PAYMENT_TERMINAL_TYPE=SIMULATED`, `MAIL_PROVIDER=smtp` (también admite `resend`, con `RESEND_API_KEY` y `RESEND_FROM`), `MAIL_HOST` y `MAIL_PORT`.

## Ejecutar y probar

```sh
mvn compile
mvn test -Dtest='!ComprasApplicationTests'
mvn spring-boot:run
```

Hay 292 tests en verde, sin fallos ni errores, con el comando de tests anterior.
`ComprasApplicationTests` es el único test `@SpringBootTest` y se excluye porque necesita una base de datos real accesible.
Los requisitos son JDK 21, PostgreSQL con la base `compras_db` y el archivo `application-local.yaml` con las credenciales de desarrollo.

## Despliegue en Railway

1. Desplegar desde GitHub, que detecta el `Dockerfile`, y configurar las variables indicadas arriba.
2. Crear el servicio PostgreSQL propio y un volumen persistente montado en `/app/uploads` con `UPLOAD_DIR`.
3. Ajustar `CORS_ALLOWED_ORIGINS` y `FRONTEND_URL` al dominio real del frontend.
4. No existe `GET /ping` ni `/me`. El healthcheck puede ser TCP al puerto o una petición `OPTIONS` a `/api/local/auth/login`, que es pública.
5. El dominio `*.up.railway.app` incluye HTTPS automático.

## Notas

- `application-local.yaml` y `uploads/` no se versionan en git (ver `.gitignore`).
- Si se agrega un permiso a `PermissionName`, hay que actualizar el CHECK `permissions_name_check` en una migración Flyway nueva y mantener el enum sincronizado con las migraciones.
- La evolución futura del sistema, incluida la venta online que sigue pendiente, está en `docs/PLAN.md`.
