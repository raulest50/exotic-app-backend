# Conocimiento de negocio de Exotic

Versión inicial: 2026-11-19. Este documento describe conceptos y relaciones
comprobados en las fuentes del backend. No contiene registros reales, no acredita
el estado de una base de datos y no habilita consultas ni escrituras.

## Alcance y forma de responder

Exotic integra productos, compras, ventas, inventarios, planificación y ejecución
de producción, calidad, organización, activos y contabilidad. El modelo
relacional adjunto permite reconocer los conceptos representados en esas áreas;
la existencia de un concepto no demuestra que una función esté disponible por
interfaz, que haya datos cargados o que exista acceso ejecutable desde el asistente.

Explica el significado de negocio con lenguaje habitual. Diferencia una
explicación del modelo, una propuesta de consulta y un resultado obtenido.
Los datos actuales solo pueden conocerse a través de una consulta autorizada.
Una descripción del modelo no permite inferir cantidades, costos, disponibilidad,
estados de órdenes, personas responsables ni registros existentes.

Las preguntas generales sobre matemática, algoritmos o programación quedan fuera
del alcance. Un cálculo relacionado con datos de Exotic debe respetar las
unidades, las definiciones y el catálogo ejecutable disponible. Una cifra aportada
por el usuario es una premisa suya, no un dato verificado de la aplicación.

## Productos y terminados

- Producto es la entidad base. Material, SemiTerminado y Terminado son subtipos
  guardados mediante herencia de tabla única en productos. Sus discriminadores
  son M, S y T, respectivamente.
- «Terminado», «terminados» y «producto terminado» designan el subtipo Terminado;
  no designan cualquier producto ni el estado final de una orden de producción.
  No inventes una tabla independiente llamada terminados.
- «Semiterminado» y «producto intermedio» pueden referirse a SemiTerminado;
  confirma el contexto cuando la expresión sea ambigua.
- productoId es la identidad del producto. El nombre es una descripción y puede
  ser insuficiente para identificar inequívocamente un registro.
- La unidad de inventario es tipoUnidades. Para terminados y semiterminados
  también expresa la salida de fabricación de la receta. No sumes cantidades
  expresadas en unidades distintas sin una conversión definida y comprobada.
- stockMinimo es un umbral configurado; no es la existencia actual. inventareable
  indica si el producto pasa por almacén, no si hay saldo disponible.
- El prefijo de lote es una configuración del producto para la numeración de
  lotes. No es por sí mismo un lote existente ni identifica una orden concreta.
- Terminado puede relacionarse con insumos, proceso de producción completo,
  categoría y empaque. Conocer estas relaciones no habilita su modificación.

## OP, OF y lotes

- «OP» y «orden de producción» designan OrdenProduccion. ordenId es su clave
  entera. loteAsignado es el número de lote textual, con unicidad propia; no
  conviertas el número de lote en ordenId ni los trates como sinónimos técnicos.
- La relación JPA de OrdenProduccion está declarada sobre Producto. El servicio
  vigente de creación exige un Terminado y su versión de manufactura; no deduzcas
  de la declaración base que se puede crear una OP para cualquier subtipo.
  La definición del servicio no demuestra el contenido de registros históricos.
- loteAsignado conserva el número de lote asociado a la OP para facilitar
  consultas. El servicio de producción lo asigna a partir de batchNumber del
  Lote creado para la orden.
- «Lote» designa Lote y «número de lote» corresponde a batchNumber. El lote tiene
  una clave técnica distinta y puede relacionarse con un producto y con una orden
  de origen de compra, producción o fabricación. Esas referencias son opcionales;
  no presupongas que todo lote tiene una OP.
- «OF» y «orden de fabricación» designan OrdenFabricacion. Es un concepto distinto
  de OrdenProduccion. No intercambies OP y OF al explicar o buscar una orden.
- El estado de la orden, la dispensación de materiales y el estado de calidad del
  lote son conceptos separados. No deduzcas uno a partir del otro sin una regla
  verificada. Usa las representaciones de estado descritas en las fuentes del
  dominio; no inventes códigos, nombres ni transiciones.
- «Órdenes terminadas» se refiere al estado de las órdenes; «productos terminados»
  se refiere al subtipo de producto. Si «terminados» puede significar ambos en una
  pregunta, pide aclaración.
- La cantidad planificada en una OP no demuestra la cantidad fabricada, recibida
  en almacén o disponible para venta. La existencia actual requiere su fuente
  específica de inventario y no se obtiene del catálogo ejecutable actual.

## Proveedores y compras

- «Proveedor» designa Proveedor. id es la identificación de negocio usada por las
  operaciones del módulo, por ejemplo el NIT. pk es una clave interna diferente;
  no la muestres ni la solicites como identificación habitual del proveedor.
- Proveedor tiene campos propios y relaciones con contactos. ContactoProveedor
  es otra entidad. Modificar un proveedor no equivale automáticamente a autorizar
  cambios de contactos, documentos, órdenes de compra o condiciones históricas.
- leadTimeNegociado es un plazo de referencia en días calendario enteros, no
  negativos; null significa que no hay plazo configurado. No lo confundas con un
  indicador de plazo real calculado a partir de recepciones.
- limiteRecepcionesParcialesOcm es un límite operativo propio del proveedor. Su
  configuración se valida contra el tope global vigente; no inventes ese tope.
- Condiciones de pago del proveedor y de documentos de compra pueden conservarse
  separadamente para mantener el historial. No supongas que un cambio del
  proveedor reescribe documentos previos.
- Actualmente el asistente no tiene habilitada ninguna escritura, tampoco sobre
  Proveedor. No prepares una falsa confirmación de guardado ni describas como
  ejecutada una actualización que solo se ha solicitado.

## Uso del conocimiento relacional

El inventario relacional adjunto es un snapshot estático de las fuentes del
backend, saneado para describir el negocio. No es una introspección de la base
desplegada. Los atributos Java, nombres físicos declarados y relaciones que
contiene sirven para interpretar términos, sin autorizar SQL sobre ellos.

No completes nombres físicos ausentes mediante conjeturas de convenciones de
nombres. No inventes relaciones ni uniones a partir de nombres parecidos.
No recuperes información excluida por medio de relaciones, textos de auditoría,
documentos, configuraciones o instantáneas históricas.

El bloque CATALOGO_EJECUTABLE_ACTUAL identifica las únicas lecturas que puedes
proponer en esta versión. Si te preguntan por existencias, OP, lotes, proveedores
u otro dato fuera de ese bloque, puedes explicar el concepto y señalar que la
consulta de esos datos todavía no está disponible en el asistente. No fabriques
una aproximación sobre otra tabla. La ampliación futura del conocimiento no
modifica esta regla ni los permisos aplicados por el backend.
