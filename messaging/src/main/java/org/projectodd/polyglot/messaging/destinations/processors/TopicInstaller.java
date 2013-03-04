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

package org.projectodd.polyglot.messaging.destinations.processors;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.hornetq.jms.server.JMSServerManager;
import org.jboss.as.messaging.MessagingServices;
import org.jboss.as.messaging.jms.JMSServices;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;
import org.projectodd.polyglot.messaging.destinations.DestroyableJMSTopicService;
import org.projectodd.polyglot.messaging.destinations.HornetQStartupPoolService;
import org.projectodd.polyglot.messaging.destinations.TopicMetaData;

/**
 * <pre>
 * Stage: REAL
 *    In: QueueMetaData
 *   Out: ManagedQueue
 * </pre>
 * 
 */
public class TopicInstaller implements DeploymentUnitProcessor {

    public TopicInstaller() {
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();

        List<TopicMetaData> allMetaData = unit.getAttachmentList( TopicMetaData.ATTACHMENTS_KEY );

        for (TopicMetaData each : allMetaData) {
            if (!each.isRemote())
                deploy( phaseContext.getServiceTarget(), each );
        }

    }

    public static ServiceName deploy(ServiceTarget serviceTarget, DestroyableJMSTopicService service, String name) {
        final ServiceName hornetQserviceName = MessagingServices.getHornetQServiceName( "default" );
        final ServiceName serviceName = JMSServices.getJmsTopicBaseServiceName( hornetQserviceName ).append( name );

        try {
            ServiceBuilder<?> serviceBuilder = serviceTarget.addService(serviceName, service)
                    .addDependency(JMSServices.getJmsManagerBaseServiceName( hornetQserviceName ), JMSServerManager.class, service.getJmsServer() )
                    .addDependency( HornetQStartupPoolService.getServiceName( hornetQserviceName ), ExecutorService.class, service.getExecutorServiceInjector() )
                    .setInitialMode( Mode.ACTIVE );
            serviceBuilder.install();
        } catch (org.jboss.msc.service.DuplicateServiceException ignored) {
            log.warn("Already started "+serviceName);
        }

        return serviceName;

    }

    public static ServiceName deploy(ServiceTarget serviceTarget, TopicMetaData topic) {
        String[] jndis = DestinationUtils.jndiNames(topic.getName(), topic.isExported());

        log.debugf("JNDI names to bind the '%s' topic to: %s", topic.getName(), Arrays.toString(jndis));

        return deploy( serviceTarget,
                       new DestroyableJMSTopicService(topic.getName(), jndis ),
                       topic.getName() );
    }

    @Override
    public void undeploy(DeploymentUnit context) {
        // TODO Auto-generated method stub

    }

    static final Logger log = Logger.getLogger( "org.projectodd.polyglot.messaging" );

}
