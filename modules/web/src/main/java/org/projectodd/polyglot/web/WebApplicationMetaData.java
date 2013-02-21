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

package org.projectodd.polyglot.web;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.projectodd.polyglot.core.util.TimeInterval;

public class WebApplicationMetaData {
    public static final AttachmentKey<WebApplicationMetaData> ATTACHMENT_KEY = AttachmentKey.create( WebApplicationMetaData.class );

    public void addHost(String host) {
        if (host != null && !this.hosts.contains( host )) {
            this.hosts.add( host );
        }
    }
    
    public void addHosts(List<String> hosts) {
        if (hosts != null) {
            for (String each : hosts) {
                addHost( each );
            }
        }
    }

    public List<String> getHosts() {
        return this.hosts;
    }

    public void setContextPath(String contextPath) {
        // https://issues.jboss.org/browse/TORQUE-1031
        if (contextPath != null)
            this.contextPath = contextPath.replaceAll("/*$", "").replaceAll("^/*", "/");
    }

    public String getContextPath() {
        return this.contextPath;
    }

    public void setStaticPathPrefix(String staticPathPrefix) {
        if (staticPathPrefix != null)
            this.staticPathPrefix = staticPathPrefix;
    }

    public String getStaticPathPrefix() {
        return this.staticPathPrefix;
    }

    public void attachTo(DeploymentUnit unit) {
        unit.putAttachment( ATTACHMENT_KEY, this );
    }

    /**
     * Set the session timeout for inactive web sessions.
     * 
     * <p>
     * Pass a negative interval for the timeout value to indicate <b>never</b>.
     * Not recommended.
     * </p>
     * 
     */
    public void setSessionTimeout(TimeInterval timeout) {
        this.sessionTimeout = timeout;
    }

    /**
     * Retrieve the session timeout for inactive web sessions, as seconds.
     * 
     * @return The timeout, as seconds, or <code>-1</code> indicating no
     *         timeout.
     */
    public int getSessionTimeout() {
        return (int) this.sessionTimeout.valueAs( TimeUnit.SECONDS );
    }

    private List<String> hosts = new ArrayList<String>();
    private String contextPath;
    private String staticPathPrefix;
    private TimeInterval sessionTimeout = new TimeInterval();
}
