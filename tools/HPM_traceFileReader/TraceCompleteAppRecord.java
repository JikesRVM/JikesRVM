/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id:&

/**
 * Represent a trace record as a user defined complete application record.
 *
 * @author Peter F. Sweeney
 * @date 3/2/2003
 */

public class TraceCompleteAppRecord extends TraceRecord
{
  /*
   * Fields
   */ 
  // virtual processor id
  public int vpid = 0;
  // appliction name
  public String app_name = null;

  /**
   * Constructor
   *
   * @param app_name  name of application that is started
   */
  TraceCompleteAppRecord(int vpid, String app_name) {
    this.vpid     = vpid;
    this.app_name = app_name;
  }
  /**
   * print trace record
   */
  public boolean print()
  {
    System.out.println("VP "+vpid+" Complete application "+app_name);
    return true;
  }
}
