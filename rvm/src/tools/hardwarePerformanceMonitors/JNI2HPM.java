/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * This class provides a jni interface to access the PowerPC
 * hardware performance monitors.
 * Trampoline code to hpm.c methods.
 * No dependency on "C" include files.
 *
 * @author Peter Sweeney
 * creation date 6/27/2001
 */

public class JNI2HPM 
{
  private static final boolean debug = true;
  static final int OK_CODE = 0;

  // native methods
  public static native int  hpmInit();
  public static native int  hpmSetSettings();
  public static native int  hpmDeleteSettings();
  public static native int  hpmGetSettings();
  public static native int  hpmSetEvent(  int e1, int e2, int e3, int e4);
  public static native int  hpmSetEventX(int e5, int e6, int e7, int e8);
  public static native int  hpmSetModeUser();
  public static native int  hpmSetModeKernel();
  public static native int  hpmSetModeBoth();
  public static native int  hpmStartCounting();
  public static native int  hpmStopCounting();
  public static native int  hpmResetCounters();
  public static native long hpmGetCounter(int counter);
  public static native int  hpmPrint();
  public static native int  hpmTest();

  // where to find native method implementations
  static {
    System.loadLibrary("jni2hpm");
  }
  /**
   * Initialize hardware performance monitors and 
   * start counting specified events.
   * Expects the environment variables:
   *  HPM_EVENT_1, HPM_EVENT_2, HPM_EVENT_3, HPM_EVENT_4, 
   *  HPM_EVENT_5, HPM_EVENT_6, HPM_EVENT_7, HPM_EVENT_8, 
   * to be set to integer values.
   */
  public static void init() 
  {
    int      e1=0,    e2=0,    e3=0,    e4=0,    
             e5=0,    e6=0,    e7=0,    e8=0;
    String s_e1="", s_e2="", s_e3="", s_e4="", 
           s_e5="", s_e6="", s_e7="", s_e8="";
    int      mode=0;
    String s_mode="";
    if(debug)System.out.print("JNI2HPM.init()\n");
    // Get system properties that determine HPM events to count.
    try {
      s_e1   = System.getProperty("HPM_EVENT_1", "0");
      s_e2   = System.getProperty("HPM_EVENT_2", "0");
      s_e3   = System.getProperty("HPM_EVENT_3", "0");
      s_e4   = System.getProperty("HPM_EVENT_4", "0");
      s_e5   = System.getProperty("HPM_EVENT_5", "0");
      s_e6   = System.getProperty("HPM_EVENT_6", "0");
      s_e7   = System.getProperty("HPM_EVENT_7", "0");
      s_e8   = System.getProperty("HPM_EVENT_8", "0");
      s_mode = System.getProperty("HPM_MODE",    "0");
    } catch (SecurityException e) {
      System.err.print("***JNI2HPM.init(): Security Exception");
      System.err.print("  attempting to access system properties");
      e.printStackTrace();
      System.exit(-1);
    }
    if(debug)System.out.print("JNI2HPM.init(): system properties Event 1: "+
			      s_e1+", 2: "+s_e2+", 3: "+s_e3+", 4: "+
			      s_e4+", 5: "+s_e5+", 6: "+s_e6+", 7: "+
			      s_e7+", 8: "+s_e8+", mode: "+s_mode+"\n");
    try {
      e1 = Integer.parseInt(s_e1);      e2 = Integer.parseInt(s_e2);
      e3 = Integer.parseInt(s_e3);      e4 = Integer.parseInt(s_e4);
      e5 = Integer.parseInt(s_e5);      e6 = Integer.parseInt(s_e6);
      e7 = Integer.parseInt(s_e7);      e8 = Integer.parseInt(s_e8);
      mode = Integer.parseInt(s_mode);
    } catch (NumberFormatException e) {
      System.err.print("***JNI2HPM.init(): Number Format Exception");
      System.err.print("   can't translate string options for Hardware Performance Monitor");
      System.err.print(" Event 1: "+s_e1+", 2: "+s_e2+", 3: "+s_e3+
		            ", 4: "+s_e4+", 5: "+s_e5+", 6: "+s_e6+
		            ", 7: "+s_e7+", 8: "+s_e8+", mode: "+mode+"\n");
      e.printStackTrace();
      System.exit(-1);
    }
    if(debug)System.out.print("JNI2HPM.init(): Event 1: "+e1+
			      ", 2: "+e2+", 3: "+e3+", 4: "+e4+
			      ", 5: "+e5+", 6: "+e6+", 7: "+e7+
			      ", 8: "+e8+", mode: "+mode+"\n");
  
    if (hpmInit() != OK_CODE) {
      System.err.print("JNI2HPM.init(): hpmInit() failed!\n");
      System.exit(-1);
    }
    if (hpmSetEvent(e1, e2, e3, e4) != OK_CODE) {
      System.err.print("JNI2HPM.init(): hpmSetEvent("+
		       e1+", "+e2+", "+e3+", "+e4+") failed!\n");
      System.exit(-1);
    }
    if (hpmSetEventX(e5, e6, e7, e8) != OK_CODE) {
      System.err.print("JNI2HPM.init(): hpmSetEventX("+e5+", "+
		       e6+", "+e7+", "+e8+") failed!\n");
      System.exit(-1);
    }
    if        (mode == 0) {
      if (hpmSetModeUser() != OK_CODE) {
	System.err.print("JNI2HPM.init(): hpmSetModeUser() failed!\n");
	System.exit(-1);
      }
    } else if (mode == 1) {
      if (hpmSetModeKernel() != OK_CODE) {
	System.err.print("JNI2HPM.init(): hpmSetModeKernel() failed!\n");
	System.exit(-1);
      }
    } else {
      if (hpmSetModeBoth() != OK_CODE) {
	System.err.print("JNI2HPM.init(): hpmSetModeBoth() failed!\n");
	System.exit(-1);
      }
    }
    if (hpmSetSettings() != OK_CODE) {
      System.err.print("JNI2HPM.init(): hpmSetSettings() failed!\n");
      System.exit(-1);
    }
  }
  
  /**
   * Write out the hardware performance monitor counters.
   * Assume counting is already stopped.
   */
  public static void dump() 
  {
    if(debug)System.out.print("JNI2HPM.dump()\n");
    // stop counting
    //    if (hpmStopCounting() != OK_CODE) {
    //      System.err.print("JNI2HPM.report(): hpmStopCounting() failed!\n");
    //      System.exit(-1);
    //    }
    // print counter values
    if (hpmPrint() != OK_CODE) {
      System.err.print("JNI2HPM.dump(): hpmPrintx() failed!\n");
      System.exit(-1);
    }
  }
}
