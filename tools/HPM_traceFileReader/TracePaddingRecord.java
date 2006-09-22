/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
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
