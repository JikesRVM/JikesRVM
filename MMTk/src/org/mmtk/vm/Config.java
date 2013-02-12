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

  Config(BuildTimeConfig config) {
    ACTIVE_PLAN            = config.getPlanName();
    HEADER_MARK_BITS        = config.getBooleanProperty("mmtk.headerMarkBit",true);
  }

  public void printConfig() {
    Log.writeln("================ MMTk Configuration ================");
    Log.write("plan = "); Log.writeln(ACTIVE_PLAN);
    Log.write("HEADER_MARK_BITS = ");  Log.writeln(HEADER_MARK_BITS);
    Log.writeln("====================================================");
  }

  public void printConfigXml() {
    Log.writeln("<config>");
    Xml.configItem("plan",ACTIVE_PLAN);
    Xml.configItem("header-mark-bit",HEADER_MARK_BITS);
    Log.writeln("</config>");
  }
}
