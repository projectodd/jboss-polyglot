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

package org.projectodd.polyglot.core.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.jar.JarFile;

import org.jboss.as.server.deployment.module.ModuleRootMarker;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.as.server.deployment.module.VFSResourceLoader;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleClassLoader;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.Resource;
import org.jboss.modules.ResourceLoader;
import org.jboss.modules.ResourceLoaderSpec;
import org.jboss.modules.ResourceLoaders;
import org.jboss.vfs.VFS;
import org.jboss.vfs.VirtualFile;

public class ResourceLoaderUtil {

    public static ResourceRoot createResourceRoot(File file, boolean mark) {
        return createResourceRoot( file.getAbsolutePath(), mark );   
    }
    
    public static ResourceRoot createResourceRoot(String path, boolean mark) {
        final ResourceRoot resource = new ResourceRoot( VFS.getChild( path ), null );
        if (mark) {
          ModuleRootMarker.mark(resource);
        }
        
        return resource;
    }
    
    public static ResourceLoaderSpec createLoaderSpec(File file) throws IOException {
        ResourceLoader loader = ResourceLoaders.createJarResourceLoader( file.getName(), new JarFile( file ) );
        return ResourceLoaderSpec.createResourceLoaderSpec( loader );
    }
    
    public static ResourceLoaderSpec createLoaderSpec(Resource resource) throws IOException {
        try {
            
            return createLoaderSpec( VFS.getChild( resource.getURL().toURI() ) );
        } catch (URISyntaxException e) {
            //ffs
            e.printStackTrace();
            
            return null;
        }
    }
    
    public static ResourceLoaderSpec createLoaderSpec(ResourceRoot resource) throws IOException {
        return createLoaderSpec( resource.getRoot() );
    }
    
    public static ResourceLoaderSpec createLoaderSpec(VirtualFile file) throws IOException {
        ResourceLoader loader = new VFSResourceLoader( file.getName(), file, false );
        return ResourceLoaderSpec.createResourceLoaderSpec( loader );
    }
    
    @SuppressWarnings("rawtypes")
    public static ResourceLoader[] getExistingResourceLoaders(Class klass) throws SecurityException, NoSuchMethodException, IllegalArgumentException, 
        IllegalAccessException, InvocationTargetException {
    
        return getExistingResourceLoaders( Module.forClass( klass ) );
    }
    
    public static ResourceLoader[] getExistingResourceLoaders(Module module) throws SecurityException, NoSuchMethodException, IllegalArgumentException, 
      IllegalAccessException, InvocationTargetException {
        return getExistingResourceLoaders( module.getClassLoader() );
    }
    
    public static ResourceLoader[] getExistingResourceLoaders(ModuleClassLoader cl) throws SecurityException, NoSuchMethodException, IllegalArgumentException, 
        IllegalAccessException, InvocationTargetException {
        
        Method method = ModuleClassLoader.class.getDeclaredMethod( "getResourceLoaders" );
        method.setAccessible( true );
        Object result = method.invoke( cl );

        return (ResourceLoader[]) result;

    }

    @SuppressWarnings("rawtypes")
    public static void refreshAndRelinkResourceLoaders(Class klass, List<ResourceLoaderSpec> loaderSpecs, boolean mergeExisting) throws 
        SecurityException, IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        
        refreshAndRelinkResourceLoaders( Module.forClass( klass ), loaderSpecs, mergeExisting );
    }
    
    public static void refreshAndRelinkResourceLoaders(Module module, List<ResourceLoaderSpec> loaderSpecs, boolean mergeExisting) throws 
        SecurityException, IllegalArgumentException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {

        List<ResourceLoaderSpec> specs = new ArrayList<ResourceLoaderSpec>();
        specs.addAll( loaderSpecs );
        
        ModuleLoader moduleLoader = module.getModuleLoader();
        
        if (mergeExisting) {
            for (ResourceLoader each : getExistingResourceLoaders( module )) {
                specs.add( ResourceLoaderSpec.createResourceLoaderSpec( each ) );
            }
        }

        Method method = ModuleLoader.class.getDeclaredMethod( "setAndRefreshResourceLoaders", Module.class, Collection.class );
        method.setAccessible( true );
        method.invoke( moduleLoader, module, specs );

        Method refreshMethod = ModuleLoader.class.getDeclaredMethod( "refreshResourceLoaders", Module.class );
        refreshMethod.setAccessible( true );
        refreshMethod.invoke( moduleLoader, module );

        Method relinkMethod = ModuleLoader.class.getDeclaredMethod( "relink", Module.class );
        relinkMethod.setAccessible( true );
        relinkMethod.invoke( moduleLoader, module );
    }

}
