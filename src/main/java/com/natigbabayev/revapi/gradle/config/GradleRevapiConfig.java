/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.natigbabayev.revapi.gradle.config;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;
import org.immutables.value.Value;
import com.natigbabayev.revapi.gradle.ImmutableStyle;

@Value.Immutable
@ImmutableStyle
@JsonDeserialize(as = ImmutableGradleRevapiConfig.class)
public abstract class GradleRevapiConfig {
    @Value.NaturalOrder
    protected abstract SortedMap<GroupNameVersion, String> versionOverrides();

    @Value.NaturalOrder
    protected abstract SortedMap<Version, PerProjectAcceptedBreaks> acceptedBreaks();

    public final Optional<Version> versionOverrideFor(GroupNameVersion groupNameVersion) {
        return Optional.ofNullable(versionOverrides().get(groupNameVersion)).map(Version::fromString);
    }

    public final GradleRevapiConfig addVersionOverride(GroupNameVersion groupNameVersion, String versionOverride) {
        return ImmutableGradleRevapiConfig.builder()
                .from(this)
                .putVersionOverrides(groupNameVersion, versionOverride)
                .build();
    }

    public final Set<AcceptedBreak> acceptedBreaksFor(GroupAndName groupNameVersion) {
        return acceptedBreaks().values().stream()
                .flatMap(perProjectAcceptedBreaks ->
                        perProjectAcceptedBreaks.acceptedBreaksFor(groupNameVersion).stream())
                .collect(Collectors.toSet());
    }

    public final GradleRevapiConfig addAcceptedBreaks(
            GroupNameVersion groupNameVersion, Set<AcceptedBreak> acceptedBreaks) {

        PerProjectAcceptedBreaks existingAcceptedBreaks =
                acceptedBreaks().getOrDefault(groupNameVersion.version(), PerProjectAcceptedBreaks.empty());

        PerProjectAcceptedBreaks newPerProjectAcceptedBreaks =
                existingAcceptedBreaks.merge(groupNameVersion.groupAndName(), acceptedBreaks);

        Map<Version, PerProjectAcceptedBreaks> newAcceptedBreaks = new HashMap<>(acceptedBreaks());
        newAcceptedBreaks.put(groupNameVersion.version(), newPerProjectAcceptedBreaks);

        return ImmutableGradleRevapiConfig.builder()
                .from(this)
                .acceptedBreaks(newAcceptedBreaks)
                .build();
    }

    public static class Builder extends ImmutableGradleRevapiConfig.Builder {}

    public static Builder builder() {
        return new Builder();
    }

    public static GradleRevapiConfig empty() {
        return builder().build();
    }

    public static ObjectMapper newYamlObjectMapper() {
        return configureObjectMapper(
                new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)));
    }

    public static ObjectMapper newJsonObjectMapper() {
        return configureObjectMapper(new ObjectMapper()).enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
    }

    private static ObjectMapper configureObjectMapper(ObjectMapper objectMapper) {
        return objectMapper.registerModule(new Jdk8Module()).setSerializationInclusion(Include.NON_EMPTY);
    }
}
