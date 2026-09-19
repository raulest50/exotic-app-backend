# Instrucciones para agentes — backend

Estas instrucciones aplican a todo el repositorio `exotic-app-backend`.

## Contexto del proyecto

- Backend Spring Boot 3.2.5 sobre Java 21, construido con Gradle (Kotlin DSL).
- Base de datos PostgreSQL. Las migraciones se gestionan con Flyway.
- Hay dos repositorios hermanos, normalmente clonados junto a este:
  `../exotic-app-frontend` y `../exotic-app-e2e`. La suite contractual de
  `exotic-app-e2e` lee directamente los `Resource` y DTO Java de este
  repositorio, así que cambiar rutas, payloads o enums puede romperla sin que
  este repositorio falle. Avisarlo al reportar el cambio.
- El código de aplicación vive bajo `src/main/java/exotic/app/planta`, separado
  en `config`, `dto`, `model`, `repo`, `resource`, `security` y `service`.
  Respetar esa separación: los `resource` exponen HTTP, los `service` contienen
  la lógica y los `repo` el acceso a datos.

## Configuración local

- `src/main/resources/application.yml` es el único archivo de configuración
  versionado. El `.gitignore` excluye `*.properties`.
- `application.properties` y `application-jwt.properties` existen solo en la
  máquina local y contienen credenciales reales de base de datos, correo, API de
  TRM y el secreto JWT. Un clon nuevo no arranca sin ellos y el repositorio no
  incluye plantillas `.example`.
- Nunca imprimir, copiar, resumir ni mover a otro archivo el contenido de
  `src/main/resources/application*.properties`. Referirse a las claves por
  nombre, nunca por valor.
- No quitar `*.properties` del `.gitignore` ni forzar el versionado de esos
  archivos con `git add -f`.

## Seguridad de migraciones de base de datos

- Flyway debe permanecer habilitado en todos los entornos, incluido el
  desarrollo local, las pruebas automatizadas, staging y producción.
- Nunca establecer `spring.flyway.enabled=false`, `SPRING_FLYWAY_ENABLED=false`
  ni un override equivalente.
- Mantener la gestión de esquema de Hibernate en modo validación con
  `spring.jpa.hibernate.ddl-auto=validate` (o
  `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`). No usar `none`, `update`, `create`
  ni `create-drop` para evadir Flyway.
- Si una migración impide el arranque, diagnosticar y corregir la migración o el
  esquema afectado. No desactivar Flyway ni la validación de Hibernate como
  workaround.
- Tratar los cambios en la configuración de migraciones, las variables de
  entorno de Render y el historial de Flyway como operaciones sensibles de base
  de datos. Requieren autorización explícita del usuario.
- Las migraciones nuevas van en `src/main/resources/db/migration` siguiendo la
  numeración existente (`V<n>__descripcion.sql`). No renumerar ni editar una
  migración ya aplicada: añadir una nueva.
- Los archivos `fix-*.sql` de la raíz son parches locales de checksum, están
  ignorados por Git y no deben commitearse.

## Validación después de cambios de código

La validación de este repositorio es **local**. No existe red de seguridad
automatizada: el workflow `.github/workflows/gradle-build.yml` dispara en la
rama `master`, la rama de trabajo es `main`, y por tanto nunca se ejecuta.
Además está configurado con `-x test`. No asumir que la CI verifica nada.

Compilar desde la raíz del repositorio:

```powershell
.\gradlew.bat build -x test
```

Ese es el gate mínimo para cualquier cambio de código.

## Pruebas

- Ejecutar pruebas únicamente cuando el usuario lo pida de forma explícita.
  Nunca correr la suite como verificación predeterminada.
- Cuando el usuario las pida, la suite completa es `.\gradlew.bat test`
  (JUnit 5). Varias pruebas usan Testcontainers y requieren un Docker en
  ejecución.
- Existe una suite acotada registrada como
  `.\gradlew.bat transaccionesAlmacenLocalTest`, que corre solo las pruebas con
  el tag `transacciones-almacen-local` y protege el módulo
  `TransaccionesAlmacen` del frontend.
- La cobertura actual es baja (31 archivos de prueba frente a 853 de
  producción). No tratar el resultado de la suite como evidencia de que un
  cambio amplio es correcto.

## Criterio de finalización

Antes de entregar un cambio de código:

- `.\gradlew.bat build -x test` debe pasar;
- las migraciones nuevas deben estar numeradas correctamente y no modificar
  migraciones ya aplicadas;
- no deben introducirse credenciales ni valores de `application*.properties` en
  el código, los commits ni el informe;
- deben preservarse los cambios locales ajenos a la tarea;
- el informe final debe enumerar los comandos ejecutados, sus resultados y
  cualquier verificación que no haya sido posible completar.
