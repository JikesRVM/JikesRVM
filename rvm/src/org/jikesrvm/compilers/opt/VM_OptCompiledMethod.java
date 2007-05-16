/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2003, 2005
 */

package org.jikesrvm.compilers.opt;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.VM_PrintLN;
import org.jikesrvm.classloader.VM_Array;
import org.jikesrvm.classloader.VM_MemberReference;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.common.VM_CompiledMethod;
import org.jikesrvm.compilers.common.VM_ExceptionTable;
import org.jikesrvm.compilers.opt.ir.InlineGuard;
import org.jikesrvm.compilers.opt.ir.OPT_IR;
import org.jikesrvm.compilers.opt.ir.OPT_Instruction;
import static org.jikesrvm.compilers.opt.ir.OPT_Operators.IG_PATCH_POINT;
import org.jikesrvm.osr.OSR_EncodedOSRMap;
import org.jikesrvm.runtime.VM_DynamicLink;
import org.jikesrvm.runtime.VM_ExceptionDeliverer;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_StackBrowser;
import org.jikesrvm.scheduler.VM_Processor;
import org.jikesrvm.scheduler.VM_Scheduler;
import org.jikesrvm.scheduler.VM_Thread;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.SynchronizedObject;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Offset;

/**
 * An implementation of VM_CompiledMethod for the OPT compiler.
 *
 * <p> NOTE: VM_OptCompilerMethod live as long as their corresponding
 * compiled machine code.  Therefore, they should only contain
 * state that is really required to be persistent.  Anything
 * transitory should be stored on the OPT_IR object.
 */
@SynchronizedObject
@Uninterruptible
public final class VM_OptCompiledMethod extends VM_CompiledMethod {

  public VM_OptCompiledMethod(int id, VM_Method m) {
    super(id, m);
  }

  /**
   * Get compiler that generated this method's machine code.
   */
  public int getCompilerType() {
    return VM_CompiledMethod.OPT;
  }

  /**
   * @return Name of the compiler that produced this compiled method.
   */
  public String getCompilerName() {
    return "optimizing compiler";
  }

  /**
   * Get handler to deal with stack unwinding and exception delivery
   * for this method's stackframes.
   */
  public VM_ExceptionDeliverer getExceptionDeliverer() {
    return exceptionDeliverer;
  }

  /**
   * Find "catch" block for a machine instruction of this method.
   */
  @Interruptible
  public int findCatchBlockForInstruction(Offset instructionOffset, VM_Type exceptionType) {
    if (eTable == null) {
      return -1;
    } else {
      return VM_ExceptionTable.findCatchBlockForInstruction(eTable, instructionOffset, exceptionType);
    }
  }

  /**
   * Fetch symbolic reference to a method that's called
   * by one of this method's instructions.
   * @param dynamicLink place to put return information
   * @param instructionOffset offset of machine instruction that issued
   *                          the call
   */
  public void getDynamicLink(VM_DynamicLink dynamicLink, Offset instructionOffset) {
    int bci = _mcMap.getBytecodeIndexForMCOffset(instructionOffset);
    VM_NormalMethod realMethod = _mcMap.getMethodForMCOffset(instructionOffset);
    if (bci == -1 || realMethod == null) {
      VM.sysFail("Mapping to source code location not available at Dynamic Linking point\n");
    }
    realMethod.getDynamicLink(dynamicLink, bci);
  }

  /**
   * Find source line number corresponding to one of this method's
   * machine instructions.
   */
  public int findLineNumberForInstruction(Offset instructionOffset) {
    int bci = _mcMap.getBytecodeIndexForMCOffset(instructionOffset);
    if (bci < 0) {
      return 0;
    }
    return ((VM_NormalMethod) method).getLineNumberForBCIndex(bci);
  }

  /**
   * Set the stack browser to the innermost logical stack frame of this method
   */
  @Interruptible
  public void set(VM_StackBrowser browser, Offset instr) {
    VM_OptMachineCodeMap map = getMCMap();
    int iei = map.getInlineEncodingForMCOffset(instr);
    if (iei >= 0) {
      int[] inlineEncoding = map.inlineEncoding;
      int mid = VM_OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);

      browser.setInlineEncodingIndex(iei);
      browser.setBytecodeIndex(map.getBytecodeIndexForMCOffset(instr));
      browser.setCompiledMethod(this);
      browser.setMethod(VM_MemberReference.getMemberRef(mid).asMethodReference().peekResolvedMethod());

      if (VM.TraceStackTrace) {
        VM.sysWrite("setting stack to frame (opt): ");
        VM.sysWrite(browser.getMethod());
        VM.sysWrite(browser.getBytecodeIndex());
        VM.sysWrite("\n");
      }
    } else {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
  }

  /**
   * Advance the VM_StackBrowser up one internal stack frame, if possible
   */
  @Interruptible
  public boolean up(VM_StackBrowser browser) {
    VM_OptMachineCodeMap map = getMCMap();
    int iei = browser.getInlineEncodingIndex();
    int[] ie = map.inlineEncoding;
    int next = VM_OptEncodedCallSiteTree.getParent(iei, ie);
    if (next >= 0) {
      int mid = VM_OptEncodedCallSiteTree.getMethodID(next, ie);
      int bci = VM_OptEncodedCallSiteTree.getByteCodeOffset(iei, ie);

      browser.setInlineEncodingIndex(next);
      browser.setBytecodeIndex(bci);
      browser.setMethod(VM_MemberReference.getMemberRef(mid).asMethodReference().peekResolvedMethod());

      if (VM.TraceStackTrace) {
        VM.sysWrite("up within frame stack (opt): ");
        VM.sysWrite(browser.getMethod());
        VM.sysWrite(browser.getBytecodeIndex());
        VM.sysWrite("\n");
      }

      return true;
    } else {
      return false;
    }
  }

  /**
   * Print this compiled method's portion of a stack trace.
   * @param instructionOffset   The offset of machine instruction from
   *                            start of method
   * @param out    The PrintStream to print the stack trace to.
   */
  @Interruptible
  public void printStackTrace(Offset instructionOffset, VM_PrintLN out) {
    VM_OptMachineCodeMap map = getMCMap();
    int iei = map.getInlineEncodingForMCOffset(instructionOffset);
    if (iei >= 0) {
      int[] inlineEncoding = map.inlineEncoding;
      int bci = map.getBytecodeIndexForMCOffset(instructionOffset);
      for (int j = iei; j >= 0; j = VM_OptEncodedCallSiteTree.getParent(j, inlineEncoding)) {
        int mid = VM_OptEncodedCallSiteTree.getMethodID(j, inlineEncoding);
        VM_NormalMethod m =
            (VM_NormalMethod) VM_MemberReference.getMemberRef(mid).asMethodReference().peekResolvedMethod();
        int lineNumber = m.getLineNumberForBCIndex(bci); // might be 0 if unavailable.
        out.print("\tat ");
        out.print(m.getDeclaringClass());
        out.print('.');
        out.print(m.getName());
        out.print('(');
        out.print(m.getDeclaringClass().getSourceName());
        out.print(':');
        out.print(lineNumber);
        out.print(')');
        out.println();
        if (j > 0) {
          bci = VM_OptEncodedCallSiteTree.getByteCodeOffset(j, inlineEncoding);
        }
      }
    } else {
      out.print("\tat ");
      out.print(method.getDeclaringClass());
      out.print('.');
      out.print(method.getName());
      out.print('(');
      out.print(method.getDeclaringClass().getSourceName());
      out.print("; machine code offset: ");
      out.printHex(instructionOffset.toInt());
      out.print(')');
      out.println();
    }
  }

  @Interruptible
  public int size() {
    int size = VM_TypeReference.VM_ExceptionTable.peekResolvedType().asClass().getInstanceSize();
    size += _mcMap.size();
    if (eTable != null) size += VM_Array.IntArray.getInstanceSize(eTable.length);
    if (patchMap != null) size += VM_Array.IntArray.getInstanceSize(patchMap.length);
    return size;
  }

  //----------------//
  // implementation //
  //----------------//
  private static final ArchitectureSpecific.VM_OptExceptionDeliverer exceptionDeliverer =
      new ArchitectureSpecific.VM_OptExceptionDeliverer();

  private OSR_EncodedOSRMap _osrMap;

  @Interruptible
  public void createFinalOSRMap(OPT_IR ir) {
    this._osrMap = new OSR_EncodedOSRMap(ir.MIRInfo.osrVarMap);
  }

  public OSR_EncodedOSRMap getOSRMap() {
    return this._osrMap;
  }

  //////////////////////////////////////
  // Information the opt compiler needs to persistently associate
  // with a particular compiled method.

  /** The primary machine code maps */
  private VM_OptMachineCodeMap _mcMap;
  /** The encoded exception tables (null if there are none) */
  private int[] eTable;
  private int[] patchMap;

  /**
   * unsigned offset (off the framepointer) of nonvolatile save area
   * in bytes
   */
  private char nonvolatileOffset;
  /**
   * unsigned offset (off the framepointer) of caught exception
   * object in bytes
   */
  private char exceptionObjectOffset;
  /**
   * size of the fixed portion of the stackframe
   */
  private char stackFrameFixedSize;
  /**
   * first saved nonvolatile integer register (-1 if no nonvolatile
   * GPRs)
   */
  private byte firstNonvolatileGPR;
  /**
   * first saved nonvolatile floating point register (-1 if no
   * nonvolatile FPRs)
   */
  private byte firstNonvolatileFPR;
  /** opt level at which the method was compiled */
  private byte optLevel;
  /** were the volatile registers saved? */
  private boolean volatilesSaved;
  /** is the current method executing with instrumentation */
  private boolean instrumented;

  public int getUnsignedNonVolatileOffset() {
    return nonvolatileOffset;
  }

  public int getUnsignedExceptionOffset() {
    return exceptionObjectOffset;
  }

  public int getFirstNonVolatileGPR() {
    return firstNonvolatileGPR;
  }

  public int getFirstNonVolatileFPR() {
    return firstNonvolatileFPR;
  }

  public int getOptLevel() {
    return optLevel;
  }

  public boolean isSaveVolatile() {
    return volatilesSaved;
  }

  public boolean isInstrumentedMethod() {
    return instrumented;
  }

  public int getFrameFixedSize() {
    return stackFrameFixedSize;
  }

  public void setUnsignedNonVolatileOffset(int x) {
    if (VM.VerifyAssertions) VM._assert(x >= 0 && x < 0xFFFF);
    nonvolatileOffset = (char) x;
  }

  public void setUnsignedExceptionOffset(int x) {
    if (VM.VerifyAssertions) VM._assert(x >= 0 && x < 0xFFFF);
    exceptionObjectOffset = (char) x;
  }

  public void setFirstNonVolatileGPR(int x) {
    if (VM.VerifyAssertions) VM._assert(x >= -1 && x < 0x7F);
    firstNonvolatileGPR = (byte) x;
  }

  public void setFirstNonVolatileFPR(int x) {
    if (VM.VerifyAssertions) VM._assert(x >= -1 && x < 0x7F);
    firstNonvolatileFPR = (byte) x;
  }

  public void setOptLevel(int x) {
    if (VM.VerifyAssertions) VM._assert(x >= 0 && x < 0x7F);
    optLevel = (byte) x;
  }

  public void setSaveVolatile(boolean sv) {
    volatilesSaved = sv;
  }

  public void setInstrumentedMethod(boolean _instrumented) {
    instrumented = _instrumented;
  }

  public void setFrameFixedSize(int x) {
    if (VM.VerifyAssertions) VM._assert(x >= 0 && x < 0xFFFF);
    stackFrameFixedSize = (char) x;
  }

  /**
   * Return the number of non-volatile GPRs used by this method.
   */
  public int getNumberOfNonvolatileGPRs() {
    if (VM.BuildForPowerPC) {
      return ArchitectureSpecific.VM_RegisterConstants.NUM_GPRS - getFirstNonVolatileGPR();
    } else if (VM.BuildForIA32) {
      return ArchitectureSpecific.VM_RegisterConstants.NUM_NONVOLATILE_GPRS - getFirstNonVolatileGPR();
    } else if (VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);
    }
    return -1;
  }

  /**
   * Return the number of non-volatile FPRs used by this method.
   */
  public int getNumberOfNonvolatileFPRs() {
    if (VM.BuildForPowerPC) {
      return ArchitectureSpecific.VM_RegisterConstants.NUM_FPRS - getFirstNonVolatileFPR();
    } else if (VM.BuildForIA32) {
      return ArchitectureSpecific.VM_RegisterConstants.NUM_NONVOLATILE_FPRS - getFirstNonVolatileFPR();
    } else if (VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);
    }
    return -1;
  }

  /**
   * Set the number of non-volatile GPRs used by this method.
   */
  public void setNumberOfNonvolatileGPRs(short n) {
    if (VM.BuildForPowerPC) {
      setFirstNonVolatileGPR(ArchitectureSpecific.VM_RegisterConstants.NUM_GPRS - n);
    } else if (VM.BuildForIA32) {
      setFirstNonVolatileGPR(ArchitectureSpecific.VM_RegisterConstants.NUM_NONVOLATILE_GPRS - n);
    } else if (VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);
    }
  }

  /**
   * Set the number of non-volatile FPRs used by this method.
   */
  public void setNumberOfNonvolatileFPRs(short n) {
    if (VM.BuildForPowerPC) {
      setFirstNonVolatileFPR(ArchitectureSpecific.VM_RegisterConstants.NUM_FPRS - n);
    } else if (VM.BuildForIA32) {
      setFirstNonVolatileFPR(ArchitectureSpecific.VM_RegisterConstants.NUM_NONVOLATILE_FPRS - n);
    } else if (VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);
    }
  }

  /**
   * Print the eTable
   */
  @Interruptible
  public void printExceptionTable() {
    if (eTable != null) VM_ExceptionTable.printExceptionTable(eTable);
  }

  /**
   * @return the machine code map for the compiled method.
   */
  public VM_OptMachineCodeMap getMCMap() {
    return _mcMap;
  }

  /**
   * Create the final machine code map for the compiled method.
   * Remember the offset for the end of prologue too for debugger.
   * @param ir the ir
   * @param machineCodeLength the number of machine code instructions.
   */
  @Interruptible
  public void createFinalMCMap(OPT_IR ir, int machineCodeLength) {
    _mcMap = VM_OptMachineCodeMap.create(ir, machineCodeLength);
  }

  /**
   * Create the final exception table from the IR for the method.
   * @param ir the ir
   */
  @Interruptible
  public void createFinalExceptionTable(OPT_IR ir) {
    if (ir.hasReachableExceptionHandlers()) {
      eTable = VM_OptExceptionTable.encode(ir);
    }
  }

  /**
   * Create the code patching maps from the IR for the method
   * @param ir the ir
   */
  @Interruptible
  public void createCodePatchMaps(OPT_IR ir) {
    // (1) count the patch points
    int patchPoints = 0;
    for (OPT_Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = s.nextInstructionInCodeOrder()) {
      if (s.operator() == IG_PATCH_POINT) {
        patchPoints++;
      }
    }
    // (2) if we have patch points, create the map.
    if (patchPoints != 0) {
      patchMap = new int[patchPoints * 2];
      int idx = 0;
      for (OPT_Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = s.nextInstructionInCodeOrder()) {
        if (s.operator() == IG_PATCH_POINT) {
          int patchPoint = s.getmcOffset();
          int newTarget = InlineGuard.getTarget(s).target.getmcOffset();
          // A patch map is the offset of the last byte of the patch point
          // and the new branch immediate to lay down if the code is ever patched.
          if (VM.BuildForIA32) {
            patchMap[idx++] = patchPoint - 1;
            patchMap[idx++] = newTarget - patchPoint;
          } else if (VM.BuildForPowerPC) {

            // otherwise, it must be RVM_FOR_POWERPC
            /* since currently we use only one NOP scheme, the offset
            * is adjusted for one word
            */
            patchMap[idx++] = (patchPoint >> ArchitectureSpecific.VM_RegisterConstants.LG_INSTRUCTION_WIDTH) - 1;
            patchMap[idx++] =
                (newTarget - patchPoint + (1 << ArchitectureSpecific.VM_RegisterConstants.LG_INSTRUCTION_WIDTH));
          } else if (VM.VerifyAssertions) {
            VM._assert(VM.NOT_REACHED);
          }
        }
      }
    }
  }

  /**
   * Apply the code patches to the INSTRUCTION array of cm
   */
  @Interruptible
  public void applyCodePatches(VM_CompiledMethod cm) {
    if (patchMap != null) {
      for (int idx = 0; idx < patchMap.length; idx += 2) {
        ArchitectureSpecific.VM_CodeArray code = cm.codeArrayForOffset(Offset.fromIntZeroExtend(patchMap[idx]));
        if (VM.BuildForIA32) {
          ArchitectureSpecific.VM_Assembler.patchCode(code, patchMap[idx], patchMap[idx + 1]);
        } else if (VM.BuildForPowerPC) {
          ArchitectureSpecific.OPT_Assembler.patchCode(code, patchMap[idx], patchMap[idx + 1]);
        } else if (VM.VerifyAssertions) {
          VM._assert(VM.NOT_REACHED);
        }
      }

      if (VM.BuildForPowerPC) {
        /* we need synchronization on PPC to handle the weak memory model.
        * before the class loading finish, other processor should get
        * synchronized.
        */

        boolean DEBUG_CODE_PATCH = false;

        // let other processors see changes; although really physical processors
        // need synchronization, we set each virtual processor to execute
        // isync at thread switch point.
        VM_Magic.sync();

        if (VM_Scheduler.syncObj == null) {
          VM_Scheduler.syncObj = new Object();
        }

        // how may processors to be synchronized
        // no current process, no the first dummy processor
        VM_Scheduler.toSyncProcessors = VM_Scheduler.numProcessors - 1;

        synchronized (VM_Scheduler.syncObj) {
          for (int i = 0; i < VM_Scheduler.numProcessors; i++) {
            VM_Processor proc = VM_Scheduler.processors[i + 1];
            // do not sync the current processor
            if (proc != VM_Processor.getCurrentProcessor()) {
              proc.requestPostCodePatchSync();
            }
          }
        }

        if (DEBUG_CODE_PATCH) {
          VM.sysWriteln("processors to be synchronized : ", VM_Scheduler.toSyncProcessors);
        }

        // do sync only when necessary
        while (VM_Scheduler.toSyncProcessors > 0) {
          VM_Thread.yield();
        }

        if (DEBUG_CODE_PATCH) {
          VM.sysWrite("all processors get synchronized!\n");
        }
      }

    }
  }

}
