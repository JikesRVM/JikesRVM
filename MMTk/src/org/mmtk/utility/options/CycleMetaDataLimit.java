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
package org.mmtk.utility.options;

/**
 * Trigger cycle detection if the meta data volume grows to this limit.
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
