/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id:&
package com.ibm.JikesRVM;

/**
 * HPM configuration information.
 *
 * @author Peter F. Sweeney 
 */
public final class HPM_info
{
  public static final int MAX_COUNTERS = 8;
  public static final int MAX_VALUES   = MAX_COUNTERS+1;
  /*
   * Version number
   *  1) initial 
   *  2) better compression of trace format (eliminate a doubleword)
   */
  public int version_number = 2;
  /*
   * Possible HPM counters
   */
  public int    numberOfCounters = 0;		// number of counters
  public int    maxNumberOfEventsPerCounter = 0;// number of events per counter
  public String processorName   = "";		// processor name
  public String filenamePrefix = "HPM";         // trace and header file name prefices
  /*
   * Return header filename with out path.
   * Assume header and trace files in the same directory.
   */
  public String headerFilename() {
    String filename = filenamePrefix;
    int index = filenamePrefix.lastIndexOf('/');
    if (index != -1) {
      filename = filenamePrefix.substring(index);
    }
    return filename+".headerFile";
  }

  public  int              mode            = 12;

  /*
   * Per counter data
   * Counters start at 1.
   * The 0th entry is for real time.
   */
  // short name description for event
  public String  []short_names;
  // event id
  public int     []ids;
  // event status; i.e. verified, unverified, ...
  public int     []status;
  // thresholdable event
  public boolean []thresholdable;

  public HPM_info() {
    short_names   = new String[ MAX_VALUES];
    ids           = new int[    MAX_VALUES];
    status        = new int[    MAX_VALUES];
    thresholdable = new boolean[MAX_VALUES];
    for (int i=0; i<MAX_VALUES; i++) {
      short_names[  i] = "";
      ids[          i] = 0;
      status[       i] = -1;
      thresholdable[i] = false;
    }
    short_names[0] = "REAL_TIME     ";
  }
  public void dump(){
    System.out.println(processorName+" has "+numberOfCounters+" counters with "+mode+" mode");
    dump_info();
  }
  public void dump_short_names(){
    for (int i=0; i<=numberOfCounters; i++) {
      System.out.println(short_names[i]+" ");
    }
  }
  public String short_name(int i) throws VM_PragmaUninterruptible {
    if (i>numberOfCounters) {
      VM.sysWrite("***HPM_info.short_name(");VM.sysWrite(i);VM.sysWrite(") ");VM.sysWrite(i);
      VM.sysWrite(" > number of counters ");VM.sysWrite(numberOfCounters);VM.sysWrite("!***");
      VM.sysWriteln();
      VM.sysExit(VM.exitStatusHPMTrouble);
    }
    return short_names[i];
  }
  public void dump_info(){
    for (int i=0; i<=numberOfCounters; i++) {
      System.out.println(short_names[i]+","+ids[i]+","+status[i]+","+thresholdable[i]+";");
    }
  }
}

