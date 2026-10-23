# Compras-Backend

Backend REST para el módulo de **compras** del sistema de ventas (alimentación).
Spring Boot 4 · Java 21 · PostgreSQL · Spring Data JPA · Spring Security + JWT.

> Proyecto hermano de `Compras-Frontend-Local` (Angular) y `Compras-Frontend-Cliente` (Next.js) (Angular 21 standalone + SSR). El
> frontend consume esta API en `http://localhost:8081/api`.

## Stack

| Capa | Tecnología |
|---|---|
| Framework | Spring Boot 4.0.5 (Java 21, Maven) |
| Persistencia | PostgreSQL (JPA / Hibernate 7, `ddl-auto: update`) |
| Seguridad | Spring Security + JWT (jjwt 0.11.5, HS256, expiración 5h) |
| Correo | Gmail SMTP (recuperación / cambio de contraseña) |
| Terminal de pagos | `SIMULATED` (default) o `PHYSICAL` (Socket TCP) |
| Imágenes | Multipart, `./uploads` (máx 20MB) |
| Tests | JUnit + Mockito + H2 (`@DataJpaTest`, `@WebMvcTest`, MockMvc) |

## Módulos

- **Compras**: registro de mercancía comprada a proveedores. Al registrar una
  compra se **suma stock** al producto y se guarda su **costo real**; al
  cancelarla se revierte el stock (mín 0). Detalle por renglón (producto,
  cantidad, costo unitario y subtotal).
- **Proveedores**: CRUD completo (RFC único).
- **Productos**: CRUD multipart (imagen), búsqueda por código de barras/texto,
  filtro por categoría y **borrado lógico** (dar de baja / reactivar) para
  conservar histórico. Un producto con ventas/compras no se elimina físicamente (409).
- **Categorías**: CRUD para clasificar productos.
- **Ventas / POS**: ventas en efectivo o tarjeta (débito/crédito), historial.
- **Pagos**: estados PENDING → PROCESSING → APPROVED / REJECTED / REVERSED,
  reintentos (máx 3), idempotencia, reversas y monitor automático cada 5 min.
- **Caja**: apertura/cierre y corte de caja (resumen).
- **Reportes**: agregación **en SQL** con DTOs: tendencia (día/mes/año), top
  productos, métodos de pago, categorías, stock bajo y **márgenes**
  (costo real vs precio, para priorizar renegociación).
- **Usuarios / Roles / Permisos**: gestión de acceso con permisos finos y
  autorización por `@PreAuthorize("hasAuthority('...')")`.
- **Auth**: login, me/authorities, forgot/reset/change-password.

## Arquitectura

```
controller/ -> service/ (interfaz) -> service/impl/ -> repository/ -> model/entity/
   │                                      │
   │                    mapper/  <-> DTOs  ─┤
   │                                      │
   model/dto/  config/  exceptions/  jobs/
```

- **12 controllers · 68 endpoints** bajo `/api/local/...`.
- **37 permisos** en `PermissionName` (ver tabla de endpoints).
- Bootstraps de arranque (respetan `APP_SEED_BOOTSTRAPS`): `RoleBootstrap` →
  `AdminBootstrap` → `PermissionBootstrap` → `RolePermissionBootstrap`. Siembran
  **solo si la BD está vacía** (no self-healing). Admin inicial:
  `18jarquinsanchezerik1a@gmail.com` / `1234`.
- `PaymentMonitorJob` (`@Scheduled`, cada 5 min) resuelve pagos PENDING > 5 min.

## Prefijos de la API

La API está dividida en dos dominios independientes, separados por prefijo y por
tipo de usuario:

| Prefijo | Quién lo usa | Qué hay | Autenticación |
|---|---|---|---|
| `/api/local/**` | `Compras-Frontend-Local` (Angular, empleado) | POS y gestión: venta, cobro, caja, inventario, compras, proveedores, usuarios/roles/permisos, reportes | JWT de **empleado** + permiso (`@PreAuthorize`) |
| `/api/tienda/**` | `Compras-Frontend-Cliente` (Next.js, cliente) | Catálogo, carrito, checkout, auth de cliente, pedidos | **Nace en la Fase 3.** El catálogo será público de solo lectura |
| `/api/uploads/**` | Ambos | Imágenes de platillos/productos | **Público** (es contenido estático) |

`/api/uploads/**` NO se mueve bajo `/api/local` a propósito: el catálogo público
del storefront tendrá que mostrar las mismas imágenes.

## Endpoints principales

Módulo -> base bajo el prefijo `/api/local`:

| Módulo | Base | Notas |
|---|---|---|
| Auth | `/api/local/auth` | login, forgot/reset/change-password, me/authorities — público |
| Users | `/api/local/users` | CRUD + `PATCH /{id}/active` |
| Roles | `/api/local/roles` | CRUD |
| Permissions | `/api/local/permissions` | GET all |
| Products | `/api/local/products` | CRUD multipart, `/barcode/{bc}`, `/search?q=`, `?category=`, `/inactive`, `PATCH /{id}/deactivate`, `PATCH /{id}/active` |
| Categories | `/api/local/categories` | CRUD |
| Sales | `/api/local/sales` | POST (efectivo/tarjeta), GET historial, PATCH `/{id}/confirm`, PATCH `/{id}/cancel` |
| Payments | `/api/local/payments` | card, status/{tx}, retry/{id}, reverse/{id} |
| Purchases | `/api/local/purchases` | GET (todos, por proveedor, detalle), POST (nace PENDIENTE), PATCH `/{id}/confirm`, DELETE (cancelar) |
| Providers | `/api/local/providers` | CRUD |
| Cash | `/api/local/cash` | create, open (por número), close (exige cuadrar), summary, active, history, available, next-number, number/`{number}` |
| Reports | `/api/local/reports` | trend, top-products, payment-methods, categories, low-stock, **margins**, summary, **profit**, **cash/`{cashId}`** (`VER_REPORTES`) |
| Uploads | `/api/uploads/**` | **Público** — sirve imágenes de productos |

## Seguridad

- API stateless con **JWT** (HS256, expiración 5h). Secret desde `JWT_SECRET`.
- Rutas públicas: `OPTIONS /**`, `/api/local/auth/**` y `/api/uploads/**`.
- Autorización por permiso mediante `@PreAuthorize("hasAuthority('...')")`.
  Lista de permisos (34) en `model/enums/PermissionName.java`.
- CORS configurado globalmente desde `CORS_ALLOWED_ORIGINS` (lista separada por comas).
  El default de desarrollo ya incluye **ambos** frontends:
  `http://localhost:4200` (Angular) y `http://localhost:3000` (Next.js).
- Las contraseñas se guardan con BCrypt.

## Configuración y variables de entorno

Los valores sensibles se inyectan por variables de entorno (`${VAR}`). En local
viven en `src/main/resources/application-local.yaml` (**gitignored**, no se
sube); en producción, en las Variables del servicio (p. ej. Railway).

Obligatorias (sin default — la app no arranca sin ellas):

```
DB_USER, DB_PASSWORD, MAIL_USERNAME, MAIL_PASSWORD, JWT_SECRET,
ADMIN_EMAIL, ADMIN_PASSWORD, PAYMENT_MERCHANT_ID, PAYMENT_TERMINAL_ID,
PAYMENT_KEYSTORE_PASSWORD
```

Con default ajustable:

```
DB_HOST=localhost          DB_PORT=5432            DB_NAME=compras_db
PORT=8081                  CORS_ALLOWED_ORIGINS=http://localhost:4200
FRONTEND_URL=http://localhost:4200
UPLOAD_DIR=./uploads       UPLOAD_URL=http://localhost:8081/api/uploads
JPA_DDL_AUTO=update        SHOW_SQL=true
APP_SEED_BOOTSTRAPS=true
PAYMENT_TERMINAL_TYPE=SIMULATED
```

## Cómo ejecutar

```sh
.\mvnw.cmd compile           # compilar
.\mvnw.cmd test              # tests (requiere PostgreSQL local levantado)
.\mvnw.cmd spring-boot:run   # arrancar la API
```

Requisitos: JDK 21, PostgreSQL corriendo en `localhost:5432` con la BD
`ventas_db`, y `application-local.yaml` con las credenciales.

## Tests

**130 tests en verde**:

- Repositorios (`@DataJpaTest` + H2): `UserRepositoryTest`, `ProductRepositoryTest`,
  `ProviderRepositoryTest`, `ReportQueryTest`.
- Servicios (Mockito): `ProductImplTest`, `PurchaseImplTest`, `FileStorageServiceTest`.
- Controllers (`@WebMvcTest` + `@WithMockUser` + MockMvc): Auth, User, Role,
  Permission, Product, Category, Sale, Payment, Purchase, Provider, Cash y Report.
- Config: `RolePermissionBootstrapTest`, `ComprasApplicationTests`.

## Despliegue (Railway)

1. Railway → New Project → Deploy from GitHub repo (detecta el `Dockerfile`).
2. Configurar las Variables obligatorias y ajustables listadas arriba.
3. **Postgres**: crear un servicio Railway Postgres y apuntar `DB_HOST`/`DB_PORT`.
4. **Volumen persistente** montado en `/app/uploads` (UID 1001) para las imágenes.
5. **Healthcheck**: NO existe `GET /ping`; apúntalo a un endpoint público, p. ej.
   `GET /api/local/auth/me`.
6. HTTPS automático con el dominio `*.up.railway.app`; el frontend (Netlify)
   usa la URL `https://<backend>.up.railway.app/api`.

## Estructura del repo

```
Compras-Backend/
├── pom.xml
├── Dockerfile
├── mvnw.cmd
├── docs/                      # guías y glosario de estudio (PDF)
├── uploads/                   # imágenes de productos (no versionado)
└── src/
    ├── main/
    │   ├── java/com/erikjarquin/compras/
    │   │   ├── config/            # Security, JWT, bootstraps, terminal, web
    │   │   ├── controller/        # 12 REST controllers
    │   │   ├── service/           # interfaces + impl/
    │   │   ├── repository/        # Spring Data JPA
    │   │   ├── model/             # entity/, dto/, enums/
    │   │   ├── mapper/            # entity ↔ DTO
    │   │   ├── exceptions/        # por módulo + handler global
    │   │   ├── jobs/              # monitoreo de pagos
    │   │   └── ComprasApplication.java
    │   └── resources/             # application.yaml (sin secretos)
    └── test/java/com/erikjarquin/compras/
```

## Notas

- `application-local.yaml` y `uploads/` no se versionan en git (ver `.gitignore`).
- El esquema lo crea **Flyway** (`db/migration/V1__init.sql` = base,
  `V2__venta_confirmada.sql` = ciclo de vida de la venta,
  `V3__compras_confirmadas_costo_y_cajas.sql` = compras confirmadas + costo
  congelado + cajas numeradas + precio de venta + cuadre de caja), no Hibernate.
  Al agregar permisos nuevos al enum
  hay que mantener sincronizado el CHECK `permissions_name_check` de las
  migraciones.
- `ComprasApplicationTests` (`@SpringBootTest`) es el único test que NO corre en
  la suite: exige una BD real accesible. Usa
  `mvn test -Dtest='!ComprasApplicationTests'` para los 234 tests de lógica.
- Documentación técnica en [`docs/`](./docs): `01-Fase1-Paquete-Compras-y-Prefijo-Api-Local.pdf`,
  `02-Ciclo-Ventas-Integridad-Stock-Compras.pdf`,
  `03-Confirmar-Compras-Utilidad-Cajas-Numeradas.pdf` y
  `04-Precio-Venta-Caja-Cuadra-Validaciones.pdf`, con el código real de los archivos.