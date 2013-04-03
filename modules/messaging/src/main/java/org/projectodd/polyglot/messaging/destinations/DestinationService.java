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

package org.projectodd.polyglot.messaging.destinations;

import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;

public class DestinationService implements Service<Void> {
    @SuppressWarnings("rawtypes")
    public DestinationService(String queueName, AtomicInteger referenceCount, ServiceListener... listeners) {
        this.listeners = listeners;
        this.referenceCount = referenceCount;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override 
    public synchronized void start(StartContext context) throws StartException {
        for(ServiceListener each : this.listeners) {
            context.getController().addListener(each);
        }
        this.referenceCount.getAndIncrement();
    }

    @Override
    public Void getValue() throws IllegalStateException,
            IllegalArgumentException {
        return null;
    }

    @Override
    public void stop(StopContext context) {
        this.referenceCount.getAndDecrement();
    }

    @SuppressWarnings("rawtypes")
    private ServiceListener[] listeners;
    private AtomicInteger referenceCount;
}
