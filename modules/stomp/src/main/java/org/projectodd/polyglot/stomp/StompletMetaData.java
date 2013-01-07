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

package org.projectodd.polyglot.stomp;

import java.util.Map;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.AttachmentList;
import org.jboss.as.server.deployment.DeploymentUnit;

public class StompletMetaData {
    
    public static final AttachmentKey<AttachmentList<StompletMetaData>> ATTACHMENTS_KEY = AttachmentKey.createList( StompletMetaData.class );
    
    public StompletMetaData(String name) {
        this.name = name;
    }
    
    public void attachTo(DeploymentUnit unit) {
        unit.addToAttachmentList( ATTACHMENTS_KEY, this );
    }

    public String getName() {
        return this.name;
    }

    public void setDestinationPattern(String destinationPattern) {
        this.destinationPattern = destinationPattern;
    }

    public String getDestinationPattern() {
        return this.destinationPattern;
    }

    public void setStompletConfig(Map<String, String> stompletConfig) {
        this.stompletConfig = stompletConfig;
    }

    public Map<String, String> getStompletConfig() {
        return this.stompletConfig;
    }

    private String name;
    private String destinationPattern;
    private Map<String, String> stompletConfig;

}
