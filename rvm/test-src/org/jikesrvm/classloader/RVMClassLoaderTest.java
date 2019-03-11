/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.io.File;

import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

@RunWith(VMRequirements.class)
@Category(RequiresBuiltJikesRVM.class)
public class RVMClassLoaderTest {

  private static String savedApplicationRepositories;

  @BeforeClass
  public static void saveCorrectRepos() {
    savedApplicationRepositories = RVMClassLoader.getApplicationRepositories();
  }

  @After
  public void restoreCorrectRepos() {
    RVMClassLoader.overwriteApplicationRepositoriesForUnitTest(savedApplicationRepositories);
    RVMClassLoader.overwriteAgentPathForUnitTest(null);
  }

  @Test
  public void applicationRepositoriesNeedToBeRebuiltToGetAgentPathAddedToApplicationRepositories() throws Exception {
    String applicationRepositories = RVMClassLoader.getApplicationRepositories();
    String agentPath = "agentPath";
    RVMClassLoader.addAgentRepositories(agentPath);
    RVMClassLoader.rebuildApplicationRepositoriesWithAgents();
    String newApplicationRepositories = RVMClassLoader.getApplicationRepositories();
    assertThat(newApplicationRepositories, is(applicationRepositories + File.pathSeparator + agentPath));
  }

  @Test
  public void multipleAgentPatsCanBeAdded() throws Exception {
    String applicationRepositories = RVMClassLoader.getApplicationRepositories();
    String agentPath = "agentPath";
    RVMClassLoader.addAgentRepositories(agentPath);
    String secondAgentPath = "theOtherAgent";
    RVMClassLoader.addAgentRepositories(secondAgentPath);
    RVMClassLoader.rebuildApplicationRepositoriesWithAgents();
    String newApplicationRepositories = RVMClassLoader.getApplicationRepositories();
    assertThat(newApplicationRepositories, is(applicationRepositories + File.pathSeparator + agentPath + File.pathSeparator + secondAgentPath));
  }

}
