/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
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
