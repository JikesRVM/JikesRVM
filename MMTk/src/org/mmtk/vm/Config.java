package org.mmtk.vm;

import org.mmtk.utility.Log;
import org.mmtk.utility.statistics.Xml;

public class Config {
  /* The name of the active plan */
  private final String ACTIVE_PLAN;
  
  /** Mark bit in the header or on the side ? */
  public final boolean HEADER_MARK_BITS;

  Config(BuildTimeConfig config) {
    ACTIVE_PLAN            = config.getPlanName();
    HEADER_MARK_BITS        = config.getBooleanProperty("mmtk.headerMarkBit",false);
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
