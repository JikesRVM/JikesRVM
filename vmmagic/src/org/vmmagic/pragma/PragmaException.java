/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright ANU. 2004
 */
//$Id$
package org.vmmagic.pragma;

import com.ibm.jikesrvm.classloader.VM_Method;;
/**
 * Commenting required.
 * 
 * @author Daniel Frampton
 */
public class PragmaException extends RuntimeException {
  static final long serialVersionUID = 0; // Keep Eclipse quiet
  public static boolean declaredBy(VM_Method method) {
    return true;
  }
}
