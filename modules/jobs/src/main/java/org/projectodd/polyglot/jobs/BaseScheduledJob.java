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

import org.projectodd.polyglot.core.util.TimeInterval;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.SchedulerException;

public class BaseScheduledJob extends BaseJob implements BaseScheduledJobMBean {
    
    public BaseScheduledJob(Class<? extends Job> jobClass, String group, String name, String description, String cronExpression, boolean singleton, boolean stoppedOnStart) {
        this( jobClass, group, name, description, cronExpression, null, singleton, stoppedOnStart );
    }
    
    public BaseScheduledJob(Class<? extends Job> jobClass, String group, String name, String description, String cronExpression, TimeInterval timeout, boolean singleton, boolean stoppedOnStart) {
        super(jobClass, group, name, description, timeout, singleton, stoppedOnStart);
        this.cronExpression = cronExpression;

    }
    
    @Override
    protected void _start() throws ParseException, SchedulerException {
        getScheduler().scheduleJob( buildJobDetail(),
                                    baseTrigger()
                                        .withSchedule( CronScheduleBuilder.cronSchedule( this.cronExpression ) )
                                        .build() );

    }

    public void reschedule(String spec) throws Exception {
        reschedule( spec, getTimeout() );
    }

    public void reschedule(long timeout) throws Exception {
        reschedule( getCronExpression(), timeout );
    }
    
    public void reschedule(String spec, long timeout) throws Exception {  
        setCronExpression( spec );
        setTimeout( timeout );
        restart();
    }
    
    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getCronExpression() {
        return this.cronExpression;
    }

    private String cronExpression;

}
