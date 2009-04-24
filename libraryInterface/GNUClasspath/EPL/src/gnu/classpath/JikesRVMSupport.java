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
package gnu.classpath;

import org.vmmagic.unboxed.Address;

import org.jikesrvm.VM;

/**
 * Library support interface of Jikes RVM
 */
public class JikesRVMSupport {
  public static Address getAddressFromPointer(Pointer pointer) {
    if (VM.BuildFor32Addr)
      return Address.fromIntSignExtend(((Pointer32)pointer).data);
    else
      return Address.fromLong(((Pointer64)pointer).data);
  }

  public static Pointer getPointerFromAddress(Address address) {
    if (VM.BuildFor32Addr)
      return new Pointer32(address.toInt());
    else
      return new Pointer64(address.toLong());
  }
}
