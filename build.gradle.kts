plugins {
	java
	id("org.springframework.boot") version "3.2.5"
	id("io.spring.dependency-management") version "1.1.4"
}
// fast1dev
group = "exotic-app"
version = "v1"

java {
	sourceCompatibility = JavaVersion.VERSION_21
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.session:spring-session-core")


	// added for endpoint that implements bulk load reading uploaded excel file
	implementation("org.apache.poi:poi-ooxml:5.4.0")

	implementation("org.postgresql:postgresql")

	// Flyway for database migrations (PostgreSQL support included in flyway-core 9.x)
	implementation("org.flywaydb:flyway-core")

	implementation("org.springframework.boot:spring-boot-starter-validation")

	compileOnly("org.projectlombok:lombok")
	//runtimeOnly("com.mysql:mysql-connector-j") // ya no uso mysql sino postgres pq es la disponible en render
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.testcontainers:junit-jupiter:1.20.4")
	testImplementation("org.testcontainers:postgresql:1.20.4")

	// Added line for Spring Boot DevTools
	developmentOnly("org.springframework.boot:spring-boot-devtools")

	// for email sending capabilities
	implementation("org.springframework.boot:spring-boot-starter-mail")

	// para escanear las clases, se usa en backend endpoins information
	implementation("org.reflections:reflections:0.10.2")

	// for PDF generation
	implementation("com.itextpdf:itextpdf:5.5.13.3")
	implementation("com.itextpdf:layout:7.2.5")
	implementation("com.itextpdf:kernel:7.2.5")

	// JWT dependencies for secure authentication
	implementation("io.jsonwebtoken:jjwt-api:0.11.5")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

	// Argon2 password hashing
	implementation("org.bouncycastle:bcprov-jdk15on:1.70")

	// Jackson module for Java 8 date/time types
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")


        // Dependencias JAXB eliminadas: ya no se consultan datos externos para la TRM
	
}


// tambien como ya no usare multicontainer app, posiblemente estos task aca abajo ya no sean necesarios pero
// igual los dejo de referencia.

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.register<Test>("transaccionesAlmacenLocalTest") {
	group = "verification"
	description = "Runs the local integration suite that protects the frontend TransaccionesAlmacen module."
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	shouldRunAfter(tasks.test)
	useJUnitPlatform {
		includeTags("transacciones-almacen-local")
	}
}

tasks.register<JavaExec>("buildWithoutTest") {
	group = "build"
	description = "Builds the project excluding tests"

	mainClass.set("org.gradle.wrapper.GradleWrapperMain")
	classpath = files("gradle/wrapper/gradle-wrapper.jar")
	args = listOf("build", "-x", "test")
}

tasks.register<Copy>("generateTargetJar") {
	dependsOn("buildWithoutTest")

	doFirst{
		delete(file("target/app.jar"))
	}

	from("build/libs") {
		include("*.jar")
		rename { "app.jar" }
	}
	into("target")
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
