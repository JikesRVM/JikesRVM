/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
//$Id$
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_Uninterruptible;

/**
 * A pointer enumeration class.  This class is used to enumerate roots,
 * adding them to the rootValues deque.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $date: $
 */
public class RootEnumerator extends Enumerate implements VM_Uninterruptible {
  private AddressDeque rootValues;

  /**
   * Constructor (empty).
   */
  public RootEnumerator(AddressDeque rootValues) {
    this.rootValues = rootValues; 
  }

  /**
   * Enumerate a pointer.  In this case we add each referent object to
   * the rootValues deque.
   *
   * @param location The address of the field being enumerated.
   */
  public void enumeratePointerLocation(VM_Address location) 
    throws VM_PragmaInline {
    VM_Address value = VM_Magic.getMemoryAddress(location);
    if (!value.isZero())
      rootValues.push(value);
  }
}
