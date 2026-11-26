package exotic.app.planta.config;

import exotic.app.planta.service.commons.DatabaseImportMaintenanceService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;

/**
 * Pool dedicado y acotado para {@code @Scheduled}: no compite con el pool de hilos HTTP de Tomcat.
 */
@Configuration
public class SchedulingConfig implements SchedulingConfigurer {
    private final DatabaseImportMaintenanceService maintenance;

    public SchedulingConfig(DatabaseImportMaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolTaskScheduler businessTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        ThreadPoolTaskScheduler scheduler = businessTaskScheduler();
        // Admission and draining share the same gate as HTTP requests. Missed recurring runs
        // resume at their next scheduled time; none can query a schema while it is replaced.
        registrar.setTaskScheduler(new TaskScheduler() {
            private Runnable guarded(Runnable task) { return () -> maintenance.runScheduled(task); }
            @Override public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
                return scheduler.schedule(guarded(task), trigger);
            }
            @Override public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
                return scheduler.schedule(guarded(task), startTime);
            }
            @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
                return scheduler.scheduleAtFixedRate(guarded(task), startTime, period);
            }
            @Override public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
                return scheduler.scheduleAtFixedRate(guarded(task), period);
            }
            @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
                return scheduler.scheduleWithFixedDelay(guarded(task), startTime, delay);
            }
            @Override public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
                return scheduler.scheduleWithFixedDelay(guarded(task), delay);
            }
        });
    }
}
