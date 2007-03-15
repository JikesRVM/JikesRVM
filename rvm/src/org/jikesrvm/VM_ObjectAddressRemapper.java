/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm;

import org.vmmagic.unboxed.*;

/**
 * Facility for remapping object addresses across virtual machine address 
 * spaces.  Used by boot image writer to map local (jdk) objects into remote 
 * (boot image) addresses.  Used by debugger to map local (jdk) objects into 
 * remote (debugee vm) addresses.
 *
 * See also VM_Magic.setObjectAddressRemapper()
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public interface VM_ObjectAddressRemapper {
  /**
   * Map an object to an address.
   * @param object in "local" virtual machine
   * @return its address in a foreign virtual machine
   */
  <T> Address objectAsAddress(T object);

  /**
   * Map an address to an object.
   * @param address value obtained from "objectAsAddress"
   * @return corresponding object
   */
  Object addressAsObject(Address address);
}
