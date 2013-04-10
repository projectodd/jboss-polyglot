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

import org.jboss.as.clustering.jgroups.ChannelFactory;
import org.jboss.as.clustering.jgroups.subsystem.ChannelFactoryService;
import org.jboss.as.clustering.jgroups.subsystem.ChannelService;
import org.jboss.as.server.Services;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.value.InjectedValue;
import org.jgroups.Channel;
import org.projectodd.polyglot.core.util.ClusterUtil;

public class HASingletonInstaller implements DeploymentUnitProcessor {

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        deploy(phaseContext.getDeploymentUnit(), phaseContext.getServiceTarget(), "global");
    }

    @Override
    public void undeploy(DeploymentUnit context) {
    }

    public static void deployOnce(DeploymentUnit unit, ServiceTarget target, String name) {  
        ServiceName serviceName = HASingleton.serviceName(unit, name);
        if (unit.getServiceRegistry().getService(serviceName) == null) {
            deploy(unit, target, name);
        }
        
    }
    
    public static void deploy(DeploymentUnit unit, ServiceTarget target, String name) {
        ServiceName serviceName = HASingleton.serviceName(unit, name);
        ServiceBuilder<Void> builder = target.addService( serviceName, new HASingleton() );

        boolean clustered = ClusterUtil.isClustered( unit.getServiceRegistry() ); 
        if (clustered) {
            builder.setInitialMode( Mode.NEVER );
        } else {
            builder.setInitialMode( Mode.ACTIVE );
        }

        ServiceController<Void> singletonController = builder.install();

        if (clustered) {
            String hasingletonId = unit.getName() + "-hasingleton-" + name;
            ServiceName channelServiceName = ChannelService.getServiceName( hasingletonId );
            InjectedValue<ChannelFactory> channelFactory = new InjectedValue<ChannelFactory>();
            target.addService( channelServiceName, new ChannelService( hasingletonId, channelFactory ) )
                    .addDependency( ChannelFactoryService.getServiceName( null ), ChannelFactory.class, channelFactory )
                    .setInitialMode( Mode.ON_DEMAND )
                    .install();

            HASingletonCoordinatorService coordinator = new HASingletonCoordinatorService( singletonController, hasingletonId );
            target.addService( serviceName.append( "coordinator" ), coordinator )
                    .addDependency( channelServiceName, Channel.class, coordinator.getChannelInjector() )
                    .addDependency( Services.JBOSS_SERVICE_MODULE_LOADER, ModuleLoader.class, coordinator.getModuleLoaderInjector() )
                    .install();
        }
	
    }
    
    private static final Logger log = Logger.getLogger( "org.projectodd.polyglot.hasingleton" );

}
