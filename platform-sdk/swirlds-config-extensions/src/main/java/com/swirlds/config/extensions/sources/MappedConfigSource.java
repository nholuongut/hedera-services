/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.config.extensions.sources;

import com.swirlds.config.api.source.ConfigSource;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A {@link ConfigSource} that can be used as a wrapper for other config sources, providing functionality to define
 * mappings for given properties. This allows the same property value defined for one name in the wrapped
 * {@link ConfigSource} to be accessed by both names.
 * <p>
 * For example, suppose you want to rename the "dbUrl" property to "general.databaseUrl". You can use a
 * {@code MappingConfigSource} to create a mapping between "dbUrl" and "general.databaseUrl", so that you can use both
 * names with the same value during the transition time.
 * </p>
 * <p>
 * Note that multiple mappings defined for a property is not allowed and will throw an {@link IllegalArgumentException}
 * at runtime.
 * </p>
 * <p>
 * Note that adding a mapping to a property not defined in the original {@link ConfigSource} will throw an
 * {@link IllegalArgumentException} at runtime.
 * </p>
 * <p>
 * Note that the ordinal of this {@code ConfigSource} is taken from the original {@link ConfigSource} that was wrapped
 * by this class.
 * </p>
 *
 * @see ConfigSource
 * @see ConfigMapping
 */
public class MappedConfigSource extends AbstractConfigSource {
    private static final String PROPERTY_NOT_FOUND = "Property '{}' not found in original config source";
    private static final String PROPERTY_ALREADY_DEFINED = "Property '%s' already defined";
    private static final String DUPLICATE_PROPERTY = "Property '{}' already found in original config source";
    private static final String PROPERTY_ALREADY_MAPPED = "Property '%s' has already a mapping defined";
    private static final Logger logger = LogManager.getLogger(MappedConfigSource.class);

    private final ConfigSource wrappedSource;

    private final Queue<ConfigMapping> configMappings;
    private final Map<String, String> properties;

    /**
     * Constructor that takes the wrapped config.
     *
     * @param wrappedSource the wrapped config
     */
    public MappedConfigSource(@NonNull final ConfigSource wrappedSource) {
        this.wrappedSource = Objects.requireNonNull(wrappedSource, "wrappedSource must not be null");
        configMappings = new ConcurrentLinkedQueue<>();
        properties = new HashMap<>();
    }

    /**
     * Adds the mappedName {@code 'mappedName'<->'originalName'}.
     *
     * @param mappedName   the mappedName name
     * @param originalName the original name
     */
    public void addMapping(@NonNull final String mappedName, @NonNull final String originalName) {
        addMapping(new ConfigMapping(mappedName, originalName));
    }

    /**
     * Adds the mappedName {@code 'mappedName'<->'originalName'}.
     *
     * @param configMapping defined mapping
     */
    public void addMapping(@NonNull final ConfigMapping configMapping) {
        Objects.requireNonNull(configMapping, "configMapping must not be null");

        if (configMappings.stream()
                .map(ConfigMapping::mappedName)
                .anyMatch(m -> Objects.equals(m, configMapping.mappedName()))) {
            throw new IllegalArgumentException(PROPERTY_ALREADY_DEFINED.formatted(configMapping.mappedName()));
        }

        if (configMappings.stream()
                .map(ConfigMapping::originalName)
                .anyMatch(o -> Objects.equals(o, configMapping.originalName()))) {
            throw new IllegalArgumentException(PROPERTY_ALREADY_MAPPED.formatted(configMapping.originalName()));
        }

        configMappings.add(configMapping);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    protected Map<String, String> getInternalProperties() {
        if (properties.isEmpty()) {
            final Map<String, String> internalProperties = wrappedSource.getProperties();
            final Map<String, String> mappedProperties = new HashMap<>();

            configMappings.forEach(configMapping -> {
                if (internalProperties.containsKey(configMapping.mappedName())) {
                    logger.warn(DUPLICATE_PROPERTY, configMapping.mappedName());
                } else if (!internalProperties.containsKey(configMapping.originalName())) {
                    logger.warn(PROPERTY_NOT_FOUND, configMapping.originalName());
                } else {
                    mappedProperties.put(
                            configMapping.mappedName(), internalProperties.get(configMapping.originalName()));
                    logger.debug("Added config mapping: {}", configMapping);
                }
            });

            properties.putAll(internalProperties);
            properties.putAll(mappedProperties);
        }
        return Collections.unmodifiableMap(properties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrdinal() {
        return wrappedSource.getOrdinal();
    }
}
