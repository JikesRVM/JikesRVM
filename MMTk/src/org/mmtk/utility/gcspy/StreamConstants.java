/*
 * (C) Copyright Richard Jones, 2003
 * Computing Laboratory, University of Kent at Canterbury
 * All rights reserved.
 */
package org.mmtk.utility.gcspy;

/**
 * This interface provides constants used by the GCspy framweork.
 * These must correspond with values in gcspy_stream.h
 * Presentation
 * <table>
      <tr><td>PRESENTATION_PLAIN</td>      <td>presented as is</td>
      <tr><td>PRESENTATION_PLUS</td>       <td>as max+ if value exceeds max else as is</td>
      <tr><td>PRESENTATION_MAX_VAR </td>
	  <td>TODO</td>
      <tr><td>PRESENTATION_PERCENT</td>    <td>as value (percent)</td>
      <tr><td>PRESENTATION_PERCENT_VAR</td><td>ditto but handles zero case??</td>
      <tr><td>PRESENTATION_ENUM </td>
	  <td>TODO</td>
   </table>
   Paint style
   <table>
      <tr><td>PAINT_STYLE_PLAIN</td>
	  <td>TODO</td>
      <tr><td>PAINT_STYLE_ZERO</td>
	  <td>TODO</td>
   </table>
   Data types
   <table>
      <tr><td>BYTE_TYPE</td>  <td>stream of bytes</td>
      <tr><td>SHORT_TYPE</td> <td>stream of shorts</td>
      <tr><td>INT_TYPE</td>   <td>stream of ints</td>
   </table>
 * StreamConstants
 *
 * $Id$
 *
 * @author Richard Jones
 * @version $Revision$
 * @date $Date$
 */

public interface StreamConstants {

  public static final int NAME_LEN                     = 40;
  public static final int PRESENTATION_PLAIN           =  0;
  public static final int PRESENTATION_PLUS            =  1; 
  public static final int PRESENTATION_MAX_VAR         =  2;
  public static final int PRESENTATION_PERCENT         =  3;
  public static final int PRESENTATION_PERCENT_VAR     =  4;
  public static final int PRESENTATION_ENUM            =  5;

  public static final int PAINT_STYLE_PLAIN            =  0;
  public static final int PAINT_STYLE_ZERO             =  1;

  public static final int BYTE_TYPE                    =  0;
  public static final int SHORT_TYPE                   =  1;
  public static final int INT_TYPE                     =  2;

  public static final int ENUM_MAX_LEN                 = 20;
  public static final int ENUM_MAX_NUM                 =  5;
}
