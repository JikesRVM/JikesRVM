/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Trigger cycle detection if the meta data volume grows to this limit.
 * 
 *
 * @author Daniel Frampton
 */
public class CycleMetaDataLimit extends PagesOption {
  /**
   * Create the option.
   */
  public CycleMetaDataLimit() {
    super("Cycle Meta Data Limit",
        "Trigger cycle detection if the meta data volume grows to this limit",
        4096);
  }
}
