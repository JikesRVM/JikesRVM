/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.vm.gcspy;

import org.mmtk.utility.Log;
import org.mmtk.utility.gcspy.drivers.AbstractDriver;
import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Abstract class for the GCspy Space abstraction.<p>
 *
 * Implementing classes will largely forward calls to the gcspy C library.
 */
@Uninterruptible public abstract class ServerSpace {

  /****************************************************************************
   *
   * Class variables
   */

  /** The "unused" string */
  protected static final String DEFAULT_UNUSED_STRING = "NOT USED";

  /****************************************************************************
  *
  * Instance variables
  */

  /** the space's ID */
  protected int spaceId;
  /** a pointer to the C driver, {@code gcspy_gc_drivert *driver} */
  protected Address driver;
  protected static final boolean DEBUG = false;


  /**
   * Get a pointer to the native driver
   * @return The address of the C driver, {@code gcspy_gc_drivert *}, used in all calls
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
   * @return the address of the C {@code gcspy_gc_stream_t}
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
  public abstract void streamEnd();

  /**
   * Start to send a summary
   * @param id The stream's ID
   * @param len The number of items to be sent
   */
  public abstract void summary(int id, int len);

  /**
   * Send a summary value
   * @param val The value
   */
  public abstract void summaryValue(int val);

  /**
   * End the summary
   */
  public abstract void summaryEnd();

  /**
   * Send all the control info for the space
   * @param space The GCspy driver for this space
   * @param tileNum The number of tiles
   */
  public abstract void sendControls(AbstractDriver space, int tileNum);

  /**
   * Send info for this space
   * @param info A pointer to the information (held as C string)
   */
  public abstract void spaceInfo(Address info);

  /**
   * End the transmission (for this event)
   */
  public void endCommunication() {
    if (DEBUG) Log.write("endComm\n");
  }
}
