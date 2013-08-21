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
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.projectodd.polyglot.messaging.destinations.DestinationService;
import org.projectodd.polyglot.messaging.destinations.DestinationUtils;
import org.projectodd.polyglot.messaging.destinations.DestroyableJMSQueueService;
import org.projectodd.polyglot.messaging.destinations.QueueMetaData;

import java.util.Arrays;

public class QueueInstaller extends DestinationInstaller {

    public QueueInstaller(ServiceTarget globalTarget) {
        super(globalTarget);
    }

    public static ServiceName queueServiceName(String name) {
        return JMSServices.getJmsQueueBaseServiceName(MessagingServices.getHornetQServiceName( "default" ))
                .append( name );
    }

    public static synchronized ServiceName deploySync(DeploymentUnit unit,
                                                      ServiceTarget serviceTarget,
                                                      ServiceTarget globalTarget,
                                                      String name,
                                                      String selector,
                                                      boolean durable,
                                                      boolean exported) {
        return deploy(unit, serviceTarget, globalTarget, true, name, selector, durable, exported);
    }

    public static synchronized ServiceName deployAsync(final DeploymentUnit unit,
                                                       final ServiceTarget serviceTarget,
                                                       final ServiceTarget globalTarget,
                                                       String name,
                                                       String selector,
                                                       boolean durable,
                                                       boolean exported) {
        return deploy(unit, serviceTarget, globalTarget, false, name, selector, durable, exported);
    }

    private static synchronized ServiceName deploy(final DeploymentUnit unit,
                                                   final ServiceTarget serviceTarget,
                                                   final ServiceTarget globalTarget,
                                                   boolean waitForStart,
                                                   final String name,
                                                   final String selector,
                                                   final boolean durable,
                                                   boolean exported) {

        final String[] jndis = DestinationUtils.jndiNames(name, exported);

        log.debugf("JNDI names to bind the '%s' queue to: %s", name, Arrays.toString(jndis));

        return deploy(unit,
                      serviceTarget,
                      globalTarget,
                      name,
                      queueServiceName(name),
                      new DestinationServiceFactory() {
                          public DestinationService newService() {
                              return new DestroyableJMSQueueService(name,
                                                                    selector,
                                                                    durable,
                                                                    jndis);
                          }
                      },
                      new ValidatorFactory() {
                          @Override
                          public ReconfigurationValidator newValidator(DestinationService service) {
                              return new QueueReconfigurationValidator((DestroyableJMSQueueService)service,
                                                                       durable,
                                                                       selector,
                                                                       jndis);
                          }
                      },
                      waitForStart);
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

