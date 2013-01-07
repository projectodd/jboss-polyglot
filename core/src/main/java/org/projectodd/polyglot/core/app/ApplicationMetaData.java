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

package org.projectodd.polyglot.core.app;

import java.io.File;
import java.util.Map;

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentUnit;

public abstract class ApplicationMetaData {
    public static final AttachmentKey<ApplicationMetaData> ATTACHMENT_KEY = AttachmentKey.create( ApplicationMetaData.class );
    public static final String DEFAULT_ENVIRONMENT_NAME = "development";
    
    public ApplicationMetaData(String applicationName) {
        this.applicationName = sanitize( applicationName );
    }

    public void setRoot(File root) {
        this.root = root;
    }

    public File getRoot() {
        return this.root;
    }
    
    public void explode(File root) {
        this.root = root;
        this.archive = true;
    }

    public String getApplicationName() {
        return this.applicationName;
    }

    public boolean isArchive() {
        return this.archive;
    }

    
    public void attachTo(DeploymentUnit unit) {
        unit.putAttachment( ATTACHMENT_KEY, this );
    }
    
    protected String sanitize(String name) {
        int lastSlash = name.lastIndexOf( "/" );
        if ( lastSlash >= 0 ) {
            name = name.substring( lastSlash+1 );
        }
        int lastDot = name.lastIndexOf( "." );
        if (lastDot >= 0) {
            name = name.substring( 0, lastDot );
        }
        int lastKnob = name.lastIndexOf( "-knob" );
        if (lastKnob >= 0) {
            name = name.substring( 0, lastKnob );
        }
        return name.replaceAll( "\\.", "-" );
    }
    
    public boolean isDevelopmentMode() {
        String env = this.environmentName;
        return env == null || env.trim().equalsIgnoreCase( "development" );
    }

    public void setEnvironmentName(String environmentName) {
        this.environmentName = environmentName;
    }

    public String getEnvironmentName() {
        return this.environmentName;
    }

    public void setEnvironmentVariables(Map<String, String> environment) {
        this.environment = environment;
    }

    public Map<String, String> getEnvironmentVariables() {
        return this.environment;
    }

    public void applyDefaults() {
        if (this.environmentName == null) {
            this.environmentName = DEFAULT_ENVIRONMENT_NAME;
        }
    }

    public String toString() {
        return toString( "" );
    }
    
    public String toString(String additional) {
        return "[" + this.getClass().getSimpleName() + 
                "\n  root=" + this.root + 
                "\n  environmentName=" + this.environmentName + 
                "\n  archive=" + this.archive + 
                "\n  environment=" + this.environment +
                additional + "]";
    }

    private File root;
    private String applicationName;
    private boolean archive = false;
    private String environmentName;
    private Map<String, String> environment;
}
