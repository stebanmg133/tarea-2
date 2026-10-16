# pedidos-restaurante

Sistema de pedidos para restaurante (Mesa, Domicilio y Para llevar) pensado para ejecutarse
desde **IntelliJ IDEA** en el PC del local (servidor) y ser usado simultáneamente por varios
dispositivos de la red local (Wi-Fi) por medio del navegador. Incluye toma de pedidos, vistas de
preparación por área, historial, catálogo configurable, impresión **desde el servidor**, y un
módulo de **bloqueo temporal ("hombre muerto")** con sección administrativa protegida.

> Los precios y productos que se cargan son **de demostración y editables**. No representan
> precios reales; ajústelos en Catálogo antes de usar el sistema en producción.

---

## 1. Requisitos y entorno

| Componente | Versión usada / verificada |
|---|---|
| Java | 21 (Temurin 21.0.12.1) |
| Maven | 3.9.16 (Maven Wrapper incluido: `mvnw.cmd`) |
| Spring Boot | 3.5.16 |
| Base de datos | H2 2.3.232 en modo archivo |
| Sistema operativo probado | Windows 11 |
| IDE | IntelliJ IDEA |

Dependencias principales: Spring Web, Spring Data JPA, Thymeleaf, Validation, H2 y
`spring-security-crypto` (solo para hash BCrypt). No se usan dependencias innecesarias.

---

## 2. Cómo ejecutar

### 2.1 Desde IntelliJ IDEA (recomendado)

1. `File > Open` y seleccione la carpeta del proyecto.
2. IntelliJ detecta el `pom.xml` y descarga dependencias automáticamente.
3. Abra `src/main/java/com/restaurante/pedidos/PedidosApplication.java` y ejecute
   (botón ▶). Se levanta en `http://localhost:8080/`.
4. Para configurar variables de entorno o argumentos: `Run > Edit Configurations...`
   → seleccione la configuración `PedidosApplication` → use **Environment variables** o
   **VM options** (ver sección 4.3).

> **Nota sobre Maven / sin internet:** este equipo no tiene `mvn` global y **no hay acceso a
> internet**. El proyecto incluye el **Maven Wrapper** (`mvnw.cmd`) y ya está configurado para
> usar la distribución de Maven que quedó en caché (`~/.m2/wrapper/dists`), por lo que
> **funciona totalmente sin conexión**. No es necesario descargar nada.

### 2.2 Línea de comandos (sin internet)

Todas las dependencias y plugins ya están en la caché local (`~/.m2/repository`), por lo que
basta añadir `-o` (modo *offline*) para que Maven no intente contactar ningún repositorio.

```bat
:: Compilar y ejecutar las pruebas (offline)
mvnw.cmd -o clean test

:: Empaquetar el ejecutable (offline)
mvnw.cmd -o clean package

:: Arrancar directamente con el plugin de Spring Boot (offline)
mvnw.cmd -o spring-boot:run

:: Ejecutar el .jar ya empaquetado (no requiere Maven)
java -jar target/pedidos-restaurante.jar
```

### 2.3 Atajos de un clic

En la raíz del proyecto hay dos scripts listos para usar (doble clic o desde la consola):

| Script          | Qué hace                                                                 |
|-----------------|--------------------------------------------------------------------------|
| `ejecutar.cmd`  | Compila si hace falta y **arranca la aplicación** en `http://localhost:8080`. |
| `compilar.cmd`  | Ejecuta `mvnw.cmd -o package` (compila + pruebas + genera el `.jar`).     |

> **Si algún día aparece el error `No se puede resolver el nombre remoto:
> 'repo.maven.apache.org'`**, significa que el wrapper no encontró la distribución de Maven
> en la caché e intentó descargarla. Con la caché ya enlazada (ver más abajo) esto no vuelve a
> ocurrir. Alternativa: use el Maven incluido en IntelliJ, que también funciona offline:
> `"<IntelliJ>\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" -o package`.

El archivo de base de datos se crea en `./data/pedidos.mv.db` relativo al **directorio de
trabajo** desde donde se ejecute la aplicación.

---

## 3. Estructura del proyecto

```
pedidos-restaurante/
├─ config/
│  ├─ security.properties            # credencial inicial REAL (EXCLUIDO de git)
│  └─ security.properties.example    # ejemplo sin secretos (versionable)
├─ data/                             # base H2, backups y simulaciones (EXCLUIDO de git)
├─ src/main/java/com/restaurante/pedidos/
│  ├─ domain/       entidades JPA (Order, OrderItem, Product, ...)
│  ├─ repository/   repositorios Spring Data
│  ├─ service/      lógica de negocio (OrderService, PrintService, LockService, ...)
│  ├─ web/          PageController (vistas) y web/api/* (REST)
│  ├─ security/     interceptores CSRF, bloqueo y administración
│  ├─ print/        TicketRenderer (formato de comanda)
│  ├─ config/       propiedades, WebConfig, DataInitializer, handler de errores
│  └─ dto/          objetos de transferencia
├─ src/main/resources/
│  ├─ application.properties
│  ├─ templates/    páginas Thymeleaf (index, mesas, pedido, areas, pedidos, catalogo, admin, impresion, sistema)
│  └─ static/       css/app.css, js/app.js
└─ src/test/java/... pruebas automatizadas
```

---

## 4. Configuración

### 4.1 `application.properties`

Valores relevantes (todos documentados en el propio archivo):

- `server.port` — puerto (por defecto **8080**, configurable con `SERVER_PORT`).
- `server.address=0.0.0.0` — escucha en la red local.
- `spring.datasource.url` — `jdbc:h2:file:./data/pedidos;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000`.
- `spring.jpa.hibernate.ddl-auto=update` — **no destructivo**; conserva los datos.
- `spring.h2.console.enabled=false` — la consola H2 **no** se expone.
- `pedidos.print.mode` — `SIMULATION` (por defecto) o `REAL`.
- `pedidos.security.interval-months` — intervalo de validación (por defecto **2**).
- `pedidos.backup.dir` — carpeta de copias (`./data/backups`).

### 4.2 Variables de entorno

| Variable | Efecto |
|---|---|
| `SERVER_PORT` | Puerto del servidor (por defecto 8080). |
| `PEDIDOS_ADMIN_PASSWORD` | Credencial administrativa inicial (fuente segura). |
| `PEDIDOS_ADMIN_EMAIL` | Correo administrativo inicial. |
| `PEDIDOS_PRINT_MODE` | `SIMULATION` o `REAL`. |
| `PEDIDOS_PRINTER_NAME` | Nombre de la impresora del servidor. |
| `PEDIDOS_PAPER_WIDTH` | Ancho de papel en caracteres (por defecto 42). |

### 4.3 En IntelliJ

`Run > Edit Configurations...` → `PedidosApplication`:

- **Environment variables:** p. ej. `SERVER_PORT=9090;PEDIDOS_PRINT_MODE=SIMULATION`.
- **VM options:** p. ej. `-Dpedidos.admin.password=unaClaveSegura -Dserver.port=9090`.

---

## 5. Credencial inicial y gestión de secretos

La credencial administrativa inicial (la misma que desbloquea el módulo de bloqueo) se resuelve,
en este orden:

1. Variable de entorno `PEDIDOS_ADMIN_PASSWORD`.
2. Propiedad de sistema `-Dpedidos.admin.password=...`.
3. Archivo local `config/security.properties` (propiedad `admin.initial-password`).
4. Si no hay ninguna fuente: se genera una contraseña aleatoria y se avisa por consola.

Reglas de seguridad:

- La contraseña **nunca** se guarda en el código fuente ni se escribe en logs.
- Se almacena **hasheada con BCrypt** (no en texto plano).
- `config/security.properties` está **excluido de git** (`.gitignore`); se versiona solo
  `config/security.properties.example` **sin secretos**.
- Tras el primer arranque el sistema marca `mustChangePassword=true`; cámbiela en
  **Administración**. Evite usar la credencial inicial en producción.

---

## 6. Acceso desde la red local (LAN)

El servidor PC ejecuta la aplicación; los demás dispositivos entran por la **IP del servidor** y
el puerto configurado.

1. **Descubrir la IP del servidor** (en el PC servidor):
   ```bat
   ipconfig
   ```
   Tome la IPv4 del adaptador de red (p. ej. `192.168.1.50`).
2. En el celular/tablet/otro PC (misma Wi-Fi) abra: `http://192.168.1.50:8080`
3. **Regla de firewall de Windows** (en el PC servidor, una sola vez, como administrador):
   ```bat
   netsh advfirewall firewall add rule name="Pedidos 8080" dir=in action=allow protocol=TCP localport=8080
   ```
   Alternativa: doble clic en **`abrir-firewall.cmd`** (pide permisos de administrador y crea la regla).
4. **No exponga la aplicación directamente a Internet.** Para acceso remoto use VPN o una
   arquitectura segura. La consola H2 y las rutas de administración **no** deben publicarse.

---

## 7. Tipos de pedido

| Tipo | Identificación | Reglas |
|---|---|---|
| **Mesa** | M01–M15 | Ocupa la mesa automáticamente; se libera **solo** al entregar todo el pedido. |
| **Domicilio** | — | Requiere nombre, teléfono y dirección; el **valor del domicilio se suma** al total. |
| **Para llevar** | — | Sin mesa ni dirección; sin cobro de domicilio. |

Cada tipo se distingue por **color + ícono + texto** en la interfaz.

---

## 8. Áreas de preparación y distribución

Cinco áreas: **Pizzas y Panzerottis, Cocina general, Gaseosas, Comidas, Bebidas**.

- Un pedido es **una sola identidad con un solo total**, aunque sus artículos se repartan entre
  áreas. Las áreas son **vistas de trabajo**, no pedidos independientes: no hay duplicación ni
  cobro doble.
- Un producto puede tener **uno o varios destinos**. Se crea **una tarea por área**, pero el
  artículo y el cobro son únicos.
- Los destinos son configurables (hoy por áreas asociadas al producto; preparado para selección
  múltiple en el futuro).
- Cuando una tarea pasa a **Listo** o **Entregado**, sale de la vista activa del área pero se
  conserva en el historial.

---

## 9. Estados

- **De artículo / tarea por área:** Pendiente, En preparación, Listo, Entregado, Cancelado.
- **Estado global del pedido:** se **deriva** de forma consistente de sus artículos activos
  (una sola fuente de verdad). Al entregar todo, el pedido queda Entregado y, si es de mesa, la
  mesa se libera.
- Los artículos/áreas **cancelados** se excluyen de los totales; los pedidos **cancelados** no
  cuentan en los totales financieros pero **se conservan** en la base de datos.

---

## 10. Edición de pedidos

- Se pueden **agregar, modificar o retirar** artículos, cambiar variantes/sabores, agregar
  observaciones y cancelar (con confirmación).
- Los **cambios se registran** (historial de eventos del pedido).
- Si un pedido **ya fue impreso** y luego se modifica, se marca que **la comanda debe actualizarse**
  (`needsReprint`).
- El total, subtotal y valor del domicilio se **calculan siempre en el backend**; nunca se confía
  en totales enviados por el cliente.

---

## 11. Impresión (desde el servidor)

- La impresión parte **del servidor** (no del navegador del empleado). Se admiten impresiones
  **por área** y **de pedido completo**.
- Cada intento tiene un **ID de trabajo** propio y un estado:
  **Pendiente, Enviando, Aceptado por el sistema de impresión, Error, Estado desconocido,
  Reintento solicitado**.
- Se **deduplica** el doble clic: dos solicitudes idénticas seguidas producen **un solo trabajo**.
- Se permite **reintentar** (genera un nuevo intento con su ID).
- **Honestidad de estado:** "Aceptado por el sistema de impresión" significa que el trabajo entró
  a la **cola**, **no** que ya salió en papel. El campo `physicalConfirmed` es siempre `false`
  porque el servidor no puede confirmar físicamente el papel.
- **Modo simulación:** si no hay impresora real disponible, `pedidos.print.mode=SIMULATION` escribe
  la comanda en `data/print-simulation/` y **lo indica claramente** (los trabajos quedan marcados
  como `simulated=true`).

### Preparar la impresora real

1. Instale la impresora en el **PC servidor** (impresora térmica 58/80 mm) y **compártala/compruebe
   que imprime** desde Windows.
2. En **Administración → Impresión**, cambie el modo a `REAL` y elija la impresora del servidor.
   (O bien: `PEDIDOS_PRINT_MODE=REAL` y `PEDIDOS_PRINTER_NAME="Nombre exacto"`.)
3. Ajuste el **ancho de papel** (caracteres) si el texto se corta.

### Solución de problemas de impresión

- **Estado "Error – No hay impresora configurada":** el modo es `REAL` pero no hay impresora
  seleccionada o el nombre no coincide. Verifique el nombre exacto en Windows.
- **Estado "Error – No se encontró la impresora X":** revise el nombre y que esté instalada en el
  servidor.
- **Estado "Desconocido":** la cola de Windows aceptó/​rechazó el trabajo sin confirmación; revise
  la cola (`Dispositivos e impresoras`) y **reintente** desde `/impresion`.
- **La impresora está en "Pausado" o "Desconectado":** reactívela en la cola de Windows.
- **Sale en blanco o cortado:** ajuste el ancho de papel y el tamaño de fuente/configuración de la
  impresora.

---

## 12. Módulo de bloqueo temporal ("hombre muerto")

- **Intervalo inicial:** 2 meses (configurable). La fecha de vencimiento se guarda **en el
  servidor** (persistente).
- Al vencer, las pantallas operativas muestran el mensaje exacto
  **"Página bloqueada, ingrese contraseña"** y las APIs de negocio quedan bloqueadas. Solo quedan
  accesibles las rutas de desbloqueo/autenticación.
- **Administración** muestra: estado (activo/bloqueado), próxima fecha de validación, período de
  renovación, cambio de contraseña principal, gestión de contraseña temporal, cambio de correo
  administrativo, cambio de intervalo, activar/desactivar el módulo e historial de validación.
- Reglas: `nextDueAt = lastValidationAt + intervalMonths`; al cambiar el intervalo se recalcula
  desde la última validación.
- **El bloqueo nunca destruye pedidos, productos ni configuración.**

### Recuperación (requiere acceso al servidor)

No existe una "contraseña maestra oculta". La recuperación se hace **con acceso al servidor**:

- Arranque con argumentos (o variables) de recuperación, o use el `MaintenanceRunner`:
  `--pedidos.security.recover` restablece la fecha de vencimiento; agregue
  `--pedidos.security.recover.reset-password` para restablecer la contraseña desde la fuente segura.
- La recuperación **no borra** datos.

---

## 13. Seguridad

- Validación y autorización **en el servidor**; el cliente nunca define precios, totales ni estados
  privilegiados.
- Persistencia con **JPA y consultas parametrizadas** (sin SQL concatenado con datos de usuario).
- **CSRF:** token entregado por `GET /api/session` y enviado por el cliente en la cabecera
  `X-XSRF-TOKEN` en las operaciones mutantes. Verificado: sin token la operación responde **HTTP 403**.
- **XSS:** el frontend escapa el contenido antes de insertarlo en el DOM.
- **Protección de intentos:** límite de intentos de contraseña (throttle) en el acceso
  administrativo/desbloqueo.
- Cookie de sesión **HttpOnly** y **SameSite=strict**.
- **Consola H2 desactivada**; sin secretos en logs, HTML ni API.
- Sin credenciales por defecto en el despliegue final: la credencial inicial debe cambiarse.

---

## 14. Concurrencia e integridad de datos

- **Numeración de pedidos** `PED-001`, `PED-002`, ... persistente y **segura bajo concurrencia**:
  la fila única de `order_sequence` se bloquea con `SELECT ... FOR UPDATE` dentro de la transacción.
  El número no se reutiliza aunque el pedido se cancele.
  - La fila de secuencia se **crea de forma idempotente al iniciar** para evitar una carrera al
    insertarla entre varios dispositivos.
  - `LOCK_TIMEOUT=10000` en H2 hace que las transacciones **esperen** su turno en lugar de fallar.
- **Ocupación de mesas:** bloqueo pesimista de la fila de la mesa.
- **Edición de pedidos:** control de versión optimista (`@Version`).
- **Idempotencia:** `client_token` único por pedido evita duplicados por reintentos.
- Se **persiste y confirma** antes de informar éxito al usuario.

---

## 15. Base de datos, copias de seguridad y restauración

- Base: **H2 en modo archivo** en `./data/pedidos.mv.db`. `ddl-auto=update` (no destructivo).
- **Copia de seguridad online y consistente** con `BACKUP TO` de H2 (no copia un archivo activo a
  mano). Se genera en `./data/backups`. Los registros de copia se ven en Administración.
- Nunca se **borra** la base de datos automáticamente.

### Restaurar una copia

1. **Detenga la aplicación.**
2. Conserve la base actual (`data/pedidos.mv.db`) como respaldo.
3. Restaure mediante H2 (`DROP ALL OBJECTS; RUNSCRIPT FROM '<copia>.zip'`) o
   reemplazando el archivo de base de datos por el de la copia.

---

## 16. Pruebas automatizadas

Ejecutar:
```bat
mvnw.cmd test
```

Cobertura (31 pruebas, todas en verde):

| Área | Archivo de prueba |
|---|---|
| Arranque del contexto | `PedidosApplicationTests` |
| Pedidos: tipos, totales en backend, mesa, estados, idempotencia, edición | `OrderFlowTest` |
| Numeración + concurrencia | `OrderNumberingConcurrencyTest` |
| Catálogo, sabores/variantes/áreas, inactivos, distribución sin doble cobro | `CatalogAndAreasTest` |
| Bloqueo ("hombre muerto"), desbloqueo, contraseñas, que no destruye pedidos | `LockModuleTest` |
| Impresión: simulación, dedupe, reintento, por área | `PrintServiceTest` |
| Copia de seguridad verificable | `BackupServiceTest` |

> Las pruebas usan una base H2 **aislada** en `target/test-db` y carpetas en `target/`, de modo que
> **no tocan los datos reales** de `./data`.

---

## 17. Pantallas

| Ruta | Descripción |
|---|---|
| `/` | Panel del día (pedidos activos, mesas, ventas, impresión, backups). |
| `/mesas` | Selección de tipo y mesa / nuevo pedido. |
| `/pedido` | Toma de pedido y edición (productos, carrito, totales, impresión). |
| `/areas` | Vistas de preparación por área. |
| `/pedidos` | Listado e historial con filtros. |
| `/catalogo` | Configuración del catálogo (requiere administración). |
| `/admin` | Administración y seguridad. |
| `/impresion` | Estado de impresión. |
| `/sistema` | Estado del sistema (BD, impresión, bloqueo, backups) y datos de LAN. |

---

## 18. Notas y limitaciones

- **Impresión:** en este entorno **no** había una impresora física disponible; la impresión se
  probó en **modo simulación** y se reporta como tal. Configure `REAL` para usar una impresora real.
- **Acceso LAN:** pensado para red local. No exponer directamente a Internet.
- **Precios demo:** edite el catálogo con los precios y productos reales.
- **Un solo servidor:** el modelo de numeración y mesas está protegido en la base; el despliegue
  previsto es un único PC servidor.

---

## 19. Licencia / uso

Proyecto de ejemplo para uso interno del restaurante. Ajuste productos, precios y configuración
antes de operar en producción.
