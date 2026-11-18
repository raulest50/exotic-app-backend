# AgentAssistant: API de pago y cupo estimado

Implementación del 18 de noviembre de 2026. La comunicación usa Responses API
desde Spring mediante `OpenAiAssistantGateway`, que implementa
`AssistantAiGateway`. No utiliza suscripciones, OAuth, login de Codex ni un
servidor intermediario. El frontend no configura cuentas ni recibe la API key.

## Configuración privada del backend

Configurar estas variables en la ejecución de Spring (IDE, proceso o secretos
del servicio remoto). No son variables `VITE_*` y no van en el frontend:

| Variable | Configuración |
| --- | --- |
| `OPENAI_API_KEY` | Clave privada del proyecto de OpenAI. |
| `ASSISTANT_ENABLED` | `true` para habilitar el proveedor; predeterminado `false`. |
| `ASSISTANT_BUDGET_USD` | Importe asignado a este cupo de Exotic; por ejemplo `20`. Predeterminado `0`, que impide peticiones pagadas. |
| `ASSISTANT_QUOTA_ID` | Referente estable, predeterminado `exotic-initial`. Mantenerlo al reiniciar o desplegar. |

No pegar la clave en chats, comandos, código, configuraciones compartidas del IDE
ni capturas. Los archivos `application*.properties` locales permanecen privados
y no se han leído ni modificado en esta implementación.
Los parámetros de la integración retirada `app.agent-assistant.enabled` y
`app.agent-assistant.model` no activan esta API. La nueva configuración interna
usa `app.agent-assistant.api.*`, para no reutilizar por accidente ajustes previos.

Para la ejecución local se admite además `.local/agent-assistant.properties`,
importado opcionalmente desde el directorio de trabajo del backend. Está fuera
del classpath y del artefacto compilado; `/.local/` y `*.properties` se excluyen de
Git. Este archivo se preparó en este PC con permisos NTFS explícitos para la
cuenta que ejecuta Java y SYSTEM. No se transfirió la propiedad del archivo.
No copiarlo al repositorio de despliegue ni incluirlo en imágenes.
Al ejecutar desde IntelliJ, conservar el directorio de trabajo en la raíz del
backend. Los entornos remotos continúan usando variables y secretos del servidor.

Modelo predeterminado: `gpt-5.4-mini-2026-03-17`. Tarifas Standard verificadas el
18/11/2026: entrada 0.75, entrada cacheada 0.075 y salida 4.50 USD por millón.
Si el operador cambia `ASSISTANT_MODEL`, debe elegir un modelo compatible con
Responses, Structured Outputs y `reasoning.effort=none`, y actualizar también
`ASSISTANT_INPUT_USD_PER_MILLION`, `ASSISTANT_CACHED_INPUT_USD_PER_MILLION` y
`ASSISTANT_OUTPUT_USD_PER_MILLION` según el modelo y modalidad contratados.
No se solicitan herramientas, caché de retención extendida ni niveles Fast.

Referencias: [modelo y tarifas](https://developers.openai.com/api/docs/models/gpt-5.4-mini),
[Responses](https://developers.openai.com/api/docs/guides/text),
[salida estructurada](https://developers.openai.com/api/docs/guides/structured-outputs).

## Uso y alcance

Con las variables configuradas y Flyway aplicado al arrancar Spring:

1. Entrar a Exotic con acceso vigente al asistente y consulta de Productos.
2. Abrir el panel y escribir `hola`. La respuesta se obtiene del proveedor real;
   no existe un saludo artificial ni fallback simulado.
3. Preguntar por productos o materiales. La IA propone una lectura del catálogo
   permitido, que el backend compila y valida.
4. Pulsar **Consultar información**. Se exige el permiso independiente de
   respaldo; se comprueba el archivo previo antes de ejecutar la lectura.
5. El resultado se muestra en tabla. El SQL no se representa visualmente; sigue
   formando parte del contrato técnico de propuestas y operaciones.

Se conservan límites de historial, aislamiento de propuestas por usuario,
revalidación de permisos, transacción PostgreSQL de solo lectura, consultas
parametrizadas y límites de resultados. No se permiten escrituras. Los resultados
no se envían a OpenAI. El proveedor recibe pregunta, historial textual y catálogo
permitido, con `store=false`, salida estructurada, máximo 1600 tokens de salida
y sin herramientas. No hay reintentos automáticos de peticiones pagadas.

El estado comprueba configuración y cupo, sin hacer una llamada pagada de sondeo.
La validez de la clave, acceso al modelo y disponibilidad de OpenAI se comprueban
al enviar la primera pregunta; los errores del proveedor se sanean.

## Barra de porcentaje

`AssistantUsageService` estima el consumo a partir de los tokens de entrada,
entrada cacheada y salida informados en cada respuesta. Aplica las tarifas
privadas del servidor y calcula el porcentaje restante respecto al presupuesto
asignado. El frontend recibe solo `usage.configured` y `usage.remainingPercent`;
no recibe importes, tarifas, contadores ni credenciales. Actualiza el estado al
abrir o recuperar foco y después de cada petición. El cupo es compartido entre
los usuarios del mismo backend y referente, no es una bolsa individual.

`AgentAssistantUsageRepository` guarda identificador de respuesta, referente,
modelo, tokens, costo estimado y fecha; nunca prompts, SQL, resultados o usuarios.
La PK evita contar dos veces la misma respuesta. Se registra antes de interpretar
el contenido, también si el modelo rechaza la petición o la respuesta es
incompleta. Los registros sobreviven reinicios y despliegues mientras se conserve
la base de datos. Si no puede registrarse el consumo, se suspenden nuevas
generaciones en ese proceso para no seguir gastando sin contabilidad.

La barra muestra **Cupo estimado restante**, con dos decimales. No representa el
saldo exacto de OpenAI: no incluye otros proyectos, aplicaciones o consumos que
no alcanzaran a registrarse por una caída, error de red o de base de datos.
Tampoco incorpora automáticamente recargas, impuestos, vencimiento de créditos o
cambios de tarifa. Es una referencia de Exotic, no un límite de facturación
garantizado; la última solicitud puede sobrepasar el remanente. Usar los controles
de gasto de OpenAI para límites efectivos del proveedor.

Una recarga acumulativa se refleja aumentando el presupuesto del mismo referente;
así se conserva lo ya gastado. Solo cambiar `ASSISTANT_QUOTA_ID` cuando el operador
decida iniciar un nuevo referente con una asignación explícita. Si entornos
distintos comparten la cuenta OpenAI y bases distintas, asignar a cada uno una
parte del presupuesto: sus contadores no se sincronizan entre bases.

## Persistencia y contrato

Migraciones aditivas idénticas: `db/migration/V128__add_agent_assistant_usage.sql`
y `db/migration-v1/V005__add_agent_assistant_usage.sql`. Cada perfil usa su línea;
no se modifican perfiles, migraciones anteriores, historial ni checksums.
La implementación no ha ejecutado estas migraciones contra una base de datos.

Rutas autenticadas bajo `/agent-assistant`, todas con `Cache-Control: no-store`:
`GET /access`, `GET /status`, `POST /chat`, `POST /proposals/{id}/execute` y
`GET /operations/{id}`. Solo se amplía `/status` con el objeto `usage`.
Actualizar los consumidores contractuales de `exotic-app-e2e` si requieren
comparaciones exactas del estado o de los textos anteriores.

## Validación realizada

- `gradlew.bat build -x test compileTestJava`: correcto con Java 21.
- Pruebas autorizadas: `OpenAiAssistantGatewayTest`, `AssistantUsageServiceTest`
  y `AgentAssistantServiceTest`: 28 pruebas, sin fallos. No usan red, BD ni saldo.
- Frontend: `bun run vite build` correcto; lint focalizado del asistente correcto.
- `bun run lint` global falla en `DispensacionV2Step3SeleccionOrdenes.tsx:65`
  (`react-hooks/exhaustive-deps`), ajeno a estos cambios; no se añadieron supresiones.
- Diagnóstico `bunx tsc --noEmit --project tsconfig.json`: fallos en otros módulos;
  no reporta errores en `src/pages/AgentAssistant/`.
- Ladle falla por incompatibilidad de herramientas (`Missing field moduleType`).
  Revisión visual del componente realizada con Vite y un entrypoint temporal.

Activación local completada: clave guardada directamente desde la ventana oficial,
archivo excluido de Git, compilación correcta y recarga mediante el DevTools ya
presente en IntelliJ. La comprobación de solo lectura del JMX local existente
confirmó `app.agent-assistant.api.enabled=true`, cupo asignado y `Ready=true`.
El proceso conserva Flyway y Hibernate; no se modificó su configuración.
No fue necesario crear un proceso Spring adicional ni cambiar la ejecución de IDE.

Pendiente: iniciar sesión en Exotic tras la renovación del JWT y verificar saludo
y lectura con el entorno completo. Aún no se ha consumido saldo en esta activación
ni se ha demostrado un saludo real. La revisión automática rechazó transferir la
propiedad del archivo privado por incertidumbre sobre la identidad; se acreditó
la cuenta real del proceso y se resolvió con permisos, conservando la propiedad.

## Gaphor: sincronización manual pendiente

En **AgentAssistant — integración local**, sustituir el gateway OAuth retirado
por `AssistantAiGateway` + `OpenAiAssistantGateway`; retirar el flujo de conexión
de cuentas y representar Responses API desde Spring. Añadir
`AssistantUsageService` y `repo/agentassistant/AgentAssistantUsageRepository.java`,
con dependencia a la tabla `agent_assistant_usage`, y los dos artefactos de
migración. Actualizar el estado de la integración y configuración por servidor.

En **AgentAssistant — uso de solo lectura**, conservar confirmación y respaldo;
actualizar la interfaz para no mostrar SQL y añadir el porcentaje estimado.
No se modificó `ExoticApp.gaphor`; la sincronización sigue pendiente.
