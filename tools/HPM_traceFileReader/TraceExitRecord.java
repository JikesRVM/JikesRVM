/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id:&

/**
 * Represent a trace record as a user defined exit RVM record.
 *
 * @author Peter F. Sweeney
 * @date 6/4/2003
 */

public class TraceExitRecord extends TraceRecord
{
  /*
   * Fields
   */ 
  // virtual processor id
  public int vpid = 0;
  // appliction name
  public int value = 0;

  /**
   * Constructor
   *
   * @param app_name  name of application that is started
   */
  TraceExitRecord(int vpid, int value) {
    this.vpid  = vpid;
    this.value = value;
  }
  /**
   * print trace record
   */
  public boolean print()
  {
    System.out.println("VP "+vpid+" Exit RVM "+value);
    return true;
  }
}
