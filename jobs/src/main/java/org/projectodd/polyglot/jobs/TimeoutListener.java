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

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;
import org.projectodd.polyglot.core.util.TimeInterval;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;


public class TimeoutListener implements JobListener {

    public TimeoutListener(TimeInterval timeout) {
        this.timeout = timeout;
    }
    
    @Override
    public void started(final JobExecutionContext context, final NotifiableJob job) {
        
        if (this.timeout.interval > 0) {
            
            this.timeoutFuture = timeoutExecutor.schedule( new Runnable() {
                public void run() {

                    try {
                        ((InterruptableJob) context.getJobInstance()).interrupt();
                    } catch (Exception e) {
                        log.error( "Failed to interrupt job " + job.getJobKey(), e );
                    }


                }
            }, timeout.interval, timeout.unit );
        }

    }

    @Override
    public void finished(JobExecutionContext context, NotifiableJob job) {
        cancel();

    }

    @Override
    public void error(JobExecutionContext context, NotifiableJob job,
            Exception exception) {
        cancel();
    }

    @Override
    public void interrupted(NotifiableJob job) {
    }
    
    protected void cancel() {
        if (this.timeoutFuture != null) {
            this.timeoutFuture.cancel( true );
            this.timeoutFuture = null;
        }
    }
    
    private TimeInterval timeout;
    @SuppressWarnings("rawtypes")
    private Future timeoutFuture;
    private static ScheduledExecutorService timeoutExecutor = Executors.newScheduledThreadPool( 3, new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger( 1 );
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread( runnable );
            thread.setName( "torquebox-timeout-" + count.getAndIncrement() );
            return thread;
        }
        
    } );
    
    private static final Logger log = Logger.getLogger( "org.projectodd.polyglot.jobs" );
}
