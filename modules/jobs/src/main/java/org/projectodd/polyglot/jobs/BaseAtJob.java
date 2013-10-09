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

import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.polyglot.core.util.ClusterUtil;
import org.projectodd.polyglot.core.util.TimeInterval;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.Trigger.CompletedExecutionInstruction;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.KeyMatcher;
import org.quartz.listeners.TriggerListenerSupport;

public class BaseAtJob extends BaseJob {

    public BaseAtJob(Class<? extends Job> jobClass, 
                     String group, 
                     String name, 
                     String description, 
                     TimeInterval timeout, 
                     boolean singleton) {
        super(jobClass, group, name, description, timeout, singleton, false);
    }

    @Override
    public void start(StartContext context) throws StartException {
        this.clustered = ClusterUtil.isClustered(context.getController().getServiceContainer());
        super.start(context);
    }    

    @Override
    protected void _start() throws ParseException, SchedulerException {
        Scheduler scheduler = getScheduler();

        if (isSingleton()
                && this.clustered) {
            Map statusMap = statusMap();

            if (Status.COMPLETE.equals(statusMap.get(STATUS_KEY))) {
                log.debug("Job complete, not starting: " + coordinationContext());
                return;
            }

            if (this.repeat > 0) {
                if (statusMap.containsKey(COUNT_KEY)) {
                    Integer count = (Integer)statusMap.get(COUNT_KEY);
                    this.repeat -= count;
                    log.debug("Job repeat count updated to " + this.repeat
                                      + ": " + coordinationContext());
                    if (this.repeat < 0) {
                        log.debug("Job repeat count less than zero, not starting: "
                                          + coordinationContext());
                        return;
                    }
                } else {
                    statusMap.put(COUNT_KEY, 0);
                }
            }

            scheduler.getListenerManager().
                    addTriggerListener(new TriggerStatusListener(this),
                                       KeyMatcher.keyEquals(TriggerKey.triggerKey(getTriggerName(), getGroup())));

            statusMap.put(STATUS_KEY, Status.STARTED);

            updateStatusMap(statusMap);
        }

        initTrigger();

        scheduler.scheduleJob(buildJobDetail(), this.trigger);

    }

    public void updateStatusMap(Map m) {
        coordinationMap().put(coordinationContext(), m);
    }
                                      
    public Map<String, Object> statusMap() {
        Map<String, Object> statusMap = (Map<String, Object>)coordinationMap().get(coordinationContext());

        if (statusMap == null) {
            statusMap = new HashMap<String, Object>();
        }

        return statusMap;
    }

    public void initTrigger() {
        TriggerBuilder<Trigger> trigger = baseTrigger();
        
        if (this.startAt != null) {
            trigger.startAt( this.startAt );    
        }
                  
        if (this.endAt != null) {
            trigger.endAt( this.endAt );
        }
        
        if (this.interval > 0) {
            SimpleScheduleBuilder schedule = 
                    SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInMilliseconds( this.interval );
            if (this.repeat > 0) {
                schedule.withRepeatCount( this.repeat );
            } else {
                schedule.repeatForever();
            }
            trigger.withSchedule( schedule );
        } else if (this.repeat > 0) {
            throw new IllegalArgumentException("Attempted to schedule a job with a repeat but no interval. job: " + getName() );
        }
         
        this.trigger = trigger.build();
    }
    
    public void setStartAt(Date startAt) {
        this.startAt = startAt;
    }

    public void setEndAt(Date endAt) {
        this.endAt = endAt;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public void setRepeat(int repeat) {
        this.repeat = repeat;
    }

    @SuppressWarnings("rawtypes")
    public Injector<ConcurrentMap> getCoordinationMapInjector() {
        return this.coordinationMapInjector;
    }

    @SuppressWarnings("rawtypes")
    public ConcurrentMap coordinationMap() {
        return this.coordinationMapInjector.getValue();
    }

    public String coordinationContext() {
        return getGroup() + "." + getName();
    }

    private enum Status {
        STARTED, COMPLETE
    }

    private String STATUS_KEY = "s";
    private String COUNT_KEY = "c";

    private Trigger trigger;
    private boolean clustered = false;
    private Date startAt = null;
    private Date endAt = null;
    private long interval = 0;
    private int repeat = 0;
    @SuppressWarnings("rawtypes")
    private InjectedValue<ConcurrentMap> coordinationMapInjector = new InjectedValue<ConcurrentMap>();
    
    class TriggerStatusListener extends TriggerListenerSupport {

        @SuppressWarnings("rawtypes")
        public TriggerStatusListener(BaseAtJob job) {
            this.job = job;
        }
        
        @Override
        public String getName() {
            return "trigger-status-listener." + this.job.coordinationContext();
        }

        @Override
        public void triggerComplete(Trigger trigger, 
                                    JobExecutionContext ignored,
                                    CompletedExecutionInstruction alsoIgnored) {
            Map statusMap = this.job.statusMap();
            boolean update = false;
            
            if (!trigger.mayFireAgain()) {
                log.debug("Marking job as complete: " + this.job.coordinationContext());
                statusMap.put(STATUS_KEY, Status.COMPLETE);
                update = true;
            }

            if (statusMap.containsKey(COUNT_KEY)) {
                statusMap.put(COUNT_KEY, ((Integer)statusMap.get(COUNT_KEY)) + 1);
                update = true;
                log.debug("Updated :repeat count to " + statusMap.get(COUNT_KEY) 
                          + " for: " + this.job.coordinationContext());
            }

            if (update) {
                this.job.updateStatusMap(statusMap);
            }
        }

        private BaseAtJob job;
      }
}
