/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2004
 *
 * $Id$
 */
package java.util;

import com.ibm.jikesrvm.VM_SysCall;
/** java.util.VMTimeZone for compatibility with GNU classpath 0.11.
 *
 * @author Steven Augart
 * @date 5 October 2004
 */
public class VMTimeZone {
  static TimeZone getDefaultTimeZoneId() {
    return null;                // We don't need to do anything here; Jikes
                                // RVM automatically takes care of this for
                                // us, since "bin/runrvm" always sets the
                                // user.timezone property. 
  }
    
}
