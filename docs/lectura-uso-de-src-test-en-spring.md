# Lectura: como usar `src/test` en un backend Spring

## Contexto de esta lectura

Esta lectura fue preparada el **20 de abril de 2026** contrastando:

- documentacion oficial reciente de **Spring Boot**
- documentacion oficial reciente de **Spring Framework**
- documentacion oficial reciente de **JUnit**
- convenciones oficiales de **Maven** y **Gradle**
- una referencia clasica de ingenieria de software sobre estrategia de pruebas: **Martin Fowler / Thoughtworks**

Tambien la aterrice a tu proyecto actual, que hoy usa:

- `Spring Boot 3.2.5`
- `Java 21`
- `Gradle Kotlin DSL`
- `spring-boot-starter-test`

Importante: la documentacion mas reciente que revise ya muestra cambios que van mas adelante que tu version. Por ejemplo, en las docs nuevas aparece `@MockitoBean`, mientras que en tu proyecto actual sigue siendo normal y valido usar `@MockBean`.

---

## 1. La idea principal

`src/test` no es un folder "extra" ni un folder opcional que este ahi por casualidad. Es el lugar reservado para el **codigo de pruebas** y los **recursos de pruebas**.

En un proyecto Java moderno, la idea general es esta:

- `src/main/java`: codigo de produccion
- `src/main/resources`: recursos de produccion
- `src/test/java`: pruebas automatizadas
- `src/test/resources`: datos, configuraciones y archivos auxiliares para pruebas

La primera idea importante es esta:

**`src/test` no existe para reemplazar `src/main`; existe para darte una segunda zona del proyecto donde puedes comprobar, validar y proteger el comportamiento de `src/main`.**

---

## 2. Un poco de historia

Algo que a veces no se explica es que `src/test` no nacio por Spring. Nacio antes, como parte de las convenciones del ecosistema Java.

### 2.1. Primero vino la convencion del build

Maven formalizo una estructura estandar de proyecto donde:

- `src/test/java` es para codigo de pruebas
- `src/test/resources` es para recursos de pruebas

Gradle luego adopto la misma idea con el source set `test`.

Eso significa que cuando ves `src/test`, realmente estas viendo una **convencion del build system** que Spring aprovecha muy bien.

### 2.2. Luego Spring lo convirtio en una capacidad de primer nivel

Spring no solo "tolera" tests: los trata como una parte central del desarrollo. Con el tiempo, Spring fue agregando:

- soporte para pruebas unitarias sin levantar el contenedor
- soporte para pruebas de integracion con `ApplicationContext`
- soporte para pruebas de web layer con `MockMvc`
- soporte para slices como `@WebMvcTest` o `@DataJpaTest`
- cacheo del contexto para que muchas pruebas sean mas rapidas

### 2.3. Spring Boot empujo esto todavia mas

Spring Boot simplifico mucho el arranque con `spring-boot-starter-test`, que te trae JUnit, Spring Test, Mockito, AssertJ, JSONassert, JsonPath y Awaitility.

En otras palabras: hoy, en Spring, probar no es una actividad secundaria. Es parte del camino normal de desarrollo.

---

## 3. Entonces, para que deberias usar `src/test`?

La respuesta corta es:

- para automatizar validaciones
- para evitar regresiones
- para documentar comportamiento
- para refactorizar con mas seguridad
- para detectar bugs antes de desplegar

La respuesta larga es que `src/test` te sirve para varias capas distintas.

### 3.1. `src/test/java`

Aqui van clases como:

- `AuthServiceTest`
- `ProductoServiceTest`
- `EliminacionesForzadasResourceTest`

Es decir: clases que ejecutan codigo y verifican resultados.

### 3.2. `src/test/resources`

Aqui van cosas como:

- `application-test.properties`
- JSON de ejemplo
- archivos SQL para sembrar datos de prueba
- fixtures
- configuraciones de JUnit
- archivos `.http` para pruebas manuales o exploratorias

En tu proyecto esto ya existe y se esta usando de una forma interesante: tienes muchos `.http` en `src/test/resources/http`.

Eso es totalmente valido, pero con una aclaracion importante:

**los `.http` sirven muy bien para exploracion, debugging y pruebas manuales; no reemplazan los tests automatizados con JUnit.**

---

## 4. Lo mas importante: no hay una sola forma correcta

Esta es probablemente la parte que mas te interesa.

La respuesta es:

**no existe una unica forma valida de usar `src/test` en Spring.**

Existen varias formas validas, y de hecho Spring documenta varias. Lo que si existe es una forma mas recomendable de combinarlas.

### 4.1. Forma 1: tests unitarios puros

Son los mas pequenos y rapidos.

Caracteristicas:

- no levantan Spring
- crean el objeto manualmente con `new`
- mockean dependencias con Mockito
- verifican logica de negocio

Ejemplo tipico:

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock AuthenticationManager authenticationManager;

    @InjectMocks AuthService authService;

    @Test
    void authenticateUser_inactiveUser_throwsException() {
        // arrange, act, assert
    }
}
```

Esto ya lo haces hoy en tu proyecto, por ejemplo en tus tests de servicios.

Cuando usarlo:

- reglas de negocio
- decisiones condicionales
- validaciones
- coordinacion entre colaboradores
- logica que no necesita Spring para ser probada

### 4.2. Forma 2: tests de slice del web layer

Aqui el objetivo no es probar toda la app, sino solo una capa.

Para controladores MVC, el caso mas comun es:

- `@WebMvcTest`
- `MockMvc`

Esto prueba:

- rutas
- codigos HTTP
- serializacion JSON
- validacion
- manejo de errores MVC

sin necesidad de levantar toda la aplicacion.

Ejemplo:

```java
@WebMvcTest(ProductoController.class)
class ProductoControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ProductoService productoService;

    @Test
    void shouldReturn200() throws Exception {
        // ...
    }
}
```

Esto tambien ya existe en tu proyecto con `EliminacionesForzadasResourceTest`.

### 4.3. Forma 3: tests de slice de persistencia

Si quieres probar JPA, queries, mapeos o comportamiento de repositorios, suele usarse:

- `@DataJpaTest`

Esto es mucho mejor que meter todo en `@SpringBootTest` por costumbre.

Cuando usarlo:

- custom queries
- relaciones JPA
- constraints
- mapeos
- comportamiento del repositorio con la base

### 4.4. Forma 4: tests de integracion completos

Cuando necesitas comprobar que varias capas trabajan juntas, usas:

- `@SpringBootTest`

Esto ya levanta la aplicacion mucho mas completa.

Es util para:

- integracion real entre varias capas
- arranque del contexto
- configuracion general
- flows importantes de negocio
- pruebas con base, seguridad, serializacion y wiring reales

Pero tiene un costo:

- es mas lento
- puede requerir mas setup
- si abusas de esta anotacion, tu suite se vuelve pesada

### 4.5. Forma 5: tests end-to-end o smoke tests

Estos son los mas grandes.

Ejemplos:

- `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- llamadas HTTP reales al servidor levantado

Sirven para validar flujos criticos de punta a punta.

No deberian ser todos tus tests. Normalmente deberian ser pocos pero muy bien elegidos.

### 4.6. Forma 6: recursos manuales y pruebas exploratorias

Tus archivos `.http` en `src/test/resources/http` entran aqui.

Son utiles para:

- reproducir bugs
- probar endpoints rapido
- dejar ejemplos de requests para el equipo
- inspeccionar respuestas

Esto es valido, pero lo ideal es verlos como complemento de:

- unit tests
- web tests
- integration tests

No como sustituto.

---

## 5. La recomendacion moderna no es escoger una sola forma, sino combinarlas

La mejor respuesta practica suele ser una mezcla.

La idea se parece a la test pyramid:

- muchos tests unitarios
- una cantidad moderada de tests de slice
- pocos tests integrales o end-to-end

Eso te da:

- feedback rapido
- buena cobertura de comportamiento
- menor costo de mantenimiento
- menos flakiness

Si haces lo contrario y todo lo vuelves `@SpringBootTest`, normalmente vas a obtener:

- tests lentos
- setup complicado
- fallos menos claros
- menor ganas de correr la suite

---

## 6. Que dicen las practicas recomendadas hoy

### 6.1. Usa el test mas pequeno que pruebe lo que te importa

Esta es una de las mejores reglas practicas.

Si una regla de negocio puede probarse con un test unitario, no necesitas levantar Spring.

Si quieres verificar rutas, validacion y JSON de un controller, `@WebMvcTest` suele ser mejor que `@SpringBootTest`.

Si quieres verificar JPA y queries, `@DataJpaTest` suele ser mejor que un full context test.

### 6.2. No dupliques pruebas sin necesidad

Una mala practica frecuente es verificar lo mismo muchas veces en varias capas.

Ejemplo malo:

- probar la regla en el service con unit test
- volver a probar la misma regla detalladamente en controller test
- volver a probar la misma regla detalladamente en full integration test

Mejor:

- el service prueba la regla
- el controller prueba contrato HTTP
- el integration test prueba el ensamblaje real del flujo

Cada prueba con una responsabilidad clara.

### 6.3. Manten espejo de paquetes cuando tenga sentido

Una practica muy comun y muy util es reflejar en `src/test/java` la estructura de `src/main/java`.

Por ejemplo:

- `src/main/java/exotic/app/planta/service/users/AuthService.java`
- `src/test/java/exotic/app/planta/service/users/AuthServiceTest.java`

Eso hace mucho mas facil encontrar la prueba de cada clase.

### 6.4. Los tests deben describir comportamiento, no implementacion privada

Un buen test responde cosas como:

- "que pasa si el usuario esta inactivo"
- "que retorna este endpoint si la operacion esta bloqueada"
- "que ocurre si la categoria cambia y hay ordenes activas"

Un mal test se obsesiona con detalles internos que pueden cambiar durante refactor.

### 6.5. Haz que los nombres cuenten la historia

JUnit moderno favorece mucho esto.

Buenas practicas:

- nombres descriptivos
- `@DisplayName`
- `@Nested` para escenarios
- `DisplayNameGenerator.ReplaceUnderscores` si te gusta un estilo mas legible

Esto mejora la lectura del reporte de pruebas.

### 6.6. Usa `src/test/resources` de forma intencional

Este folder es excelente para:

- fixtures JSON
- data de ejemplo
- scripts SQL
- configuraciones solo de test
- propiedades de perfil de test
- archivos `.http`

Una suite madura casi siempre usa `src/test/resources`, no solo `src/test/java`.

### 6.7. Cuida el costo de cargar contexto

Spring cachea contextos de prueba para acelerar ejecucion.

Eso ayuda mucho, pero igual conviene:

- no crear contextos distintos sin necesidad
- no abusar de `@DirtiesContext`
- preferir slices cuando el full context no es necesario

### 6.8. Seguridad tambien se prueba

Si tu backend usa Spring Security, no basta con solo probar el service.

Tambien conviene probar:

- `401`
- `403`
- usuarios autenticados
- roles/permisos
- filtros de seguridad

En tu proyecto veo comentada esta dependencia:

```kotlin
//testImplementation("org.springframework.security:spring-security-test")
```

Si quieres incorporar testing de seguridad en serio, este seria un muy buen siguiente paso.

---

## 7. Lo que ya estas haciendo bien en tu proyecto

Tu backend ya tiene varias senales positivas:

### 7.1. Ya tienes la dependencia correcta base

En tu `build.gradle.kts` ya tienes:

```kotlin
testImplementation("org.springframework.boot:spring-boot-starter-test")
```

y tambien:

```kotlin
tasks.withType<Test> {
    useJUnitPlatform()
}
```

Eso significa que la base tecnica para empezar bien ya existe.

### 7.2. Ya tienes tests unitarios utiles

Por ejemplo:

- `AuthServiceTest`
- `ProductoServiceTest`

Esos tests van por muy buen camino porque prueban reglas de negocio sin necesidad de levantar toda la aplicacion.

### 7.3. Ya tienes un test de web layer

`EliminacionesForzadasResourceTest` usa:

- `@WebMvcTest`
- `MockMvc`
- `@MockBean`

Eso es exactamente una de las formas recomendadas de probar controllers.

### 7.4. Ya usas `src/test/resources` activamente

Tus archivos `.http` dentro de `src/test/resources/http` tienen valor real.

Mi lectura de eso es:

- tu proyecto ya tiene una cultura incipiente de testing
- simplemente esa cultura ha estado mas cargada hacia debugging manual y pruebas puntuales
- ahora toca convertirla en una estrategia mas sistematica

---

## 8. Algo importante sobre versiones: `@MockBean` vs `@MockitoBean`

Aqui hay un detalle fino pero muy valioso.

En las docs mas recientes, Spring muestra una direccion mas nueva:

- `@MockitoBean`
- `@MockitoSpyBean`

Ademas, en Spring Boot moderno `@MockBean` aparece deprecado para remocion futura.

Pero tu proyecto hoy esta en `Spring Boot 3.2.5`.

Entonces, la recomendacion realista para ti es:

- **hoy** puedes seguir usando `@MockBean` con tranquilidad en tu version actual
- **si mas adelante actualizas a Boot 3.4+ / 4.x**, vale la pena migrar gradualmente hacia `@MockitoBean`

O sea:

**no estas haciendo nada mal por usar `@MockBean` hoy.**

Solo conviene saber hacia donde va el ecosistema.

---

## 9. Una estrategia recomendada para empezar a usar `src/test` mejor

Si yo fuera a incorporar testing contigo de forma progresiva en este backend, no intentaria hacerlo "todo de una".

Haria esto:

### Fase 1. Regla simple

A partir de ahora:

**cada vez que agregues o cambies una regla de negocio en un service, escribes al menos un test unitario.**

Eso solo ya mejora mucho el proyecto.

### Fase 2. Controllers criticos

Para cada controller importante:

- un test feliz
- un test de error
- un test de validacion

con `@WebMvcTest` y `MockMvc`.

### Fase 3. Repositorios y JPA

Cada vez que agregues:

- una query custom
- una relacion delicada
- un filtro complejo

escribes un `@DataJpaTest`.

### Fase 4. Flujos de alto valor

Escoges muy pocos flujos realmente importantes y les pones:

- `@SpringBootTest`

por ejemplo:

- autenticacion
- una operacion de negocio muy critica
- un flujo transaccional importante

---

## 10. Una propuesta concreta para tu proyecto

Si quieres empezar sin abrumarte, yo arrancaria asi.

### Semana 1

- seguir escribiendo tests unitarios de services
- normalizar nombres y estructura
- agregar `src/test/resources/application-test.properties` si aun no existe

### Semana 2

- cubrir 2 o 3 controllers criticos con `@WebMvcTest`
- revisar respuestas `200`, `400`, `403`, `404`

### Semana 3

- activar `spring-security-test`
- empezar a probar autenticacion y autorizacion

### Semana 4

- agregar pocos `@DataJpaTest` para repositorios realmente importantes
- agregar 1 o 2 `@SpringBootTest` de humo para flujos clave

---

## 11. Que formas validas existen en la practica

Si me preguntas "hay varias formas validas o una sola?", mi respuesta final es esta:

### Si, hay varias formas validas

Son validas todas estas:

- tests unitarios puros con JUnit + Mockito
- tests de controller con `@WebMvcTest`
- tests de repositorio con `@DataJpaTest`
- tests completos con `@SpringBootTest`
- tests HTTP manuales con `.http`
- tests con datos en `src/test/resources`
- organizacion espejo de paquetes
- organizacion por feature, si el equipo la prefiere

### Lo que no cambia es el criterio

Aunque haya varias formas validas, casi siempre conviene seguir estas ideas:

- usar el test mas pequeno posible
- no duplicar cobertura sin razon
- probar comportamiento observable
- mantener tests rapidos y legibles
- reservar full integration tests para lo que realmente lo merece

Entonces:

**no hay una sola forma correcta.**

Pero tampoco es "todo vale".

Hay varias formas buenas, y se distinguen por sus tradeoffs.

---

## 12. Como se veria una estructura madura

Un ejemplo razonable para tu backend podria verse asi:

```text
src/
  main/
    java/
      exotic/app/planta/...
    resources/
      application.properties

  test/
    java/
      exotic/app/planta/
        service/
          users/
            AuthServiceTest.java
        resource/
          commons/
            EliminacionesForzadasResourceTest.java
        repo/
          producto/
            ProductoRepoTest.java
        integration/
          AuthFlowIntegrationTest.java
    resources/
      application-test.properties
      fixtures/
        users/
          login-request.json
      sql/
        users-test-data.sql
      http/
        users/
          auth.http
```

No es obligatorio que sea exactamente asi.

Pero si refleja bien una idea madura:

- automatizado en `java`
- datos de soporte en `resources`
- manual/exploratorio en `http`

---

## 13. Mis recomendaciones mas concretas para ti

Si me pides resumir todo esto en recomendaciones accionables, te diria:

1. No sigas ignorando `src/test`; conviertelo en parte normal de tu flujo.
2. Empieza por services con tests unitarios, porque ahi el retorno es altisimo.
3. Usa `@WebMvcTest` para controllers en vez de saltar directo a `@SpringBootTest`.
4. Usa `src/test/resources` para fixtures, SQL, properties y tus `.http`.
5. No reemplaces JUnit con `.http`; usa ambos, cada uno en su rol.
6. Agrega `spring-security-test` cuando empieces a cubrir autenticacion y permisos.
7. Mantente atento a la evolucion del ecosistema: en versiones nuevas veras `@MockitoBean` en vez de `@MockBean`.

---

## 14. Cierre

La mejor forma de pensar `src/test` no es:

"aqui pongo pruebas cuando me sobre tiempo"

sino:

"aqui vive el mecanismo que me deja cambiar `src/main` con confianza"

Si adoptas esa mentalidad, el folder de test deja de verse como un extra y pasa a verse como una parte estructural del backend.

Y no, no existe una unica forma valida de usarlo.

Lo correcto en Spring casi siempre es combinar:

- pruebas unitarias
- pruebas de slice
- algunas pruebas de integracion
- recursos de apoyo en `src/test/resources`

Esa combinacion suele ser mucho mejor que escoger una sola estrategia para todo.

---

## 15. Fuentes consultadas

### Documentacion oficial

- Spring Boot Testing overview: <https://docs.spring.io/spring-boot/reference/testing/index.html>
- Spring Boot test scope dependencies: <https://docs.spring.io/spring-boot/reference/testing/test-scope-dependencies.html>
- Spring Boot testing Spring applications: <https://docs.spring.io/spring-boot/reference/testing/spring-applications.html>
- Spring Boot testing Spring Boot applications: <https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html>
- Spring Boot test slices appendix: <https://docs.spring.io/spring-boot/3.5/appendix/test-auto-configuration/slices.html>
- Spring Framework integration testing: <https://docs.spring.io/spring-framework/reference/testing/integration.html>
- Spring Framework MockMvc setup options: <https://docs.spring.io/spring-framework/reference/testing/mockmvc/setup-options.html>
- Spring Framework context caching: <https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html>
- Spring Framework bean overriding in tests: <https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/bean-overriding.html>
- Spring Framework `@MockitoBean`: <https://docs.spring.io/spring-framework/reference/testing/annotations/integration-spring/annotation-mockitobean.html>
- Spring Boot `@MockBean` API: <https://docs.spring.io/spring-boot/api/java/org/springframework/boot/test/mock/mockito/MockBean.html>
- Maven standard directory layout: <https://maven.apache.org/guides/introduction/introduction-to-the-standard-directory-layout.html>
- Gradle Java plugin source sets: <https://docs.gradle.org/current/userguide/java_plugin.html>
- JUnit display names: <https://docs.junit.org/current/writing-tests/display-names.html>
- JUnit nested tests: <https://docs.junit.org/current/writing-tests/nested-tests.html>

### Referencia experta de estrategia de pruebas

- Martin Fowler / Thoughtworks, "The Practical Test Pyramid": <https://martinfowler.com/articles/practical-test-pyramid.html>

---

## 16. Nota final para tu caso puntual

Tu proyecto **ya tiene base real para madurar testing**. No estas empezando desde cero.

Lo que veo es esto:

- ya tienes dependency de test
- ya tienes tests unitarios
- ya tienes al menos un web slice test
- ya usas `src/test/resources`

Entonces tu siguiente salto no es "aprender testing desde nada".

Tu siguiente salto es mas bien:

**ordenar, sistematizar y volver habitual algo que ya empezo a existir en tu backend.**
