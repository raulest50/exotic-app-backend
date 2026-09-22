# Validación de planes por pasos y al guardar

El formulario compartido de Calidad y Control de procesos valida Identificación
y Ubicación al pulsar Siguiente. Guardar borrador revisa nuevamente los tres pasos,
incluidas las mediciones, con las mismas funciones de validación del frontend.
Los errores identificables llevan al paso y campo correspondiente y conservan el
contenido del formulario. Anterior permite regresar sin validar.

## Disponibilidad del código

Se añaden las consultas de lectura:

- `GET /api/calidad/controles-calidad/planes/disponibilidad-codigo?codigo=...`
- `GET /api/produccion/controles-proceso/planes/disponibilidad-codigo?codigo=...`

Ambas requieren nivel 2 en la pestaña de planes del módulo correspondiente y
responden `{ "codigoNormalizado": "ENSAYO_01", "disponible": true }`.
Usan la misma normalización y búsqueda global que la creación: incluyen códigos
de ambos módulos, independientemente del estado de sus versiones. No reservan
códigos ni exponen información del plan que los ocupa.

Solo se consultan al avanzar desde Identificación al crear un plan nuevo. Al
editar un borrador o crear otra versión de un plan existente, el código sigue
siendo inmutable y no se consulta su disponibilidad. Una consulta fallida impide
avanzar y permite reintentar; cerrar el editor cancela e invalida la respuesta.

## Guardado y compatibilidad

El servidor mantiene todas las validaciones del guardado y la restricción única
`control_plan_codigo_key` creada por V101. No hay migraciones ni cambios de datos.
Un duplicado detectado antes de insertar o durante una inserción concurrente
responde HTTP 409 con los campos existentes del error y:

```json
{ "errorCode": "PLAN_CODE_ALREADY_EXISTS", "field": "codigo" }
```

El caso detectado previamente devolvía HTTP 400; ahora comparte el 409 específico
con la colisión concurrente. El frontend interpreta el identificador del error,
no el texto. Otras restricciones mantienen su error original. No se realizan
consultas después de un flush fallido; la transacción se revierte.

Las validaciones de DTO pueden incluir `field` para localizar el campo incorrecto.
Los campos nuevos se omiten cuando no aplican. Documentar estos contratos en la
suite hermana `exotic-app-e2e` si incorpora estas nuevas consultas o comprueba
el estado HTTP de los códigos duplicados.

## Validación pendiente y despliegue

Se añaden pruebas de reglas del formulario, cancelación/reintento de consultas,
normalización, permisos, duplicados y clasificación de restricciones. No se
ejecutan builds, pruebas, instalaciones ni servidores locales por las
instrucciones del workspace. La revisión local se limita a inspección estática
y `git diff --check`.

Desplegar primero backend y después frontend. En staging, comprobar en ambos módulos:

1. Código/nombre vacíos o con espacios no permiten avanzar y reciben el foco.
2. Código repetido, incluso de otro módulo o de un plan retirado, impide avanzar.
3. Código disponible permite avanzar y conserva el valor normalizado.
4. Ubicación incompleta bloquea el segundo paso; una selección válida lo permite.
5. Límites numéricos, muestreo fraccionario y escala vacía se rechazan al guardar;
   el valor booleano esperado `false` es válido.
6. Regresar, cambiar datos y guardar vuelve a revisar todo el plan.
7. Editar una versión no consulta la disponibilidad de su propio código.
8. Dos usuarios que superaron el primer paso con el mismo código: solo uno guarda;
   el otro vuelve a Identificación con un mensaje específico y sus datos intactos.
9. Fallo de red, doble clic y cierre durante la consulta no avanzan indebidamente
   ni aplican respuestas tardías; al reintentar se consulta nuevamente.
10. Los usuarios sin nivel 2 no pueden consultar disponibilidad ni crear planes.
