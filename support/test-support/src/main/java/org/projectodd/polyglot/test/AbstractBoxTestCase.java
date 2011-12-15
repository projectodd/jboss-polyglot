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

package org.projectodd.polyglot.test;

import java.io.File;

public class AbstractBoxTestCase {

    public boolean isWindows() {
        return System.getProperty( "os.name" ).toLowerCase().matches( ".*windows.*" );
    }

    public String vfsAbsolutePrefix() {
        if (isWindows()) {
            return "/C:";
        }

        return "";
    }

    public String absolutePrefix() {
        if (isWindows()) {
            return "C:";
        }

        return "";
    }

    public String toVfsPath(String path) {
        if (path.startsWith( "vfs:" )) {
            return path;
        }

        if (path.startsWith( "/" )) {
            return "vfs:" + path;
        }

        if (path.matches( "^[A-Za-z]:.*" )) {
            return "vfs:/" + path;
        }

        return toVfsPath( pwd() + File.separator + path );

    }

    public File pwd() {
        return new File( System.getProperty( "user.dir" ) );
    }

}
