# Especificación Técnica — Proyecto E-commerce (Portafolio)

## 1. Resumen ejecutivo

Este documento especifica el desarrollo de una plataforma de comercio electrónico (e-commerce) full-stack, concebida como proyecto de portafolio técnico. A diferencia de un proyecto de negocio real, el objetivo primario es pedagógico: servir como vehículo práctico para adquirir y demostrar competencias en **arquitectura de microservicios, containerización con Docker y despliegue en infraestructura cloud (AWS)**.

El dominio de e-commerce se eligió porque, a diferencia de un CRUD o un To-Do list, presenta problemas reales de ingeniería: persistencia transaccional, integridad de datos entre entidades relacionadas, integración con servicios de pago externos, manejo de estado en el cliente, y suficiente complejidad de dominio como para justificar una eventual descomposición en microservicios.

Este documento está dirigido a cualquier desarrollador (incluyendo el propio autor en el futuro) que necesite entender, retomar o extender el proyecto sin contexto previo: define el alcance, las decisiones de arquitectura y stack, el modelo de datos, y el plan de evolución por fases.

> **v1.1** — se incorporó control de concurrencia (`version`, `idempotency_key`), se unificó el carrito en PostgreSQL, se definió el flujo de reserva de stock (*inventory hold*) en el checkout, y se adelantaron Swagger/testing a la Fase 1.

## 2. Objetivos

### 2.1 Objetivos funcionales

- Construir una plataforma de e-commerce funcional de extremo a extremo: catálogo, carrito, checkout con pago real (modo test), gestión de órdenes y panel administrativo.
- Modelar correctamente un dominio con relaciones no triviales (usuarios, productos con variantes, órdenes, pagos), aplicando integridad transaccional.

### 2.2 Objetivos de aprendizaje técnico (prioridad real del proyecto)

- **Docker**: multi-stage builds, redes entre contenedores, volúmenes, `docker-compose` para orquestar un entorno local multi-servicio.
- **Arquitectura de microservicios**: descomposición de un dominio, comunicación síncrona (REST) y asíncrona (mensajería), patrones de resiliencia (circuit breaker), consistencia eventual (Saga).
- **AWS**: despliegue progresivo (EC2 → ECS/Fargate → opcionalmente EKS), uso de RDS, S3, SES, ECR, Secrets Manager, CloudWatch.
- **CI/CD**: pipelines automatizados con GitHub Actions para build, test y despliegue diferenciado por entorno.
- **Buenas prácticas de backend**: diseño por capas, migraciones versionadas (Flyway/Liquibase), manejo de configuración por perfil (Spring Profiles).

### 2.3 Objetivo no perseguido

Este proyecto **no** busca ser un producto viable de negocio ni competir en features con plataformas reales (Shopify, etc.). El criterio de éxito es la calidad de la ingeniería demostrada, no la completitud comercial.

## 3. Alcance del MVP

### 3.1 Incluido en el MVP (v1)

- Registro, login y autenticación con JWT (roles CUSTOMER / ADMIN)
- Catálogo de productos con categorías, variantes (talla/color) e imágenes
- Búsqueda y filtros básicos (categoría, precio)
- Carrito de compras persistente (usuario logueado y modo invitado con merge al loguear)
- Checkout con integración de pago real en modo sandbox (Stripe o MercadoPago)
- Gestión de órdenes: creación, estados (PENDING, PAID, SHIPPED, DELIVERED, CANCELLED), historial del usuario
- Reserva de stock al iniciar checkout y descuento definitivo al confirmarse el pago (evita overselling)
- Panel de administración básico: CRUD de productos/categorías, listado y cambio de estado de órdenes
- Notificación por email de confirmación de orden (vía Mailtrap en desarrollo)
- Entorno local completo con Docker Compose

### 3.2 Fuera del MVP (fases posteriores)

- Reviews y ratings de productos
- Wishlist
- Descomposición en microservicios
- Entornos QA y producción desplegados en AWS
- CI/CD automatizado
- Dashboard de métricas para el admin
- Múltiples direcciones de envío por usuario

### 3.3 Criterio de corte

Una feature entra al MVP solo si es indispensable para completar el flujo compra-pago-entrega de principio a fin. Todo lo demás se documenta como backlog de fase 2+ y no bloquea el cierre de la v1.

## 4. Stack tecnológico

| Capa | Tecnología | Justificación |
|---|---|---|
| Frontend | Next.js (React) | SSR/SSG para SEO de páginas de producto |
| Validación de formularios | Zod | Validación de esquemas type-safe, integrable con formularios y con las respuestas de la API |
| Estado de cliente | Zustand | Estado de UI ligero (carrito local, filtros) sin el boilerplate de Redux |
| Data fetching / caching | TanStack Query | Manejo de estado de servidor, caché, revalidación |
| Backend | Spring Boot (Java) | Framework maduro para APIs REST robustas, con seguridad y ORM de primer nivel |
| ORM | Spring Data JPA / Hibernate | Mapeo objeto-relacional, manejo de transacciones |
| Base de datos | PostgreSQL | Relacional; necesaria por la integridad referencial y transacciones ACID que exige el dominio |
| Migraciones | Flyway | Versionado del esquema de base de datos, reproducible entre entornos |
| Cache / sesiones | Redis | Cache de catálogo, rate limiting y refresh tokens (el carrito vive siempre en PostgreSQL, incluido el de invitado) |
| Storage de objetos | MinIO (local) / Amazon S3 (QA-prod) | Almacenamiento de imágenes de producto, compatible con el SDK de S3 en ambos casos |
| Envío de emails | Mailtrap (local/QA) / Amazon SES (prod) | Captura de emails de prueba sin riesgo de envío accidental; SES para envío real |
| Pagos | Stripe (o MercadoPago) | Pasarela de pago con modo sandbox y manejo de webhooks |
| Contenerización | Docker + Docker Compose | Empaquetado reproducible del entorno local |
| Cloud | AWS (EC2/ECS, RDS, S3, SES, ECR, Secrets Manager, CloudWatch) | Plataforma cloud objetivo de aprendizaje del proyecto |
| CI/CD | GitHub Actions | Automatización de build, test y despliegue por entorno |
| Mensajería (fase 2) | RabbitMQ o Kafka | Comunicación asíncrona entre microservicios |

## 5. Arquitectura

### 5.1 Fase 1 — Monolito modular

El proyecto arranca como un **monolito modular** en Spring Boot: un único desplegable, pero organizado internamente en paquetes por dominio de negocio, no por capa técnica genérica. Esto facilita una eventual extracción a microservicios sin reescribir lógica de negocio.

```
com.ecommerce.modules
├── user        (auth, perfiles, roles)
├── catalog     (productos, categorías, variantes, imágenes)
├── cart        (carrito, items)
├── order       (órdenes, items de orden)
├── payment     (integración con pasarela, webhooks)
└── notification (envío de emails)
```

Cada paquete expone su propia capa `controller` / `service` / `repository`, y solo se comunica con otros paquetes a través de interfaces de servicio explícitas — nunca accediendo directamente al repositorio de otro dominio. Esta regla es la que hace viable la fase 2.

### 5.2 Fase 2 — Descomposición en microservicios

Cuando el monolito esté completo y estable, se extraen como servicios independientes (candidatos, no obligatorio hacerlo con los 5 a la vez):

- `auth-service`
- `catalog-service`
- `order-service`
- `payment-service`
- `notification-service`

Comunicación síncrona vía REST para consultas, y asíncrona vía **RabbitMQ/Kafka** para eventos de dominio (ej. `OrderCreated` → dispara descuento de stock y envío de email). Se introduce un **API Gateway** (Spring Cloud Gateway) como punto único de entrada, y **Resilience4j** para circuit breakers.

### 5.3 Diagrama de componentes (alto nivel)

```
[Next.js] → [API Gateway] → [auth-service]
                          → [catalog-service] → [Postgres/catalog]
                          → [order-service]   → [Postgres/orders]
                          → [payment-service] → [Stripe/MercadoPago]
                                              ↘
                                          [RabbitMQ] → [notification-service] → [SES/Mailtrap]
[Redis] (sesiones/carrito invitado)
[S3/MinIO] (imágenes de producto)
```

### 5.4 Convenciones de nomenclatura (backend)

Aplican a todos los módulos, no solo a `user` — para que el proyecto se sienta escrito por una sola persona con criterio consistente, no como un collage de tutoriales distintos.

| Capa | Convención | Ejemplo | Nota |
|---|---|---|---|
| Entity | `<Entidad>` (singular, sin sufijo) | `User.java` | Representa una instancia, no una colección |
| DTO | `<Acción><Entidad>Request` / `<Entidad>Response` | `RegisterRequest.java`, `UserResponse.java` | Sin sufijo `DTO` — la carpeta `dto/` ya lo indica; el sufijo real que importa es la dirección del dato |
| Repository | `<Entidad>Repository` (interfaz) | `UserRepository.java` | Extiende `JpaRepository`; sin implementación propia salvo consultas custom |
| Service | `<Entidad>Service` (clase concreta, sin interfaz por defecto) | `UserService.java` | Interfaz separada (`Service` + `ServiceImpl`) **solo** cuando existen múltiples implementaciones reales intercambiables — ej. `PaymentGateway` con `StripePaymentService`/`MercadoPagoPaymentService` en el módulo de pagos |
| Controller | `<Entidad>Controller` | `UserController.java` | Anotaciones de Swagger (`@Operation`, etc.) directo en los métodos — sin interfaz `Api` separada, complejidad innecesaria para el alcance del MVP |
| Mapper | `<Entidad>Mapper` (interfaz MapStruct) | `UserMapper.java` → genera `UserMapperImpl` | Mapeo entidad↔DTO generado en compilación, no a mano ni con reflexión en runtime |
| Seeder | `<Entidad>Seeder` | `UserSeeder.java` | `CommandLineRunner` protegido con `@Profile("dev")` — nunca corre en QA/producción |
| Enum | Nombre singular descriptivo | `Role.java` | Mapeado con `@Enumerated(EnumType.STRING)`, nunca `ORDINAL` |
| Excepción de dominio | `<Hecho>Exception`, en `modules.<dominio>.exception`, hereda de una categoría | `EmailAlreadyRegisteredException.java` | Nombra lo que pasó en el negocio; nunca lleva código HTTP |
| Categoría de excepción | Clase `abstract` en `common.exception` | `ConflictException.java` | El `GlobalExceptionHandler` solo conoce categorías: `common` no depende de ningún módulo |

**Principio general detrás de "un servicio, no uno por acción":** un servicio por caso de uso (`CreateUserService`, `UpdateUserService`...) es un patrón válido de Clean Architecture, pero resuelve un problema de escala/complejidad que este proyecto no tiene todavía — mismo criterio que la decisión monolito-vs-microservicios de la Sección 5.1/5.2: no se paga complejidad que el tamaño actual del dominio no justifica.

**Manejo de errores**: el service lanza excepciones de dominio, nunca `ResponseStatusException` ni códigos HTTP. El `GlobalExceptionHandler` (`@RestControllerAdvice`, en `common.exception`) hereda de `ResponseEntityExceptionHandler` y responde todos los errores con `ProblemDetail` (RFC 9457, `application/problem+json`); la validación agrega una propiedad `errors` con el mensaje de cada campo. Los 401/403 de Spring Security ocurren en los filtros y no pasan por este handler.

**Ubicación de los DTOs**: dentro de `dto/`, separados por dirección del dato en `dto/request/` y `dto/response/` (ej. `dto/request/RegisterRequest.java`, `dto/response/UserResponse.java`). Escala mejor que una carpeta plana cuando un módulo acumula decenas de DTOs.

**Nombre de las migraciones de Flyway**: `V<yyyyMMddHHmmss>__<descripcion_en_snake_case>.sql` (ej. `V20260926100000__create_users_table.sql`), en vez de versiones secuenciales (`V1`, `V2`...). La marca de tiempo evita conflictos de numeración cuando dos ramas crean migraciones a la vez. Regla invariable: una migración ya aplicada nunca se edita, se crea una nueva.

**Colección de Bruno (pruebas de API)**: versionada en `bruno/ecommerce/`, junto al código. Se sube: los requests y los environments sin secretos (solo `base_url` local). No se sube nunca: tokens, contraseñas reales ni variables marcadas como secret; el token de una respuesta de login se guarda como variable de ejecución (en memoria), no en archivos. Las contraseñas de los bodies de prueba son siempre obviamente falsas (ej. `Test1234!`). Nombres de requests en inglés y sin espacios (ej. `Register.yml`). Antes de cada commit que toque `bruno/`: `grep -rniE "password|token|secret|eyJ" bruno/` y leer el resultado por su código de salida (`echo $?`: 0 encontró coincidencias, 1 no encontró nada, 2 falló la búsqueda), no solo por el texto; y revisar con `git diff --staged` qué se va a commitear de verdad.

### 5.5 Orden de creación de archivos por módulo

Aplica a cualquier módulo nuevo (catálogo, carrito, órdenes, pagos...), no solo a `user`. La regla general: nunca escribir un archivo que necesite referenciar algo que todavía no existe — seguir la flecha de dependencia.

```
1. Migración SQL (Flyway)   — no depende de nada; define la verdad física del dato
2. Entity                  — depende de la migración (describe la misma tabla en Java)
3. Repository               — depende de la Entity (JpaRepository<Entity, Id>)
4. DTOs (Request/Response)  — independientes de la Entity en compilación, pero se diseñan mirando el dominio ya definido
5. Mapper                   — depende de Entity + DTOs a la vez (traduce entre ambos)
6. Service                  — depende de Repository + Mapper + DTOs
7. Controller                — depende de todo lo anterior; sin lógica propia, delega al Service
```

Única excepción de orden estricto: los DTOs pueden escribirse en paralelo con la Entity, ya que no se importan entre sí — el resto de la cadena si es secuencial de verdad, porque el compilador la fuerza.

## 6. Modelo de datos

### 6.1 Entidades

Índice rápido de las 11 entidades — atributos completos y tipos físicos en 6.4, razón de ser de cada campo en 6.6.

**users** — identidad de cualquier persona que puede autenticarse (cliente o admin)

**categories** — organiza productos en un árbol navegable (subcategorías vía auto-referencia)

**products** — el "modelo" abstracto de un producto, independiente de talla/color

**product_variants** — la unidad real que se vende (talla/color/stock propios)

**product_images** — galería de fotos de un producto

**carts** — selección en curso, de un usuario logueado o un visitante anónimo

**cart_items** — una línea del carrito (una variante + cantidad)

**orders** — el registro permanente e inmutable de una compra

**order_items** — snapshot congelado de qué se compró y a qué precio

**payments** — la transacción de pago asociada a una orden

**reviews** — retroalimentación de clientes sobre productos (fuera del MVP v1, ver 3.2; esquema ya diseñado)

### 6.2 Relaciones

```
users 1───N orders
users 1───N carts
users 1───N reviews

categories 1───N products
categories 1───N categories (self-referencia)

products 1───N product_variants
products 1───N product_images
products 1───N reviews

carts 1───N cart_items
cart_items N───1 product_variants

orders 1───N order_items
orders 1───1 payments
order_items N───1 product_variants
```

### 6.3 Reglas de diseño clave

- **Snapshot de precio**: `order_items.unit_price` nunca se recalcula contra el precio actual del producto; se congela al momento de la compra.
- **Carrito único en Postgres**: todo carrito (logueado o invitado) vive en `carts` / `cart_items`; Redis no duplica esta información — se reserva para caché de catálogo, rate limiting y refresh tokens.
- **Variantes obligatorias**: todo producto tiene al menos una variante (aunque sea "default"), para mantener un modelo de stock consistente.
- **Idempotencia de pagos**: `payments.idempotency_key` garantiza que un webhook de Stripe reenviado (reintento de red) nunca descuente stock ni cree una orden duplicada.
- **Reserva de stock (inventory hold)**: `product_variants.reserved_stock` se incrementa al crear el PaymentIntent y se convierte en descuento definitivo sobre `stock_quantity` (protegido con `version`, bloqueo optimista) solo cuando el webhook confirma el pago; evita overselling cuando dos compras compiten por la misma unidad.

### 6.4 Tipos físicos (PostgreSQL)

El modelo conceptual (6.1-6.3) define entidades y relaciones; esta sección fija el **tipo físico exacto** de cada columna en PostgreSQL — una tabla por entidad, en el mismo orden en que se han trabajado a lo largo del documento.

#### users

| Columna | Tipo | Nulo | Default | Notas |
|---|---|---|---|---|
| id | BIGSERIAL | NOT NULL | — | PK |
| name | VARCHAR(255) | NOT NULL | — |  |
| email | VARCHAR(255) | NOT NULL | — | UNIQUE — implementar como índice único parcial `WHERE deleted_at IS NULL` (ver 6.5) |
| password_hash | VARCHAR(255) | NOT NULL | — | hash BCrypt (60 chars), con margen |
| role | VARCHAR(20) | NOT NULL | 'CUSTOMER' | `CHECK (role IN ('CUSTOMER', 'ADMIN'))` — la base de datos rechaza valores fuera del enum, igual que en `reviews.rating` |
| created_at | TIMESTAMPTZ | NOT NULL | now() |  |
| updated_at | TIMESTAMPTZ | NOT NULL | now() |  |
| deleted_at | TIMESTAMPTZ | NULL | — | soft delete — ver 6.5 |

#### categories

| Columna | Tipo | Nulo | Default | Notas |
|---|---|---|---|---|
| id | BIGSERIAL | NOT NULL | — | PK |
| name | VARCHAR(150) | NOT NULL | — |  |
| slug | VARCHAR(180) | NOT NULL | — | UNIQUE |
| parent_category_id | BIGINT | NULL | — | FK, self-reference (raíz sin padre) |
| created_at | TIMESTAMPTZ | NOT NULL | now() |  |
| updated_at | TIMESTAMPTZ | NOT NULL | now() |  |
| deleted_at | TIMESTAMPTZ | NULL | — | soft delete — ver 6.5 |

#### products

| Columna | Tipo | Nulo | Default | Notas |
|---|---|---|---|---|
| id | BIGSERIAL | NOT NULL | — | PK |
| name | VARCHAR(255) | NOT NULL | — |  |
| slug | VARCHAR(280) | NOT NULL | — | UNIQUE |
| description | TEXT | NULL | — | contenido opcional |
| base_price | BIGINT | NOT NULL | — | pesos enteros (COP sin decimales) |
| category_id | BIGINT | NOT NULL | — | FK |
| is_active | BOOLEAN | NOT NULL | true | toggle de negocio, distinto de deleted_at (ver 6.5) |
| created_at | TIMESTAMPTZ | NOT NULL | now() |  |
| updated_at | TIMESTAMPTZ | NOT NULL | now() |  |
| deleted_at | TIMESTAMPTZ | NULL | — | soft delete — ver 6.5 |

#### product_variants

| Columna | Tipo | Nulo | Default | Notas |
|---|---|---|---|---|
| id | BIGSERIAL | NOT NULL | — | PK |
| product_id | BIGINT | NOT NULL | — | FK |
| sku | VARCHAR(50) | NOT NULL | — | UNIQUE |
| size | VARCHAR(50) | NULL | — |  |
| color | VARCHAR(50) | NULL | — |  |
| price_override | BIGINT | NULL | — | solo si difiere del base_price |
| stock_quantity | INTEGER | NOT NULL | 0 |  |
| reserved_stock | INTEGER | NOT NULL | 0 | inventory hold — ver 7.4 |
| version | BIGINT | NOT NULL | 0 | bloqueo optimista (@Version) |
| image_url | VARCHAR(500) | NULL | — | foto específica de la variante |
| created_at | TIMESTAMPTZ | NOT NULL | now() |  |
| updated_at | TIMESTAMPTZ | NOT NULL | now() |  |
| deleted_at | TIMESTAMPTZ | NULL | — | soft delete — ver 6.5 |

#### product_images

| Columna | Tipo | Nulo | Default | Notas |
|---|---|---|---|---|
| id | BIGSERIAL | NOT NULL | — | PK |
| product_id | BIGINT | NOT NULL | — | FK |
| url | VARCHAR(500) | NOT NULL | — | referencia S3/MinIO |
| position | SMALLINT | NOT NULL | 0 | orden de la galería |
| created_at | TIMESTAMPTZ | NOT NULL | now() |  |
| updated_at | TIMESTAMPTZ | NOT NULL | now() |  |
| deleted_at | TIMESTAMPTZ | NULL | — | soft delete — ver 6.5 |

#### carts

| Columna | Tipo | Nulo | Default | Notas |
|---|---|---|---|---|
| id | BIGSERIAL | NOT NULL | — | PK |
| user_id | BIGINT | NULL | — | nulo si es carrito de invitado |
| session_id | VARCHAR(100) | NULL | — | nulo si el usuario está logueado |
| created_at | TIMESTAMPTZ | NOT NULL | now() |  |
| updated_at | TIMESTAMPTZ | NOT NULL | now() |  |
| deleted_at | TIMESTAMPTZ | NULL | — | soft delete — ver 6.5 |

#### cart_items

| Columna | Tipo | Nulo | Default | Notas |
|---|---|---|---|---|
| id | BIGSERIAL | NOT NULL | — | PK |
| cart_id | BIGINT | NOT NULL | — | FK |
| product_variant_id | BIGINT | NOT NULL | — | FK |
| quantity | INTEGER | NOT NULL | 1 |  |
| created_at | TIMESTAMPTZ | NOT NULL | now() |  |
| updated_at | TIMESTAMPTZ | NOT NULL | now() |  |
| deleted_at | TIMESTAMPTZ | NULL | — | soft delete — ver 6.5 |

#### orders

| Columna | Tipo | Nulo | Default | Notas |
|---|---|---|---|---|
| id | BIGSERIAL | NOT NULL | — | PK |
| user_id | BIGINT | NOT NULL | — | FK — sin checkout de invitado (ver 6.6) |
| status | VARCHAR(20) | NOT NULL | 'PENDING' | máquina de estados |
| total_amount | BIGINT | NOT NULL | — | pesos enteros |
| shipping_address | JSONB | NOT NULL | — | requerido para envío |
| created_at | TIMESTAMPTZ | NOT NULL | now() |  |
| updated_at | TIMESTAMPTZ | NOT NULL | now() |  |

**Sin ****`deleted_at`** — junto con `order_items` y `payments`, es la única excepción: registro financiero inmutable (ver 6.5).

#### order_items

| Columna | Tipo | Nulo | Default | Notas |
|---|---|---|---|---|
| id | BIGSERIAL | NOT NULL | — | PK |
| order_id | BIGINT | NOT NULL | — | FK |
| product_variant_id | BIGINT | NOT NULL | — | FK |
| quantity | INTEGER | NOT NULL | — |  |
| unit_price | BIGINT | NOT NULL | — | snapshot congelado, pesos enteros |
| created_at | TIMESTAMPTZ | NOT NULL | now() |  |
| updated_at | TIMESTAMPTZ | NOT NULL | now() |  |

**Sin ****`deleted_at`** — inmutable (ver 6.5).

#### payments

| Columna | Tipo | Nulo | Default | Notas |
|---|---|---|---|---|
| id | BIGSERIAL | NOT NULL | — | PK |
| order_id | BIGINT | NOT NULL | — | FK |
| provider | VARCHAR(20) | NOT NULL | — |  |
| provider_payment_id | VARCHAR(255) | NULL | — | se completa tras la respuesta de la pasarela |
| status | VARCHAR(20) | NOT NULL | 'PENDING' |  |
| amount | BIGINT | NOT NULL | — | pesos enteros |
| idempotency_key | VARCHAR(255) | NOT NULL | — | UNIQUE |
| created_at | TIMESTAMPTZ | NOT NULL | now() |  |
| updated_at | TIMESTAMPTZ | NOT NULL | now() |  |

**Sin ****`deleted_at`** — inmutable (ver 6.5).

#### reviews

| Columna | Tipo | Nulo | Default | Notas |
|---|---|---|---|---|
| id | BIGSERIAL | NOT NULL | — | PK |
| product_id | BIGINT | NOT NULL | — | FK |
| user_id | BIGINT | NOT NULL | — | FK |
| rating | SMALLINT | NOT NULL | — | CHECK (rating BETWEEN 1 AND 5) |
| comment | TEXT | NULL | — | rating es obligatorio, el comentario no |
| created_at | TIMESTAMPTZ | NOT NULL | now() |  |
| updated_at | TIMESTAMPTZ | NOT NULL | now() |  |
| deleted_at | TIMESTAMPTZ | NULL | — | soft delete — ver 6.5 |

**Restricción adicional**: `UNIQUE(product_id, user_id)` — un usuario puede dejar como máximo una reseña por producto.

**Decisiones de tipo que merecen justificación:**

- **`BIGINT`**** para dinero, no ****`NUMERIC`****/****`FLOAT`****/****`DOUBLE`**: el proyecto factura en COP, y el peso colombiano no usa decimales en la práctica — todos los precios son pesos enteros. Un entero nativo evita tanto el error de redondeo binario de `FLOAT`/`DOUBLE` como el overhead innecesario de `NUMERIC` cuando no hay decimales que precisar.
- **`TEXT`**** en vez de ****`VARCHAR(n)`**** para ****`description`**** y ****`comment`**: en PostgreSQL ambos tienen el mismo rendimiento interno — `VARCHAR(n)` solo agrega un límite de validación. Para contenido de longitud impredecible, `TEXT` evita un tope arbitrario.
- **`TIMESTAMPTZ`**** en vez de ****`TIMESTAMP`**: guarda el instante exacto en UTC internamente, independiente de la zona horaria del servidor — evita bugs cuando la app corre en una región de AWS distinta a donde se programa.
- **`JSONB`**** para ****`shipping_address`**: binario e indexable (a diferencia de `JSON` texto plano), permite consultar campos internos sin necesidad de una tabla `addresses` separada todavía.
- **Capa anticorrupción para Stripe**: Stripe NO trata el COP como moneda "zero-decimal" en su API (a diferencia del CLP chileno) — sigue esperando los montos multiplicados por 100. Esa conversión vive únicamente en el módulo `payment`, en la clase que arma la petición a Stripe; el resto del sistema siempre trabaja en pesos enteros reales.

### 6.5 Estrategia de borrado (físico vs. lógico)

| Tabla | Estrategia | Columna |
|---|---|---|
| users, categories, products, product_variants, product_images, carts, cart_items, reviews | **Lógico (uniforme)** | `deleted_at TIMESTAMPTZ NULL` |
| orders, order_items, payments | **Ninguno — única excepción, inmutables** | se cancelan vía `status = CANCELLED`, nunca se borran ni lógica ni físicamente; sin columna `deleted_at` disponible, a propósito — su ausencia hace imposible aplicarles soft delete por error |

**Por qué borrado lógico en products/categories/users:** las órdenes históricas referencian estas entidades vía FK; un borrado físico rompería la integridad referencial o forzaría un `CASCADE` que destruiría el detalle de compras pasadas. `deleted_at` permite "ocultar" del catálogo/sistema activo sin perder el historial.

**Por qué orders/payments nunca se borran:** son registros financieros — su inmutabilidad es la garantía de auditoría. La cancelación es un estado del negocio (`CANCELLED`), no una desaparición del dato.

**Índices únicos parciales:** las columnas `UNIQUE` (email, sku, slug) deben migrar a índices únicos parciales para convivir con el borrado lógico, o un registro "borrado" bloquea permanentemente ese valor:

```
CREATE UNIQUE INDEX users_email_active_idx ON users(email) WHERE deleted_at IS NULL;
```

**Nota sobre alcance:** el borrado lógico oculta el dato pero no lo anonimiza. Cumplimiento real de derecho al olvido (GDPR, Ley 1581 en Colombia) requeriría una estrategia de anonimización aparte — reconocida aquí como decisión consciente fuera del alcance del MVP, no como omisión.

**`products.is_active`**** no es redundante con ****`deleted_at`** — son dos ejes independientes. `is_active` es un toggle de negocio, frecuente y reversible (ocultar del catálogo temporalmente sin perder nada); `deleted_at` es la discontinuación real del producto (poco frecuente, preserva el registro solo por integridad referencial con órdenes históricas). Un producto puede estar inactivo sin estar borrado. Regla general del proyecto: nunca dos columnas distintas para representar el mismo hecho — un solo `deleted_at` nullable basta como control (`IS NULL` = activo) y como fecha del borrado a la vez, sin necesidad de un `is_active`/`is_deleted` adicional en ninguna otra tabla.

### 6.6 Diccionario de datos — por qué cada campo

Cinco razones posibles para que un campo exista: **identidad** (PK), **regla de negocio** (el mundo real lo exige), **relación** (FK hacia otra tabla), **auditoría** (created_at/updated_at/deleted_at), o **necesidad técnica** (version, idempotency_key, session_id).

#### users — identidad de cualquier persona que puede autenticarse (cliente o admin)

- `id`: identidad. Clave surrogada (no el email) porque un email puede cambiar y un ID nunca debería
- `email`: regla de negocio — es cómo el usuario inicia sesión; UNIQUE porque dos cuentas no pueden compartir credencial de acceso
- `password_hash`: regla de negocio (seguridad) — nunca se guarda la contraseña real, solo su hash BCrypt
- `name`: regla de negocio — para mostrar en recibos, emails, panel admin; no se usa para autenticar
- `role`: regla de negocio — distingue CUSTOMER de ADMIN para autorización
- `created_at/updated_at/deleted_at`: auditoría
- **Relaciones**: `users 1───N orders` (un usuario hace muchas órdenes, cada orden es de un solo usuario — y como `orders.user_id` es NOT NULL, esto implica que **no hay checkout de invitado**, solo carrito de invitado; comprar requiere cuenta. **Decisión consciente**: simplifica el modelo — evita duplicar datos de contacto en la orden y el problema de vincular compras de invitado a una cuenta creada después — sin perder la posibilidad de registrarse en el mismo flujo de checkout, como parte de la experiencia de usuario). `users 1───N carts` (puede tener varios carritos a lo largo del tiempo, aunque la app típicamente solo usa el más reciente como "activo"). `users 1───N reviews`

#### categories — organiza productos en un árbol navegable

- `name`: regla de negocio — nombre visible al cliente
- `slug`: regla de negocio (SEO/UX) — versión amigable para URL (`/categoria/electronica`) en vez de mostrar el `id` numérico
- `parent_category_id`: relación (self-reference) — nullable; NULL significa "categoría raíz", con valor significa "soy subcategoría de otra"
- **Relaciones**: `categories 1───N products` (una categoría agrupa muchos productos; cada producto pertenece a exactamente una — simplificación consciente del MVP, un catálogo más avanzado permitiría múltiples categorías por producto). `categories 1───N categories` (auto-referencia, forma el árbol de subcategorías)

#### products — el "modelo" abstracto de un producto (independiente de talla/color)

- `description`: regla de negocio — copy de marketing; TEXT porque la longitud es impredecible
- `base_price`: regla de negocio — precio de referencia; puede ser sobreescrito por variante específica
- `category_id`: relación
- `is_active`: regla de negocio — toggle reversible de visibilidad (distinto de `deleted_at`, ver 6.5)
- **Relaciones**: `products 1───N product_variants` (un modelo tiene muchas combinaciones vendibles). `products 1───N product_images` (galería). `products 1───N reviews`

#### product_variants — la unidad real que se vende (con talla/color/stock propios)

- `sku`: regla de negocio — código de inventario usado por sistemas externos/almacén, independiente del `id` interno
- `size / color`: regla de negocio — nullable porque no todo producto tiene esos atributos (ej. un libro no tiene talla)
- `price_override`: regla de negocio — solo se llena si esta variante específica cuesta distinto al `base_price` del producto
- `stock_quantity / reserved_stock`: regla de negocio — inventario real disponible vs. apartado en checkouts en curso (ver Sección 7.4, inventory hold)
- `version`: necesidad técnica — contador de bloqueo optimista, JPA lo incrementa solo en cada `UPDATE` para detectar modificaciones concurrentes
- `image_url`: regla de negocio — foto específica de esta variante (ej. una foto distinta por color), separada de la galería general en `product_images`
- **Relaciones**: `product_variants N───1 products`. `product_variants 1───N cart_items`, `1───N order_items` (la misma variante puede aparecer en muchos carritos/órdenes de distintos clientes a lo largo del tiempo)

#### product_images — galería de fotos de un producto

- `url`: regla de negocio — referencia al archivo en S3/MinIO
- `position`: regla de negocio — orden de despliegue (0 = foto principal)
- **Relaciones**: `product_images N───1 products`

#### carts — selección en curso, de un usuario logueado o un visitante anónimo

- `user_id`: relación, nullable — se llena si el carrito es de un usuario logueado
- `session_id`: necesidad técnica, nullable — identifica a un visitante anónimo vía cookie, cuando no hay `user_id` (HTTP no tiene memoria entre peticiones; esta es la forma de "recordar" al mismo visitante sin cuenta)
- **Regla de negocio no forzable por SQL**: exactamente uno de `user_id`/`session_id` debe tener valor, nunca ambos ni ninguno — se valida en el `service`, no en la base de datos
- **Relaciones**: `carts N───1 users` (nullable). `carts 1───N cart_items`

#### cart_items — una línea del carrito (una variante + cantidad)

- `quantity`: regla de negocio
- **Relaciones**: `cart_items N───1 carts`, `N───1 product_variants`

#### orders — el registro permanente de una compra

- `user_id`: relación, NOT NULL — toda orden pertenece a una cuenta registrada (sin checkout de invitado)
- `status`: regla de negocio — máquina de estados del pedido
- `total_amount`: regla de negocio — suma calculada de los `order_items` al momento de la compra
- `shipping_address`: regla de negocio — JSONB para no requerir aún una tabla `addresses` separada
- **Relaciones**: `orders N───1 users`. `orders 1───N order_items`. `orders 1───1 payments`

#### order_items — snapshot congelado de qué se compró y a qué precio

- `unit_price`: **el campo más importante de esta tabla** — copia congelada del precio al momento exacto de la compra, totalmente independiente de si `product_variants.price_override` o `products.base_price` cambian después. Sin este snapshot, el historial de compras mostraría precios incorrectos cada vez que subes o bajas un precio
- **Relaciones**: `order_items N───1 orders`, `N───1 product_variants`

#### payments — la transacción de pago asociada a una orden

- `provider` / `provider_payment_id`: relación con el sistema externo — cuál pasarela y su ID de transacción
- `idempotency_key`: necesidad técnica — evita procesar dos veces el mismo webhook reenviado (ver Sección 6.3)
- **Relaciones**: `payments 1───1 orders`

#### reviews — retroalimentación de clientes sobre productos

- `rating`: regla de negocio, `CHECK (rating BETWEEN 1 AND 5)`
- `comment`: regla de negocio, opcional (el rating sí es obligatorio, el texto no)
- **Relaciones**: `reviews N───1 products`, `N───1 users`. **Decisión confirmada**: `UNIQUE(product_id, user_id)` — un usuario puede dejar como máximo una reseña por producto (puede editarla, no duplicarla).

**Decisiones de tipo que merecen justificación:**

- **`BIGINT`**** para dinero, no ****`NUMERIC`****/****`FLOAT`****/****`DOUBLE`**: el proyecto factura en COP, y el peso colombiano no usa decimales en la práctica (aunque técnicamente tenga centavos) — todos los precios son pesos enteros. Un entero nativo evita tanto el error de redondeo binario de `FLOAT`/`DOUBLE` como el overhead innecesario de `NUMERIC` cuando no hay decimales que precisar.
- **`TEXT`**** en vez de ****`VARCHAR(n)`**** para ****`description`**** y ****`comment`**: en PostgreSQL ambos tienen el mismo rendimiento interno — `VARCHAR(n)` solo agrega un límite de validación. Para contenido de longitud impredecible, `TEXT` evita un tope arbitrario.
- **`TIMESTAMPTZ`**** en vez de ****`TIMESTAMP`**: guarda el instante exacto en UTC internamente, independiente de la zona horaria del servidor — evita bugs cuando la app corre en una región de AWS distinta a donde se programa.
- **`JSONB`**** para ****`shipping_address`**: binario e indexable (a diferencia de `JSON` texto plano), permite consultar campos internos sin necesidad de una tabla `addresses` separada todavía.
- **Capa anticorrupción para Stripe**: Stripe NO trata el COP como moneda "zero-decimal" en su API (a diferencia del CLP chileno) — sigue esperando los montos multiplicados por 100. Esa conversión vive únicamente en el módulo `payment`, en la clase que arma la petición a Stripe; el resto del sistema (entidades, lógica de negocio, frontend) siempre trabaja en pesos enteros reales, sin saberlo.

## 7. Módulos funcionales

### 7.1 Autenticación y usuarios

- Registro/login con email y contraseña (hash con BCrypt)
- Autenticación stateless con JWT (access token + refresh token), implementada con el soporte nativo de Spring Security (starter security-oauth2-resource-server, firma HS256 con Nimbus) en vez de JJWT: JJWT no soporta Jackson 3, que es el que usa Spring Boot 4 por defecto, y obligaría a una dependencia extra solo para esquivar el conflicto
- Roles: CUSTOMER, ADMIN — autorización a nivel de endpoint (Spring Security)
- Recuperación de contraseña vía email con token de un solo uso

### 7.2 Catálogo

- CRUD de productos, categorías (con subcategorías) y variantes
- Carga de imágenes hacia S3/MinIO, con URL persistida en `product_images`
- Listado con paginación, filtro por categoría y rango de precio, búsqueda por nombre

### 7.3 Carrito

- Persistencia en base de datos si el usuario está logueado; por `session_id` si es invitado
- Merge automático del carrito de invitado al carrito del usuario al loguearse
- Validación de stock disponible al agregar/actualizar cantidad

### 7.4 Checkout y pagos

- Reserva temporal de stock (inventory hold) al crear el PaymentIntent, con expiración de ~15 minutos
- Integración con Stripe (modo test): creación de intento de pago, confirmación vía webhook
- El estado de la orden solo pasa a `PAID` cuando el webhook confirma el pago (nunca por respuesta directa del cliente, para evitar fraude/inconsistencia); el procesamiento es idempotente gracias a idempotency_key en payments, que ignora reintentos del mismo webhook
- Al confirmarse el pago, la reserva se convierte en descuento definitivo de stock; si expira sin pago, un job en background la libera automáticamente
- Cálculo de total incluyendo envío (simplificado en MVP)

### 7.5 Órdenes

- Historial de compras por usuario
- Máquina de estados: PENDING → PAID → SHIPPED → DELIVERED (o CANCELLED en cualquier punto anterior a SHIPPED)
- Descuento de stock en dos tiempos: reserva (`reserved_stock`) al iniciar checkout, descuento definitivo (`stock_quantity`) al confirmarse el pago, protegido con bloqueo optimista vía `version`

### 7.6 Panel de administración

- CRUD de productos y categorías
- Listado y cambio de estado de órdenes
- (Fase 2) Dashboard con métricas básicas: ventas por período, productos más vendidos

## 8. Infraestructura y entornos

| Aspecto | Local (dev) | QA (staging) | Producción |
|---|---|---|---|
| Cómputo | Docker Compose en máquina local | EC2 pequeña o ECS/Fargate | ECS/Fargate (o EKS si se profundiza) |
| Base de datos | Postgres en contenedor | RDS (instancia pequeña) | RDS (multi-AZ opcional, con backups) |
| Storage de imágenes | MinIO en contenedor | S3 | S3 + CloudFront (CDN) |
| Emails | Mailtrap | Mailtrap | Amazon SES |
| Pagos | Stripe modo test | Stripe modo test | Stripe modo live |
| Secretos | `.env.local` (no versionado) | AWS Secrets Manager / Parameter Store | AWS Secrets Manager |
| Despliegue | Manual (`docker-compose up`) | Automático vía CI/CD al hacer push a `develop` | Manual/aprobado vía CI/CD desde `main` |
| Logs | Consola local | CloudWatch | CloudWatch + alarmas |
| HTTPS | No aplica | Opcional | ALB + ACM (certificado real) |

### 8.1 Principio de paridad

El código de la aplicación nunca debe saber contra qué proveedor concreto habla: se programa contra la interfaz del SDK de S3 (válido para MinIO y S3 real) y contra `JavaMailSender`/SMTP (válido para Mailtrap y SES). Solo cambia la configuración por entorno (`application-dev.yml`, `application-qa.yml`, `application-prod.yml`), nunca el código de negocio.

## 9. Roadmap por fases

### Fase 1 — Monolito funcional (local)

- Modelo de datos implementado en Postgres con Flyway
- Módulos: auth, catálogo, carrito, checkout, órdenes, panel admin
- Docker Compose local: Spring Boot + Postgres + Redis + MinIO + Mailtrap + Next.js
- Documentación de API con OpenAPI/Swagger, mantenida desde el primer endpoint
- Tests unitarios y de integración (incluyendo concurrencia en `order`/`inventory`) como parte de la definición de "hecho" de cada módulo
- **Entregable**: aplicación completa corriendo end-to-end en local, documentada y probada

### Fase 2 — Despliegue en AWS (monolito)

- Dockerización productiva del backend y frontend
- Despliegue en EC2 simple, luego migración a ECS/Fargate
- RDS reemplaza a Postgres en contenedor; S3 reemplaza a MinIO; SES reemplaza a Mailtrap
- Entornos QA y producción diferenciados, con Secrets Manager
- **Entregable**: aplicación accesible públicamente en AWS, con CI/CD básico (GitHub Actions) hacia QA

### Fase 3 — Descomposición en microservicios

- Extracción de 2-3 servicios desde el monolito modular (ej. `payment-service`, `notification-service`)
- Introducción de broker de mensajería (RabbitMQ/Kafka) para eventos de dominio
- API Gateway y manejo de resiliencia (Resilience4j)
- **Entregable**: arquitectura híbrida (monolito + microservicios) documentada y desplegada

### Fase 4 — Refinamiento y observabilidad

- CI/CD completo a producción con aprobación manual
- CloudWatch con alarmas, logging estructurado
- Ampliación de cobertura de tests y documentación de API a los módulos nuevos de las fases 2-3 (microservicios)
- **Entregable**: proyecto listo para presentar en portafolio con documentación técnica completa

## 10. Criterios de éxito y buenas prácticas

### 10.1 Testing

- Tests unitarios de servicios (JUnit + Mockito) para lógica de negocio crítica (cálculo de totales, descuento de stock)
- Tests de integración para endpoints principales (Testcontainers con Postgres real)
- Tests de frontend para flujos críticos (checkout, carrito)

### 10.2 CI/CD

- Pipeline en GitHub Actions: lint → test → build de imagen Docker → push a ECR → deploy
- Despliegue automático a QA en cada push a `develop`; a producción solo desde `main`, con aprobación manual

### 10.3 Documentación

- README con instrucciones de arranque local (`docker-compose up`)
- Documentación de API con OpenAPI/Swagger
- Este documento como referencia viva de arquitectura y decisiones

### 10.4 Seguridad

- Nunca credenciales hardcodeadas: todo vía variables de entorno o Secrets Manager
- Contraseñas con hash (BCrypt), nunca en texto plano
- Validación de entrada en frontend (Zod) y backend (Bean Validation), sin confiar solo en el cliente
- HTTPS obligatorio en QA y producción

### 10.5 Definición de "terminado"

Una fase se considera completa cuando su entregable corre de punta a punta sin intervención manual fuera de lo documentado, tiene tests que cubren su lógica crítica, y está documentada en este mismo documento antes de pasar a la siguiente fase.
