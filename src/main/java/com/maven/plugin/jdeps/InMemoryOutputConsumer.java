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

import org.codehaus.plexus.util.cli.CommandLineUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class InMemoryOutputConsumer extends CommandLineUtils.StringStreamConsumer {
    private ArrayList<String> data = new ArrayList<>();

    private Map<String, String> dependencies = new HashMap<>();

    public void consumeLine(String line) {
        data.add(line);
    }

    public Map<String, String> getDependencies() {
        return dependencies;
    }
}
