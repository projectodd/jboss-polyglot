/*
 * Copyright 2008-2013 Red Hat, Inc, and individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 * 
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

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
    
    public BaseJobScheduler(String name, int threadCount) {
        this( name );
        this.threadCount = threadCount;
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
        
        if (this.threadCount > 0) {
            props.setProperty( StdSchedulerFactory.PROP_THREAD_POOL_PREFIX + ".threadCount", 
                    ((Integer)this.threadCount).toString() );
        }
        
        StdSchedulerFactory factory = new StdSchedulerFactory( props );
        this.scheduler = factory.getScheduler();
        this.scheduler.setJobFactory( this.jobFactory );
        this.scheduler.start();
    }

    public String getName() {
        return this.name;
    }

    public int getThreadCount() {
        return threadCount;
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

    
    public boolean isStarted() {
        boolean started = false;
    
        try {
            started = this.scheduler != null && this.scheduler.isStarted();
        } catch (SchedulerException e) {
            //ignore
        }
        
        return started;        
    }

    private String name;
    private int threadCount = -1;
    private Scheduler scheduler;
    private JobFactory jobFactory;
        
    private static final Logger log = Logger.getLogger( "org.projectodd.polyglot.jobs" );
}
