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

package org.projectodd.polyglot.hasingleton;

import org.jboss.as.clustering.jgroups.ChannelFactory;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;

public class HASingletonCoordinatorService implements Service<HASingletonCoordinator> {

    public HASingletonCoordinatorService(ServiceController<Void> haSingletonController, String partitionName) {
        this.haSingletonController = haSingletonController;
        this.partitionName = partitionName;
    }

    @Override
    public HASingletonCoordinator getValue() throws IllegalStateException, IllegalArgumentException {
        return this.coordinator;
    }

    @Override
    public void start(StartContext context) throws StartException {
        log.info(  "Start HASingletonCoordinator"  );
        try {
            ChannelFactory factory = this.channelFactoryInjector.getValue();
            this.coordinator = new HASingletonCoordinator( this.haSingletonController, factory, this.partitionName );
            this.coordinator.start();
        } catch (Exception e) {
            throw new StartException( e );
        }
        log.info(  "Started HASingletonCoordinator"  );
    }

    @Override
    public void stop(StopContext context) {
        try {
            this.coordinator.stop();
            this.coordinator = null;
        } catch (Exception e) {
            log.error( "Unable to stop HA partition", e );
        }
    }

    public Injector<ChannelFactory> getChannelFactoryInjector() {
        return this.channelFactoryInjector;
    }
    
    private static final Logger log = Logger.getLogger( "org.projectodd.polyglot.hasingleton" );

    private InjectedValue<ChannelFactory> channelFactoryInjector = new InjectedValue<ChannelFactory>();;
    private ServiceController<Void> haSingletonController;
    private String partitionName;
    private HASingletonCoordinator coordinator;


}
