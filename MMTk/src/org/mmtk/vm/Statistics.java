/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;

import org.vmmagic.pragma.Uninterruptible;


@Uninterruptible public abstract class Statistics {
  /**
   * Returns the number of collections that have occured.
   * 
   * @return The number of collections that have occured.
   */
  public abstract int getCollectionCount();

  /**
   * Read cycle counter
   */
  public abstract long cycles();

  /**
   * Convert cycles to milliseconds
   */
  public abstract double cyclesToMillis(long c);
  
  /**
   * Convert cycles to seconds
   */
  public abstract double cyclesToSecs(long c);

  /**
   * Convert milliseconds to cycles
   */
  public abstract long millisToCycles(double t);

  /**
   * Convert seconds to cycles
   */
  public abstract long secsToCycles(double t);
}
