package edu.upb.semillero.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class AnalysisSchedulingConfiguration {
    @Bean
    public Clock analysisClock() {
        return Clock.systemUTC();
    }

    // Keep the default scheduler separate: YouTube may block during a long upload.
    @Bean(name = "taskScheduler")
    public ThreadPoolTaskScheduler taskScheduler() {
        return scheduler("video-scheduler-");
    }

    @Bean(name = "analysisScheduler")
    public ThreadPoolTaskScheduler analysisScheduler() {
        return scheduler("analysis-scheduler-");
    }

    private ThreadPoolTaskScheduler scheduler(String prefix) {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix(prefix);
        return scheduler;
    }
}
