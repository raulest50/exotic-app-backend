package exotic.app.planta.service.agentassistant;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Offline context contract: no application context, entity instances, network or database. */
class AgentAssistantContextTest {
    private static final String DOMAIN = "Dominio ficticio de Exotic: OP de producción.";
    private static final String MODEL = "Modelo ficticio: OrdenProduccion relaciona producto y lote.";
    private static final Pattern INCLUDED_CLASS = Pattern.compile("(?m)^Clase: `([^`]+)`\\s*$");
    private static final Pattern EXCLUDED_CLASS = Pattern.compile("(?m)^- Clase excluida: `([^`]+)`; motivo: (.+)\\.$");

    @Test void assemblesBothDocumentsBeforeTheOnlyExecutableCatalog() {
        var context = context(DOMAIN, MODEL);
        String prompt = context.instructions();
        assertThat(prompt).contains("\nCONOCIMIENTO_DE_DOMINIO\n" + DOMAIN
                + "\nMODELO_RELACIONAL_DE_NEGOCIO_SOLO_CONOCIMIENTO\n" + MODEL
                + "\nCATALOGO_EJECUTABLE_ACTUAL\n");
        assertThat(prompt).endsWith(ReadOnlyAssistantSql.catalog());
        assertThat(prompt.indexOf("\nCATALOGO_EJECUTABLE_ACTUAL\n"))
                .isEqualTo(prompt.lastIndexOf("\nCATALOGO_EJECUTABLE_ACTUAL\n"));
    }

    @Test void defaultLoaderIncludesTheActualPackagedDocuments() throws IOException {
        var context = new AgentAssistantContext();
        assertThat(context.instructions()).contains(
                readDocument("agentassistant/exotic-domain-context.md").strip(), modelDocument().strip());
        assertThat(context.instructions()).endsWith(ReadOnlyAssistantSql.catalog());
    }

    @Test void versionIsStableForIdenticalContentAndChangesWithEitherDocument() {
        var baseline = context(DOMAIN, MODEL);
        assertThat(baseline.version()).matches("[0-9a-f]{64}");
        assertThat(context(DOMAIN, MODEL).version()).isEqualTo(baseline.version());
        assertThat(context(DOMAIN + " Revisión de dominio.", MODEL).version()).isNotEqualTo(baseline.version());
        assertThat(context(DOMAIN, MODEL + " Revisión de modelo.").version()).isNotEqualTo(baseline.version());
    }

    @Test void invalidDocumentsFailClosedInEitherContextPosition() {
        for (Resource invalid : List.of(resource(""), resource(" \n\t "),
                new ByteArrayResource(new byte[]{(byte) 0xc3, (byte) 0x28}))) {
            assertInvalid(invalid, true);
            assertInvalid(invalid, false);
        }
        assertInvalid(null, true);
        assertInvalid(null, false);
    }

    @Test void resourceFailuresDoNotExposeTheResourceDescriptionOrOriginalException() {
        Resource unavailable = new AbstractResource() {
            @Override public String getDescription() { return "private-fixture-location"; }
            @Override public InputStream getInputStream() throws IOException {
                throw new IOException("private-fixture-diagnostic");
            }
        };
        assertInvalid(unavailable, true);
        assertInvalid(unavailable, false);
    }

    @Test void sizeLimitsApplyToUtf8BytesAndAcceptTheExactBoundary() {
        String domainAtLimit = "á".repeat(16_384);
        String modelAtLimit = "á".repeat(131_072);
        var accepted = context(domainAtLimit, modelAtLimit);
        assertThat(accepted.instructions()).contains(domainAtLimit, modelAtLimit);
        assertInvalid(resource(domainAtLimit + "a"), true);
        assertInvalid(resource(modelAtLimit + "a"), false);
    }

    @Test void assembledContextCannotMakeTheCompilerAcceptBusinessTablesOrWrites() {
        // Business knowledge is deliberately richer than the executable product/material view.
        var context = context(DOMAIN, MODEL);
        assertThat(context.instructions()).contains("OrdenProduccion");
        for (String sql : List.of("SELECT orden_id FROM ordenes_produccion LIMIT 10",
                "UPDATE productos SET nombre = 'fixture'",
                "SELECT nombre FROM asistente_productos; DELETE FROM productos")) {
            assertThatThrownBy(() -> ReadOnlyAssistantSql.compile(sql)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(ReadOnlyAssistantSql.compile("SELECT nombre FROM asistente_productos LIMIT 10")).isNotNull();
    }

    @Test void everyJpaEntityIsIncludedOrExplicitlyExcludedWithAReason() throws Exception {
        String document = modelDocument();
        Set<String> entities = annotatedModelClasses(Set.of(Entity.class.getName()));
        List<String> included = INCLUDED_CLASS.matcher(document).results().map(match -> match.group(1)).toList();
        List<String> excluded = EXCLUDED_CLASS.matcher(document).results().map(match -> match.group(1)).toList();
        assertThat(entities).isNotEmpty();
        assertThat(included).isNotEmpty().doesNotHaveDuplicates();
        assertThat(excluded).isNotEmpty().doesNotHaveDuplicates();
        assertThat(included).doesNotContainAnyElementsOf(excluded);
        var classified = new TreeSet<>(included);
        classified.addAll(excluded);
        assertThat(classified).as("Every new entity needs a reviewed inclusion or exclusion")
                .containsExactlyInAnyOrderElementsOf(entities);
        assertThat(excluded).containsAll(entities.stream().filter(name -> name.startsWith("exotic.app.planta.model.users.")
                || name.endsWith(".SuperMasterConfig") || name.endsWith(".RegistroIdempotenciaControl")).toList());
    }

    @Test void sourceFingerprintRequiresReviewWhenJpaFieldsOrMappingsChange() throws Exception {
        String document = modelDocument();
        var fingerprint = Pattern.compile("(?m)^Huella de fuentes JPA \\(SHA-256; UTF-8; LF\\): `([0-9a-f]{64})`\\.$")
                .matcher(document);
        assertThat(fingerprint.find()).as("A reviewed source fingerprint must be recorded").isTrue();
        String recorded = fingerprint.group(1);
        assertThat(fingerprint.find()).isFalse();
        Set<String> sources = new TreeSet<>();
        for (String name : annotatedModelClasses(Set.of(Entity.class.getName(), Embeddable.class.getName(),
                MappedSuperclass.class.getName()))) {
            String topLevel = name.split("\\$", 2)[0];
            sources.add("src/main/java/" + topLevel.replace('.', '/') + ".java");
        }
        assertThat(sources).isNotEmpty();
        var digest = MessageDigest.getInstance("SHA-256");
        for (String source : sources) {
            String normalized = Files.readString(Path.of(source), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").replace('\r', '\n');
            digest.update((source + "\n").getBytes(StandardCharsets.UTF_8));
            digest.update(normalized.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
        }
        assertThat(HexFormat.of().formatHex(digest.digest()))
                .as("Review the curated snapshot and exclusions before accepting changed JPA sources")
                .isEqualTo(recorded);
    }

    @Test void publishedMembersExistAndNeverLinkToExcludedEntitiesOrSecretControls() throws Exception {
        String document = modelDocument();
        Set<String> excluded = new TreeSet<>(EXCLUDED_CLASS.matcher(document).results().map(match -> match.group(1)).toList());
        Map<String, String> sections = includedSections(document);
        assertThat(sections).isNotEmpty();
        Pattern secretControl = Pattern.compile("(?i).*(password|passwd|credential|secreto|token|username|useragent).*|(?i).*json$|(?i)^ip$");
        for (var section : sections.entrySet()) {
            Class<?> entity = Class.forName(section.getKey(), false, getClass().getClassLoader());
            Map<String, Field> fields = persistentFields(entity);
            List<String> published = publishedMembers(section.getValue());
            assertThat(section.getValue()).as(section.getKey()).contains("\nAtributos: ", "\nRelaciones: ");
            assertThat(published).as(section.getKey()).doesNotHaveDuplicates();
            assertThat(fields.keySet()).as("Published members must exist in " + section.getKey()).containsAll(published);
            assertRelationshipMetadata(section.getValue(), fields);
            for (Field field : fields.values()) {
                if (secretControl.matcher(field.getName()).matches() || referencesExcluded(field.getGenericType(), excluded)
                        || field.getType().equals(byte[].class)) {
                    assertThat(published).as("Omit private member %s.%s", section.getKey(), field.getName())
                            .doesNotContain(field.getName());
                }
            }
        }
    }

    @Test void knownSensitiveBusinessMetadataIsOmittedWhileBusinessStructureRemains() throws Exception {
        Map<String, String> sections = includedSections(modelDocument());
        assertExcludedMembers(sections, "exotic.app.planta.model.producto.costos.CargaCostosLote",
                "tokenHash", "tokenExpiraEn", "intentosToken", "generacionesToken", "ultimaGeneracionTokenEn");
        assertExcludedMembers(sections, "exotic.app.planta.model.produccion.OrdenProduccion",
                "canceladaPor", "canceladaPorUsername", "canceladaPorNombreCompleto", "requerimientosMaterialesJson");
        assertExcludedMembers(sections, "exotic.app.planta.model.organizacion.personal.IntegrantePersonal",
                "nombres", "apellidos", "celular", "direccion", "email", "nombreContactoEmergencia",
                "celularContactoEmergencia", "estadoCivil", "numeroHijos", "numeroCuentaBancaria", "banco", "salario");
        assertExcludedMembers(sections, "exotic.app.planta.model.compras.Proveedor", "url", "rutUrl", "camaraUrl");
        assertExcludedMembers(sections, "exotic.app.planta.model.produccion.batchrecord.BatchRecordFirma",
                "firmante", "usernameFirmante", "nombreFirmante", "cedulaFirmante", "rolFirmante", "ipOrigen", "userAgent");
        assertExcludedMembers(sections, "exotic.app.planta.model.produccion.batchrecord.BatchRecord", "ultimoErrorDocumental");
        assertThat(publishedMembers(sections.get("exotic.app.planta.model.produccion.OrdenProduccion")))
                .contains("ordenId", "estadoOrden", "producto", "cantidadProducir");
        assertThat(publishedMembers(sections.get("exotic.app.planta.model.organizacion.personal.IntegrantePersonal")))
                .contains("id", "cargo", "estado");
    }

    private static AgentAssistantContext context(String domain, String model) {
        return new AgentAssistantContext(resource(domain), resource(model));
    }

    private static ByteArrayResource resource(String value) {
        return new ByteArrayResource(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String modelDocument() throws IOException {
        return readDocument("agentassistant/exotic-relational-model.md");
    }

    private static String readDocument(String path) throws IOException {
        try (InputStream stream = new ClassPathResource(path).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> annotatedModelClasses(Set<String> annotations) throws IOException {
        var resolver = new PathMatchingResourcePatternResolver();
        var readers = new CachingMetadataReaderFactory(resolver);
        Set<String> names = new TreeSet<>();
        for (Resource resource : resolver.getResources("classpath*:exotic/app/planta/model/**/*.class")) {
            var metadata = readers.getMetadataReader(resource).getAnnotationMetadata();
            if (annotations.stream().anyMatch(metadata::hasAnnotation)) names.add(metadata.getClassName());
        }
        return names;
    }

    private static Map<String, String> includedSections(String document) {
        Map<String, String> sections = new HashMap<>();
        for (String section : document.split("(?m)^### ")) {
            var declaration = INCLUDED_CLASS.matcher(section);
            if (declaration.find()) sections.put(declaration.group(1), section);
        }
        return sections;
    }

    private static List<String> publishedMembers(String section) {
        assertThat(section).as("An included entity section is required").isNotNull();
        var lines = Pattern.compile("(?m)^(?:Atributos|Relaciones): (.+)$").matcher(section);
        List<String> members = new ArrayList<>();
        while (lines.find()) {
            for (String entry : lines.group(1).split(";")) {
                var member = Pattern.compile("^\\s*([\\p{L}_$][\\p{L}\\p{N}_$]*)\\s*(?::|->)").matcher(entry);
                if (member.find()) members.add(member.group(1));
            }
        }
        return members;
    }

    private static Map<String, Field> persistentFields(Class<?> entity) {
        Map<String, Field> fields = new HashMap<>();
        for (Class<?> type = entity; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!field.isSynthetic() && !Modifier.isStatic(field.getModifiers()) && !Modifier.isTransient(field.getModifiers())
                        && !field.isAnnotationPresent(Transient.class)) fields.putIfAbsent(field.getName(), field);
            }
        }
        return fields;
    }

    private static boolean referencesExcluded(Type type, Set<String> excluded) {
        if (type instanceof Class<?> target) return excluded.contains(target.getName());
        if (type instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) if (referencesExcluded(argument, excluded)) return true;
        } else if (type instanceof GenericArrayType array) return referencesExcluded(array.getGenericComponentType(), excluded);
        else if (type instanceof WildcardType wildcard) {
            for (Type bound : wildcard.getUpperBounds()) if (referencesExcluded(bound, excluded)) return true;
            for (Type bound : wildcard.getLowerBounds()) if (referencesExcluded(bound, excluded)) return true;
        }
        return false;
    }

    private static void assertRelationshipMetadata(String section, Map<String, Field> fields) {
        var relations = Pattern.compile("(?m)^Relaciones: (.+)$").matcher(section);
        Map<String, Class<? extends java.lang.annotation.Annotation>> cardinalities = Map.of(
                "ManyToOne", jakarta.persistence.ManyToOne.class, "OneToOne", jakarta.persistence.OneToOne.class,
                "OneToMany", jakarta.persistence.OneToMany.class, "ManyToMany", jakarta.persistence.ManyToMany.class);
        while (relations.find()) {
            if (relations.group(1).strip().equals("sin relaciones propias publicadas.")) continue;
            for (String entry : relations.group(1).split(";")) {
                var relation = Pattern.compile("^\\s*([\\p{L}_$][\\p{L}\\p{N}_$]*) -> ([\\p{L}_$][\\p{L}\\p{N}_$]*) \\[(ManyToOne|OneToOne|OneToMany|ManyToMany)\\]\\.?\\s*$")
                        .matcher(entry);
                assertThat(relation.matches()).as("Unrecognized relationship metadata: " + entry).isTrue();
                Field field = fields.get(relation.group(1));
                assertThat(field).as(entry).isNotNull();
                assertThat(field.isAnnotationPresent(cardinalities.get(relation.group(3)))).as(entry).isTrue();
                Type target = field.getGenericType();
                if (target instanceof ParameterizedType parameterized) target = parameterized.getActualTypeArguments()[0];
                assertThat(target).as(entry).isInstanceOf(Class.class);
                assertThat(((Class<?>) target).getSimpleName()).as(entry).isEqualTo(relation.group(2));
            }
        }
    }

    private static void assertExcludedMembers(Map<String, String> sections, String name, String... excluded) {
        assertThat(publishedMembers(sections.get(name))).as(name).doesNotContain(excluded);
    }

    private static void assertInvalid(Resource invalid, boolean domain) {
        String resourcePath = domain ? "agentassistant/exotic-domain-context.md" : "agentassistant/exotic-relational-model.md";
        assertThatThrownBy(() -> new AgentAssistantContext(domain ? invalid : resource(DOMAIN),
                domain ? resource(MODEL) : invalid))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("El recurso de contexto del asistente no está disponible o no es válido: " + resourcePath)
                .hasNoCause();
    }
}
