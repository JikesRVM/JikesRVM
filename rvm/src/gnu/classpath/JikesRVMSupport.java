/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 * (C) Copyright IBM Corp 2002, 2004, 2005
 */
//$Id$
package gnu.classpath;

import org.vmmagic.unboxed.Address;

import com.ibm.jikesrvm.VM;

/**
 * Library support interface of Jikes RVM
 *
 * @author Elias Naur
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
