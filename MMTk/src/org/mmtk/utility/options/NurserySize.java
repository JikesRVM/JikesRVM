/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.vmmagic.pragma.UninterruptiblePragma;

/**
 * A composite option that provides a min/max interface to MMTk,
 * and a fixed/bounded option interface to the VM/user.
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class NurserySize {
  // values
  private FixedNursery fixedNursery;
  private BoundedNursery boundedNursery;

  /**
   * Create the options.
   */
  public NurserySize() {
    boundedNursery = new BoundedNursery();
    fixedNursery = new FixedNursery(boundedNursery);
  }

  /**
   * Read the upper bound of the nursery size.
   *
   * @return maximum number of pages in the nursery.
   */
  public int getMaxNursery() throws UninterruptiblePragma {
    return boundedNursery.getPages();
  }

  /**
   * Read the lower bound of the nursery size.
   *
   * @return minimum number of pages in the nursery.
   */
  public int getMinNursery() throws UninterruptiblePragma {
    return fixedNursery.getPages();
  } 
}
