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

import java.io.File;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentResult;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import com.natigbabayev.revapi.gradle.ResolveOldApi.OldApi;
import com.natigbabayev.revapi.gradle.config.AcceptedBreak;
import com.natigbabayev.revapi.gradle.config.GroupAndName;

public final class RevapiPlugin implements Plugin<Project> {
    public static final String VERSION_OVERRIDE_TASK_NAME = "revapiVersionOverride";
    public static final String ACCEPT_BREAK_TASK_NAME = "revapiAcceptBreak";
    public static final String ACCEPT_ALL_BREAKS_TASK_NAME = "revapiAcceptAllBreaks";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(LifecycleBasePlugin.class);
        if (!project.getPlugins().hasPlugin("java")
                && !project.getPlugins().hasPlugin("java-library")
                && !project.getPlugins().hasPlugin("com.android.library")
                && !project.getPlugins().hasPlugin("com.android.application")) {
            project.getPluginManager().apply(JavaPlugin.class);
        }

        RevapiExtension extension = project.getExtensions().create("revapi", RevapiExtension.class, project);

        ConfigManager configManager = new ConfigManager(configFile(project));

        Provider<Optional<OldApi>> maybeOldApi = ResolveOldApi.oldApiProvider(project, extension, configManager);
        Spec<Task> oldApiIsPresent =
                _task -> extension.getOldJar().isPresent() || maybeOldApi.get().isPresent();

        TaskProvider<RevapiAnalyzeTask> analyzeTask = project.getTasks()
                .register("revapiAnalyze", RevapiAnalyzeTask.class, task -> {
                    task.getAcceptedBreaks().set(acceptedBreaks(project, configManager, extension.oldGroupAndName()));

                    // Configure JAR inputs - explicit JARs take precedence and skip Java plugin dependencies
                    if (extension.getNewJar().isPresent()) {
                        task.getNewApiJars().set(project.files(extension.getNewJar()));
                        task.getNewApiDependencyJars().set(project.files()); // empty for explicit JARs
                    } else {
                        // Only create configurations when we need them (not using explicit JARs)
                        Configuration revapiNewApi = project.getConfigurations().create("revapiNewApi", conf -> {
                            conf.extendsFrom(
                                    project.getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
                            configureApiUsage(project, conf);
                            conf.setCanBeConsumed(false);
                            conf.setVisible(false);
                        });

                        Configuration revapiNewApiElements = project.getConfigurations()
                                .create("revapiNewApiElements", conf -> {
                                    conf.extendsFrom(project.getConfigurations()
                                            .getByName(JavaPlugin.API_ELEMENTS_CONFIGURATION_NAME));
                                    configureApiUsage(project, conf);
                                    conf.setCanBeConsumed(false);
                                    conf.setVisible(false);
                                });

                        // we don't want to just grab the output of the 'jar' task, because people using
                        // 'com.palantir.shadow-jar' actually publish the output of a different task: 'shadowJar'
                        FileCollection thisJarFile = project.getConfigurations()
                                .getByName(JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME)
                                .getOutgoing()
                                .getArtifacts()
                                .getFiles();

                        FileCollection otherProjectsOutputs = revapiNewApiElements
                                .getIncoming()
                                .artifactView(vc -> vc.componentFilter(ci -> ci instanceof ProjectComponentIdentifier))
                                .getFiles();

                        // Note: this should propagate the dependency on the necessary tasks to build the other projects
                        task.getNewApiJars().set(thisJarFile.plus(otherProjectsOutputs));
                        task.getNewApiDependencyJars()
                                .set(revapiNewApi.minus(task.getNewApiJars().get()));
                    }

                    if (extension.getOldJar().isPresent()) {
                        task.getOldApiJars().set(project.files(extension.getOldJar()));
                        task.getOldApiDependencyJars().set(project.files()); // empty for explicit JARs
                    } else {
                        task.getOldApiJars().set(maybeOldApi.map(oldApi -> oldApi.map(OldApi::jars)
                                .map(project::files)
                                .orElseGet(project::files)));
                        task.getOldApiDependencyJars().set(maybeOldApi.map(oldApi -> oldApi.map(OldApi::dependencyJars)
                                .map(project::files)
                                .orElseGet(project::files)));
                    }

                    task.getJarsToReportBreaks().set(project.provider(() -> task.getNewApiJars()
                            .get()
                            .plus(task.getOldApiJars().get())));

                    task.getAnalysisResultsFile().set(new File(project.getBuildDir(), "revapi/revapi-results.json"));

                    task.onlyIf(oldApiIsPresent);
                });

        TaskProvider<RevapiReportTask> reportTask = project.getTasks()
                .register("revapi", RevapiReportTask.class, task -> {
                    task.dependsOn(analyzeTask);
                    task.getAnalysisResultsFile().set(analyzeTask.flatMap(RevapiAnalyzeTask::getAnalysisResultsFile));
                    task.getJunitOutputFile().set(junitOutput(project));

                    task.onlyIf(oldApiIsPresent);
                });

        project.getTasks().findByName(LifecycleBasePlugin.CHECK_TASK_NAME).dependsOn(reportTask);

        project.getTasks().register(ACCEPT_ALL_BREAKS_TASK_NAME, RevapiAcceptAllBreaksTask.class, task -> {
            task.dependsOn(analyzeTask);

            task.getOldGroupNameVersion().set(project.getProviders().provider(extension::oldGroupNameVersion));
            task.getConfigManager().set(configManager);
            task.getAnalysisResultsFile().set(analyzeTask.flatMap(RevapiAnalyzeTask::getAnalysisResultsFile));
            task.onlyIf(oldApiIsPresent);
        });

        project.getTasks().register(VERSION_OVERRIDE_TASK_NAME, RevapiVersionOverrideTask.class, task -> {
            task.getConfigManager().set(configManager);
        });

        project.getTasks().register(ACCEPT_BREAK_TASK_NAME, RevapiAcceptBreakTask.class, task -> {
            task.getConfigManager().set(configManager);
        });
    }

    /** In order to ensure we resolve the right variants with usage {@link Usage.JAVA_API}. */
    private static void configureApiUsage(Project project, Configuration conf) {
        conf.attributes(attrs ->
                attrs.attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_API)));
    }

    private Provider<Set<AcceptedBreak>> acceptedBreaks(
            Project project, ConfigManager configManager, Provider<GroupAndName> oldGroupAndNameProvider) {

        return GradleUtils.memoisedProvider(
                project,
                () -> configManager.fromFileOrEmptyIfDoesNotExist().acceptedBreaksFor(oldGroupAndNameProvider.get()));
    }

    // visible for testing
    static Provider<Set<Jar>> allJarTasksIncludingDependencies(Project project, Configuration configuration) {
        // Provider so that we don't resolve the configuration at compile time, which is bad for gradle performance
        return GradleUtils.memoisedProvider(
                project, () -> configuration.getIncoming().getResolutionResult().getAllComponents().stream()
                        .map(ComponentResult::getId)
                        .filter(resolvedComponentResult ->
                                resolvedComponentResult instanceof ProjectComponentIdentifier)
                        .map(resolvedComponentResult -> (ProjectComponentIdentifier) resolvedComponentResult)
                        .map(ProjectComponentIdentifier::getProjectPath)
                        .map(project.getRootProject()::project)
                        .flatMap(dependentProject -> dependentProject
                                .getTasks()
                                .withType(Jar.class)
                                .matching(jar -> jar.getName().equals(JavaPlugin.JAR_TASK_NAME))
                                .stream())
                        .collect(Collectors.toSet()));
    }

    private static File configFile(Project project) {
        return new File(project.getRootDir(), ".revapi/revapi.yml");
    }

    private File junitOutput(Project project) {
        Optional<String> circleReportsDir = Optional.ofNullable(System.getenv("CIRCLE_TEST_REPORTS"));
        File reportsDir = circleReportsDir.map(File::new).orElseGet(project::getBuildDir);
        return new File(reportsDir, "junit-reports/revapi/revapi-" + project.getName() + ".xml");
    }
}
