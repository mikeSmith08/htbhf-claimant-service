package uk.gov.dhsc.htbhf.claimant;

import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import uk.gov.dhsc.htbhf.claimant.scheduler.AutowiringSpringBeanJobFactory;
import uk.gov.dhsc.htbhf.claimant.scheduler.CreateCardJob;

import javax.sql.DataSource;

/**
 * Configuration for scheduling the creation of new cards.
 */
@Configuration
public class SchedulerConfig {

    /**
     * Create a job factory that is spring context aware.
     * @param applicationContext spring application context.
     * @return spring aware job factory
     */
    @Bean
    public JobFactory jobFactory(ApplicationContext applicationContext) {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    /**
     * Creates a job detail instance containing the job to create new cards.
     * @return job detail
     */
    @Bean
    public JobDetail createCardJobDetail() {
        return JobBuilder.newJob(CreateCardJob.class)
                .withIdentity("Create new cards")
                .storeDurably()
                .build();
    }

    /**
     * Trigger the job every number of minutes (value in configuration) and run indefinitely (first run is number of minutes after start up).
     * @param intervalInMinutes Number of minutes to wait between every trigger. First run will be this many minutes after startup.
     * @return the job trigger
     */
    @Bean
    public Trigger createCardTrigger(@Value("${card.new-card-interval-in-minutes}") Integer intervalInMinutes) {
        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMinutes(intervalInMinutes).repeatForever();

        return TriggerBuilder.newTrigger().forJob(createCardJobDetail())
                .withSchedule(scheduleBuilder)
                .build();
    }

    /**
     * Create a scheduler that uses use a database to retrieve and manage job details.
     * @param dataSource default spring datasource used to connect to the database
     * @param jobFactory spring aware job factory
     * @return scheduler
     */
    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(DataSource dataSource, JobFactory jobFactory, Trigger trigger) {
        SchedulerFactoryBean factory = new SchedulerFactoryBean();
        factory.setJobDetails(createCardJobDetail());
        factory.setTriggers(trigger);
        factory.setDataSource(dataSource);
        factory.setJobFactory(jobFactory);
        factory.setConfigLocation(new ClassPathResource("quartz.properties"));
        return factory;
    }
}