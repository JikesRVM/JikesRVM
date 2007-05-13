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
package org.mmtk.utility.gcspy;

/**
 * This interface provides constants used by the GCspy framweork.
 * These must correspond with values in gcspy_stream.h
 * Presentation
 * <table>
      <tr><td>PRESENTATION_PLAIN</td>      <td>presented as is</td>
      <tr><td>PRESENTATION_PLUS</td>       <td>as max+ if value exceeds max else as is</td>
      <tr><td>PRESENTATION_MAX_VAR</td>    <td>ditto but takes max value from a specified stream</td>
      <tr><td>PRESENTATION_PERCENT</td>    <td>as value (percent)</td>
      <tr><td>PRESENTATION_PERCENT_VAR</td><td>ditto but takes max value from a specified stream</td>
      <tr><td>PRESENTATION_ENUM </td>      <td>chooses from an enumeration</td>
   </table>
   Paint style
   <table>
      <tr><td>PAINT_STYLE_PLAIN</td>       <td>Paints as is</td>
      <tr><td>PAINT_STYLE_ZERO</td>        <td>ditto but treats zero values specially</td>
   </table>
   Data types
   <table>
      <tr><td>BYTE_TYPE</td>  <td>stream of bytes</td>
      <tr><td>SHORT_TYPE</td> <td>stream of shorts</td>
      <tr><td>INT_TYPE</td>   <td>stream of ints</td>
   </table>
 * StreamConstants
 * 
 *
 */

public interface StreamConstants {

  int NAME_LEN = 40;
  int PRESENTATION_PLAIN       = 0;
  int PRESENTATION_PLUS        = 1;
  int PRESENTATION_MAX_VAR     = 2;
  int PRESENTATION_PERCENT     = 3;
  int PRESENTATION_PERCENT_VAR = 4;
  int PRESENTATION_ENUM        = 5;

  int PAINT_STYLE_PLAIN = 0;
  int PAINT_STYLE_ZERO  = 1;

  int BYTE_TYPE  = 0;
  int SHORT_TYPE = 1;
  int INT_TYPE   = 2;

  int ENUM_MAX_LEN = 20;
  int ENUM_MAX_NUM = 5;
}
