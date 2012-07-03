/*
 * Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

import org.jboss.logging.Logger;
import org.projectodd.polyglot.core.util.TimeInterval;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;


public class TimeoutListener implements JobListener {

    public TimeoutListener(TimeInterval timeout) {
        this.timeout = timeout;
    }
    
    @Override
    public void started(final JobExecutionContext context, final BaseJob job) {
        
        if (this.timeout.interval > 0) {
            //TODO Replace ExecutorService by JBossThreadPool
            ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
            
            this.timeoutExecutor = service.schedule(new Runnable() {
                public void run() {

                    try {
                        ((InterruptableJob) context.getJobInstance()).interrupt();
                    } catch (Exception e) {
                        log.error("Failed to interrupt job " + job.getJobName(), e);
                    }


                }
            }, timeout.interval, timeout.unit );
        }

    }

    @Override
    public void finished(JobExecutionContext context, BaseJob job) {
        cancel();

    }

    @Override
    public void error(JobExecutionContext context, BaseJob job,
            Exception exception) {
        cancel();
    }

    @Override
    public void interrupted(BaseJob job) {
    }
    
    protected void cancel() {
        if (this.timeoutExecutor != null) {
            this.timeoutExecutor.cancel( true );
            this.timeoutExecutor = null;
        }
    }
    
    private TimeInterval timeout;
    @SuppressWarnings("rawtypes")
    private Future timeoutExecutor;
    
    private static final Logger log = Logger.getLogger( "org.projectodd.polyglot.jobs" );
}
