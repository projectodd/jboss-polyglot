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

import org.jboss.as.messaging.MessagingServices;
import org.jboss.as.messaging.jms.JMSServices;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.projectodd.polyglot.messaging.destinations.DestinationService;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;
import org.projectodd.polyglot.messaging.destinations.DestroyableJMSQueueService;
import org.projectodd.polyglot.messaging.destinations.QueueMetaData;

import java.util.Arrays;
import java.util.List;

public class QueueInstaller extends DestinationInstaller implements DeploymentUnitProcessor {

    public QueueInstaller(ServiceTarget globalTarget) {
        super(globalTarget);
    }

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();

        List<QueueMetaData> allMetaData = unit.getAttachmentList( QueueMetaData.ATTACHMENTS_KEY );

        for (QueueMetaData each : allMetaData) {
            if (!each.isRemote())
                deploy( phaseContext.getDeploymentUnit(), 
                        phaseContext.getServiceTarget(), 
                        this.globalTarget,
                        each );
        }

    }

    public static ServiceName queueServiceName(String name) {
        return JMSServices.getJmsQueueBaseServiceName(MessagingServices.getHornetQServiceName( "default" ))
                .append( name );
    }

    public static synchronized ServiceName deploy(final DeploymentUnit unit,
                                                  final ServiceTarget serviceTarget,
                                                  final ServiceTarget globalTarget,
                                                  final QueueMetaData queue) {

        final String[] jndis = DestinationUtils.jndiNames(queue.getName(), queue.isExported());

        log.debugf("JNDI names to bind the '%s' queue to: %s", queue.getName(), Arrays.toString(jndis));

        return deploy(unit,
                      serviceTarget,
                      globalTarget,
                      queue.getName(),
                      queueServiceName(queue.getName()),
                      new DestinationServiceFactory() {
                          public DestinationService newService() {
                              return new DestroyableJMSQueueService(queue.getName(),
                                                                    queue.getSelector(),
                                                                    queue.isDurable(),
                                                                    jndis);
                          }
                      },
                      new ValidatorFactory() {
                          @Override
                          public ReconfigurationValidator newValidator(DestinationService service) {
                              return new QueueReconfigurationValidator((DestroyableJMSQueueService)service,
                                                                       queue.isDurable(),
                                                                       queue.getSelector(),
                                                                       jndis);
                          }
                      });
    }

    @Override
    public void undeploy(DeploymentUnit context) {

    }

    static final Logger log = Logger.getLogger( "org.projectodd.polyglot.messaging" );

    protected static class QueueReconfigurationValidator extends ReconfigurationValidator {
        QueueReconfigurationValidator(DestroyableJMSQueueService actual,
                                      boolean durable,
                                      String selector,
                                      String[] jndi) {
            boolean jndiEqual = jndiEqual(actual.getJndi(), jndi);
            boolean selectorEqual = (selector == null && actual.getSelector() == null) ||
                    (selector != null && selector.equals(actual.getSelector()));
            this.reconfigure = actual.isDurable() != durable ||
                    !selectorEqual ||
                    !jndiEqual;

            if (actual.isDurable() != durable)
                add("durable", actual.isDurable(), durable);

            if (!selectorEqual)
                add("selector", actual.getSelector(), selector);

            if (!jndiEqual)
                add("jndi", actual.getJndi(), jndi);
        }

    }
}

