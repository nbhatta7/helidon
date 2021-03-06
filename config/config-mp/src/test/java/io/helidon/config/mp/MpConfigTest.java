/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.config.mp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.hasSize;

/**
 * Test MicroProfile config implementation.
 */
public class MpConfigTest {
    private static Config config;

    @BeforeAll
    static void initClass() {
        config = ConfigProviderResolver.instance()
                .getBuilder()
                .withSources(MpConfigSources.create(Map.of("mp-1", "mp-value-1",
                                                           "mp-2", "mp-value-2",
                                                           "app.storageEnabled", "false",
                                                           "mp-array", "a,b,c",
                                                           "mp-list.0", "1",
                                                           "mp-list.1", "2",
                                                           "mp-list.2", "3")),
                             MpConfigSources.create(Map.of("app.storageEnabled", "true",
                                                           ConfigSource.CONFIG_ORDINAL, "1000")))
                .build();
    }

    @Test
    void testConfigSources() {
        Iterable<ConfigSource> configSources = config.getConfigSources();
        List<ConfigSource> asList = new ArrayList<>();
        for (ConfigSource configSource : configSources) {
            asList.add(configSource);
        }

        assertThat(asList, hasSize(2));

        assertThat(asList.get(0), instanceOf(MpMapSource.class));
        assertThat(asList.get(1), instanceOf(MpMapSource.class));

        // first is the one with higher config ordinal
        ConfigSource map = asList.get(0);
        assertThat(map.getValue("app.storageEnabled"), is("true"));

        map = asList.get(1);
        assertThat(map.getValue("mp-1"), is("mp-value-1"));
        assertThat(map.getValue("mp-2"), is("mp-value-2"));
        assertThat(map.getValue("app.storageEnabled"), is("false"));
    }

    @Test
    void testOptionalValue() {
        assertThat(config.getOptionalValue("app.storageEnabled", Boolean.class), is(Optional.of(true)));
        assertThat(config.getOptionalValue("mp-1", String.class), is(Optional.of("mp-value-1")));
    }

    @Test
    void testStringArray() {
        String[] values = config.getValue("mp-array", String[].class);
        assertThat(values, arrayContaining("a", "b", "c"));
    }

    @Test
    void testIntArray() {
        Integer[] values = config.getValue("mp-list", Integer[].class);
        assertThat(values, arrayContaining(1, 2, 3));
    }

    @Test
    void mutableTest() {
        // THIS MUST WORK - the spec says the sources can be mutable and config must use the latest values
        var mutable = new MutableConfigSource();

        Config config = ConfigProviderResolver.instance().getBuilder()
                .withSources(mutable)
                .build();

        String value = config.getValue("key", String.class);
        assertThat(value, is("initial"));

        String updated = "updated";
        mutable.set(updated);
        value = config.getValue("key", String.class);
        assertThat(value, is(updated));
    }

    @Test
    void arrayTest() {
        MutableConfigSource cs = new MutableConfigSource();
        cs.set("large:cheese\\,mushroom,medium:chicken,small:pepperoni");
        Config config = ConfigProviderResolver.instance().getBuilder()
                .withConverter(Pizza.class, 10, value -> {
                    String[] parts = value.split(":");
                    if (parts.length == 2) {
                        String size = parts[0];
                        String flavor = parts[1];
                        return new Pizza(flavor, size);
                    }

                    return null;
                })
                .withSources(cs)
                .build();

        Pizza[] value = config.getValue("key",
                                        Pizza[].class);

        assertThat(value, notNullValue());
        assertThat(value, arrayWithSize(3));
        assertThat(value, is(new Pizza[] {
                new Pizza("cheese,mushroom", "large"),
                new Pizza("chicken", "medium"),
                new Pizza("pepperoni", "small")
        }));
    }

    private static class MutableConfigSource implements ConfigSource {
        private final AtomicReference<String> value = new AtomicReference<>("initial");

        @Override
        public Map<String, String> getProperties() {
            return Map.of("key", value.get());
        }

        @SuppressWarnings("ReturnOfNull")
        @Override
        public String getValue(String propertyName) {
            if ("key".equals(propertyName)) {
                return value.get();
            }
            // this is required by the specification (null returns if not found)
            return null;
        }

        @Override
        public String getName() {
            return getClass().getName();
        }

        private void set(String value) {
            this.value.set(value);
        }
    }

    public static class Pizza {
        private final String flavor;
        private final String size;

        private Pizza(String flavour, String size) {
            this.flavor = flavour;
            this.size = size;
        }

        @Override
        public String toString() {
            return flavor + ":" + size;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Pizza pizza = (Pizza) o;
            return flavor.equals(pizza.flavor) &&
                    size.equals(pizza.size);
        }

        @Override
        public int hashCode() {
            return Objects.hash(flavor, size);
        }
    }
}

