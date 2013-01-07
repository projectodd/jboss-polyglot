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

import java.lang.reflect.Method;

import org.apache.catalina.connector.Connector;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

public class WebConnectorConfigService implements Service<WebConnectorConfigService> {

    public WebConnectorConfigService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public void start(StartContext context) throws StartException {
        if (this.maxThreads != null) {
            try {
                Connector connector = injectedConnector.getValue();
                Method m = connector.getProtocolHandler().getClass().getMethod( "setMaxThreads", int.class );
                m.invoke( connector.getProtocolHandler(), this.maxThreads );
            } catch (Exception e) {
                throw new StartException( e );
            }
        }
    }

    public void stop(StopContext context) {
        // nothing to do
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
    }

    public Injector<Connector> getConnectorInjector() {
        return injectedConnector;
    }

    private Integer maxThreads;
    private final InjectedValue<Connector> injectedConnector = new InjectedValue<Connector>();

}
