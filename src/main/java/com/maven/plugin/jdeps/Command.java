package com.maven.plugin.jdeps;

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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * copied and modified from <a href="https://github.com/apache/maven-jdeps-plugin/blob/master/src/main/java/org/apache/maven/plugins/jdeps/AbstractJDKInternalsMojo.java">...</a>
 * <p>
 * Abstract Mojo for JDeps
 *
 * @author Robert Scholte
 */
public class Command {

    private MavenProject project;

    private MavenSession session;

    private Log log;

    private String logPrefix;

    private ToolchainManager toolchainManager;

    private InMemoryOutputConsumer outputConsumer;


    public Command(MavenProject project, MavenSession session, Log log, String logPrefix, ToolchainManager toolchainManager, InMemoryOutputConsumer outputConsumer) {
        this.project = project;
        this.session = session;
        this.log = log;
        this.logPrefix = logPrefix;
        this.toolchainManager = toolchainManager;
        this.outputConsumer = outputConsumer;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!new File(getClassesDirectory()).exists()) {
            log.info(String.format("%s No classes to analyze", logPrefix));
            return;
        }

        String jExecutable;
        try {
            jExecutable = getJDepsExecutable();
        } catch (IOException e) {
            throw new MojoFailureException("Unable to find jdeps command: " + e.getMessage(), e);
        }

        Commandline cmd = new Commandline();
        cmd.setExecutable(jExecutable);

        addJDepsOptions(cmd);
        addJDepsClasses(cmd, getDependenciesToAnalyze());
        executeJDepsCommandLine(cmd);
    }

    protected void addJDepsOptions(Commandline cmd) {
        cmd.createArg().setValue("-v");
        cmd.createArg().setValue("-verbose:class");
    }

    protected Set<Path> getDependenciesToAnalyze() {
        Set<Path> jdepsClasses = new LinkedHashSet<>();
        jdepsClasses.add(Paths.get(getClassesDirectory()));
        jdepsClasses.add(Paths.get(getTestClassesDirectory()));
        return jdepsClasses;
    }

    protected void addJDepsClasses(Commandline cmd, Set<Path> dependenciesToAnalyze) {
        // <classes> can be a pathname to a .class file, a directory, a JAR file, or a fully-qualified class name.
        for (Path dependencyToAnalyze : dependenciesToAnalyze) {
            cmd.createArg().setFile(dependencyToAnalyze.toFile());
        }
    }

    private String getJDepsExecutable() throws IOException {
        Toolchain tc = getToolchain();

        String jdepsExecutable = null;
        if (tc != null) {
            jdepsExecutable = tc.findTool("jdeps");
        }

        String jdepsCommand = "jdeps" + (SystemUtils.IS_OS_WINDOWS ? ".exe" : "");

        File jdepsExe;

        if (StringUtils.isNotEmpty(jdepsExecutable)) {
            jdepsExe = new File(jdepsExecutable);

            if (jdepsExe.isDirectory()) {
                jdepsExe = new File(jdepsExe, jdepsCommand);
            }

            if (SystemUtils.IS_OS_WINDOWS && jdepsExe.getName().indexOf('.') < 0) {
                jdepsExe = new File(jdepsExe.getPath() + ".exe");
            }

            if (!jdepsExe.isFile()) {
                throw new IOException("The jdeps executable '" + jdepsExe
                        + "' doesn't exist or is not a file.");
            }
            return jdepsExe.getAbsolutePath();
        }

        jdepsExe = new File(SystemUtils.getJavaHome() + File.separator + ".." + File.separator + "sh", jdepsCommand);

        // ----------------------------------------------------------------------
        // Try to find jdepsExe from JAVA_HOME environment variable
        // ----------------------------------------------------------------------
        if (!jdepsExe.exists() || !jdepsExe.isFile()) {
            Properties env = CommandLineUtils.getSystemEnvVars();
            String javaHome = env.getProperty("JAVA_HOME");
            if (StringUtils.isEmpty(javaHome)) {
                throw new IOException("The environment variable JAVA_HOME is not correctly set.");
            }
            if ((!new File(javaHome).getCanonicalFile().exists())
                    || (new File(javaHome).getCanonicalFile().isFile())) {
                throw new IOException("The environment variable JAVA_HOME=" + javaHome
                        + " doesn't exist or is not a valid directory.");
            }

            jdepsExe = new File(javaHome + File.separator + "bin", jdepsCommand);
        }

        if (!jdepsExe.getCanonicalFile().exists() || !jdepsExe.getCanonicalFile().isFile()) {
            throw new IOException("The jdeps executable '" + jdepsExe
                    + "' doesn't exist or is not a file. Verify the JAVA_HOME environment variable.");
        }

        return jdepsExe.getAbsolutePath();
    }

    private void executeJDepsCommandLine(Commandline cmd)
            throws MojoExecutionException {
        // no quoted arguments
        log.info(String.format("{} Executing: " + CommandLineUtils.toString(cmd.getCommandline()).replaceAll("'", ""), logPrefix));


        CommandLineUtils.StringStreamConsumer err = new CommandLineUtils.StringStreamConsumer() {
            @Override
            public void consumeLine(String line) {
                if (!line.startsWith("Picked up JAVA_TOOL_OPTIONS:")) {
                    super.consumeLine(line);
                }
            }
        };
        CommandLineUtils.StringStreamConsumer out = outputConsumer;


        try {
            int exitCode = CommandLineUtils.executeCommandLine(cmd, out, err);

            String output = (StringUtils.isEmpty(out.getOutput()) ? null : '\n' + out.getOutput().trim());

            if (exitCode != 0) {
                if (StringUtils.isNotEmpty(output)) {
                    log.info(output);
                }

                StringBuilder msg = new StringBuilder("\nExit code: ");
                msg.append(exitCode);
                if (StringUtils.isNotEmpty(err.getOutput())) {
                    msg.append(" - ").append(err.getOutput());
                }
                msg.append('\n');
                msg.append("Command line was: ").append(cmd).append('\n').append('\n');

                throw new MojoExecutionException(msg.toString());
            }

            if (StringUtils.isNotEmpty(output)) {
                log.info(output);
            }
        } catch (CommandLineException e) {
            throw new MojoExecutionException("Unable to execute jdeps command: " + e.getMessage(), e);
        }

        // ----------------------------------------------------------------------
        // Handle JDeps warnings
        // ----------------------------------------------------------------------

        if (StringUtils.isNotEmpty(err.getOutput())) {
            log.warn("JDeps Warnings");

            StringTokenizer token = new StringTokenizer(err.getOutput(), "\n");
            while (token.hasMoreTokens()) {
                String current = token.nextToken().trim();

                log.warn(current);
            }
        }
    }

    private Toolchain getToolchain() {
        Toolchain tc = null;
        if (toolchainManager != null) {
            tc = toolchainManager.getToolchainFromBuildContext("jdk", session);

            if (tc == null) {
                // Maven 3.2.6 has plugin execution scoped Toolchain Support
                try {
                    Method getToolchainsMethod =
                            toolchainManager.getClass().getMethod("getToolchains", MavenSession.class, String.class,
                                    Map.class);

                    @SuppressWarnings("unchecked")
                    List<Toolchain> tcs =
                            (List<Toolchain>) getToolchainsMethod.invoke(toolchainManager, session, "jdk",
                                    Collections.singletonMap("version", "[17,)"));

                    if (tcs != null && tcs.size() > 0) {
                        // pick up latest, jdeps of JDK9 has more options compared to JDK8
                        tc = tcs.get(tcs.size() - 1);
                    }
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException |
                         InvocationTargetException e) {
                    // ignore
                }
            }
        }

        return tc;
    }

    private String getClassesDirectory() {
        return project.getBuild().getOutputDirectory();
    }

    private String getTestClassesDirectory() {
        return project.getBuild().getTestOutputDirectory();
    }
}
