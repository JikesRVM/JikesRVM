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
package org.mmtk.vm;

/**
 * Build-time configuration constants for MMTk.
 */
public abstract class BuildTimeConfig {

  /**
   * @return The name of the current MMTk plan
   */
  public abstract String getPlanName();

  /**
   * Return a property of type String
   * @param name The name of the property
   * @return The value of the property
   */
  public abstract String getStringProperty(String name);

  /**
   * Return a property of type String, with default.
   * @param name The name of the property
   * @param dflt Default value
   * @return The value of the property
   */
  public abstract String getStringProperty(String name, String dflt);

  /**
   * Return a property of type int
   * @param name The name of the property
   * @return The value of the property
   */
  public abstract int getIntProperty(String name);

  /**
   * Return a property of type String, with default.
   * @param name The name of the property
   * @param dflt Default value
   * @return The value of the property
   */
  public abstract int getIntProperty(String name, int dflt);

  /**
   * Return a property of type boolean
   * @param name The name of the property
   * @return The value of the property
   */
  public abstract boolean getBooleanProperty(String name);

  /**
   * Return a property of type boolean, with default.
   * @param name The name of the property
   * @param dflt Default value
   * @return The value of the property
   */
  public abstract boolean getBooleanProperty(String name, boolean dflt);
}
