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
package org.jikesrvm.compilers.common.assembler;

import org.vmmagic.pragma.NoInline;

public abstract class AbstractLister {

  protected static final String NEWLINE = System.getProperty("line.separator");

  /**
   * Gives the lister a message associated with a particular
   * bytecode. This is used by the baseline compilers to print the
   * bytecode associated with portions of machine code. (The
   * optimizing compiler does not do this because its association
   * between bytecodes and generated code is much less direct).
   *
   * @param biStart the offset of the current bytecode in the
   *     current method's bytecode array.
   * @param msg a message descriptive of the current bytecode.
   */
  public abstract void noteBytecode(int biStart, String msg);

  @NoInline
  public final void noteBytecode(int biStart, String bcode, int x) {
    noteBytecode(biStart, bcode + " " + x);
  }

  @NoInline
  public final void noteBytecode(int biStart, String bcode, long x) {
    noteBytecode(biStart, bcode + " " + x);
  }

  @NoInline
  public final void noteBytecode(int biStart, String bcode, Object o) {
    noteBytecode(biStart, bcode + " " + o);
  }

  @NoInline
  public final void noteBytecode(int biStart, String bcode, int x, int y) {
    noteBytecode(biStart, bcode + " " + x + " " + y);
  }

  @NoInline
  public final void noteBranchBytecode(int biStart, String bcode, int off, int bt) {
    noteBytecode(biStart, bcode + " " + off + " [" + bt + "] ");
  }

  @NoInline
  public final void noteTableswitchBytecode(int biStart, int l, int h, int d) {
    noteBytecode(biStart, "tableswitch [" + l + "--" + h + "] " + d);
  }

  @NoInline
  public final void noteLookupswitchBytecode(int biStart, int n, int d) {
    noteBytecode(biStart, "lookupswitch [<" + n + ">]" + d);
  }

}

