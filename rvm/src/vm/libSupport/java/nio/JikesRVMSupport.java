/*
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
    return gnu.classpath.JikesRVMSupport.getAddressFromPointer(buffer.address);
  }

  public static Buffer newDirectByteBuffer(Address address, long capacity) {
    Pointer pointer = gnu.classpath.JikesRVMSupport.getPointerFromAddress(address);
    return new DirectByteBufferImpl.ReadWrite(null, pointer, (int)capacity, (int)capacity, 0);
  }
}
