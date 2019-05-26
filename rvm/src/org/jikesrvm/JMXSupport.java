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
package org.jikesrvm;

import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.runtime.CommandLineArgs;
import org.jikesrvm.runtime.Time;

/**
 * Support class for implementations of the JMX runtime beans.
 */
public class JMXSupport {

  public static String[] getInputArguments() {
    return CommandLineArgs.getInputArgs();
  }

  public static String getName() {
    StringBuilder name = new StringBuilder();
    if (VM.BuildFor32Addr) {
      name.append("32");
    } else {
      name.append("64");
    }
    name.append(" bit Jikes RVM using ");
    if (VM.BuildForGnuClasspath) {
      name.append("GNU Classpath");
    }
    if (VM.BuildForIA32) {
      name.append(" on IA32");
    } else if (VM.BuildForPowerPC) {
      name.append(" on PowerPC");
    }
    name.append(" (");
    name.append(getVmInfo());
    name.append(")");
    return name.toString();
  }

  public static long getStartTime() {
    return Time.bootTime();
  }

  public static String getVmInfo() {
    return getCompilerInfo() + ", " + getGCInfo();
  }

  public static String getCompilerInfo() {
    if (VM.BuildForAdaptiveSystem) {
      return "Adaptive Optimization System and Opt Compiler";
    } else {
      return "Baseline Compiler";
    }
  }

  public static String getGCInfo() {
    return "garbage collection plan: " + Selected.name;
  }

}
