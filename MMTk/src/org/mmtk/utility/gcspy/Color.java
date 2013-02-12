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
package org.mmtk.utility.gcspy;

import org.mmtk.vm.VM;

import org.vmmagic.pragma.*;

/**
 * Cut-down implementation of java.awt.Color sufficient to provide
 * the server side (Stream) with colours
 */
@Uninterruptible public class Color {

  /*Some gcspy standard colours (taken from gcspy_color_db.c). */
  public static final Color Black     = new Color(0, 0, 0);
  public static final Color Blue      = new Color(0, 0, 255);
  public static final Color Cyan      = new Color(0, 255, 255);
  public static final Color DarkGray  = new Color(64, 64, 64);
  public static final Color Gray      = new Color(128, 128, 128);
  public static final Color Green     = new Color(0, 255, 0);
  public static final Color LightGray = new Color(192, 192, 192);
  public static final Color Magenta   = new Color(255, 0, 255);
  public static final Color MidGray   = new Color(160, 160, 160);
  public static final Color NavyBlue  = new Color(0, 0, 150);
  public static final Color OffWhite  = new Color(230, 230, 230);
  public static final Color Orange    = new Color(255, 200, 0);
  public static final Color Pink      = new Color(255, 175, 175);
  public static final Color Red       = new Color(255, 0, 0);
  public static final Color White     = new Color(255, 255, 255);
  public static final Color Yellow    = new Color(255, 255, 0);

  private short r; // red component
  private short g; // green component
  private short b; // blue component

  /**
   * Constructor for a simple RGB colour model.
   *
   * @param r red component
   * @param g green component
   * @param b blue component
   */
  public Color(short r, short g, short b) {
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert((0 <= r) && (r <= 255) &&
                           (0 <= g) && (g <= 255) &&
                           (0 <= b) && (b <= 255));
    this.r = r;
    this.g = g;
    this.b = b;
  }

  /**
   * Constructor for a simple RGB colour model.
   *
   * @param r red component
   * @param g green component
   * @param b blue component
   */
  private Color(int r, int g, int b) {
    this((short) r, (short) g, (short) b);
  }


  /**
   * Red component
   *
   * @return the red component
   */
  public short getRed() { return r; }

  /**
   * Green component
   *
   * @return the green component
   */
  public short getGreen() { return g; }

  /**
   * Blue component
   *
   * @return the blue component
   */
  public short getBlue() { return b; }
}
