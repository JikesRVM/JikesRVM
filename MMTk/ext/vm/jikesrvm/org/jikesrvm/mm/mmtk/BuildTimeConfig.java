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
package org.jikesrvm.mm.mmtk;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.Selected;

/**
 * Read build-time configuration information for MMTk from a Java properties
 * file.  Properties read through this mechanism should be read at build time
 * and saved in static final fields.
 * <p>
 * This is a wrapper for a java.util.Properties object.
 */
public class BuildTimeConfig extends org.mmtk.vm.BuildTimeConfig {

  /**
   * The underlying properties object
   */
  private Properties props;

  public BuildTimeConfig(String property_file_property, String default_property_file) {
    props = getProperties(property_file_property,default_property_file);
  }

  public BuildTimeConfig(String property_file_property) {
    props = getProperties(property_file_property,null);
  }

  /**
   * @param property_file_property The name of the property that sets
   * the location of the properties file
   * @param default_property_file The default properties file.
   *
   */
  private Properties getProperties(String property_file_property, String default_property_file) {
    Properties props = new Properties();
    String propFileName;
    if (default_property_file == null) {
      propFileName = System.getProperty(property_file_property);
      if (propFileName == null) {
        System.err.println(property_file_property+" must specify a properties file");
        VM.sysExit(1);
      }
    } else {
      propFileName = System.getProperty(property_file_property, default_property_file);
    }
    File propFile = new File(propFileName);

    try {
      BufferedInputStream propFileStream = new BufferedInputStream(new FileInputStream(propFile));
      props.load(propFileStream);
      propFileStream.close();
    } catch (FileNotFoundException e) {
      if (!propFileName.equals(default_property_file)) {
        System.err.println(propFileName+" not found.");
        VM.sysExit(1);
      }
    } catch (IOException e) {
      e.printStackTrace();
      VM.sysExit(1);
    }
    return props;
  }

  @Override
  public String getPlanName() {
    return Selected.name;
  }

  @Override
  public boolean getBooleanProperty(String name, boolean dflt) {
    String value = props.getProperty(name,Boolean.toString(dflt));
    return Boolean.valueOf(value);
  }

  @Override
  public boolean getBooleanProperty(String name) {
    String value = props.getProperty(name);
    if (value == null)
      throw new RuntimeException("Undefined property "+name);
    return Boolean.valueOf(value);
  }

  @Override
  public int getIntProperty(String name, int dflt) {
    String value = props.getProperty(name,Integer.toString(dflt));
    return Integer.valueOf(value);
  }

  @Override
  public int getIntProperty(String name) {
    String value = props.getProperty(name);
    if (value == null)
      throw new RuntimeException("Undefined property "+name);
    return Integer.valueOf(value);
  }

  @Override
  public String getStringProperty(String name, String dflt) {
    return props.getProperty(name,dflt);
  }

  @Override
  public String getStringProperty(String name) {
    String value = props.getProperty(name);
    if (value == null)
      throw new RuntimeException("Undefined property "+name);
    return value;
  }


}
