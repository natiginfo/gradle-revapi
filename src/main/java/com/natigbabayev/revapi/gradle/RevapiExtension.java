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

package com.natigbabayev.revapi.gradle;

import java.util.Collections;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import com.natigbabayev.revapi.gradle.config.GroupAndName;
import com.natigbabayev.revapi.gradle.config.GroupNameVersion;
import com.natigbabayev.revapi.gradle.config.Version;

@SuppressWarnings("DesignForExtension")
public class RevapiExtension {
    private final Property<String> oldGroup;
    private final Property<String> oldName;
    private final ListProperty<String> oldVersions;
    private final Provider<GroupAndName> oldGroupAndName;
    private final RegularFileProperty oldJar;
    private final RegularFileProperty newJar;

    public RevapiExtension(Project project) {
        this.oldGroup = project.getObjects().property(String.class);
        this.oldGroup.set(
                project.getProviders().provider(() -> project.getGroup().toString()));

        this.oldName = project.getObjects().property(String.class);
        this.oldName.set(project.getProviders().provider(project::getName));

        this.oldVersions = project.getObjects().listProperty(String.class);
        // No default Git-based version detection - users must explicitly configure versions

        this.oldGroupAndName = project.provider(() ->
                GroupAndName.builder().group(oldGroup.get()).name(oldName.get()).build());

        this.oldJar = project.getObjects().fileProperty();
        this.newJar = project.getObjects().fileProperty();
    }

    public Property<String> getOldGroup() {
        return oldGroup;
    }

    public Property<String> getOldName() {
        return oldName;
    }

    public ListProperty<String> getOldVersions() {
        return oldVersions;
    }

    public void setOldVersion(String oldVersionValue) {
        oldVersions.set(Collections.singletonList(oldVersionValue));
    }

    GroupNameVersion oldGroupNameVersion() {
        if (oldVersions.get().isEmpty()) {
            throw new IllegalStateException(
                    "No oldVersions configured. Either set oldVersions explicitly or use explicit JAR files with"
                            + " oldJar/newJar properties.");
        }
        return oldGroupAndName()
                .get()
                .withVersion(Version.fromString(oldVersions.get().get(0)));
    }

    Provider<GroupAndName> oldGroupAndName() {
        return oldGroupAndName;
    }

    public RegularFileProperty getOldJar() {
        return oldJar;
    }

    public RegularFileProperty getNewJar() {
        return newJar;
    }
}
