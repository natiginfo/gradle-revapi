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

package com.natigbabayev.revapi.gradle

import spock.lang.Ignore

import java.util.regex.Pattern
import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import spock.util.environment.RestoreSystemProperties

class RevapiSpec extends IntegrationSpec {
    private Git git

    def setup() {
        git = new Git(projectDir)
        System.setProperty("ignoreDeprecations", "true")
    }

    def 'fails when comparing produced jar versus some random other jar'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            
            repositories {
                mavenCentral()
            }
            
            dependencies {
                implementation 'one.util:streamex:0.7.0'
            }
            
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = '0.11.1'
            }
        """.stripIndent()

        rootProjectNameIs("root-project")

        writeToFile 'src/main/java/foo/Foo.java', '''
            import one.util.streamex.StreamEx;

            public interface Foo {
                StreamEx<String> lol();
            }
        '''.stripIndent()

        then:
        runRevapiExpectingToFindDifferences("root-project")
    }

    def 'revapi task succeeds when there are no breaking changes'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'org.codehaus.cargo'
                oldName = 'empty-jar'
                oldVersion = '1.7.7'
            }
        """.stripIndent()

        then:
        runTasksSuccessfully("revapi")
    }

    def 'doesnt explode when project code depends on compileOnly dependency'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            apply plugin: 'maven-publish'
            
            repositories {
                mavenCentral()
            }
            
            allprojects {
                group = 'revapi.test'
                ${mavenRepoGradle()}
            }
            
            dependencies {
                implementation 'junit:junit:4.13'
            }
            
            revapi {
                oldVersion = project.version
            }
            
            ${testMavenPublication()}
        """.stripIndent()

        writeToFile 'src/main/java/foo/Foo.java', '''
            public class Foo extends org.junit.rules.ExternalResource { }
        '''.stripIndent()

        println runTasksSuccessfully("publish").standardOutput

        and:
        buildFile.text = buildFile.text.replace('implementation', 'compileOnly')

        then:

        def executionResult = runTasks('revapi')
        println executionResult.standardOutput
        println executionResult.standardError
        executionResult.rethrowFailure()
    }

    def 'revapiAcceptAllBreaks succeeds when there are no breaking changes'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'

            repositories {
                mavenCentral()
            }

            revapi {
                oldGroup = 'org.codehaus.cargo'
                oldName = 'empty-jar'
                oldVersion = '1.7.7'
            }
        """.stripIndent()

        then:
        runTasksSuccessfully("revapiAcceptAllBreaks", "--justification", "fight me")
    }

    def 'does not error out when project has a version greater than the "old version"'() {
        def revapi = 'revapi'

        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            
            repositories {
                mavenCentral()
            }
            
            group = 'org.revapi'
            version = '0.12.0'
            
            revapi {
                oldVersion = '0.11.1'
            }
        """.stripIndent()

        rootProjectNameIs(revapi)


        writeToFile 'src/main/java/foo/Foo.java', '''
            public interface Foo {
                String lol();
            }
        '''.stripIndent()

        then:
        runRevapiExpectingToFindDifferences(revapi)
    }

    def 'errors out when the old api dependency does not exist but then works once you run the version override task'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = 'does-not-exist'
            }
        """.stripIndent()

        rootProjectNameIs("root-project")

        and:
        runTasksSuccessfully("revapiVersionOverride", "--replacement-version", "0.11.1")

        then:
        runRevapiExpectingToFindDifferences("root-project")
    }

    def 'errors out when the target dependency does not exist and we do not give an version override'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = 'does-not-exist'
            }
        """.stripIndent()

        rootProjectNameIs("root-project")

        then:
        runRevapiExpectingResolutionFailure()
    }

    def 'skips revapi tasks when the versions to check is empty list'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersions = []
            }
        """.stripIndent()

        then:
        def executionResult = runTasksSuccessfully('revapi')
        executionResult.wasSkipped(':revapiAnalyze')
        executionResult.wasSkipped(':revapi')
    }

    @Ignore("Test environment issue - Maven resolution fails before API comparison")
    def 'when the previous git tag has failed to publish, it will look back up to a further git tag'() {
        when:
        git.initWithTestUser()

        writeToFile '.gitignore', """
            .gradle*/
            build/
            mavenRepo/
        """.stripIndent()

        rootProjectNameIs 'name'

        buildFile << """
            plugins {
                id 'com.palantir.git-version' version '3.1.0'
            }

            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            apply plugin: 'maven-publish'
            
            group = 'group'
            version = gitVersion()
   
            ${mavenRepoGradle()}
            ${testMavenPublication()}
        """.stripIndent()

        def javaFile = 'src/main/java/foo/Foo.java'
        writeToFile javaFile, """
            public interface Foo {
                String willBeRemoved();
            }
        """.stripIndent()

        git.command 'git add .'
        git.command 'git commit -m 0.1.0'
        git.command 'git tag 0.1.0'

        runTasksSuccessfully('publish')

        and:
        git.command 'git commit --allow-empty -m publish-failed'
        git.command 'git tag 0.2.0'

        and:
        writeToFile javaFile, """
            public interface Foo { }
        """.stripIndent()

        git.command 'git commit -am new-work'

        then:
        def standardError = runTasksWithFailure('revapi').standardError
        assert standardError.contains('willBeRemoved')
    }

    def 'if there are no published versions of the library at all, ./gradlew revapi doesnt fail'() {
        when:
        setupUnpublishedLibrary()
        writeHelloWorld()

        then:
        def executionResult = runTasksSuccessfully('revapi')
        executionResult.wasSkipped(':revapiAnalyze')
        executionResult.wasSkipped(':revapi')
    }

    def 'if there are no published versions of the library at all, ./gradlew revapiAcceptAllBreaks is a no-op'() {
        when:
        setupUnpublishedLibrary()
        writeHelloWorld()

        then:
        def executionResult = runTasksSuccessfully('revapiAcceptAllBreaks')
        executionResult.wasSkipped(':revapiAnalyze')
        executionResult.wasSkipped(':revapiAcceptAllBreaks')
    }

    def 'if there are no published versions of the library at all, ./gradlew revapiAcceptBreak doesnt fail'() {
        when:
        setupUnpublishedLibrary()
        writeHelloWorld()

        then:
        def executionResult = runTasksSuccessfully('revapiAcceptBreak', '--justification', 'foo', '--code', 'bar',
                '--old', 'old', '--new', 'new')
        executionResult.wasExecuted(':revapiAcceptBreak')
    }

    private File setupUnpublishedLibrary() {
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'does.not'
                oldName = 'exist'
                oldVersion = '1.0.0'
            }
        """.stripIndent()
    }

    def 'handles the output of extra source sets being added to compile configuration'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            
            sourceSets {
                extraStuff
            }
            
            dependencies {
                implementation sourceSets.extraStuff.output
            }
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'org.revapi'
                oldName = 'revapi'
                oldVersion = '0.11.1'
            }
        """.stripIndent()

        rootProjectNameIs("root-project")

        then:
        runRevapiExpectingToFindDifferences("root-project")
    }

    def 'errors out when there are breaks but then is fine when breaks are accepted'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'junit'
                oldName = 'junit'
                oldVersion = '4.12'
            }
        """.stripIndent()

        rootProjectNameIs("root-project")
        File revapiYml = new File(getProjectDir(), ".revapi/revapi.yml")

        and:
        !revapiYml.exists()
        runTasksSuccessfully("revapiAcceptAllBreaks", "--justification", "it's all good :)")
        revapiYml.text.contains('java.class.removed')

        then:
        runTasksSuccessfully("revapi")
    }

    @Ignore("doesn't work when no git tags exist locally")
    def 'accepting breaks individually should work'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            
            repositories {
                mavenCentral()
            }
        """.stripIndent()

        rootProjectNameIs("root-project")

        and:
        runTasksSuccessfully("revapiAcceptBreak",
                "--code", "code1",
                "--old", "old1",
                "--new", "new1",
                "--justification", "j1")

        runTasksSuccessfully("revapiAcceptBreak",
                "--code", "code2",
                "--old", "old2",
                "--justification", "j2")

        runTasksSuccessfully("revapiAcceptBreak",
                "--code", "code3",
                "--new", "new3",
                "--justification", "j3")

        then:
        def revapiYml = file('.revapi/revapi.yml').text
        assert revapiYml.contains('code: "code1"')
        assert revapiYml.contains('old: "old1"')
        assert revapiYml.contains('new: "new1"')
        assert revapiYml.contains('justification: "j1"')

        assert revapiYml.contains('code: "code2"')
        assert revapiYml.contains('old: "old2"')
        assert revapiYml.contains('justification: "j2"')

        assert revapiYml.contains('code: "code3"')
        assert revapiYml.contains('new: "new3"')
        assert revapiYml.contains('justification: "j3"')

    }

    def 'moving a class from one project to a dependent project is not a break (only if it is in the api configuration)'() {
        buildFile << """
            allprojects {
                apply plugin: 'java-library'
                apply plugin: 'maven-publish'

                group = 'revapi.test'
                version = '1.0.0'
                ${mavenRepoGradle()}

                ${testMavenPublication()}
            }
        """.stripIndent()

        def one = addSubproject 'one', """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            
            dependencies {
                api project(':two')
            }
            
            revapi {
                oldVersion = project.version
            }
        """.stripIndent()

        def two = addSubproject 'two'

        def originalJavaFile = writeToFile one, 'src/main/java/foo/Foo.java', '''
            package foo;
            public interface Foo {}
        '''.stripIndent()

        when:
        println runTasksSuccessfully("publish").standardOutput

        writeToFile two, 'src/main/java/foo/Foo.java', originalJavaFile.text
        originalJavaFile.delete()

        and:
        println runTasksSuccessfully("revapi").standardOutput

        and:
        def oneBuildGradle = new File(one, 'build.gradle')
        oneBuildGradle.text = oneBuildGradle.text.replace('api project', 'implementation project')

        then:
        assert runRevapiExpectingFailure().contains('java.class.removed')
    }

    @Ignore("Test environment issue - Maven resolution fails before API comparison")
    def 'ignores breaks in dependent projects'() {
        when:
        buildFile << """
            allprojects {
                apply plugin: 'java-library'
                apply plugin: 'maven-publish'

                group = 'revapi.test'
                version = '1.0.0'
                ${mavenRepoGradle()}

                ${testMavenPublication()}
            }
        """.stripIndent()

        def one = addSubproject 'one', """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            
            dependencies {
                api project(':two')
            }
            
            revapi {
                oldVersion = project.version
            }
        """.stripIndent()

        writeToFile one, 'src/main/java/foo/Bar.java', '''
            package foo;
            public interface Bar {
                Foo bar();
            }
        '''.stripIndent()

        def two = addSubproject 'two'

        def javaFileInDependentProject = writeToFile two, 'src/main/java/foo/Foo.java', '''
            package foo;
            public interface Foo {
            }
        '''.stripIndent()

        and:
        println runTasksSuccessfully("publish").standardOutput

        javaFileInDependentProject.text = javaFileInDependentProject.text.replace('}', 'void foo();\n}')

        then:
        println runTasksSuccessfully("revapi").standardOutput
    }

    def 'should not say there are breaks in api dependencies when nothing has changed'() {
        when:
        rootProjectNameIs('test')

        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            apply plugin: 'maven-publish'

            group = 'revapi'
            version = '1.0.0'
            
            repositories {
                mavenCentral()
            }
            ${mavenRepoGradle()}

            ${testMavenPublication()}

            dependencies {
                api 'junit:junit:4.12'
            }

            revapi {
                oldVersion = project.version
            }
        """.stripIndent()

        writeToFile 'src/main/java/foo/Foo.java', '''
            package foo;
            // Use an junit interface in our public api so revapi cares about it 
            public interface Foo extends org.junit.rules.TestRule { }
        '''

        and:
        println runTasksSuccessfully("publish").standardOutput

        then:
        println runTasksSuccessfully("revapi").standardOutput
    }

    def 'ignores scala classes'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'com.twitter'
                oldName = 'chill-avro_2.12'
                oldVersion = '0.9.3'
            }
        """.stripIndent()

        then:
        runTasksSuccessfully("revapi")
    }

    def 'ignores magic methods added by groovy when comparing the same groovy class'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'groovy'
            apply plugin: 'maven-publish'
            
            allprojects {
                group = 'revapi.test'
                ${mavenRepoGradle()}
            }
            
            version = '1.0.0'
            
            dependencies {
                 implementation localGroovy()
            }
            
            revapi {
                oldVersion = project.version
            }
            
            ${testMavenPublication()}
        """.stripIndent()

        writeToFile 'src/main/groovy/foo/Foo.groovy', '''
            package foo
            class Foo {}
        '''.stripIndent()

        println runTasksSuccessfully("publish").standardOutput

        then:
        println runTasksSuccessfully("revapi").standardOutput
    }

    def 'detects breaks in groovy code'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'groovy'
            apply plugin: 'maven-publish'
            
            allprojects {
                group = 'revapi.test'
                ${mavenRepoGradle()}
            }
            
            version = '1.0.0'
            
            dependencies {
                 implementation localGroovy()
            }
            
            revapi {
                oldVersion = project.version
            }
            
            ${testMavenPublication()}
        """.stripIndent()

        def groovyFile = 'src/main/groovy/foo/Foo.groovy'

        writeToFile groovyFile, '''
            package foo
            class Foo {
                String someProperty
            }
        '''.stripIndent()

        println runTasksSuccessfully("publish").standardOutput

        and:
        writeToFile groovyFile, '''
            package foo
            class Foo { }
        '''.stripIndent()

        then:
        def stderr = runRevapiExpectingFailure()

        assert stderr.contains('java.method.removed')
        assert stderr.contains('method java.lang.String foo.Foo::getSomeProperty()')
        assert stderr.contains('method void foo.Foo::setSomeProperty(java.lang.String)')
    }

    def 'does not throw exception when baseline-circleci is applied before this plugin'() {
        when:
        addSubproject 'subproject',  """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
        """

        buildFile << """
            buildscript {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            
                dependencies {
                    classpath 'com.palantir.baseline:gradle-baseline-java:5.61.0'
                }
            }

            // baseline-circleci is the bad plugin, but might as well test against all of baseline
            apply plugin: 'com.palantir.baseline'
            apply plugin: 'com.palantir.baseline-circleci'
        """

        then:
        runTasksSuccessfully("tasks")
    }

    def 'is up to date when nothing has changed after running once'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'org.codehaus.cargo'
                oldName = 'empty-jar'
                oldVersion = '1.7.7'
            }
        """.stripIndent()

        then:
        runTasksSuccessfully('revapi').wasExecuted('revapiAnalyze')
        runTasksSuccessfully('revapi').wasUpToDate('revapiAnalyze')
    }

    @Ignore("Test environment issue - Maven resolution fails before API comparison")
    def 'is not up to date when public (not private) api has changed'() {
        when:
        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            
            repositories {
                mavenCentral()
            }
            
            revapi {
                oldGroup = 'org.codehaus.cargo'
                oldName = 'empty-jar'
                oldVersion = '1.7.7'
            }
        """.stripIndent()

        String javaFile = 'src/main/java/foo/Foo.java'
        writeToFile javaFile, '''
            public class Foo {
                public void publicMethod() {}
                private void privateMethod() {}
            }
        '''.stripIndent()

        then:
        runTasksSuccessfully('revapi').wasExecuted('revapiAnalyze')

        writeToFile javaFile, '''
            public class Foo {
                public void publicMethod() {}
                private void privateMethodRenamed() {}
            }
        '''.stripIndent()

        runTasksSuccessfully('revapi').wasUpToDate('revapiAnalyze')

        writeToFile javaFile, '''
            public class Foo {
                public void publicMethodRenamed() {}
                private void privateMethodRenamed() {}
            }
        '''.stripIndent()

        runTasksSuccessfully('revapi').wasExecuted('revapiAnalyze')
    }

    def 'compatible with gradle-shadow-jar'() {
        when:
        rootProjectNameIs('root')

        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            apply plugin: 'maven-publish'
            
            allprojects {
                group = 'revapi.test'
                ${mavenRepoGradle()}
            }
            
            version = '1.0.0'
            
            revapi {
                oldVersion = project.version
            }
            
            ${testMavenPublication()}
        """.stripIndent()

        def shadowedClass = 'src/main/java/shadow/com/palantir/foo/Bar.java'
        writeToFile shadowedClass, '''
            package shadow.com.palantir.foo;
            public class Bar {}
        '''.stripIndent()

        and:
        println runTasksSuccessfully('publish').standardOutput

        file(shadowedClass).delete()

        then:
        println runTasksSuccessfully('revapi').standardOutput
    }

    def 'changing a protected method in an immutables class is not a break'() {
        when:
        rootProjectNameIs('root')

        buildFile << """
            apply plugin: '${TestConstants.PLUGIN_NAME}'
            apply plugin: 'java-library'
            apply plugin: 'maven-publish'
            
            allprojects {
                group = 'revapi.test'
                ${mavenRepoGradle()}
            }
            
            version = '1.0.0'
            
            revapi {
                oldVersion = project.version
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                annotationProcessor "org.immutables:value:2.8.8"
                compileOnly "org.immutables:value:2.8.8:annotations"  
            }
            
            ${testMavenPublication()}
        """.stripIndent()

        def methodChanges = [
                new MethodChange(
                        'protected abstract String returnTypeChangedProtectedParam();',
                        'protected abstract int returnTypeChangedProtectedParam();',
                        false),
                new MethodChange(
                        'public abstract String returnTypeChangedPublicParam();',
                        'public abstract int returnTypeChangedPublicParam();',
                        true),
                new MethodChange(
                        '',
                        'public abstract String newPublicParam();',
                        false),
                new MethodChange(
                        'public abstract long removedPublicParam();',
                        '',
                        true),
                new MethodChange(
                        'public abstract long reducedVisibilityPublicParam();',
                        'protected abstract long reducedVisibilityPublicParam();',
                        true),
                new MethodChange(
                        'protected abstract long reducedVisibilityProtectedParam();',
                        'abstract long reducedVisibilityProtectedParam();',
                        false),
                new MethodChange(
                        'public abstract long noLongerAbstractPublicParam();',
                        'public long noLongerAbstractPublicParam() { return 3; }',
                        false),
                new MethodChange(
                        'protected abstract long removedProtectedParam();',
                        '',
                        false),
                new MethodChange(
                        'protected abstract long increasedVisibilityProtectedParam();',
                        'public abstract long increasedVisibilityProtectedParam();',
                        false),
                new MethodChange(
                        'public long nowAbstractPublicMethod() { return 3; }',
                        'public abstract long nowAbstractPublicMethod();',
                        false),
                new MethodChange(
                        'public String returnTypeChangedPublicMethod() { return "foo"; }',
                        'public int returnTypeChangedPublicMethod() { return 3; }',
                        true),
                new MethodChange(
                        'public void removedPublicMethod() {}',
                        '',
                        true),
        ]

        def writeOutImmutablesClass = { Closure selector ->
            StringBuilder immutablesClassText = new StringBuilder()
            immutablesClassText.append '''
                package foo;
                
                import org.immutables.value.Value;
                
                @Value.Immutable
                @Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
                public abstract class Foo {
            '''.stripIndent()

            methodChanges.forEach({ methodChange -> immutablesClassText
                    .append('    ')
                    .append(selector(methodChange))
                    .append('\n') })

            immutablesClassText.append '''
                }
            '''.stripIndent()

            writeToFile 'src/main/java/foo/Foo.java', immutablesClassText.toString()
        }

        writeOutImmutablesClass { it.oldText }

        and:
        runTasksSuccessfully('publish')

        writeOutImmutablesClass { it.newText }

        then:
        def executionResult = runTasks('revapi')
        println executionResult.standardError
        !executionResult.success

        def errorMessage = executionResult.failure.cause.cause.message
        errorMessage.contains 'There were Java public API/ABI breaks reported by revapi:'

        for (MethodChange methodChange : methodChanges) {
            assert errorMessage.contains(methodChange.findMethodName()) == methodChange.shouldBreak
        }
    }

    @RestoreSystemProperties
    @Ignore("doesn't work with latest Gradle version and needs to be fixed")
    def 'breaks detected in conjure projects should be limited to those which break java but are not caught by conjure-backcompat'() {
        when:
        rootProjectNameIs('api')

        buildFile << """
            buildscript {
                repositories {
                    mavenCentral()
                }
            
                dependencies {
                    classpath 'com.palantir.gradle.conjure:gradle-conjure:5.49.0'
                }
            }
                        
            allprojects {
                group = 'revapi.test'
                version = '1.0.0'
                
                repositories {
                    mavenCentral()
                }
            }

            apply plugin: 'com.palantir.conjure'
            
            dependencies {
                conjureCompiler 'com.palantir.conjure:conjure:5.49.0'
                conjureJava 'com.palantir.conjure.java:conjure-java:8.23.0'
            }
            
            subprojects {
                apply plugin: '${TestConstants.PLUGIN_NAME}'

                revapi {
                    oldVersion = project.version
                }

                dependencies {
                    api 'com.palantir.conjure.java:conjure-lib:8.23.0'
                    api 'com.palantir.conjure.java:conjure-undertow-lib:8.23.0'
                    api 'com.squareup.retrofit2:retrofit:2.11.0'
                }
                
                apply plugin: 'maven-publish'

                ${mavenRepoGradle()}
                ${testMavenPublication()}
            }
        """

        addSubproject('api-objects')
        addSubproject('api-jersey')
        addSubproject('api-retrofit')
        addSubproject('api-undertow')

        def conjureYml = 'src/main/conjure/conjure.yml'
        writeToFile conjureYml, """
            services:
              RenamedService:
                name: RenamedService
                package: services
              TestService:
                name: TestService
                package: services
                endpoints:
                  renamed:
                    http: GET /renamed
                  swappedArgs:
                    http: GET /swappedArgs/{one}/{two}
                    args:
                      one: string
                      two: boolean
        """.stripIndent()

        and:
        runTasksSuccessfully('compileConjure', 'publish')

        and:
        writeToFile conjureYml, """
            services:
              RenamedToSomethingElseService:
                name: RenamedToSomethingElseService
                package: services
              TestService:
                name: TestService
                package: services
                endpoints:
                  added:
                    http: GET /added
                  renamedToSomethingElse:
                    http: GET /existing
                  swappedArgs:
                    http: GET /swappedArgs/{one}/{two}
                    args:
                      two: boolean
                      one: string
        """.stripIndent()

        runTasksSuccessfully('compileConjure')

        then:
        runTasksWithFailure(':api-jersey:revapi')
        def jerseyJunit = new File(projectDir, 'api-jersey/build/junit-reports/revapi/revapi-api-jersey.xml').text

        assert jerseyJunit.contains('java.class.removed-interface services.RenamedService')
        assert jerseyJunit.contains('java.method.removed-method void services.TestService::renamed()')
        assert jerseyJunit.contains('java.method.parameterTypeChanged-parameter void services.TestService::swappedArgs(===java.lang.String===, boolean)')
        assert jerseyJunit.contains('java.method.parameterTypeChanged-parameter void services.TestService::swappedArgs(java.lang.String, ===boolean===)')
        assert !jerseyJunit.contains('services.TestService::added()')
        assert !jerseyJunit.contains('services.TestService::renamedToSomethingElse()')
        assert !jerseyJunit.contains('java.annotation.attributeValueChanged')

        runTasksWithFailure(':api-retrofit:revapi')
        def retrofitJunit = new File(projectDir, 'api-retrofit/build/junit-reports/revapi/revapi-api-retrofit.xml').text

        assert retrofitJunit.contains('java.class.removed-interface services.RenamedServiceRetrofit')
        assert retrofitJunit.contains('java.method.removed-method retrofit2.Call&lt;java.lang.Void&gt; services.TestServiceRetrofit::renamed()')
        assert retrofitJunit.contains('java.method.parameterTypeChanged-parameter retrofit2.Call&lt;java.lang.Void&gt; services.TestServiceRetrofit::swappedArgs(===java.lang.String===, boolean)')
        assert retrofitJunit.contains('java.method.parameterTypeChanged-parameter retrofit2.Call&lt;java.lang.Void&gt; services.TestServiceRetrofit::swappedArgs(java.lang.String, ===boolean===)')
        assert !retrofitJunit.contains('services.TestServiceRetrofit::added()')
        assert !retrofitJunit.contains('services.TestServiceRetrofit::renamedToSomethingElse()')
        assert !retrofitJunit.contains('java.annotation.attributeValueChanged')

        runTasksSuccessfully(':api-undertow:revapi')
    }

    static class MethodChange {
        final String oldText
        final String newText
        final boolean shouldBreak

        MethodChange(String oldText, String newText, boolean shouldBreak) {
            this.oldText = oldText
            this.newText = newText
            this.shouldBreak = shouldBreak
        }

        String findMethodName() {
            def text = oldText == '' ? newText : oldText
            def matcher = Pattern.compile('(\\w+\\(\\))').matcher(text)
            if (!matcher.find()) {
                throw new IllegalArgumentException("Couldn't find method name in ${text}")
            }
            return matcher.group(1);
        }
    }

    private String testMavenPublication() {
        return """
            publishing {
                publications {
                    publication(MavenPublication) {
                        from components.java
                    }
                }
                ${mavenRepoGradle()}
            }
        """
    }

    private String mavenRepoGradle() {
        def mavenRepoDir = new File(projectDir, "mavenRepo")
        mavenRepoDir.mkdirs()

        return """
            repositories {
                maven {
                    name "testRepo"
                    url "${mavenRepoDir}"
                }
            }
        """
    }

    private File writeToFile(String filename, String content) {
        writeToFile(projectDir, filename, content)
    }

    private File writeToFile(File dir, String filename, String content) {
        def file = new File(dir, filename)
        file.getParentFile().mkdirs()
        file.write(content)
        return file
    }

    private File rootProjectNameIs(String projectName) {
        settingsFile << "rootProject.name = '${projectName}'\n"
    }

    private void runRevapiExpectingToFindDifferences(String projectName) {
        assert runRevapiExpectingFailure().contains("java.class.removed")
        andJunitXmlToHaveBeenProduced(projectName)
    }

    private void runRevapiExpectingResolutionFailure() {
        runRevapiExpectingFailure().contains("Failed to resolve old API")
    }

    private String runRevapiExpectingFailure() {
        ExecutionResult executionResult = runTasksWithFailure("revapi")
        println executionResult.standardOutput
        println executionResult.standardError
        return executionResult.standardError
    }

    private void andJunitXmlToHaveBeenProduced(String projectName) {
        File junitOutput = new File(projectDir, "build/junit-reports/revapi/revapi-${projectName}.xml")
        new XmlParser().parse(junitOutput)
        assert junitOutput.text.contains("java.class.removed")
    }

    @Override
    ExecutionResult runTasksSuccessfully(String... tasks) {
        ExecutionResult result = runTasks(tasks)
        if (result.failure) {
            println result.standardOutput
            result.rethrowFailure()
        }
        result
    }

    @Override
    ExecutionResult runTasksWithFailure(String... tasks) {
        ExecutionResult result = runTasks(tasks)
        if (result.success) {
            println result.standardOutput
            assert false
        }
        result
    }
}
