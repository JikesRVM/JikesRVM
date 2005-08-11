/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

/**
 * GCspy Tile Size.
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class GCspyTileSize extends IntOption {
  /**
   * Create the option.
   */
  public GCspyTileSize() {
    super("GCspy Tile Size",
          "GCspy Tile Size",
          131072);
  }

  /**
   * Ensure the tile size is positive
   */
  protected void validate() {
    failIf(this.value <= 0, "Unreasonable gcspy tilesize");
  }
}
