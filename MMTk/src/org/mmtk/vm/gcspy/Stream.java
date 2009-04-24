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
import org.mmtk.utility.gcspy.Color;
import org.mmtk.utility.gcspy.StreamConstants;
import org.mmtk.utility.gcspy.drivers.AbstractDriver;
import org.mmtk.vm.VM;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.Address;

/**
 *
 * Abstract class for a GCspy Stream.
 * Implementing classes will mostly forward calls
 * to the gcspy C library
 */

@Uninterruptible public abstract class Stream {

  /****************************************************************************
  *
  * Instance variables
  */

  /**
   * The address of the C stream, gcspy_gc_stream_t *stream, used in all calls
   * to the C library
   */
  protected Address stream;

  /** The owning GCspy space */
  protected ServerSpace serverSpace;

  /** The stream's ID */
  protected int streamId;

  /**
   * A summary has 1 or 2 values depending on presentation style
   * (PERCENT* styles require 2 values).
   */
  protected int summaryLen;

  /** The first summary value */
  protected int summary0;

  /** The second summary value (if any) */
  protected int summary1;

  private int min; // The minimum value for tiles

  private int max; // The maximum value for tiles

  /** use summaries? */
  protected boolean summaryEnabled;

  /** the presentation style */
  protected int presentation;

  protected static final boolean DEBUG = false;

  /**
   * Construct a new GCspy stream.
   *
   * @param driver The AbstractDriver that owns this Stream
   * @param dataType The stream's data type, one of BYTE_TYPE, SHORT_TYPE or INT_TYPE
   * @param name The name of the stream (e.g. "Used space")
   * @param minValue The minimum value for any item in this stream. Values less than
   *                 this will be represented as "minValue-"
   * @param maxValue The maximum value for any item in this stream. Values greater than
   *                 this will be represented as "maxValue+"
   * @param zeroValue The zero value for this stream
   * @param defaultValue The default value for this stream
   * @param stringPre A string to prefix values (e.g. "Used: ")
   * @param stringPost A string to suffix values (e.g. " bytes.")
   * @param presentation How a stream value is to be presented.
   * @param paintStyle How the value is to be painted.
   * @param indexMaxStream The index for the maximum stream if the presentation is *_VAR.
   * @param colour The default colour for tiles of this stream
   * @param summary Is a summary enabled?
   */
  protected Stream(
      AbstractDriver driver,
      int dataType,
      String name,
      int minValue,
      int maxValue,
      int zeroValue,
      int defaultValue,
      String stringPre,
      String stringPost,
      int presentation,
      int paintStyle,
      int indexMaxStream,
      Color colour,
      boolean summary) {

    serverSpace = driver.getServerSpace();
    summaryEnabled = summary;
    this.presentation = presentation;
    if (summary)
      setupSummary(presentation);
    min = minValue;
    max = maxValue;

    driver.addStream(this);
    if (DEBUG) {
      Log.write("Adding stream ");
      Log.write(name);
      Log.writeln(" id=", streamId);
    }
  }


  /**
   * Set the stream address and id (called by AbstractDriver.addStream).
   * @param id the id
   * @param str the address of the gcspy C gcspy_gc_stream_t *stream
   */
  public void setStream(int id, Address str) {
    streamId = id;
    stream = str;
  }

  /**
   * Return the minimum value expected for this stream.
   * @return the minimum value
   */
  public int getMinValue() { return min; }

  /**
   * Return the maximum value expected for this stream.
   * @return the maximum value
   */
  public int getMaxValue() { return max; }

  /**
   * Setup the summary array.
   * @param presentation the presentation style
   */
  @Interruptible
  private void setupSummary(int presentation) {
    switch (presentation) {
      case StreamConstants.PRESENTATION_PLAIN:
      case StreamConstants.PRESENTATION_PLUS:
      case StreamConstants.PRESENTATION_MAX_VAR:
      case StreamConstants.PRESENTATION_ENUM:
        summaryLen = 1;
        break;
      case StreamConstants.PRESENTATION_PERCENT:
      case StreamConstants.PRESENTATION_PERCENT_VAR:
        summaryLen = 2;
        break;
      default:
        VM.assertions._assert(false);
    }
  }

  /**
   * Set the summary value for presentation styles with just one value
   * @param value0 the value
   */
  public void setSummary(int value0) {
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(presentation != StreamConstants.PRESENTATION_PERCENT &&
                            presentation != StreamConstants.PRESENTATION_PERCENT_VAR);
    summary0 = value0;
  }

  /**
   * Set the summary values for presentation styles with two values (i.e.
   * PRESENTATION_PERCENT and PRESENTATION_PERCENT_VAR).
   * @param value0 the numerator value
   * @param value1 the denominator value
   */
  public void setSummary(int value0, int value1) {
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(presentation == StreamConstants.PRESENTATION_PERCENT ||
                            presentation == StreamConstants.PRESENTATION_PERCENT_VAR);
    summary0 = value0;
    summary1 = value1;
  }

  /**
   * Send the data for this stream.
   * @param event the event.
   * @param numTiles the number of tiles to send.
   */
  public abstract void send(int event, int numTiles);

  /**
   * Send the summary data for this stream.
   */
  public void sendSummary() {
    if (summaryEnabled) {
      serverSpace.summary(streamId, summaryLen);
      serverSpace.summaryValue(summary0);
      if (summaryLen == 2)
        serverSpace.summaryValue(summary1);
      serverSpace.summaryEnd();
    }
  }

}


