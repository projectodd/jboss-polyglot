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

import org.projectodd.polyglot.core.util.TimeInterval;
import org.quartz.Job;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;

public class BaseAtJob extends BaseJob {

    public BaseAtJob(Class<? extends Job> jobClass, 
                     String group, 
                     String name, 
                     String description, 
                     TimeInterval timeout, 
                     boolean singleton) {
        super(jobClass, group, name, description, timeout, singleton);
    }

    @Override
    public synchronized void start() throws ParseException, SchedulerException {
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
             
        getScheduler().scheduleJob( buildJobDetail(), trigger.build() );
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

    private Date startAt = null;
    private Date endAt = null;
    private long interval = 0;
    private int repeat = 0;
}
