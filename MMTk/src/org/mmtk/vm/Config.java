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

import org.mmtk.utility.Log;
import org.mmtk.utility.statistics.Xml;

public class Config {
  /** The name of the active plan */
  private final String ACTIVE_PLAN;

  /** Mark bit in the header or on the side ? */
  public final boolean HEADER_MARK_BITS;

  /** Zero pages on release? */
  public final boolean ZERO_PAGES_ON_RELEASE;

  Config(BuildTimeConfig config) {
    ACTIVE_PLAN            = config.getPlanName();
    HEADER_MARK_BITS        = config.getBooleanProperty("mmtk.headerMarkBit",true);
    ZERO_PAGES_ON_RELEASE  = config.getBooleanProperty("mmtk.zeroPagesOnRelease",false);
  }

  public void printConfig() {
    Log.writeln("================ MMTk Configuration ================");
    Log.writeln("plan = ");
    Log.writeln(ACTIVE_PLAN);
    Log.writeln("HEADER_MARK_BITS = ", HEADER_MARK_BITS);
    Log.writeln("ZERO_PAGES_ON_RELEASE = ", ZERO_PAGES_ON_RELEASE);
    Log.writeln("====================================================");
  }

  public void printConfigXml() {
    Log.writeln("<config>");
    Xml.configItem("plan",ACTIVE_PLAN);
    Xml.configItem("header-mark-bit",HEADER_MARK_BITS);
    Xml.configItem("zero-pages-on-release",ZERO_PAGES_ON_RELEASE);
    Log.writeln("</config>");
  }
}
