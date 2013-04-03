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

import static org.quartz.TriggerKey.triggerKey;

import java.util.concurrent.TimeUnit;

import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.polyglot.core.util.TimeInterval;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

public abstract class BaseJob implements Service<BaseJob>, BaseJobMBean {

    public BaseJob(Class<? extends Job> jobClass, 
                   String group, 
                   String name, 
                   String description, 
                   TimeInterval timeout, 
                   boolean singleton,
                   boolean stoppedAfterDeploy) {
        this.group = group;
        this.name = name;
        this.description = description;
        this.timeout = timeout;
        this.singleton = singleton;
        this.jobClass = jobClass;
        this.stoppedAfterDeploy = stoppedAfterDeploy;
    }
    
  

    @Override
    public BaseJob getValue() throws IllegalStateException,
            IllegalArgumentException {
        return this;
    }   

    @Override
    public void start(final StartContext context) throws StartException {
        context.asynchronous();
        
        context.execute(new Runnable() {
            public void run() {
                try {
                    if (stoppedAfterDeploy) {
                        log.debugf("Skipping start of '%s' job because it's set to not start on boot", getName());
                    } else {
                        BaseJob.this.start();
                    }
                    context.complete();
                } catch (Exception e) {
                    context.failed( new StartException( e ) );
                }
            }
        });
    }

    @Override
    public void stop(StopContext context) {
        stop();
    }

    public abstract void start() throws Exception;
    
    public synchronized void stop() {
        try {
            getScheduler().unscheduleJob( triggerKey(getTriggerName(), this.group) );
        } catch (SchedulerException ex) {
            log.warn( "An error occurred stopping job " + this.name, ex );
        } 
        this.jobDetail = null;  
    }

    public void restart() throws Exception {
        if (isStarted()) {
            stop();
        }
        start();
    }

    protected JobDetail buildJobDetail() {
        this.jobDetail = JobBuilder.newJob(this.jobClass)
                .withIdentity( this.name, this.group )
                .withDescription( this.description )
                .requestRecovery()
                .build();
                
        this.jobDetail.getJobDataMap().put("timeout", timeout); 
        
        return this.jobDetail;
    }
    
    protected TriggerBuilder<Trigger> baseTrigger() {
        return TriggerBuilder.newTrigger()
                .withIdentity( getTriggerName(), getGroup() )
                .forJob( getName(), getGroup() );
    }
    
    public Scheduler getScheduler() {
        return this.jobSchedulerInjector.getValue().getScheduler();
    }

    protected String getTriggerName() {
        return this.name + ".trigger";
    }

    public synchronized boolean isStarted() {
        return this.jobDetail != null;
    }

    public synchronized String getStatus() {
        if ( isStarted() ) {
            return "STARTED";
        }
        
        return "STOPPED";
    }

    public boolean isSingleton() {
        return singleton;
    }

    public String getGroup() {
        return this.group;
    }

    public String getName() {
        return this.name;
    }

    public JobKey getKey() {
        return this.jobDetail.getKey();
    }

    public String getDescription() {
        return this.description;
    }

    public Injector<BaseJobScheduler> getJobSchedulerInjector() {
        return this.jobSchedulerInjector;
    }

    public void setTimeout(TimeInterval timeout) {
        this.timeout = timeout;
    }

    /**
     * Sets the timeout in seconds.
     */
    public void setTimeout(long timeout) {
        this.timeout = new TimeInterval( timeout, TimeUnit.SECONDS );
    }

    public long getTimeout() {
        return this.timeout.valueAs( TimeUnit.SECONDS );
    }

    public boolean isStoppedAfterDeploy() {
        return stoppedAfterDeploy;
    }

    private Class<? extends Job> jobClass;
    private String group;
    private String name;
    private String description;
    private TimeInterval timeout;
    private JobDetail jobDetail;
    private boolean singleton;
    private boolean stoppedAfterDeploy = false;

    private InjectedValue<BaseJobScheduler> jobSchedulerInjector = new InjectedValue<BaseJobScheduler>();
    
    static final Logger log = Logger.getLogger( "org.projectodd.polyglot.jobs" );
}
