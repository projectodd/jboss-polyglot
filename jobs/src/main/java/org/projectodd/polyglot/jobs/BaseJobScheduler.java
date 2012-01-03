package org.projectodd.polyglot.jobs;

import java.io.IOException;
import java.util.Properties;

import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.spi.JobFactory;

public class BaseJobScheduler implements Service<BaseJobScheduler> {

    public BaseJobScheduler(String name) {
        this.name = name;
    }
    
    @Override   
    public BaseJobScheduler getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }   
       
    @Override
    public void start(final StartContext context) throws StartException {
        context.asynchronous();
            
        context.execute(new Runnable() {
            public void run() {
                try {
                    BaseJobScheduler.this.start();
                    context.complete();
                } catch (Exception e) {
                    context.failed( new StartException( e ) );
                }
            }
        });
    }

    @Override
    public void stop(StopContext context) {
        try {
            this.scheduler.shutdown( true );
        } catch (SchedulerException ex) {
        log.warn( "An error occured stopping scheduler for " + this.name, ex );
        }
    }
       
    public void start() throws IOException, SchedulerException {
        Properties props = new Properties();
        props.load( BaseJobScheduler.class.getResourceAsStream( "scheduler.properties" ) );
        props.setProperty( StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, getName() );

        StdSchedulerFactory factory = new StdSchedulerFactory( props );
        this.scheduler = factory.getScheduler();
        this.scheduler.setJobFactory( this.jobFactory );
        this.scheduler.start();
    }

    public String getName() {
        return this.name;
    }

    public Scheduler getScheduler() {
        return this.scheduler;
    }
    
    public JobFactory getJobFactory() {
        return this.jobFactory;
    }
    
    public void setJobFactory(JobFactory jobFactory) {
        this.jobFactory = jobFactory;
    }

    private String name;
    private Scheduler scheduler;
    private JobFactory jobFactory;
    
    private static final Logger log = Logger.getLogger( "org.projectodd.polyglot.jobs" );
}
