/*
 * Copyright 2008-2011 Red Hat, Inc, and individual contributors.
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

package org.projectodd.polyglot.core.as;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

import org.jboss.as.controller.Extension;
import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;

public abstract class AbstractBootstrappableExtension implements Extension {
    
    public AbstractBootstrappableExtension()  throws ClassNotFoundException {
        
        Properties props = new Properties();
        try {
            props.load( this.getClass().getResourceAsStream( "/org/projectodd/polyglot/core/bootstrap.properties" ) );
        } catch (IOException e) {
            log.info(  "No bootstrap properties found, skipping bootstrap" );
        }
        String bootstrapperClassName = props.getProperty( "org.projectodd.polyglot.core.bootstrap.class" );
        
        if (bootstrapperClassName != null) {
            Class.forName( bootstrapperClassName );
        }
    }
    
    protected void bootstrap() {
        refresh();
        relink();
    }
    
    protected void refresh() {
        Module module = Module.forClass( getClass() );
        log.debug( "refresh: " + module );
        ModuleLoader moduleLoader = module.getModuleLoader();

        try {
            Method method = ModuleLoader.class.getDeclaredMethod( "refreshResourceLoaders", Module.class );
            method.setAccessible( true );
            method.invoke( moduleLoader, module );
        } catch (SecurityException e) {
            log.fatal( e.getMessage(), e );
        } catch (NoSuchMethodException e) {
            log.fatal( e.getMessage(), e );
        } catch (IllegalArgumentException e) {
            log.fatal( e.getMessage(), e );
        } catch (IllegalAccessException e) {
            log.fatal( e.getMessage(), e );
        } catch (InvocationTargetException e) {
            log.fatal( e.getMessage(), e );
        }
    }

    protected void relink() {
        Module module = Module.forClass( getClass() );
        log.debug( "relink: " + module );
        ModuleLoader moduleLoader = module.getModuleLoader();

        try {
            Method method = ModuleLoader.class.getDeclaredMethod( "relink", Module.class );
            method.setAccessible( true );
            method.invoke( moduleLoader, module );
        } catch (SecurityException e) {
            log.fatal( e.getMessage(), e );
        } catch (NoSuchMethodException e) {
            log.fatal( e.getMessage(), e );
        } catch (IllegalArgumentException e) {
            log.fatal( e.getMessage(), e );
        } catch (IllegalAccessException e) {
            log.fatal( e.getMessage(), e );
        } catch (InvocationTargetException e) {
            log.fatal( e.getMessage(), e );
        }
    }
    
    private static Logger log = Logger.getLogger( "org.projectodd.polyglot.core" );

}
