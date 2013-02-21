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

package org.projectodd.polyglot.core;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.projectodd.polyglot.core.util.BuildInfo;

/**
 * Build/version information provider.
 * 
 * @author Toby Crawley
 *   */
public class ProjectInfo {

    public ProjectInfo(String projectName, String propertiesPath) throws IOException {
        this.projectName = projectName;
        this.buildInfo = new BuildInfo( getClass().getClassLoader(), propertiesPath );
    }
    
    /**
     * Retrieve the version of the project.
     * 
     * <p>
     * The version is typically a string that could be used as part of a maven
     * artifact coordinate, such as <code>1.0.1</code> or
     * <code>2.x.incremental.4</code>.
     * </p>
     */
    public String getVersion() {
        return getComponentValue( this.projectName, "version" );
    }

    /**
     * Retrieve the git commit revision use in this build.
     */
    public String getRevision() {
        return getComponentValue( this.projectName, "build.revision" );
    }

    /**
     * Retrieve the build number, if built by our CI server.
     */
    public String getBuildNumber() {
        return getComponentValue( this.projectName, "build.number" );
    }

    /**
     * Retrieve the user who performed the build.
     * 
     */
    public String getBuildUser() {
        return getComponentValue( this.projectName, "build.user" );
    }

    public List<String> getComponentNames() {
        return this.buildInfo.getComponentNames();
    }

    public Map<String, String> getComponentBuildInfo(String componentName) {
        return this.buildInfo.getComponentInfo( componentName );
    }

    public String getComponentValue(String component, String key) {
        return this.buildInfo.get( component, key );
    }
    
    protected String formatOutput(String label, String value) {
    
        StringBuffer output = new StringBuffer( "  " );
        output.append( label );
        int length = output.length();
        if (length < 20) {
            for (int i = 0; i < 20 - length; i++) {
                output.append( '.' );
            }
        }
    
        output.append( ' ' );
        output.append( value );
    
        return output.toString();
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    private String projectName;
    private BuildInfo buildInfo;

}
