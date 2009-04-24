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

  /**
   * @return The name of the current MMTk plan
   */
  public String getPlanName() {
    return Harness.plan.getValue();
  }

  /**
   * Return a property of type String
   * @param name The name of the property
   * @return The value of the property
   */
  public String getStringProperty(String name) {
    return System.getProperty(name);
  }

  /**
   * Return a property of type String, with default.
   * @param name The name of the property
   * @param dflt Default value
   * @return The value of the property
   */
  public String getStringProperty(String name, String dflt) {
    return System.getProperty(name, dflt);
  }

  /**
   * Return a property of type int
   * @param name The name of the property
   * @return The value of the property
   */
  public int getIntProperty(String name) {
    return Integer.parseInt(System.getProperty(name));
  }

  /**
   * Return a property of type String, with default.
   * @param name The name of the property
   * @param dflt Default value
   * @return The value of the property
   */
  public int getIntProperty(String name, int dflt) {
    String value = System.getProperty(name);
    return value == null ? dflt : Integer.parseInt(value);
  }

  /**
   * Return a property of type boolean
   * @param name The name of the property
   * @return The value of the property
   */
  public boolean getBooleanProperty(String name) {
    return Boolean.parseBoolean(System.getProperty(name));
  }

  /**
   * Return a property of type boolean, with default.
   * @param name The name of the property
   * @param dflt Default value
   * @return The value of the property
   */
  public boolean getBooleanProperty(String name, boolean dflt) {
    String value = System.getProperty(name);
    return value == null ? dflt : Boolean.parseBoolean(value);
  }
}
