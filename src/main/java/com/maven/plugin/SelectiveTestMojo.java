package com.maven.plugin;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import com.maven.plugin.jdeps.Command;
import com.maven.plugin.jdeps.InMemoryOutputConsumer;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.ToolchainManager;

import java.io.File;

@Mojo(name = "plan",
        requiresDependencyResolution = ResolutionScope.TEST,
        defaultPhase = LifecyclePhase.PROCESS_TEST_CLASSES, threadSafe = true)
public class SelectiveTestMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
    private File outputDirectory;

    @Component
    private ToolchainManager toolchainManager;

    protected MavenProject getProject() {
        return project;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        //TODO: time all the component and print the stats at the end
        //TODO: add git differ

        // generate a jdep classes & tests dependency
        InMemoryOutputConsumer parser = new InMemoryOutputConsumer();
        Command jdep = new Command(project, session, getLog(), "selective-test", toolchainManager, parser);
        long startTs = System.currentTimeMillis();
        jdep.execute();
        getLog().info("rev-test: time took to run the jdeps is " + (System.currentTimeMillis() - startTs));

        //TODO: generate a graph from jdep dependencies

        //TODO: add logic to find invert dependencies of tests from dependency graph

        //TODO: generate a test plan with relevant test case
    }
}
