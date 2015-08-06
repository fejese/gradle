/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality

import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.util.VersionNumber

class CheckstylePlugin extends AbstractCodeQualityPlugin<Checkstyle> {
    public static final String DEFAULT_CHECKSTYLE_VERSION = "5.9"
    private CheckstyleExtension extension

    @Override
    protected String getToolName() {
        return "Checkstyle"
    }

    @Override
    protected Class<Checkstyle> getTaskType() {
        return Checkstyle
    }

    @Override
    protected CodeQualityExtension createExtension() {
        extension = project.extensions.create("checkstyle", CheckstyleExtension, project)

        extension.with {
            toolVersion = DEFAULT_CHECKSTYLE_VERSION
            config = project.resources.text.fromFile("config/checkstyle/checkstyle.xml")
        }

        return extension
    }

    @Override
    protected void configureTaskDefaults(Checkstyle task, String baseName) {
        def conf = project.configurations['checkstyle']
        conf.defaultDependencies { dependencies ->
            dependencies.add(this.project.dependencies.create("com.puppycrawl.tools:checkstyle:${this.extension.toolVersion}"))
        }

        task.conventionMapping.with {
            checkstyleClasspath = { conf }
            config = { extension.config }
            configProperties = { extension.configProperties }
            ignoreFailures = { extension.ignoreFailures }
            showViolations = { extension.showViolations }
            antClassName = { getAntClassForToolVersion(extension.toolVersion) }
        }

        task.reports.xml.conventionMapping.with {
            enabled = { true }
            destination = { new File(extension.reportsDir, "${baseName}.xml") }
        }
    }

    protected String getAntClassForToolVersion(String toolVersion) {
        if(VersionNumber.parse(toolVersion) >= VersionNumber.parse('6.8')) {
            return 'com.puppycrawl.tools.checkstyle.ant.CheckstyleAntTask'
        } else {
            return 'com.puppycrawl.tools.checkstyle.CheckStyleTask'
        }
    }

    @Override
    protected void configureForSourceSet(SourceSet sourceSet, Checkstyle task) {
        task.with {
            description = "Run Checkstyle analysis for ${sourceSet.name} classes"
            classpath = sourceSet.output
        }
        task.setSource(sourceSet.allJava)
    }
}
