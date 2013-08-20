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

import org.junit.Test;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class DestinationUtilsTest {
    
    @Test
    public void testValidDestionationNames() throws Exception {
        
        Map<String, String> names = new HashMap<String, String>() {
            {
                put( "/queue/foo", "queue.foo" );
                put( ".queue.foo", "queue.foo" );
                put( "queue.foo", "queue.foo" );
                put( "//queue.foo", "queue.foo" );
                put( "..queue", "queue" );
                put( "queue-fancy.pants", "queue-fancy.pants" );
            }
        };
        
        try {
            for( Map.Entry<String, String> name : names.entrySet() ) {
                //System.err.println("Checking " + name.getKey() );
                String result = DestinationUtils.getServiceName( name.getKey() ).getCanonicalName();
                //System.err.println("result " + result );   
                assertEquals( name.getValue(), result );
            }
        } catch (IllegalArgumentException e) {
            System.err.println( e );
            assert(false);
        }
        
    }

    @Test
    public void testJndiNames() throws Exception {
        assertArrayEquals(DestinationUtils.jndiNames("/queue/test", true), new String[]{"queue/test", "jboss/exported/queue/test"});
    }
}
