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

import org.mmtk.utility.options.Option;
/**
 * Skeleton for class to handle command-line arguments and options for GC.
 * 
 **/
public abstract class Options {

  /**
   * Map a name into a key in the VM's format
   * 
   * @param name the space delimited name. 
   * @return the VM specific key.
   */
  public abstract String getKey(String name);

  /**
   * Failure during option processing. This must never return.
   * 
   * @param o The option that was being set.
   * @param message The error message.
   */
  public abstract void fail(Option o, String message);

  /**
   * Warning during option processing.
   * 
   * @param o The option that was being set.
   * @param message The warning message.
   */
  public abstract void warn(Option o, String message);
}
