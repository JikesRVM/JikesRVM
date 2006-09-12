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

public class TracePaddingRecord extends TraceRecord
{
  /*
   * Fields
   */ 
  // virtual processor id
  public int vpid = 0;
  // padding length
  public int length = 0;

  /**
   * Constructor
   *
   * @param app_name  name of application that is started
   */
  TracePaddingRecord(int vpid, int length) {
    this.vpid   = vpid;
    this.length = length;
  }
  /**
   * print trace record
   */
  public boolean print()
  {
    System.out.println("VP "+vpid+" Padding "+length+" bytes");
    return true;
  }
}
