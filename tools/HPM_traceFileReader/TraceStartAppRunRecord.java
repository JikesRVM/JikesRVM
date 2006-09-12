/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id:&

/**
 * Represent a trace record as a user defined start application run record.
 *
 * @author Peter F. Sweeney
 * @date 3/2/2003
 */

public class TraceStartAppRunRecord extends TraceRecord
{
  /*
   * Fields
   */ 
  // virutal processor id
  public int vpid = 0;
  // appliction name
  public String app_name = null;
  // appliction name
  public int run = -1;

  /**
   * Constructor
   *
   * @param app_name  name of application that is started
   * @param run       number of run that is started
   */
  TraceStartAppRunRecord(int vpid, String app_name, int run) {
    this.vpid     = vpid;
    this.app_name = app_name;
    this.run      = run;
  }
  /**
   * print trace record
   */
  public boolean print()
  {
    System.out.println("VP "+vpid+" Start application "+app_name+" run "+run);
    return true;
  }
}
