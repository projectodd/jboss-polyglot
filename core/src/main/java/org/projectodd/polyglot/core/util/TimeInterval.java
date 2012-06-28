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

public class TimeInterval {
    public long interval;
    public TimeUnit unit;
    
    public TimeInterval() {
        this( -1, TimeUnit.MILLISECONDS );
    }
    
    public TimeInterval(long interval, TimeUnit unit) {
        this.interval = interval;
        this.unit = unit;
    }
    
    public long valueAs(TimeUnit unit) {
        return unit.convert( this.interval, this.unit );
    }
    
    /** 
     * This fails if we drop below ms granularity.
     * @param interval
     * @return
     */
    @Override
    public boolean equals(Object interval) {
        return (interval != null) &&
                (interval instanceof TimeInterval) &&
                (valueAs( TimeUnit.MILLISECONDS ) == ((TimeInterval)interval).valueAs( TimeUnit.MILLISECONDS ));
    }
    
    public static TimeInterval parseInterval(String data, TimeUnit defaultUnit) {
        TimeInterval interval;
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
            
            interval = new TimeInterval( Long.parseLong( data.trim() ), timeUnit );
        } else {
            interval = new TimeInterval();
        }
        
        return interval;
    }
}