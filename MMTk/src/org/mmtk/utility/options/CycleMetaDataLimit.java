/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * Trigger cycle detection if the meta data volume grows to this limit.
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
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
