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

import java.util.ArrayList;
import java.util.List;

import org.quartz.JobExecutionContext;
import org.quartz.JobKey;


public class BaseJob {
    public BaseJob(JobKey jobKey) {
        this.jobKey = jobKey;
    }

    public void addListener(JobListener listener) {
        this.listeners.add( listener );
    }
    
    public void notifyStarted(JobExecutionContext context) {
        for (JobListener each: listeners) {
            each.started( context, this );
        }
    }
    
    public void notifyFinished(JobExecutionContext context) {
        for (JobListener each: listeners) {
            each.finished( context, this );
        }
    }
    
    public void notifyError(JobExecutionContext context, Exception exception) {
        for (JobListener each: listeners) {
            each.error( context, this, exception );
        }
    }
    
    public void notifyInterrupted() {
        for (JobListener each: listeners) {
            each.interrupted( this );
        }
    }
    
    public JobKey getJobKey() {
        return jobKey;
    }

    protected JobKey jobKey;
    private List<JobListener> listeners = new ArrayList<JobListener>();
}
