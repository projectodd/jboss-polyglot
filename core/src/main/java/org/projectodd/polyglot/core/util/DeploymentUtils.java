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

import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentUnit;

/**
 * Utilities class for deployments.
 * @author mdobozy
 *
 */
public class DeploymentUtils {

    private static final AttachmentKey<Boolean> KEY = AttachmentKey.create( Boolean.class );

    public static void markUnitAsRootless(DeploymentUnit unit) {
        unit.putAttachment( KEY, true );
    }

    public static boolean isUnitRootless(DeploymentUnit unit) {
        Boolean value = unit.getAttachment( KEY );
        return value != null && value.booleanValue();
    }

    public static boolean isUnitRooted(DeploymentUnit unit) {
        return !isUnitRootless( unit );
    }

}
