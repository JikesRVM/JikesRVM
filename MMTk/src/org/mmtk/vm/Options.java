/* 
 * (C) Copyright ANU. 2004
 */
package org.mmtk.vm;
 
/**
 * Skeleton for class to handle command-line arguments and options for GC.
 *
 * @author Daniel Frampton 
 **/
public final class Options {

  /**
   * Map a name into a key in the VM's format
   *
   * @param name the space delimited name. 
   * @return the VM specific key.
   */
  public static String getKey(String name) {
    return null;
  }

  /**
   * Failure during option processing. This must never return.
   *
   * @param o The option that was being set.
   * @param message The error message.
   */
  public static void fail(Option o, String message) {
  }

  /**
   * Warning during option processing.
   *
   * @param o The option that was being set.
   * @param message The warning message.
   */
  public static void warn(Option o, String message) {
  }
}
