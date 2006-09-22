/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp 2002, 2004, 2005
 */
//$Id: JikesRVMSupport.java,v 1.16 2006/03/01 12:23:56 dgrove-oss Exp $
package java.nio;

import org.vmmagic.unboxed.*;
import gnu.classpath.Pointer;

/**
 * Library support interface of Jikes RVM
 *
 * @author Elias Naur
 */
public class JikesRVMSupport {
  public static Address getDirectBufferAddress(Buffer buffer) {
    if (buffer.address == null)
      return Address.zero();
    else
      return gnu.classpath.JikesRVMSupport.getAddressFromPointer(buffer.address);
  }

  public static Buffer newDirectByteBuffer(Address address, long capacity) {
    Pointer pointer = gnu.classpath.JikesRVMSupport.getPointerFromAddress(address);
    return new DirectByteBufferImpl.ReadWrite(null, pointer, (int)capacity, (int)capacity, 0);
  }
}
