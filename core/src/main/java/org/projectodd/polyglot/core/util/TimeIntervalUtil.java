/*
 * Copyright 2008-2012 Red Hat, Inc, and individual contributors.
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

package org.projectodd.polyglot.core.util;

import java.util.concurrent.TimeUnit;

public class TimeIntervalUtil {

    public static IntervalData parseInterval(String data, TimeUnit defaultUnit) {
        TimeUnit timeUnit = defaultUnit;
        
        if (data != null) {
            data = data.trim(); 
            if (data.endsWith( "ms" )) {
                timeUnit = TimeUnit.MILLISECONDS;
                data = data.substring( 0, data.length() - 2 );
            } else if (data.endsWith( "m" )) {
                timeUnit = TimeUnit.MINUTES;
                data = data.substring( 0, data.length() - 1 );
            } else if (data.endsWith( "h" )) {
                timeUnit = TimeUnit.HOURS;
                data = data.substring( 0, data.length() - 1 );
            } else if (data.endsWith( "s" )) {
                timeUnit = TimeUnit.SECONDS;
                data = data.substring( 0, data.length() - 1 );
            } 
            
            return new IntervalData( Long.parseLong( data.trim() ), timeUnit );
        }
        
        return null;
    }
    
    public static class IntervalData {
        public long interval;
        public TimeUnit unit;
        
        public IntervalData(long interval, TimeUnit unit) {
            this.interval = interval;
            this.unit = unit;
        }
        
    }
}
