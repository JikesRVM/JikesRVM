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
package java.nio;

//import org.apache.harmony.luni.platform.PlatformAddressFactory;

import static org.jikesrvm.SizeConstants.BYTES_IN_ADDRESS;

import org.vmmagic.unboxed.Address;
import org.jikesrvm.VM;

/**
 * Library support interface of Jikes RVM
 */
public class JikesRVMSupport {
  public static Address getDirectBufferAddress(Buffer buffer) {
    VM.sysWriteln("GetDirectBufferAddress is called");
    return Address.fromLong(((DirectByteBuffer)buffer).address());
    //    throw new Error("TODO");
/*
      if (buffer instanceof DirectBuffer) {
          return Address.fromLong(((DirectBuffer)buffer).getBaseAddress().toLong());
      } else {
          return Address.fromIntSignExtend(-1);
      }
*/
  }

  public static ByteBuffer newDirectByteBuffer(Address address, long capacity) {
    VM.sysWriteln("NewDirectBytebuffer is called");
      //    return new ReadWriteDirectByteBuffer(PlatformAddressFactory.on(address.toLong(), BYTES_IN_ADDRESS), (int)capacity, 0);
    //      throw new Error("NewDirectByteBuffer");
    return new DirectByteBuffer(address.toLong(), (int)capacity);
  }
}
