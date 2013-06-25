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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.logging.Logger;
import org.jboss.msc.service.DuplicateServiceException;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceListener;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

public class DestinationUtils {

    public static String jndiName(String destinationName) {
        return cleanServiceName( destinationName ).
                replace( '.', '/' );
    }

    public static String exportedJndiName(String destinationName) {
        return "jboss/exported/" + jndiName(destinationName);
    }

    public static String[] jndiNames(String destinationName, boolean exported) {
        List<String> names = new ArrayList<String>();

        names.add(DestinationUtils.jndiName(destinationName));

        if (exported)
            names.add(DestinationUtils.exportedJndiName(destinationName));

        return names.toArray(new String[names.size()]);
    }

    public static String cleanServiceName(String destinationName) {
        return destinationName.replaceAll( "[./]", " " )
                .trim()
                .replace( ' ', '.' );
    }

    public static ServiceName getServiceName(String destinationName) {
        return ServiceName.parse( cleanServiceName( destinationName ) );
    }

    public static ServiceName destinationPointerName(DeploymentUnit unit, String name) {
        return unit.getServiceName().append( "polyglot", "messaging", "destination-pointer", name );
    }
    
    public static boolean destinationPointerExists(DeploymentUnit unit, String name) {
        return (unit.getServiceRegistry().getService(destinationPointerName(unit, name)) != null);
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ServiceName deployDestinationPointerService(DeploymentUnit unit, ServiceTarget target,
                                                              String destName, ServiceName globalName,
                                                              AtomicInteger references,
                                                              ServiceListener... listeners) {
        DestinationService service = new DestinationService(destName, references, listeners);
        ServiceName serviceName = destinationPointerName(unit, destName);
        
        try {
            target.addService(serviceName, service)
                .addDependency(globalName)
                .setInitialMode(Mode.ACTIVE)
                .install();
        } catch (DuplicateServiceException ignored) {
            log.warn(serviceName + " already started");
        }
                
        return serviceName;
    }
    
    static final Logger log = Logger.getLogger( "org.projectodd.polyglot.messaging" );
}
