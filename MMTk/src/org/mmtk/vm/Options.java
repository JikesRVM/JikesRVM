/* 
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright ANU. 2004
 */
package org.mmtk.vm;

import org.mmtk.utility.options.Option;
import org.vmmagic.pragma.Uninterruptible;
/**
 * Skeleton for class to handle command-line arguments and options for GC.
 * 
 * @author Daniel Frampton
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
