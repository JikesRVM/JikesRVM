/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2004
 *
 * $Id$
 */
package java.util;

import com.ibm.JikesRVM.VM_SysCall;
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
