/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id:&

/**
 * Represent a trace record.
 * A trace record may contain HPM counter values or a user defined record.
 *
 * @author Peter F. Sweeney
 * @date 2/12/2003
 */

abstract public class TraceRecord
{
  /*
   * format types 
   * Must be kept consistent with VM_HardwarePerformanceMonitor
   */
  static public int          COUNTER_TYPE = 1;
  static public int        START_APP_TYPE = 2;
  static public int     COMPLETE_APP_TYPE = 3;
  static public int    START_APP_RUN_TYPE = 4;
  static public int COMPLETE_APP_RUN_TYPE = 5;
  static public int             EXIT_TYPE = 6;
  static public int          PADDING_TYPE = 10;

  /**
   * print trace record
   */
  abstract public boolean print();
}
