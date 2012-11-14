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

import org.jikesrvm.PrintLN;
import org.jikesrvm.VM;
import org.jikesrvm.ArchitectureSpecific.BaselineCompilerImpl;
import org.jikesrvm.ArchitectureSpecific.BaselineConstants;
import org.jikesrvm.ArchitectureSpecific.BaselineExceptionDeliverer;
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
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Offset;

/**
 * Compiler-specific information associated with a method's machine
 * instructions.
 */
public final class BaselineCompiledMethod extends CompiledMethod implements BaselineConstants {

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
  private static final ExceptionDeliverer exceptionDeliverer = new BaselineExceptionDeliverer();

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

  /** Offset into stack frame when operand stack is empty */
  private final short emptyStackOffset;
  /** PPC only: last general purpose register holding part of the operand stack */
  private byte lastFixedStackRegister;
  /** PPC only: last floating point register holding part of the operand stack */
  private byte lastFloatStackRegister;

  /**
   * PPC only: location of general purpose local variables, positive
   * values are register numbers, negative are stack offsets
   */
  private final short[] localFixedLocations;

  /**
   * PPC only: location of floating point local variables, positive
   * values are register numbers, negative are stack offsets
   */
  private final short[] localFloatLocations;

  /** @return offset into stack frame when operand stack is empty */
  public int getEmptyStackOffset() {
    return emptyStackOffset;
  }

  /**
   * Location of local general purpose variable.  These Locations are
   * positioned at the top of the stackslot that contains the value
   * before accessing, substract size of value you want to access.<p>
   * e.g. to load int: load at BaselineCompilerImpl.locationToOffset(location) - BYTES_IN_INT<br>
   * e.g. to load long: load at BaselineCompilerImpl.locationToOffset(location) - BYTES_IN_LONG
   */
  @Uninterruptible
  public short getGeneralLocalLocation(int localIndex) {
    return BaselineCompilerImpl.getGeneralLocalLocation(localIndex, localFixedLocations, (NormalMethod) method);
  }

  /**
   * Location of local floating point variable.  These Locations are
   * positioned at the top of the stackslot that contains the value
   * before accessing, substract size of value you want to access.<p>
   * e.g. to load float: load at BaselineCompilerImpl.locationToOffset(location) - BYTES_IN_FLOAT<br>
   * e.g. to load double: load at BaselineCompilerImpl.locationToOffset(location) - BYTES_IN_DOUBLE
   */
  @Uninterruptible
  public short getFloatLocalLocation(int localIndex) {
    return BaselineCompilerImpl.getFloatLocalLocation(localIndex, localFloatLocations, (NormalMethod) method);
  }

  /** Offset onto stack of a particular general purpose operand stack location */
  @Uninterruptible
  public short getGeneralStackLocation(int stackIndex) {
    return BaselineCompilerImpl.offsetToLocation(emptyStackOffset - (stackIndex << LOG_BYTES_IN_ADDRESS));
  }

  /** Offset onto stack of a particular operand stack location for a floating point value */
  @Uninterruptible
  public short getFloatStackLocation(int stackIndex) {
    // for now same implementation as getGeneralStackLocation
    return getGeneralStackLocation(stackIndex);
  }

  /** Last general purpose register holding part of the operand stack */
  @Uninterruptible
  public int getLastFixedStackRegister() {
    return lastFixedStackRegister;
  }

  /** Last floating point register holding part of the operand stack */
  @Uninterruptible
  public int getLastFloatStackRegister() {
    return lastFloatStackRegister;
  }

  /** Constructor */
  public BaselineCompiledMethod(int id, RVMMethod m) {
    super(id, m);
    NormalMethod nm = (NormalMethod) m;
    //this.startLocalOffset = BaselineCompilerImpl.getStartLocalOffset(nm);
    this.emptyStackOffset = (short)BaselineCompilerImpl.getEmptyStackOffset(nm);
    this.localFixedLocations = VM.BuildForIA32 ? null : new short[nm.getLocalWords()];
    this.localFloatLocations = VM.BuildForIA32 ? null : new short[nm.getLocalWords()];
    this.lastFixedStackRegister = -1;
    this.lastFloatStackRegister = -1;
  }

  /** Compile method */
  public void compile() {
    BaselineCompilerImpl comp = new BaselineCompilerImpl(this, localFixedLocations, localFloatLocations);
    comp.compile();
    this.lastFixedStackRegister = comp.getLastFixedStackRegister();
    this.lastFloatStackRegister = comp.getLastFloatStackRegister();
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
   * Get the exception deliverer for this kind of compiled method
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
    Offset instructionIndex = instructionOffset.toWord().rsha(LG_INSTRUCTION_WIDTH).toOffset();
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
      VM.sysWrite("\n");
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

  /** Set the lock acquisition offset for synchronized methods */
  public void setLockAcquisitionOffset(int off) {
    if (VM.VerifyAssertions) VM._assert((off & 0xFFFF) == off);
    lockOffset = (char) off;
  }

  /** Get the lock acquisition offset */
  @Uninterruptible
  public Offset getLockAcquisitionOffset() {
    return Offset.fromIntZeroExtend(lockOffset);
  }

  /** Set the method has a counters array */
  void setHasCounterArray() {
    hasCounters = true;
  }

  /** Does the method have a counters array? */
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
