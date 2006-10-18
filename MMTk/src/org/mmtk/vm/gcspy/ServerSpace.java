/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Richard Jones, 2003-6
 * Computing Laboratory, University of Kent at Canterbury
 * All rights reserved.
 */
package org.mmtk.vm.gcspy;

import org.mmtk.utility.Log;
import org.mmtk.utility.gcspy.drivers.AbstractDriver;
import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Abstract class for the GCspy Space abstraction.
 * 
 * Implementing classes will largely forward calls to the gcspy C library.
 * 
 * $Id: ServerSpace.java 10806 2006-09-22 12:17:46Z dgrove-oss $
 * 
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision: 10806 $
 * @date $Date: 2006-09-22 13:17:46 +0100 (Fri, 22 Sep 2006) $
 */
public abstract class ServerSpace implements Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */
  protected static final String DEFAULT_UNUSED_STRING = "NOT USED";       // The "unused" string

  /****************************************************************************
  *
  * Instance variables
  */
  protected int spaceId;         // the space's ID
  protected Address driver;      // a pointer to the C driver, gcspy_gc_drivert *driver;
  protected static final boolean DEBUG = false;

  
  /**
   * Get a pointer to the native driver
   * @return The address of the C driver, gcspy_gc_drivert *, used in all calls
   * to the C library.
   */
  Address getDriverAddress() {
    return driver;
  }
  
  /**
   * Tell the native driver the tile name.
   * @param i the number of the tile
   * @param start the starting address of the tile
   * @param end the end address
   */
  public abstract void setTilename(int i, Address start, Address end);

  /**
   * Tell the native driver the tile name.
   * @param i the number of the tile
   * @param format the name of the tile, a format string
   * @param value The value for the format string
   */
  public abstract void setTilename(int i, Address format, long value);

  /**
   * Tell the native driver the tile names.
   * @param i the number of the tile
   * @param format The name, including format tags
   * @param value The value for the format string
   */
  public abstract void setTilename(int i, String format, long value);
  
  /**
   * Tell the C driver to resize
   * @param size the new driver size
   */
  public abstract void resize(int size);

  /** 
   * Start a transmission
   */
  public abstract void startCommunication();

  /**
   * Add a stream to the native driver
   * @param id the stream's ID
   * @return the address of the C gcspy_gc_stream_t
   */
  public abstract Address addStream(int id);

  /**
   * Start transmitting a stream.
   * @param id The stream's ID
   * @param len The number of items in the stream
   */
  public abstract void stream(int id, int len);

  /**
   * Send a byte
   * @param value The byte
   */
  public abstract void streamByteValue(byte value);

  /**
   * Send a short
   * @param value The short
   */
  public abstract void streamShortValue(short value);

  /**
   * Send an int
   * @param value The int
   */
  public abstract void streamIntValue(int value);

  /**
   * End of this stream
   */
  public abstract void streamEnd ();

  /**
   * Start to send a summary
   * @param id The stream's ID
   * @param len The number of items to be sent
   */
  public abstract void summary (int id, int len);

  /**
   * Send a summary value
   * @param val The value
   */
  public abstract void summaryValue (int val);

  /**
   * End the summary
   */
  public abstract void summaryEnd ();

  /**
   * Send all the control info for the space
   * @param space The GCspy driver for this space
   * @param tileNum The number of tiles
   */
  public abstract void sendControls (AbstractDriver space, int tileNum);

  /**
   * Send info for this space
   * @param info A pointer to the information (held as C string)
   */
  public abstract void spaceInfo (Address info);
  
  /**
   * End the transmission (for this event)
   */
  public void endCommunication() {
    if (DEBUG) Log.write("endComm\n");
  }
}
