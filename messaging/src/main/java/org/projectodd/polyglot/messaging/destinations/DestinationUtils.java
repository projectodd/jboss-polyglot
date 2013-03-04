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

import org.jboss.msc.service.ServiceName;

import java.util.ArrayList;
import java.util.List;

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

}
