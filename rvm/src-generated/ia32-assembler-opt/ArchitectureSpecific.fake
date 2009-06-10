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
package org.jikesrvm;

public class ArchitectureSpecific {
  public static final class Assembler extends org.jikesrvm.compilers.common.assembler.ia32.Assembler {
    public Assembler (int bytecodeSize) {
      super(bytecodeSize, false);
    }
    public Assembler (int bytecodeSize, boolean shouldPrint, BaselineCompilerImpl compiler) {
      super(bytecodeSize, shouldPrint, compiler);
    }
    public Assembler (int bytecodeSize, boolean shouldPrint) {
      super(bytecodeSize, shouldPrint);
    }
  }
  public static final class CodeArray extends org.jikesrvm.ia32.CodeArray {}
  public static final class BaselineCompilerImpl extends org.jikesrvm.compilers.baseline.ia32.BaselineCompilerImpl {}
  public static final class MachineCode extends org.jikesrvm.ia32.MachineCode {
    public MachineCode(ArchitectureSpecific.CodeArray array, int[] bm) {
      super(array, bm);
    }}
  public interface RegisterConstants extends org.jikesrvm.ia32.RegisterConstants {}
}
