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
package org.jikesrvm.osr.bytecodes;


/**
 *  LocalInitEnd
 */
public class ParamInitEnd extends PseudoBytecode {
  public byte[] getBytes() {
    return initBytes(2, PSEUDO_ParamInitEnd);
  }

  public int getSize() {
    return 2;
  }

  public int stackChanges() {
    return 0;
  }

  public String toString() {
    return "ParamInitEnd";
  }
}
