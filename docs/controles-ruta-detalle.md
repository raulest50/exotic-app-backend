# Detalle informativo de controles en la ruta

Los indicadores de proceso y calidad abren un modal de solo lectura en los tres
diagramas: configuración de categoría, planes de proceso y planes de calidad.
El modal muestra la configuración vigente, sus mediciones y sus parámetros.
Cuando el indicador agrupa varios planes, se elige uno mediante un selector.
El detalle se consulta al abrirlo o al seleccionar otro plan.

## Contrato añadido

`GET /api/controles/ruta/planes/{planId}/versiones/{numero}`

- `numero` es el número de versión mostrado por el indicador, no su ID interno.
- Responde con el DTO existente `PlanResponse`, con una sola versión vigente.
- Usa los mismos permisos de lectura que `GET /api/controles/ruta`: acceso a
  Parámetros por categoría, Planes de control de proceso o Planes de Calidad.
- Permite consultar ambos tipos de control desde cualquiera de esos diagramas.
- No expone borradores ni retiradas. Si el indicador corresponde a una versión
  que dejó de estar vigente, devuelve 404 e indica actualizar los indicadores.
- No modifica rutas, planes ni resultados de ejecución.

Los contratos existentes no cambian. La suite contractual del repositorio
`exotic-app-e2e` puede incorporar esta nueva ruta de lectura.

## Despliegue y validación

Desplegar primero el backend y después el frontend. No requiere migraciones.
Builds, pruebas automatizadas y validación en navegador quedan pendientes;
no se ejecutan en este equipo por las instrucciones del workspace.

Validar en staging:

1. Abrir los tres diagramas y pulsar los indicadores de proceso y calidad,
   incluida la anotación de salida final.
2. Consultar un plan numérico y uno booleano: magnitud, unidad, objetivo, límites,
   valor esperado, muestras y unidades por muestra deben coincidir con su versión.
3. Con varios planes en un indicador, cambiar de selección y comprobar que
   se muestran las mediciones del plan seleccionado.
4. Abrir y cerrar con ratón y teclado, también con la ruta expandida o en
   pantalla completa. Comprobar que el foco vuelve al indicador y que no se
   seleccionan, arrastran o eliminan elementos del diagrama.
5. Probar con acceso de lectura a un solo módulo, un usuario sin acceso y un
   indicador cuya versión haya sido retirada o reemplazada tras cargar la ruta.
6. Verificar el mensaje de error y reintento ante un fallo de consulta, y que
   el detalle de versiones del listado de planes conserva su contenido.
