/**
 ** AbstractTile.java
 **
 ** (C) Copyright Richard Jones, 2003
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package org.mmtk.vm.gcspy;



/**
 * This abstract class is the super-class for all tiles. In particular, it
 * provides the control info for each tile.
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public abstract class AbstractTile 
  implements  Uninterruptible {
  public final static String Id = "$Id$";

  // Controls used for tile presentation
  public static final byte CONTROL_USED            =  1;	// used tile
  public static final byte CONTROL_BACKGROUND      =  2;	// background tile
  public static final byte CONTROL_UNUSED          =  4;	// unused tile
  public static final byte CONTROL_SEPARATOR       =  8;	// separator
  public static final byte CONTROL_LINK            = 16;

  private byte control_;		// The value of the control for this tile

  /**
   * Clear a tile
   */
  protected void zero() {
    initControl((byte)0);
  }

  /**
   * Is a tile used?
   *
   * @return true if the tile is used
   */
  public static boolean controlIsUsed (byte val) {
    return (val & CONTROL_USED) != 0;
  }

  /**
   * Is a tile a background pseudo-tile?
   *
   * @return true if the tile is a background tile
   */
  public static boolean controlIsBackground (byte val) {
    return (val & CONTROL_BACKGROUND) != 0;
  }

  /**
   * Is a tile unused?
   *
   * @return true if the tile is unused
   */
  public static boolean controlIsUnused (byte val) {
    return (val & CONTROL_UNUSED) != 0;
  }

  /**
   * Is this a separator?
   *
   * @return true if this is a separator
   */
  public static boolean controlIsSeparator (byte val) {
    return (val & CONTROL_SEPARATOR) != 0;
  }  

  /**
   * Initialise the value of a control
   *
   * @param value The new value of the control
   */
  public void initControl(byte value) {
    control_ = value;
  }

  /**
   * Add to the control
   *
   * @param value The value to add to the control
   */
  public void addControl(byte value) {
    control_ |= value;
  }

  /** Set the control
   *
   * @param value The value to set
   */
  public void setControl(byte value) {
    control_ &= value;
  }

  /**
   * Get the value of a control
   *
   * @return The value of the control
   */
  public byte getControl() {
    return control_;
  }

  /**
   * Initialise control values in tiles
   *
   * @param tiles The tiles to initialise
   */
  public static void control (AbstractTile[] tiles) {
    for (int i = 0; i < tiles.length; ++i) {
      tiles[i].initControl(CONTROL_USED);
    }
  }

  /**
   * Set the control value in each tile in a region
   * 
   * @param tiles The tiles to set
   * @param tag The control tag
   * @param start The start index of the region
   * @param len The number of tiles in the region
   */
  public static void controlValues (AbstractTile[] tiles, byte tag, int start, int len) {
    for (int i = start; i < (start+len); ++i) {
      if (controlIsBackground(tag) ||
   	  controlIsUnused(tag)) {
        if (controlIsUsed(tiles[i].getControl()))
	  tiles[i].setControl((byte)~CONTROL_USED);
      }
      tiles[i].addControl(tag);
    }
  }

}
