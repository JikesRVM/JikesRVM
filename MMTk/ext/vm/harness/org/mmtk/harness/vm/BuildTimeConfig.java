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
package org.mmtk.harness.vm;

import org.mmtk.harness.Harness;

/**
 * Build-time configuration constants for MMTk.
 */
public class BuildTimeConfig extends org.mmtk.vm.BuildTimeConfig {

  @Override
  public String getPlanName() {
    return Harness.plan.getValue();
  }

  @Override
  public String getStringProperty(String name) {
    return System.getProperty(name);
  }

  @Override
  public String getStringProperty(String name, String dflt) {
    return System.getProperty(name, dflt);
  }

  @Override
  public int getIntProperty(String name) {
    return Integer.parseInt(System.getProperty(name));
  }

  @Override
  public int getIntProperty(String name, int dflt) {
    String value = System.getProperty(name);
    return value == null ? dflt : Integer.parseInt(value);
  }

  @Override
  public boolean getBooleanProperty(String name) {
    return Boolean.parseBoolean(System.getProperty(name));
  }

  @Override
  public boolean getBooleanProperty(String name, boolean dflt) {
    String value = System.getProperty(name);
    return value == null ? dflt : Boolean.parseBoolean(value);
  }
}
