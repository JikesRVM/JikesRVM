/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Try to limit reference counting collections to this time cap.
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class GCTimeCap extends MicrosecondsOption {
  /**
   * Create the option.
   */
  public GCTimeCap() {
    super("GC Time Cap",
          "Try to limit reference counting collections to this time cap",
          1000000);
  }
}
