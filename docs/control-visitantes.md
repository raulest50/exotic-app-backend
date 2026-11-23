# Registro de ingresos y firma opcional

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
