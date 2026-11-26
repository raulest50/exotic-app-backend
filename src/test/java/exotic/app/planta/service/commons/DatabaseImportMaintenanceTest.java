package exotic.app.planta.service.commons;

import exotic.app.planta.security.DatabaseImportMaintenanceFilter;
import exotic.app.planta.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseImportMaintenanceTest {
    @TempDir Path directory;

    @Test
    void theConfiguredSchedulerPausesAndResumesBusinessWorkAndShutsDownWithItsContext() throws Exception {
        var service = service();
        ThreadPoolTaskScheduler scheduler;
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch firstRun = new CountDownLatch(1);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DatabaseImportMaintenanceService.class, () -> service);
            context.register(SchedulingConfig.class);
            context.refresh();
            scheduler = context.getBean(ThreadPoolTaskScheduler.class);
            var registrar = new ScheduledTaskRegistrar();
            context.getBean(SchedulingConfig.class).configureTasks(registrar);
            var task = registrar.getScheduler().scheduleWithFixedDelay(() -> {
                executions.incrementAndGet();
                firstRun.countDown();
            }, Duration.ofMillis(20));
            try {
                assertTrue(firstRun.await(5, TimeUnit.SECONDS));
                int pausedCount;
                try (var lease = service.begin(UUID.randomUUID().toString())) {
                    pausedCount = executions.get();
                    Thread.sleep(100);
                    assertEquals(pausedCount, executions.get(), "Scheduled business work must not enter during import");
                    lease.complete();
                }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (executions.get() == pausedCount && System.nanoTime() < deadline) Thread.sleep(5);
                assertTrue(executions.get() > pausedCount, "Recurring work must resume after a successful import");
            } finally {
                task.cancel(false);
                registrar.destroy();
            }
        }
        assertTrue(scheduler.getScheduledThreadPoolExecutor().isShutdown());
    }

    @Test
    void waitsForInFlightWorkAndSkipsNewScheduledRunsUntilCompletion() throws Exception {
        var service = service();
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        AtomicInteger scheduled = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var request = executor.submit(() -> {
                try (var access = service.tryEnterWork()) {
                    assertNotNull(access);
                    running.countDown();
                    assertTrue(finish.await(5, TimeUnit.SECONDS));
                }
                return null;
            });
            assertTrue(running.await(5, TimeUnit.SECONDS));
            var imported = executor.submit(() -> {
                try (var lease = service.begin(UUID.randomUUID().toString())) {
                    lease.markDatabaseMutation();
                    lease.complete();
                }
                return null;
            });
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!service.isBlocked() && System.nanoTime() < deadline) Thread.sleep(5);
                assertTrue(service.isBlocked());
                assertFalse(imported.isDone(), "The replacement must wait for the running request");
                assertNull(service.tryEnterWork());
                service.runScheduled(scheduled::incrementAndGet);
                assertEquals(0, scheduled.get());
            } finally {
                finish.countDown();
            }
            request.get(5, TimeUnit.SECONDS);
            imported.get(5, TimeUnit.SECONDS);
        }
        assertFalse(service.isBlocked());
        service.runScheduled(scheduled::incrementAndGet);
        assertEquals(1, scheduled.get());
    }

    @Test
    void preservesFailedReplacementAcrossRestartButReopensAPreReplacementFailure() throws Exception {
        var service = service();
        try (var lease = service.begin(UUID.randomUUID().toString())) {
            assertFalse(lease.requiresRecovery());
        }
        assertFalse(service.isBlocked());
        try (var lease = service.begin(UUID.randomUUID().toString())) {
            lease.markDatabaseMutation();
            assertTrue(lease.requiresRecovery());
        }
        assertTrue(service.isBlocked());
        assertTrue(service().isBlocked());
        assertNull(service.tryEnterWork());
        assertThrows(IllegalStateException.class, () -> service.begin(UUID.randomUUID().toString()));
        assertTrue(Files.readString(marker()).contains("phase=REEMPLAZANDO"));
    }

    @Test
    void successfulCompletionRemovesTheMarkerAndDoesNotEnableProductionImports() throws Exception {
        var service = service();
        try (var lease = service.begin(UUID.randomUUID().toString())) {
            lease.markDatabaseMutation();
            lease.complete();
            assertFalse(lease.requiresRecovery());
        }
        assertFalse(Files.exists(marker()));
        try (var access = service.tryEnterWork()) { assertNotNull(access); }
        Files.writeString(marker(), "previous non-production maintenance");
        var production = new DatabaseImportMaintenanceService(marker(), false);
        assertFalse(production.isBlocked());
        try (var access = production.tryEnterWork()) { assertNotNull(access); }
        assertThrows(IllegalStateException.class, () -> production.begin(UUID.randomUUID().toString()));
    }

    @Test
    void servesOnlyReceiptGetsAndPreflightWhileBlockingBusinessAndNewImports() throws Exception {
        var service = service();
        var filter = new DatabaseImportMaintenanceFilter(service);
        String receipt = "/api/importacion-datos/backup-total-v2/jobs/" + UUID.randomUUID();
        try (var lease = service.begin(UUID.randomUUID().toString())) {
            for (String route : new String[]{"/api/auth/login", "/api/public/visitantes", "/api/inventario",
                    "/api/importacion-datos/backup-total/jobs", "/api/exportacion-datos/backup-total/jobs"}) {
                var response = request(filter, "POST", route);
                assertEquals(503, response.getStatus());
                assertTrue(response.getContentAsString().contains("DATABASE_IMPORT_MAINTENANCE"));
                assertEquals("5", response.getHeader("Retry-After"));
            }
            assertEquals(204, request(filter, "GET", receipt).getStatus());
            assertEquals(204, request(filter, "GET", receipt.replace("-v2", "")).getStatus());
            assertEquals(503, request(filter, "DELETE", receipt).getStatus());
            assertEquals(503, request(filter, "GET", receipt + "/download").getStatus());
            assertEquals(204, request(filter, "OPTIONS", "/api/inventario").getStatus());
        }
        assertEquals(204, request(filter, "POST", "/api/auth/login").getStatus());
    }

    private MockHttpServletResponse request(DatabaseImportMaintenanceFilter filter, String method, String path)
            throws Exception {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (incoming, outgoing) -> ((MockHttpServletResponse) outgoing).setStatus(204));
        return response;
    }

    private Path marker() { return directory.resolve("data/.database-import-maintenance"); }
    private DatabaseImportMaintenanceService service() { return new DatabaseImportMaintenanceService(marker(), true); }
}
