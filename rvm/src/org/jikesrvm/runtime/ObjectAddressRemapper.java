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
package org.jikesrvm.runtime;

import org.vmmagic.unboxed.Address;

/**
 * Facility for remapping object addresses across virtual machine address
 * spaces.  Used by boot image writer to map local (jdk) objects into remote
 * (boot image) addresses.  Used by debugger to map local (jdk) objects into
 * remote (debugee vm) addresses.
 *
 * See also Magic.setObjectAddressRemapper()
 */
public interface ObjectAddressRemapper {
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

  /**
   * Avoid duplicates of certain objects
   * @param object to intern
   * @return interned object
   */
  <T> T intern(T object);

  /**
   * Identity hash code of an object
   *
   * @param object the object to generate the identity hash code for
   * @return the identity hash code
   */
  int identityHashCode(Object object);
}
