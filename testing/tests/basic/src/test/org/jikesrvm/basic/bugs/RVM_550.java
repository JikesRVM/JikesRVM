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
package test.org.jikesrvm.basic.bugs;

import org.jikesrvm.VM;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.unboxed.Address;

/**
 * Test code demonstrating bug [ RVM-550 ] wordIsZero magic destroys CMID on PPC baseline compiler
 */
public class RVM_550 {

  final Address addr = Address.zero();

  public static void main(String[] args) {
    RVM_550 x = new RVM_550();
    x.test();
  }

  private void test() {
    VM.sysWriteln(Magic.getCompiledMethodID(Magic.getFramePointer()) > 0);
    if (addr.isZero()) {}
    VM.sysWriteln(Magic.getCompiledMethodID(Magic.getFramePointer()) > 0);
  }
}
