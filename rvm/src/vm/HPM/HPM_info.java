/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * HPM configuration information.
 *
 * @author Peter F. Sweeney 
 */
public final class HPM_info
{
  /*
   * Version number
   *  1) initial 
   *  2) better compression of trace format (eliminate a doubleword)
   *  3) support for Intel traces
   */
  static public final int version_number = 3;

  static public  final int MAX_EVENTS   = 8;
  static public  final int MAX_VALUES   = MAX_EVENTS+1;
  // number of HPM events
  static private       int numberOfEvents = -1;
  // number of total values 
  static private       int numberOfValues = -1;
  // number of fixed values above the number of HPM events
  static private final int N_FIXED_VALUES =  1;             // real time
  /**
   * Set the number of values and events
   * @param n_events number of events being counted
   */
  static public  void setNumberOfEvents(int n_events) {
    numberOfEvents = n_events;
    numberOfValues = n_events+N_FIXED_VALUES;
  }
  /**
   * Get number of values
   * @return number of values
   */
  static public  int getNumberOfValues() throws VM_PragmaUninterruptible {
    return numberOfValues;
  }
  /**
   * Get number of events
   * @return number of events being counted
   */
  static public  int getNumberOfEvents() throws VM_PragmaUninterruptible {
    return numberOfEvents;
  }

  // monitoring mode: user, kernel, etc.
  static public  int              mode            = -1;
  /*
   * What endian is used?  
   * PowerPC is big-endian, and Intel is little-endian.
   */
  static public  int              DEFAULT_ENDIAN  =  0;
  static public  int              BIG_ENDIAN      = DEFAULT_ENDIAN;
  static public  int              LITTLE_ENDIAN   =  1;
  static private int              endian      = -1;
  /**
   * Set endian
   */
  static public void setEndian(int value) {
    endian=value;
  }
  static public int getEndian() {
    return endian;
  }
  /**
   * Get endian
   */
  static public boolean isBigEndian() {
    return endian == BIG_ENDIAN;
  }

  static private String processorName   = "";                // processor name
  /**
   * Set processor name and endian
   * @param name name of processor
   */
  static public void setProcessorName(String name) {
    processorName = name;
  }
  static private boolean powerPC(String name) {
    if (name.startsWith("PowerPC") || name.startsWith("POWER") || name.startsWith("RS64-II")){
      return true;
    } 
    return false;
  }

  /**
   * Get processor name
   */
  static public String getProcessorName() {
    return processorName;
  }

  /*
   * Possible HPM counters
   */
  static public String filenamePrefix = "HPM";              // trace and header file name prefices
  /*
   * Return header filename with out path.
   * Assume header and trace files in the same directory.
   */
  static public String headerFilename() {
    String filename = filenamePrefix;
    int index = filenamePrefix.lastIndexOf('/');
    if (index != -1) {
      filename = filenamePrefix.substring(index);
    }
    return filename+".headerFile";
  }


  /*
   * Per counter data
   * Counters start at 1.
   * The 0th entry is for real time.
   */
  // short name description for event
  static public String  []short_names;
  // event id
  static public int     []ids;
  // event status; i.e. verified, unverified, ...
  static public int     []status;

  static {
    short_names   = new String[ MAX_VALUES];
    ids           = new int[    MAX_VALUES];
    status        = new int[    MAX_VALUES];
    for (int i=0; i<MAX_VALUES; i++) {
      short_names[  i] = "";
      ids[          i] = 0;
      status[       i] = -1;
    }
    short_names[0] = "REAL_TIME     ";
  }
  static public void dump(){
    System.out.println(processorName+" has "+numberOfEvents+" events with "+mode+" mode");
    dump_info();
  }
  static public void dump_short_names(){
    for (int i=0; i<=numberOfEvents; i++) {
      System.out.println(short_names[i]+" ");
    }
  }
  static public String short_name(int i) throws VM_PragmaUninterruptible {
    if (i>numberOfEvents) {
      VM.sysWrite("***HPM_info.short_name(");VM.sysWrite(i);VM.sysWrite(") ");VM.sysWrite(i);
      VM.sysWrite(" > number of events ");VM.sysWrite(numberOfEvents);VM.sysWrite("!***");
      VM.sysWriteln();
      VM.sysExit(VM.exitStatusHPMTrouble);
    }
    return short_names[i];
  }
  static public void dump_info(){
    for (int i=0; i<=numberOfEvents; i++) {
      System.out.println(short_names[i]+","+ids[i]+","+status[i]+";");
    }
  }
  /**
   * Swap byte order in integer.  Needed to change little-endian to default big-endian.
   *
   * @param value value to have bytes swapped
   */
  static public  int swapByteOrder(int value) throws VM_PragmaUninterruptible
  {  
    if(VM_HardwarePerformanceMonitors.verbose>=3){VM.sysWrite("VM_HPMs.swapByteOrder(");VM.sysWriteHex(value);}
    byte b1 = (byte)((value & 0xFF000000) >> 24);
    byte b2 = (byte)((value & 0x00FF0000) >> 16);
    byte b3 = (byte)((value & 0x0000FF00) >>  8);
    byte b4 = (byte) (value & 0x000000FF);
    value = 
      (((int)(b4 /*byte4*/ << 24)) & 0xFF000000) + 
      (((int)(b3 /*byte3*/ << 16)) & 0x00FF0000) + 
      (((int)(b2 /*byte2*/ <<  8)) & 0x0000FF00) + 
      (((int)(b1 /*byte1*/      )) & 0x000000FF);

    if(VM_HardwarePerformanceMonitors.verbose>=3){VM.sysWrite(") returns ");VM.sysWriteHex(value);VM.sysWriteln();}
    return value;
  }

}

