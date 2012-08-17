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

package org.projectodd.polyglot.web.servlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

import org.apache.catalina.servlets.DefaultServlet;
import org.apache.naming.resources.CacheEntry;
import org.apache.naming.resources.FileDirContext;

public class StaticResourceServlet extends DefaultServlet {

    private static final long serialVersionUID = 7173759925797350928L;

    private String resourceRoot;
    private String cacheDirectory;
    private String cacheExtension;

    @Override
    public void init() throws ServletException {
        super.init();
        this.resourceRoot = getServletConfig().getInitParameter( "resource.root" );
        this.cacheDirectory = getServletConfig().getInitParameter( "cache.directory" );
        this.cacheExtension = getServletConfig().getInitParameter( "cache.extension" );
        ((FileDirContext) this.resources.getDirContext()).setAllowLinking( true );
    }

    @Override
    protected String getRelativePath(HttpServletRequest request) {
        // First look for static files
        String path = resourceRoot + super.getRelativePath( request );
        CacheEntry cacheEntry = resources.lookupCache( path );

        if (cacheEntry != null) {
            if (cacheEntry.context != null) {
                if (path.endsWith( "/" )) {
                    path = path + "index.html";
                } else {
                    path = path + "/index.html";
                }
            } else if (!cacheEntry.exists && cacheDirectory != null) {
                // If page caching is enabled and a static file wasn't found,
                // look in the page cache
                path = cacheDirectory + request.getContextPath() + super.getRelativePath( request );
                // Always check page cache content on disk since the application may
                // create or expire it at any time
                resources.getCache().unload( path );
                cacheEntry = resources.lookupCache( path );
                if (cacheEntry != null && !cacheEntry.exists) {
                    // no page cache found - try appending the cache extension to the path
                    if (path.endsWith( "/" )) {
                        path = path.substring( 0, path.length() - 1 );
                    }
                    path = path + this.cacheExtension;
                    // ensure this new path isn't cached in memory either
                    resources.getCache().unload( path );
                }
            }
        }
        return path;
    }

}
