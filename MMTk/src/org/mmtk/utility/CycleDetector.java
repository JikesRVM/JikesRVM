/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package org.mmtk.utility;

import org.mmtk.vm.VM_Interface;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/*
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
abstract class CycleDetector implements Uninterruptible {
  public final static String Id = "$Id$"; 

  abstract boolean collectCycles(int count, boolean time);
  abstract void possibleCycleRoot(Address object);
}
