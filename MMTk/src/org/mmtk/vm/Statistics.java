/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;


/**
 * $Id$ 
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @author Perry Cheng
 *
 * @version $Revision$
 * @date $Date$
 */
public class Statistics {
  /**
   * Returns the number of collections that have occured.
   *
   * @return The number of collections that have occured.
   */
  public static final int getCollectionCount()
    {
    return 0;
  }

  /**
   * Read cycle counter
   */
  public static long cycles() {
    return 0L;
  }

  /**
   * Convert cycles to milliseconds
   */
  public static double cyclesToMillis(long c) {
    return (double)0;
  }

  /**
   * Convert cycles to seconds
   */
  public static double cyclesToSecs(long c) {
    return (double)0;
  }

  /**
   * Convert milliseconds to cycles
   */
  public static long millisToCycles(double t) {
    return 0L;
  }

  /**
   * Convert seconds to cycles
   */
  public static long secsToCycles(double t) {
    return 0L;
  }
}
