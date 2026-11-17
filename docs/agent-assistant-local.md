# AgentAssistant: prototipo local de solo lectura

Estado al 13 de noviembre de 2026: código compilado y 88 pruebas aisladas correctas.
La autorización oficial de la cuenta se completó personalmente y la CLI verificó
inferencia real OK con la sesión persistida. Sigue pendiente el recorrido sobre
una base local identificada. No se añadieron migraciones ni capacidades de escritura.

## Responsabilidades y uso

React presenta «Asistente Exotic» y envía message/history al backend Spring Boot.
No hay inicio de sesión del proveedor, cuenta, suscripción o selector de modelo
en la UI. AgentAssistantService comprueba permisos actuales, prepara el catálogo,
elige modelo y mantiene separados propuestas y resultados por usuario Exotic.
ChatGptGateway usa una sola cuenta del propietario, independiente de los IDs de
los usuarios de Exotic. No reutiliza la sesión de Codex ni esta conversación.

La IA devuelve explicación y SQL propuesto. ReadOnlyAssistantSql analiza toda la
consulta y recompila una sentencia nueva con parámetros; nunca se ejecuta el
texto recibido directamente. El usuario revisa la propuesta y confirma mediante
«Ejecutar lectura revisada». El backend exige respaldo completado y archivo legible
no vacío antes de consultar con AgentAssistantQueryRepository, en transacción
PostgreSQL explícitamente de lectura. Las filas no se agregan al historial de IA.

## Administración desde desarrollo

La herramienta AgentAssistantCli no inicia Spring, no lee application*.properties
ni se conecta a una base de datos. Detener el backend antes de administrarla:
CLI y backend requieren el mismo lock exclusivo del perfil durante toda su vida.
Con JDK 21, desde exotic-app-backend:

```powershell
.\gradlew.bat agentAssistant --args="help"
.\gradlew.bat agentAssistant --args="status"
.\gradlew.bat agentAssistant --args="login"
.\gradlew.bat agentAssistant --args="models"
.\gradlew.bat agentAssistant --args="probe"
.\gradlew.bat agentAssistant --args="logout"
```

login abre personalmente el navegador oficial de Sign in with ChatGPT y espera
hasta diez minutos el callback loopback 127.0.0.1:<puerto>/auth/callback. No imprime
la URL OAuth completa, códigos o tokens. El propietario completa el consentimiento.
probe envía únicamente una comprobación fija que pide OK, sin datos de Exotic ni
SQL. Puede fijarse el modelo mediante probe <slug> dentro de --args. status solo
inspecciona el perfil local; conectado significa credenciales restaurables, no una
inferencia ya verificada. Sin conexión devuelve código 3, que Gradle muestra como
fallo de la tarea. help no requiere perfil, navegador o red.

Las credenciales se guardan en el perfil nuevo y propio de Exotic:
%LOCALAPPDATA%/Exotic/agent-assistant/siwc-session.json en Windows; en POSIX, bajo
~/.local/share/Exotic/agent-assistant. El archivo contiene credenciales sensibles:
no versionarlo, mostrarlo, copiarlo ni incorporarlo a evidencias. Se protege mediante
ACL exclusiva del dueño (0700/0600 en POSIX), reemplazo atómico y siwc-session.lock.
No se afirma cifrado de contenido. No importa archivos de autenticación de Codex
ni el antiguo siwc-registration.json. El backend debe ejecutarse como el mismo
usuario del sistema operativo que autorizó la cuenta.

La renovación se serializa. Primero persiste un marcador de renovación pendiente;
después guarda atómicamente el reemplazo y solo entonces lo utiliza. Un fallo con
resultado incierto exige reconectar desde desarrollo: no reintenta un refresh token
potencialmente consumido tras reiniciar. logout retira la sesión local e intenta
revocación remota cuando tiene una sesión válida. Si no puede confirmarla, lo indica
sin afirmar una revocación completa. La reconexión cambia el identificador opaco
del servicio e invalida propuestas y resultados anteriores.

No existen endpoints de administración /connect, /disconnect u /operator/status.
Ningún usuario de la app configura, desconecta o inspecciona la cuenta proveedora.

## Activación del backend local

Por defecto está deshabilitado. Requiere app.agent-assistant.enabled=true y un
entorno reconocido como local. app.agent-assistant.model puede fijar el slug de
un modelo autorizado; si se omite se utiliza el primero del catálogo. No requiere
operator-user-id. El frontend no recibe el catálogo ni la identidad del propietario.

Antes del arranque, confirmar personalmente la base local de prototipado autorizada.
El arranque normal conserva Flyway habilitado y Hibernate en validación y puede
aplicar migraciones pendientes. No se cambia ni imprime application*.properties.
Solo después de esa comprobación:

```powershell
.\gradlew.bat bootRun --args="--app.agent-assistant.enabled=true --app.runtime-environment=local --server.address=127.0.0.1"
```

El frontend local se inicia con bun run dev y su selección existente usa el backend
local en 8080. No apuntarlo a producción para completar una prueba. El respaldo
existente necesita pg_dump compatible; su clave de ruta es app.backup.pg-dump-path.
Si falla o supera 150 segundos, no se ejecuta la consulta.

## Alcance y permisos

Requiere AGENT_ASSISTANT/MAIN >= 1 y PRODUCTOS/CONSULTA_PRODUCTOS >= 1, con fallback
existente a PRODUCTOS/MAIN únicamente cuando falta el tab específico. Conserva
excepciones administrativas y usuario vigente. El nivel 2 también solo lee.
Los permisos se revalidan al salir de la cola, al recibir la propuesta y antes de
los efectos posteriores. Una sola cuenta de IA no fusiona historiales o propietarios.

Catálogos virtuales: asistente_productos y asistente_materiales, derivados de
public.productos y discriminador M/S/T. Exponen identificación, nombre, tipo,
unidad, inventareable, stock mínimo y fecha; materiales añade tipo de material,
punto de reorden y consumo directo. Excluyen costos, IVA, observaciones, recetas,
proveedores, usuarios, personal y documentos. No hay inventario ni órdenes en esta
fase. El punto de reorden no equivale a existencias. El esquema real sigue pendiente
de comprobar en la base autorizada.

La gramática acepta SELECT con una fuente, columnas explícitas, filtros, agrupación,
orden y funciones enumeradas. Rechaza toda construcción no implementada, incluidas
algunas lecturas válidas en PostgreSQL. Las escrituras no están soportadas.

## Respaldo previo y límites

Se conserva el respaldo completo PostgreSQL V1 mediante BackupTotalExportService.
Exige además permiso administrativo u OPERACIONES_CRITICAS_BD/EXPORTACION_DATOS >= 1.
El asistente no concede ese privilegio: sin él devuelve BACKUP_PERMISSION_REQUIRED.
El archivo expira quince minutos después de terminar y se elimina al cerrar el
backend. No es una copia duradera ni una restauración comprobada. Su contenido
no se envía a la IA; el asistente conserva solo el ID temporal.

- Generación: una solicitud activa y otra admitida esperando hasta 30 segundos;
  máximo una por usuario; la tercera recibe 429. Transporte chat frontend: 200 s.
- Pregunta: 2.000 caracteres. Historial: 10 mensajes y 14.000 caracteres; roles
  usuario/asistente, sin filas de resultados ni instrucciones de sistema del cliente.
- Propuesta: 6.000 caracteres, vigencia diez minutos. Ejecución idempotente por ID.
- Resultado: 200 filas, 16 columnas, 200 KB de valores, 2.000 caracteres por celda;
  truncamiento explícito. Consulta: 5 s; espera de bloqueo: 500 ms.

## Contrato HTTP

Autenticación Exotic y Cache-Control: no-store. Salvo /access, exige prototipo local
habilitado. Rutas bajo /agent-assistant:

| Método y ruta | Responsabilidad |
| --- | --- |
| GET /access | Capacidades vigentes, sin habilitar escritura. |
| GET /status | Disponibilidad y connectionId opaco; sin identidad, modelos o instrucciones administrativas. |
| POST /chat | Recibe message/history; devuelve texto y propuesta opcional. |
| POST /proposals/{id}/execute | Confirma propuesta propia y registra operación idempotente. |
| GET /operations/{id} | Estado y resultado propio; no inicia ejecución nueva. |

La retirada de rutas administrativas cambia el contrato respecto de la implementación
anterior; la suite contractual de exotic-app-e2e no fue ejecutada. El callback
loopback temporal sigue separado de Spring y exige método/ruta exactos, state de
un uso, PKCE, nonce, vigencia, firma y sujeto de la identidad OAuth.

## Verificación y limitaciones

- Backend: build -x test y CLI help correctos con JDK 21.
- 88 pruebas aisladas autorizadas: SQL (31), servicio (18), protocolo OAuth (32),
  almacenamiento privado/CLI (7), sin DB ni llamadas a OpenAI. Incluyen lock real
  entre procesos, reinicio, renovación interrumpida, cola, revocación de permisos,
  propiedad de propuestas y respaldo fallido. Usan credenciales ficticias TempDir.
- Frontend: bun run vite build y ESLint de AgentAssistant correctos. bun run lint
  global conserva el fallo previo en DispensacionV2Step3SeleccionOrdenes.tsx:65
  (react-hooks/exhaustive-deps, lotes), sin supresiones añadidas.
- Revisión manual del componente real con adaptador ficticio: indisponibilidad,
  envío habilitado al estar disponible, message/history, propuesta, respaldo y
  tabla. Revisado a 1280x720 y 390x844, sin desborde horizontal ni errores de consola.
  Evidencia temporal: temp/asistente-exotic-cuenta-central.jpg en el workspace.
- Ladle falló previamente con Missing field moduleType en el wrapper de refresh
  de Vite. No se cambiaron dependencias para ocultarlo.
- Elinor revisó estáticamente almacenamiento, renovación, cierre y permisos. Se
  corrigió el aviso de revocación no confirmada tras renovación interrumpida.
  No constituye auditoría independiente ni prueba real de OAuth/JDBC. Lilah no
  fue convocada.
- CLI login completó autorización oficial con consentimiento personal. Una ejecución
  posterior de CLI probe restauró el perfil y obtuvo OK mediante inferencia real,
  sin enviar datos de Exotic ni acceder a una base. No necesita reconectar al reiniciar.
- La respuesta real llegó con framing SSE sin Content-Type. El adaptador admite
  cabecera ausente solo tras reconocer ese framing y aplica el mismo parser acotado
  que exige response.completed. No acepta JSON/HTML o respuestas parciales. Los
  nuevos casos de protocolo y la revisión estática de Elinor cubren el ajuste.
- Sigue pendiente identificar la base local autorizada y completar el recorrido
  pregunta -> propuesta -> respaldo -> PostgreSQL -> tabla. No se inició Spring.

La vista previa SIWC documenta aplicaciones locales/OSS y determinados usos privados;
no acredita que una suscripción personal pueda financiar a todos los usuarios de
una app alojada. Debe comprobarse elegibilidad y permiso del uso concreto. Un OAuth
válido sin chatgpt.tokens.use.direct no habilita inferencia. No hay cambio automático
a facturación API u otra cuenta. No se implementó relay, despliegue o exposición remota.

## Arquitectura pendiente de sincronización manual

No se modificó ExoticApp.gaphor en este ajuste. Por petición del usuario, los nuevos
diagramas conceptuales se visualizan solo en el chat. En las vistas existentes,
quedaría documentar manualmente AgentAssistantCli y AssistantCredentialStore; gestión
OAuth desde desarrollo; perfil persistente con lock y renovación; cuenta del servidor
independiente de usuarios; cola de generación; ausencia de administración en frontend
Resource; servicio que selecciona modelo. Conservar validación SQL, confirmación,
respaldo previo y resultados por solicitante. Congruencia pendiente de sincronización.

Comando de las cuatro clases expresamente autorizadas:

```powershell
.\gradlew.bat test --tests '*ReadOnlyAssistantSqlTest' --tests '*AgentAssistantServiceTest' --tests '*ChatGptGatewayTest' --tests '*AssistantCredentialStoreTest'
```

Fuentes oficiales consultadas:
[SIWC](https://developers.openai.com/siwc/token-sharing-open-source),
[registro](https://developers.openai.com/siwc/token-sharing-open-source/sign-in),
[sesiones](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions),
[inferencia](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference),
[limitaciones](https://developers.openai.com/siwc/token-sharing-open-source/preview-limitations).
