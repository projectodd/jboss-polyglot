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
import java.util.concurrent.ScheduledExecutorService;

import org.jboss.logging.Logger;
import org.projectodd.polyglot.core.util.TimeInterval;
import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.TriggerListener;

public class BaseTriggerListener implements TriggerListener {
    public static final String TRIGGER_LISTENER_NAME = BaseTriggerListener.class.getSimpleName(); 
    
    @Override
    public String getName() {
        return TRIGGER_LISTENER_NAME;
    }

    @Override
    public void triggerFired(Trigger trigger, final JobExecutionContext jobExecutionContext) {

    }

    @Override
    public boolean vetoJobExecution(Trigger trigger, final JobExecutionContext jobExecutionContext) {
        BaseTriggerListener.registerWatchDog(jobExecutionContext);

        return true;
    }

    private static void registerWatchDog(final JobExecutionContext jobExecutionContext) {

        TimeInterval delay = (TimeInterval) jobExecutionContext.getJobDetail().getJobDataMap().get("timeout");

        if (delay.interval > 0) {
            //TODO Replace ExecutorService by JBossThreadPool
            ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
            
            service.schedule(new Runnable() {
                public void run() {

                    log.info("|||||||||||||||| Trying to interrupt the job |||||||||||||||| ");
                    try {
                        ((InterruptableJob) jobExecutionContext.getJobInstance()).interrupt();
                    } catch (Exception e) {
                        log.error("Interruption failed", e);
                    }


                }
            }, delay.interval, delay.unit);
        }
    }

    @Override
    public void triggerMisfired(Trigger trigger) {
        // TODO include some action here
    }

    @Override
    public void triggerComplete(Trigger trigger, JobExecutionContext jobExecutionContext, int i) {
        // TODO include some action here
    }

    private static final Logger log = Logger.getLogger("org.projectodd.polyglot.jobs");


}
