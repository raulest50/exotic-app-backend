package exotic.app.planta.config.runtime;

import exotic.app.planta.service.bi.InformeProduccionAreasAssembler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModuleTimeoutPolicyTest {

    private static final String ROOT = "app.module-timeouts.";
    private static final String OPERATION = ".bi.production-areas.";
    private static final String STAGING_TIMEOUT = ROOT + "testing" + OPERATION
            + "environments.staging.transaction-seconds";

    @ParameterizedTest
    @CsvSource({
            "local, testing",
            "staging, testing",
            "production, production"
    })
    void selectsTheCatalogBudgetForEachResolvedEnvironment(String environment, String group) {
        int expected = catalogInteger("staging".equals(environment)
                ? STAGING_TIMEOUT : transactionKey(group));

        runner(environment).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ApplicationRuntimeEnvironmentResolver.class)
                    .getCurrentEnvironment().value()).isEqualTo(environment);
            assertThat(context.getBean(ModuleTimeoutPolicy.class)
                    .transactionSeconds("bi", "production-areas")).isEqualTo(expected);
        });
    }

    @Test
    void distinguishesProductionFromTestingEvenWhenTheirCatalogDefaultsCoincide() {
        int productionBudget = catalogInteger(transactionKey("production")) + 1;
        int testingBudget = catalogInteger(transactionKey("testing"));
        Consumer<Map<String, Object>> distinctProduction = catalog ->
                catalog.put(transactionKey("production"), productionBudget);

        runner("local", distinctProduction).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ModuleTimeoutPolicy.class)
                    .transactionSeconds("bi", "production-areas")).isEqualTo(testingBudget);
        });
        runner("production", distinctProduction).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ModuleTimeoutPolicy.class)
                    .transactionSeconds("bi", "production-areas")).isEqualTo(productionBudget);
        });
    }

    @Test
    void explicitProductionWinsOverAConflictingStagingProfile() {
        int productionBudget = catalogInteger(transactionKey("production"));

        runner("production")
                .withPropertyValues("spring.profiles.active=staging")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("staging"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ApplicationRuntimeEnvironmentResolver.class)
                            .getCurrentEnvironment().value()).isEqualTo("production");
                    assertThat(context.getBean(ModuleTimeoutPolicy.class)
                            .transactionSeconds("bi", "production-areas")).isEqualTo(productionBudget);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"production", "testing"})
    void rejectsAMissingBaseBudgetInEitherGroupAtStartup(String group) {
        runner("local", catalog -> catalog.remove(transactionKey(group))).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("app.module-timeouts");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"production", "testing"})
    void rejectsAMissingRequiredOperationInEitherGroupAtStartup(String group) {
        runner("local", catalog -> catalog.keySet().removeIf(key ->
                key.startsWith(ROOT + group + OPERATION))).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("app.module-timeouts");
        });
    }

    @ParameterizedTest
    @MethodSource("invalidBaseBudgets")
    void rejectsNonPositiveOrMalformedBudgetsInEitherGroupAtStartup(String group, String value) {
        runner("local", catalog -> catalog.put(transactionKey(group), value)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("app.module-timeouts");
        });
    }

    @ParameterizedTest
    @MethodSource("invalidEnvironmentBudgets")
    void alsoValidatesEnvironmentOverridesInUnusedGroupsAtStartup(String group, String value) {
        String environment = "production".equals(group) ? "production" : "staging";
        String key = ROOT + group + OPERATION + "environments." + environment + ".transaction-seconds";

        runner("local", catalog -> catalog.put(key, value)).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("app.module-timeouts");
        });
    }

    @Test
    void rejectsAnUnmappedModuleOrOperationInsteadOfReturningAFallback() {
        runner("local").run(context -> {
            assertThat(context).hasNotFailed();
            ModuleTimeoutPolicy policy = context.getBean(ModuleTimeoutPolicy.class);
            assertThatThrownBy(() -> policy.transactionSeconds("unmapped-module", "production-areas"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unmapped-module");
            assertThatThrownBy(() -> policy.transactionSeconds("bi", "unmapped-operation"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unmapped-operation");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "staging", "production"})
    void springResolvesTheRealAssemblerTransactionExpression(String environment) throws NoSuchMethodException {
        Method method = InformeProduccionAreasAssembler.class.getMethod(
                "construir", LocalDate.class, LocalDate.class);
        int expected = catalogInteger("staging".equals(environment)
                ? STAGING_TIMEOUT
                : transactionKey("production".equals(environment) ? "production" : "testing"));

        runner(environment).run(context -> {
            assertThat(context).hasNotFailed();
            TransactionAttributeSource source = context.getBean(TransactionAttributeSource.class);
            assertThat(source).isInstanceOf(AnnotationTransactionAttributeSource.class);
            // Spring supplies the BeanFactory EmbeddedValueResolver to this infrastructure bean.
            TransactionAttribute attribute = source.getTransactionAttribute(
                    method, InformeProduccionAreasAssembler.class);
            assertThat(attribute).isNotNull();
            assertThat(attribute.getTimeout()).isEqualTo(expected);
            assertThat(attribute.isReadOnly()).isTrue();
            assertThat(attribute.getPropagationBehavior())
                    .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        });
    }

    private static Stream<Arguments> invalidBaseBudgets() {
        return Stream.of("production", "testing").flatMap(group ->
                Stream.of("0", "-1", "not-a-number").map(value -> Arguments.of(group, value)));
    }

    private static Stream<Arguments> invalidEnvironmentBudgets() {
        return invalidBaseBudgets();
    }

    private static String transactionKey(String group) {
        return ROOT + group + OPERATION + "transaction-seconds";
    }

    private static int catalogInteger(String key) {
        Object value = loadCatalog().get(key);
        assertThat(value).as("Required value in module-timeouts.yml: %s", key).isNotNull();
        return Integer.parseInt(value.toString());
    }

    private static ApplicationContextRunner runner(String environment) {
        return runner(environment, catalog -> { });
    }

    private static ApplicationContextRunner runner(
            String environment, Consumer<Map<String, Object>> catalogChanges) {
        return new ApplicationContextRunner()
                .withUserConfiguration(TimeoutConfiguration.class)
                .withPropertyValues("app.runtime-environment=" + environment)
                .withInitializer(context -> {
                    context.getEnvironment().getPropertySources()
                            .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    context.getEnvironment().getPropertySources()
                            .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    Map<String, Object> catalog = loadCatalog();
                    catalogChanges.accept(catalog);
                    context.getEnvironment().getPropertySources()
                            .addLast(new MapPropertySource("module-timeout-catalog", catalog));
                });
    }

    private static Map<String, Object> loadCatalog() {
        Map<String, Object> catalog = new LinkedHashMap<>();
        try {
            for (var source : new YamlPropertySourceLoader().load(
                    "module-timeouts", new ClassPathResource("module-timeouts.yml"))) {
                if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                    throw new IllegalStateException("The timeout catalog must expose named properties.");
                }
                for (String name : enumerable.getPropertyNames()) {
                    catalog.put(name, enumerable.getProperty(name));
                }
            }
            return catalog;
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot load module-timeouts.yml", ex);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ModuleTimeoutProperties.class)
    @EnableTransactionManagement
    @Import({ModuleTimeoutPolicy.class, ApplicationRuntimeEnvironmentResolver.class})
    static class TimeoutConfiguration {
    }
}
