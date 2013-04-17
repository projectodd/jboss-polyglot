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

package org.projectodd.polyglot.hasingleton;

import java.util.concurrent.ConcurrentMap;

import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.projectodd.polyglot.cache.as.CacheService;
import org.projectodd.polyglot.core.util.ClusterUtil;

public class CoordinationMapInstaller implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        deploy(phaseContext.getDeploymentUnit(), phaseContext.getServiceTarget());
    }

    @Override
    public void undeploy(DeploymentUnit context) {
    }

    @SuppressWarnings("rawtypes")
    public static void deploy(DeploymentUnit unit, ServiceTarget target) {
        CoordinationMapService service = new CoordinationMapService(unit.getName());
        ServiceName serviceName = CoordinationMapService.serviceName(unit);
        ServiceBuilder<ConcurrentMap> builder = target.addService(serviceName, service);
        builder.addDependency(CacheService.CACHE, CacheService.class, service.getCacheServiceInjector());
        
        if (ClusterUtil.isClustered(unit.getServiceRegistry())) {
            builder.setInitialMode( Mode.ACTIVE );
        } else {
            builder.setInitialMode( Mode.NEVER );
        }

        builder.install();
    }
    
    private static final Logger log = Logger.getLogger( "org.projectodd.polyglot.hasingleton" );

}
