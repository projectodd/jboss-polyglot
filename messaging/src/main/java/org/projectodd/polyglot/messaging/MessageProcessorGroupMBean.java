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

package org.projectodd.polyglot.messaging;

import javax.jms.Destination;

public interface MessageProcessorGroupMBean {

    void start() throws Exception;

    void stop() throws Exception;

    void updateConcurrency(int concurrency) throws Exception;

    int getConcurrency();

    String getName();

    Destination getDestination();

    String getDestinationName();

    String getMessageSelector();

    String getStatus();
    
    boolean isDurable();
    
    String getClientID();

    boolean isXAEnabled();
}
