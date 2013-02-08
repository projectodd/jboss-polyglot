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

import org.jboss.logging.Logger;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.jgroups.Channel;

public class HASingletonCoordinatorService implements Service<HASingletonCoordinator> {

    public HASingletonCoordinatorService(ServiceController<Void> haSingletonController, String clusterName) {
        this.haSingletonController = haSingletonController;
        this.clusterName = clusterName;
    }

    @Override
    public HASingletonCoordinator getValue() throws IllegalStateException, IllegalArgumentException {
        return this.coordinator;
    }

    @Override
    public void start(StartContext context) throws StartException {
        log.info(  "Start HASingletonCoordinator"  );
        try {
            this.coordinator = new HASingletonCoordinator( this.haSingletonController, channelInjector, moduleLoaderInjector, clusterName );
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

    public Injector<Channel> getChannelInjector() {
        return this.channelInjector;
    }

    public Injector<ModuleLoader> getModuleLoaderInjector() {
        return this.moduleLoaderInjector;
    }
    
    private static final Logger log = Logger.getLogger( "org.projectodd.polyglot.hasingleton" );

    private InjectedValue<Channel> channelInjector = new InjectedValue<Channel>();
    private InjectedValue<ModuleLoader> moduleLoaderInjector = new InjectedValue<ModuleLoader>();
    private ServiceController<Void> haSingletonController;
    private String clusterName;
    private HASingletonCoordinator coordinator;


}
