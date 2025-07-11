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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.immutables.serial.Serial;
import org.immutables.value.Value;
import com.natigbabayev.revapi.gradle.ImmutableStyle;

@Value.Immutable
@ImmutableStyle
@Serial.Structural
public interface Version extends Comparable<Version> {

    @JsonValue
    String asString();

    @JsonCreator
    static Version fromString(String version) {
        return builder().asString(version).build();
    }

    @Override
    default int compareTo(Version other) {
        return this.asString().compareTo(other.asString());
    }

    class Builder extends ImmutableVersion.Builder {}

    static Builder builder() {
        return new Builder();
    }
}
