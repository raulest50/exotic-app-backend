# Registro de ingresos, salidas y firma opcional

El formulario público `/visitantes-registro` pertenece a Gestión Humana. Requiere
tipo de ingreso, área/persona a visitar y autorización de datos; cargo/empresa es
opcional. El autorregistro es para mayores de edad. Los ingresos de menores deben
gestionarse en recepción mediante un procedimiento representado, fuera de este flujo.

## Configuración pendiente de Novum

Antes de habilitar envíos, configurar en el entorno del backend:

| Variable | Información real que debe entregar Novum |
| --- | --- |
| `VISITANTES_POLITICA_DATOS_URL` | URL HTTPS pública de su política de tratamiento vigente |
| `VISITANTES_CONTACTO_DATOS` | Correo o dirección del responsable para consultas y reclamos |
| `VISITANTES_TELEFONO_DATOS` | Teléfono del responsable |

No hay valores ficticios por defecto. Si falta información, el aviso responde
`disponible=false` y el POST devuelve 503 sin insertar registros. Aplicar V135 y
configurar estos datos mediante el procedimiento autorizado del entorno; esta
implementación no modifica servicios remotos ni aplica migraciones.

Novum debe conservar las versiones históricas de su política aprobada y facilitar
su consulta. Conviene publicar cada versión bajo una URL identificable; conservar
la URL y el hash del aviso no archiva el contenido de una política externa mutable.

## Firma y evidencia

La firma es una imagen opcional, con autorización específica previa e independiente
del consentimiento general. Puede enviarse el registro sin firma. No autentica la
identidad y no se presenta como firma digital certificada.

El servidor guarda el texto exacto y la versión aceptados, la fecha del servidor,
y, cuando se autoriza firma, el PNG normalizado con su SHA-256 y el texto de esa
autorización. El hash detecta cambios de contenido; no acredita quién firmó. No se
guardan presión, velocidad, trazos, IP ni huellas del dispositivo. Históricos
permanecen sin evidencia nueva: no se atribuye consentimiento retroactivo.

El PNG se valida a 900×300 píxeles y máximo 128 KiB, se decodifica con dimensiones
comprobadas previamente y se recodifica sin metadatos. JSON público máximo 192 KiB.
El listado usa selección explícita sin binarios ni textos completos de consentimiento.
La imagen sólo se obtiene en `GET /gestion-humana/visitantes/{id}/firma` con permiso
`PERSONAL_PLANTA/CONTROL_VISITANTES`, sin caché.

El aviso público se consulta sin credenciales en `GET /api/public/visitantes/aviso`.
El POST exige aceptación booleana y la versión del aviso mostrado; sus textos se
fijan en servidor. Los nuevos campos y autorizaciones amplían el contrato del POST.
Desplegar frontend y backend coordinadamente; los consumidores antiguos no envían
los nuevos campos obligatorios. No se ejecutó la suite contractual E2E.

## Fundamento consultado el 23 de noviembre de 2026

La investigación de Manda recomienda una captura gráfica opcional y autorización
específica. No constituye una certificación de identidad o de cumplimiento integral.

- [Ley 1581 de 2012, arts. 8, 9 y 12](https://jurinfo.jep.gov.co/normograma/compilacion/docs/ley_1581_2012.htm): derechos, autorización previa e información del responsable.
- [Decreto 1074 de 2015, cap. 25](https://cancilleria.gov.co/sites/default/files/Normograma/docs/decreto_1074_2015_pr025.htm): autorización de sensibles, evidencia, menores, aviso y política.
- [Ley 527 de 1999, art. 7](https://normograma.dian.gov.co/dian/compilacion/docs/ley_0527_1999.htm): identificación, aprobación y confiabilidad del mecanismo. La imagen pública autodeclarada no las demuestra por sí sola.

## Independencia de módulos

`GestionHumana/Firma/FirmaCanvas.tsx`, `firmaCanvasUtils.ts` y `firma.types.ts` son
copias locales de la captura de Usuarios. Tanto Visitantes como Permisos importan
la copia de Gestión Humana. No cambian los componentes, las firmas persistidas ni
las API de Usuarios. El servicio de firma de visitantes también es independiente.

El modelo Gaphor no contiene un diagrama aplicable a esta funcionalidad. No se edita.

## Salida y trazabilidad

V136 amplía la visita existente. Clasifica los registros previos como
`SIN_SEGUIMIENTO` (sin salida inferida) y crea los nuevos como `PENDIENTE_SALIDA`.
No existe reapertura ni edición del cierre en esta versión.

`PATCH /gestion-humana/visitantes/{id}/salida` admite exclusivamente
`{"fechaSalida":"2026-11-25T13:00:00-05:00"}`. La fecha debe incluir offset.
El servidor verifica `fechaRegistro <= fechaSalida <= salidaRegistradaEn`;
obtiene `salidaRegistradaEn` de su reloj sin truncarlo y guarda instantáneas
del ID y username del usuario autenticado. Permite fechas anteriores cuando
son posteriores al ingreso. La consulta exige nivel 1 y el cierre nivel 2 de
`PERSONAL_PLANTA/CONTROL_VISITANTES`, con las excepciones administrativas vigentes.

La actualización condicional a pendiente es atómica. Un reintento devuelve el
primer cierre guardado, sin sustituir fecha ni operador. Responde 404 si no existe
la visita, 409 si es un histórico sin seguimiento y 400 por fecha inválida.
Las restricciones SQL exigen metadatos completos para `SALIO`, coherencia entre
fechas y ausencia de metadatos de salida en los otros estados. El operador se
conserva como instantánea, sin FK a una cuenta que podría cambiar o eliminarse.

El listado incluye estado, salida informada, registro del cierre y operador,
sin cargar la firma ni los textos de autorización. Ordena antes de paginar:
pendientes, salieron e históricos; dentro de cada grupo, ingreso e ID ascendentes.
Ya no admite orden configurable. Fecha, rango de horas y nombre filtran todo el
conjunto por **ingreso**: una pendiente de ayer requiere consultar ayer.

La interfaz captura fecha y hora de Colombia y muestra la demora como diferencia
entre el registro del cierre y la salida informada; no se persiste otro indicador.
Si el operador conserva los valores iniciales, el envío retiene los milisegundos
del instante inicial. Si los edita, se envía la hora indicada con offset -05:00,
independientemente de la zona del dispositivo. Tras cerrar, se vuelve a consultar
la página actual con sus filtros.

Aplicar V136 y desplegar ambos componentes coordinadamente mediante el
procedimiento autorizado. La suite contractual de `exotic-app-e2e` consume los
tipos/adaptadores: requiere revisar el nuevo resumen y la retirada del parámetro
`orden`; no se ejecutaron suites automatizadas. No se aplicó la migración ni se
verificaron cierres concurrentes en PostgreSQL durante este cambio.

### Verificación local del 25 de noviembre de 2026

- `bun run lint`: correcto.
- `bun run vite build`: correcto; mantiene el aviso de tamaño de chunks.
- `.\gradlew.bat build -x test` con Java 21: correcto, sin ejecutar pruebas.
- `bunx tsc --noEmit --project tsconfig.json`: conserva diagnósticos ajenos a
  Gestión Humana; no informó errores en los archivos de esta funcionalidad.
- Revisión manual en navegador con los componentes reales y API simulada:
  permiso visual nivel 1/2, salida inmediata, doble clic con una sola solicitud,
  rechazo de salida futura/anterior al ingreso, salida del día anterior,
  demora de 3 h 30 min, estados, filtro por nombre y cambio de página. Consola
  sin errores. Se retiraron la página y sesión ficticias y se detuvo Vite.
- La migración, los permisos efectivos de API y la concurrencia requieren
  validación ejecutada en PostgreSQL. La revisión estática de Datos y Seguridad
  fue favorable y no sustituye esa evidencia.

No se encontró un diagrama aplicable en el modelo Gaphor local; no se requieren
ajustes al alcance actualmente modelado.
