/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.caching.configuration.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.caching.BuildCacheServiceFactory;
import org.gradle.caching.configuration.BuildCache;
import org.gradle.caching.local.DirectoryBuildCache;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.SingleMessageLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

public class DefaultBuildCacheConfiguration implements BuildCacheConfigurationInternal {
    public static final String BUILD_CACHE_CAN_PULL = "org.gradle.cache.tasks.pull";
    public static final String BUILD_CACHE_CAN_PUSH = "org.gradle.cache.tasks.push";
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildCacheConfiguration.class);

    private final Instantiator instantiator;

    private BuildCache local;
    private BuildCache remote;

    private final boolean pullDisabled;
    private final boolean pushDisabled;

    private final Set<BuildCacheServiceRegistration> registrations;

    public DefaultBuildCacheConfiguration(Instantiator instantiator, List<BuildCacheServiceRegistration> allBuiltInBuildCacheServices, StartParameter startParameter) {
        this.instantiator = instantiator;
        this.registrations = Sets.newHashSet(allBuiltInBuildCacheServices);

        // TODO: Drop these system properties
        this.pullDisabled = isDisabled(startParameter, BUILD_CACHE_CAN_PULL);
        this.pushDisabled = isDisabled(startParameter, BUILD_CACHE_CAN_PUSH);

        // By default the local cache is a directory cache
        this.local = createLocalCacheConfiguration(instantiator, DirectoryBuildCache.class);
    }

    @Override
    public BuildCache getLocal() {
        return local;
    }

    @Override
    public <T extends BuildCache> T local(Class<T> type) {
        return local(type, Actions.doNothing());
    }

    @Override
    public <T extends BuildCache> T local(Class<T> type, Action<? super T> configuration) {
        if (!type.isInstance(local)) {
            if (local != null) {
                LOGGER.info("Replacing local build cache type {} with {}", local.getClass().getCanonicalName(), type.getCanonicalName());
            }
            local = createLocalCacheConfiguration(instantiator, type);
        }
        T configurationObject = Cast.uncheckedCast(local);
        configuration.execute(configurationObject);
        return configurationObject;
    }

    @Override
    public void local(Action<? super BuildCache> configuration) {
        configuration.execute(local);
    }

    @Override
    public BuildCache getRemote() {
        return remote;
    }

    @Override
    public <T extends BuildCache> T remote(Class<T> type) {
        return remote(type, Actions.doNothing());
    }

    @Override
    public <T extends BuildCache> T remote(Class<T> type, Action<? super T> configuration) {
        if (!type.isInstance(remote)) {
            if (remote != null) {
                LOGGER.info("Replacing remote build cache type {} with {}", remote.getClass().getCanonicalName(), type.getCanonicalName());
            }
            remote = createRemoteCacheConfiguration(instantiator, type);
        }
        T configurationObject = Cast.uncheckedCast(remote);
        configuration.execute(configurationObject);
        return configurationObject;
    }

    @Override
    public void remote(Action<? super BuildCache> configuration) {
        if (remote == null) {
            throw new IllegalStateException("A type for the remote build cache must be configured first.");
        }
        configuration.execute(remote);
    }

    private static <T extends BuildCache> T createLocalCacheConfiguration(Instantiator instantiator, Class<T> type) {
        T local = createBuildCacheConfiguration(instantiator, type);
        // By default, we push to the local cache.
        local.setPush(true);
        return local;
    }

    private static <T extends BuildCache> T createRemoteCacheConfiguration(Instantiator instantiator, Class<T> type) {
        T remote = createBuildCacheConfiguration(instantiator, type);
        // By default, we do not push to the remote cache.
        remote.setPush(false);
        return remote;
    }

    private static <T extends BuildCache> T createBuildCacheConfiguration(Instantiator instantiator, Class<T> type) {
        return instantiator.newInstance(type);
    }

    @Override
    public <T extends BuildCache> void registerBuildCacheService(Class<T> configurationType, Class<? extends BuildCacheServiceFactory<? super T>> buildCacheServiceFactoryType) {
        Preconditions.checkNotNull(configurationType, "configurationType cannot be null.");
        Preconditions.checkNotNull(buildCacheServiceFactoryType, "buildCacheServiceFactoryType cannot be null.");
        registrations.add(new DefaultBuildCacheServiceRegistration(configurationType, buildCacheServiceFactoryType));
    }

    @Override
    public <T extends BuildCache> Class<? extends BuildCacheServiceFactory<T>> getBuildCacheServiceFactoryType(Class<T> configurationType) {
        for (BuildCacheServiceRegistration registration : registrations) {
            Class<? extends BuildCache> registeredConfigurationType = registration.getConfigurationType();
            if (registeredConfigurationType.isAssignableFrom(configurationType)) {
                Class<? extends BuildCacheServiceFactory<?>> buildCacheServiceFactoryType = registration.getFactoryType();
                LOGGER.info("Found {} registered for {}", buildCacheServiceFactoryType, registeredConfigurationType);
                return Cast.uncheckedCast(buildCacheServiceFactoryType);
            }
        }

        // Couldn't find a registration for the given type
        throw new IllegalArgumentException(String.format("No build cache service factory for configuration type '%s' could be found.", configurationType.getSuperclass().getCanonicalName()));
    }

    @Override
    public boolean isPushDisabled() {
        return pushDisabled;
    }

    @Override
    public boolean isPullDisabled() {
        return pullDisabled;
    }

    private static boolean isDisabled(StartParameter startParameter, String property) {
        String value = startParameter.getSystemPropertiesArgs().get(property);
        if (value == null) {
            value = System.getProperty(property);
        }
        if (value == null) {
            return false;
        }
        SingleMessageLogger.nagUserOfDiscontinuedProperty(property, "Use the build cache DSL instead.");
        value = value.toLowerCase().trim();
        return value.equals("false");
    }
}
