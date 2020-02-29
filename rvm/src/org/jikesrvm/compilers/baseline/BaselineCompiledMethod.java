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
package org.jikesrvm.compilers.baseline;

import org.jikesrvm.VM;
import org.jikesrvm.architecture.ArchConstants;
import org.jikesrvm.classloader.ExceptionHandlerMap;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.ExceptionTable;
import org.jikesrvm.runtime.DynamicLink;
import org.jikesrvm.runtime.ExceptionDeliverer;
import org.jikesrvm.runtime.StackBrowser;
import org.jikesrvm.util.PrintLN;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Offset;

/**
 * Compiler-specific information associated with a method's machine
 * instructions.
 */
public abstract class BaselineCompiledMethod extends CompiledMethod {

  /** Does the baseline compiled method have a counters array? */
  private boolean hasCounters;

  /**
   * The lock acquisition offset for synchronized methods.  For
   * synchronized methods, the offset (in the method prologue) after
   * which the monitor has been obtained.  At, or before, this point,
   * the method does not own the lock.  Used by deliverException to
   * determine whether the lock needs to be released.  Note: for this
   * scheme to work, Lock must not allow a yield after it has been
   * obtained.
   */
  private char lockOffset;

  /**
   * Baseline exception deliverer object
   */
  private static final ExceptionDeliverer exceptionDeliverer;

  static {
    if (VM.BuildForIA32) {
      exceptionDeliverer = new org.jikesrvm.compilers.baseline.ia32.BaselineExceptionDeliverer();
    } else {
      exceptionDeliverer = new org.jikesrvm.compilers.baseline.ppc.BaselineExceptionDeliverer();
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
    }
  }

  /**
   * Stack-slot reference maps for the compiled method.
   */
  public ReferenceMaps referenceMaps;

  /**
   * Encoded representation of bytecode index to offset in code array
   * map.  Currently needed to support dynamic bridge magic; Consider
   * integrating with GC maps
   */
  private byte[] bytecodeMap;

  /**
   * Exception table, null if not present.
   */
  private int[] eTable;

  protected BaselineCompiledMethod(int id, RVMMethod m) {
    super(id, m);
  }

  /**
   * Saves method-specific data, if necessary.
   * <p>
   * Architecture-specific subclasses are responsible for preserving any data about this
   * method (such as local variable locations) that will be needed later.
   *
   * @param comp reference to the compiler instance that compiled this method
   */
  protected abstract void saveCompilerData(BaselineCompiler comp);

  /** Compile method */
  public void compile() {
    BaselineCompiler comp;
    if (VM.BuildForIA32) {
      comp = new org.jikesrvm.compilers.baseline.ia32.BaselineCompilerImpl(this);
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.BuildForPowerPC);
      comp = new org.jikesrvm.compilers.baseline.ppc.BaselineCompilerImpl(this);
    }
    comp.compile();
    saveCompilerData(comp);
  }

  /** @return BASELINE */
  @Override
  @Uninterruptible
  public int getCompilerType() {
    return BASELINE;
  }

  /** @return "baseline compiler" */
  @Override
  public String getCompilerName() {
    return "baseline compiler";
  }

  /**
   * @return the exception deliverer for this kind of compiled method
   */
  @Override
  @Uninterruptible
  public ExceptionDeliverer getExceptionDeliverer() {
    return exceptionDeliverer;
  }

  /**
   * Find a catch block within the compiled method
   * @param instructionOffset offset of faulting instruction in compiled code
   * @param exceptionType the type of the thrown exception
   * @return the machine code offset of the catch block.
   */
  @Override
  @Unpreemptible
  public int findCatchBlockForInstruction(Offset instructionOffset, RVMType exceptionType) {
    if (eTable == null) {
      return -1;
    } else {
      return ExceptionTable.findCatchBlockForInstruction(eTable, instructionOffset, exceptionType);
    }
  }

  @Override
  @Uninterruptible
  public void getDynamicLink(DynamicLink dynamicLink, Offset instructionOffset) {
    int bytecodeIndex = findBytecodeIndexForInstruction(instructionOffset);
    ((NormalMethod) method).getDynamicLink(dynamicLink, bytecodeIndex);
  }

  /**
   * @param instructionOffset the instruction's offset in the code for the method
   * @return The line number, a positive integer.  Zero means unable to find.
   */
  @Override
  @Uninterruptible
  public int findLineNumberForInstruction(Offset instructionOffset) {
    int bci = findBytecodeIndexForInstruction(instructionOffset);
    if (bci == -1) return 0;
    return ((NormalMethod) method).getLineNumberForBCIndex(bci);
  }

  @Override
  public boolean isWithinUninterruptibleCode(Offset instructionOffset) {
    return method.isUninterruptible();
  }

  /**
   * Find bytecode index corresponding to one of this method's
   * machine instructions.
   *
   * @param instructionOffset instruction offset to map to a bytecode index.<br>
   * Note: This method expects the offset to refer to the machine
   * instruction immediately FOLLOWING the bytecode in question.  just
   * like findLineNumberForInstruction. See CompiledMethod for
   * rationale.<br>
   * NOTE: instructionIndex is in units of instructions, not bytes
   * (different from all the other methods in this interface!!)
   * @return the bytecode index for the machine instruction, -1 if not
   *         available or not found.
   */
  @Uninterruptible
  public int findBytecodeIndexForInstruction(Offset instructionOffset) {
    Offset instructionIndex = instructionOffset.toWord().rsha(ArchConstants.getLogInstructionWidth()).toOffset();
    int candidateIndex = -1;
    int bcIndex = 0;
    Offset instrIndex = Offset.zero();
    for (int i = 0; i < bytecodeMap.length;) {
      int b0 = (bytecodeMap[i++]) & 255;  // unsign-extend
      int deltaBC, deltaIns;
      if (b0 != 255) {
        deltaBC = b0 >> 5;
        deltaIns = b0 & 31;
      } else {
        int b1 = (bytecodeMap[i++]) & 255;  // unsign-extend
        int b2 = (bytecodeMap[i++]) & 255;  // unsign-extend
        int b3 = (bytecodeMap[i++]) & 255;  // unsign-extend
        int b4 = (bytecodeMap[i++]) & 255;  // unsign-extend
        deltaBC = (b1 << 8) | b2;
        deltaIns = (b3 << 8) | b4;
      }
      bcIndex += deltaBC;
      instrIndex = instrIndex.plus(deltaIns);
      if (instrIndex.sGE(instructionIndex)) {
        break;
      }
      candidateIndex = bcIndex;
    }
    return candidateIndex;
  }

  @Override
  public void set(StackBrowser browser, Offset instr) {
    browser.setMethod(method);
    browser.setCompiledMethod(this);
    browser.setBytecodeIndex(findBytecodeIndexForInstruction(instr));

    if (VM.TraceStackTrace) {
      VM.sysWrite("setting stack to frame (base): ");
      VM.sysWrite(browser.getMethod());
      VM.sysWrite(browser.getBytecodeIndex());
      VM.sysWriteln();
    }
  }

  @Override
  public boolean up(StackBrowser browser) {
    return false;
  }

  @Override
  public void printStackTrace(Offset instructionOffset, PrintLN out) {
    out.print("\tat ");
    out.print(method.getDeclaringClass()); // RVMClass
    out.print('.');
    out.print(method.getName()); // a Atom, returned via MemberReference.getName().
    out.print("(");
    out.print(method.getDeclaringClass().getSourceName()); // a Atom
    int lineNumber = findLineNumberForInstruction(instructionOffset);
    if (lineNumber <= 0) {      // unknown line
      out.print("; machine code offset: ");
      out.printHex(instructionOffset.toInt());
    } else {
      out.print(':');
      out.print(lineNumber);
    }
    out.print(')');
    out.println();
  }

  /**
   * Print the eTable
   */
  public void printExceptionTable() {
    if (eTable != null) ExceptionTable.printExceptionTable(eTable);
  }

  /**
   * Sets the lock acquisition offset for synchronized methods.
   *
   * @param off new offset
   */
  public void setLockAcquisitionOffset(int off) {
    if (VM.VerifyAssertions) VM._assert((off & 0xFFFF) == off);
    lockOffset = (char) off;
  }

  /** @return the lock acquisition offset */
  @Uninterruptible
  public Offset getLockAcquisitionOffset() {
    return Offset.fromIntZeroExtend(lockOffset);
  }

  /** Set the method has a counters array */
  void setHasCounterArray() {
    hasCounters = true;
  }

  /** @return whether the method has a counters array */
  @Uninterruptible
  public boolean hasCounterArray() {
    return hasCounters;
  }

  /**
   * Encode/compress the bytecode map, reference (GC) map and exception table
   *
   * @param referenceMaps to encode
   * @param bcMap unencoded bytecode to code array offset map
   */
  public void encodeMappingInfo(ReferenceMaps referenceMaps, int[] bcMap) {
    int count = 0;
    int lastBC = 0, lastIns = 0;
    for (int i = 0; i < bcMap.length; i++) {
      if (bcMap[i] != 0) {
        int deltaBC = i - lastBC;
        int deltaIns = bcMap[i] - lastIns;
        if (VM.VerifyAssertions) {
          VM._assert(deltaBC >= 0 && deltaIns >= 0);
        }
        if (deltaBC <= 6 && deltaIns <= 31) {
          count++;
        } else {
          if (deltaBC > 65535 || deltaIns > 65535) {
            VM.sysFail("BaselineCompiledMethod: a fancier encoding is needed");
          }
          count += 5;
        }
        lastBC = i;
        lastIns = bcMap[i];
      }
    }
    bytecodeMap = new byte[count];
    count = lastBC = lastIns = 0;
    for (int i = 0; i < bcMap.length; i++) {
      if (bcMap[i] != 0) {
        int deltaBC = i - lastBC;
        int deltaIns = bcMap[i] - lastIns;
        if (VM.VerifyAssertions) {
          VM._assert(deltaBC >= 0 && deltaIns >= 0);
        }
        if (deltaBC <= 6 && deltaIns <= 31) {
          bytecodeMap[count++] = (byte) ((deltaBC << 5) | deltaIns);
        } else { // From before, we know that deltaBC <= 65535 and deltaIns <= 65535
          bytecodeMap[count++] = (byte) 255;
          bytecodeMap[count++] = (byte) (deltaBC >> 8);
          bytecodeMap[count++] = (byte) (deltaBC & 255);
          bytecodeMap[count++] = (byte) (deltaIns >> 8);
          bytecodeMap[count++] = (byte) (deltaIns & 255);
        }
        lastBC = i;
        lastIns = bcMap[i];
      }
    }
    // TODO: it's likely for short methods we can share the bytecodeMap
    referenceMaps.translateByte2Machine(bcMap);
    this.referenceMaps = referenceMaps;
    ExceptionHandlerMap emap = ((NormalMethod) method).getExceptionHandlerMap();
    if (emap != null) {
      eTable = BaselineExceptionTable.encode(emap, bcMap);
    }
  }

  @Override
  public int size() {
    TypeReference TYPE = TypeReference.findOrCreate(BaselineCompiledMethod.class);
    int size = TYPE.peekType().asClass().getInstanceSize();
    if (bytecodeMap != null) size += RVMArray.ByteArray.getInstanceSize(bytecodeMap.length);
    if (eTable != null) size += RVMArray.IntArray.getInstanceSize(eTable.length);
    if (referenceMaps != null) size += referenceMaps.size();
    return size;
  }
}
