/**
 ** Color
 **
 ** (C) Copyright Richard Jones, 2003
 ** Computing Laboratory, University of Kent at Canterbury
 ** All rights reserved.
 **/

package org.mmtk.vm.gcspy;

import org.mmtk.vm.VM_Interface;




/**
 * Color.java
 * 
 * Cut-down implementation of java.awt.Color sufficient to provide
 * the server side (Stream) with colours 
 *
 * @author <a href="http://www.ukc.ac.uk/people/staff/rej">Richard Jones</a>
 * @version $Revision$
 * @date $Date$
 */
public class Color
  implements  Uninterruptible {
  public final static String Id = "$Id$";
    
  /**
   * Some gcspy standard colours (taken from gcspy_color_db.c).
   */
  public static final Color Black      = new Color(  0,   0,   0);
  public static final Color Blue       = new Color(  0,   0, 255);
  public static final Color Cyan       = new Color(  0, 255, 255);
  public static final Color DarkGray   = new Color( 64,  64,  64);
  public static final Color Gray       = new Color(128, 128, 128);
  public static final Color Green      = new Color(  0, 255,   0);
  public static final Color LightGray  = new Color(192, 192, 192);
  public static final Color Magenta    = new Color(255,   0, 255);
  public static final Color MidGray    = new Color(160, 160, 160);
  public static final Color NavyBlue   = new Color(  0,   0, 150);
  public static final Color OffWhite   = new Color(230, 230, 230);
  public static final Color Orange     = new Color(255, 200,   0);
  public static final Color Pink       = new Color(255, 175, 175);
  public static final Color Red        = new Color(255,   0,   0);
  public static final Color White      = new Color(255, 255, 255);
  public static final Color Yellow     = new Color(255, 255,   0);

  private short r_;	// red component
  private short g_;	// green component
  private short b_;	// blue component
  
  /**
   * Constructor for crude RGB colour model
   * 
   * @param r red component
   * @param g green component
   * @param b blue component
   */
  public Color (short r, short g, short b) {    
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert((0 <= r) && (r <= 255) &&
		           (0 <= g) && (g <= 255) &&
		           (0 <= b) && (b <= 255));   
    this.r_ = r;
    this.g_ = g;
    this.b_ = b;
  }

  /**
   * Constructor for crude RGB colour model
   * 
   * @param r red component
   * @param g green component
   * @param b blue component
   */
  private Color (int r, int g, int b) {
    this((short) r, (short) g, (short) b);
  }
  

  /**
   * Red component
   * 
   * @return the red component
   */
  public short getRed() { return r_; }
  
  /**
   * Green component
   * 
   * @return the green component
   */
  public short getGreen() { return g_; }
  
  /**
   * Blue component
   * 
   * @return the blue component
   */
  public short getBlue() { return b_; }
}
