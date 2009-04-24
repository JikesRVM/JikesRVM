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
package org.jikesrvm.compilers.common.assembler.ppc;

/**
 * Constants exported by the assembler
 */
public interface AssemblerConstants {

  int LT = 0xC << 21 | 0 << 16;
  int GT = 0xC << 21 | 1 << 16;
  int EQ = 0xC << 21 | 2 << 16;
  int GE = 0x4 << 21 | 0 << 16;
  int LE = 0x4 << 21 | 1 << 16;
  int NE = 0x4 << 21 | 2 << 16;

}
