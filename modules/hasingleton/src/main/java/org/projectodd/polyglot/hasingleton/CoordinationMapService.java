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

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.transaction.TransactionMode;
import org.infinispan.transaction.lookup.GenericTransactionManagerLookup;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.logging.Logger;
import org.jboss.msc.inject.Injector;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.projectodd.polyglot.cache.as.CacheService;

/**
 * Provides a per-deployment ispan cache for coordination in a cluster.
 */
@SuppressWarnings("rawtypes")
public class CoordinationMapService implements Service<ConcurrentMap> {
    
    public static ServiceName serviceName(DeploymentUnit unit) {
        return unit.getServiceName().append("coordination-map");
    }
    
    public CoordinationMapService(String deploymentName) {
        this.deploymentName = deploymentName;
    }
    
    @Override
    public ConcurrentMap getValue() throws IllegalStateException, IllegalArgumentException {
        return this.cache;
    }

    @Override
    public void start(StartContext context) throws StartException {
        EmbeddedCacheManager container = (EmbeddedCacheManager)this.cacheServiceInjector.getValue().getCacheContainer();
        ConfigurationBuilder config = new ConfigurationBuilder();
        config.transaction()
            .transactionManagerLookup(new GenericTransactionManagerLookup())
            .transactionMode(TransactionMode.TRANSACTIONAL);
        config.clustering()
            .cacheMode(CacheMode.DIST_SYNC);

        String name = this.deploymentName + "-coordination-map";
        container.defineConfiguration(name, config.build());
        this.cache = container.getCache(name);
    }

    @Override
    public void stop(StopContext context) {
    }

    public Injector<CacheService> getCacheServiceInjector() {
        return cacheServiceInjector;
    }

    private String deploymentName;
    private ConcurrentMap cache;
    private InjectedValue<CacheService> cacheServiceInjector = new InjectedValue<CacheService>();

    private static final Logger log = Logger.getLogger("org.projectodd.polyglot.hasingleton");

}
