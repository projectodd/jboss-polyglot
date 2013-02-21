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

package org.projectodd.polyglot.core.as;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

import org.jboss.as.controller.Extension;
import org.jboss.logging.Logger;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleLoader;

public abstract class AbstractBootstrappableExtension implements Extension {

    public AbstractBootstrappableExtension() {
        InputStream stream = null;
        
        try {
            Properties props = new Properties();
            stream = this.getClass().getResourceAsStream( "/org/projectodd/polyglot/core/bootstrap.properties" );
            if (stream != null) {
                try {   
                    props.load( stream );
                } catch (IOException e) {
                  //really?  
                }
            } 
            String bootstrapperClassName = props.getProperty( "org.projectodd.polyglot.core.bootstrap.class" );
            
            if (bootstrapperClassName != null) {
                try {
                    Class.forName( bootstrapperClassName, true, this.getClass().getClassLoader() );
                } catch (ClassNotFoundException e) {
                    // this is ignorable in most cases
                    log.debug( "Failed to find boostrap class " + bootstrapperClassName + ": " + e );
                }
            } else { 
                log.debug(  "No bootstrap properties found, skipping bootstrap" );
            }
        } finally {
            try {
                if (stream != null) stream.close();
            } catch (IOException e) {
                //really?
            }
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
