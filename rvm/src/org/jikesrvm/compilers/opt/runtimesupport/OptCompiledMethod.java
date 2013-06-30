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
package org.jikesrvm.compilers.opt.runtimesupport;

import static org.jikesrvm.compilers.opt.ir.Operators.IG_PATCH_POINT;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.ArchitectureSpecificOpt;
import org.jikesrvm.VM;
import org.jikesrvm.PrintLN;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.ExceptionTable;
import org.jikesrvm.compilers.opt.ir.IR;
import org.jikesrvm.compilers.opt.ir.InlineGuard;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.osr.EncodedOSRMap;
import org.jikesrvm.runtime.DynamicLink;
import org.jikesrvm.runtime.ExceptionDeliverer;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Memory;
import org.jikesrvm.runtime.StackBrowser;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.unboxed.Offset;

/**
 * An implementation of CompiledMethod for the OPT compiler.
 *
 * <p> NOTE: OptCompiledMethod live as long as their corresponding
 * compiled machine code.  Therefore, they should only contain
 * state that is really required to be persistent.  Anything
 * transitory should be stored on the IR object.
 */
@Uninterruptible
public final class OptCompiledMethod extends CompiledMethod {

  public OptCompiledMethod(int id, RVMMethod m) {
    super(id, m);
  }

  /**
   * @return {@link CompiledMethod#OPT}
   */
  @Override
  public int getCompilerType() {
    return CompiledMethod.OPT;
  }

  @Override
  public String getCompilerName() {
    return "optimizing compiler";
  }

  @Override
  public ExceptionDeliverer getExceptionDeliverer() {
    return exceptionDeliverer;
  }

  /**
   * Find "catch" block for a machine instruction of this method.
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

  /**
   * Fetch symbolic reference to a method that's called
   * by one of this method's instructions.
   * @param dynamicLink place to put return information
   * @param instructionOffset offset of machine instruction that issued
   *                          the call
   */
  @Override
  public void getDynamicLink(DynamicLink dynamicLink, Offset instructionOffset) {
    int bci = _mcMap.getBytecodeIndexForMCOffset(instructionOffset);
    NormalMethod realMethod = _mcMap.getMethodForMCOffset(instructionOffset);
    if (bci == -1 || realMethod == null) {
      VM.sysFail("Mapping to source code location not available at Dynamic Linking point\n");
    }
    realMethod.getDynamicLink(dynamicLink, bci);
  }

  @Override
  @Interruptible
  public boolean isWithinUninterruptibleCode(Offset instructionOffset) {
    NormalMethod realMethod = _mcMap.getMethodForMCOffset(instructionOffset);
    return realMethod.isUninterruptible();
  }

  /**
   * Find source line number corresponding to one of this method's
   * machine instructions.
   */
  @Override
  public int findLineNumberForInstruction(Offset instructionOffset) {
    int bci = _mcMap.getBytecodeIndexForMCOffset(instructionOffset);
    if (bci < 0) {
      return 0;
    }
    return ((NormalMethod) method).getLineNumberForBCIndex(bci);
  }

  @Override
  @Interruptible
  public void set(StackBrowser browser, Offset instr) {
    OptMachineCodeMap map = getMCMap();
    int iei = map.getInlineEncodingForMCOffset(instr);
    if (iei >= 0) {
      int[] inlineEncoding = map.inlineEncoding;
      int mid = OptEncodedCallSiteTree.getMethodID(iei, inlineEncoding);

      browser.setInlineEncodingIndex(iei);
      browser.setBytecodeIndex(map.getBytecodeIndexForMCOffset(instr));
      browser.setCompiledMethod(this);
      browser.setMethod(MemberReference.getMemberRef(mid).asMethodReference().peekResolvedMethod());

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

  @Override
  @Interruptible
  public boolean up(StackBrowser browser) {
    OptMachineCodeMap map = getMCMap();
    int iei = browser.getInlineEncodingIndex();
    int[] ie = map.inlineEncoding;
    int next = OptEncodedCallSiteTree.getParent(iei, ie);
    if (next >= 0) {
      int mid = OptEncodedCallSiteTree.getMethodID(next, ie);
      int bci = OptEncodedCallSiteTree.getByteCodeOffset(iei, ie);

      browser.setInlineEncodingIndex(next);
      browser.setBytecodeIndex(bci);
      browser.setMethod(MemberReference.getMemberRef(mid).asMethodReference().peekResolvedMethod());

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

  @Override
  @Interruptible
  public void printStackTrace(Offset instructionOffset, PrintLN out) {
    OptMachineCodeMap map = getMCMap();
    int iei = map.getInlineEncodingForMCOffset(instructionOffset);
    if (iei >= 0) {
      int[] inlineEncoding = map.inlineEncoding;
      int bci = map.getBytecodeIndexForMCOffset(instructionOffset);
      for (int j = iei; j >= 0; j = OptEncodedCallSiteTree.getParent(j, inlineEncoding)) {
        int mid = OptEncodedCallSiteTree.getMethodID(j, inlineEncoding);
        NormalMethod m =
            (NormalMethod) MemberReference.getMemberRef(mid).asMethodReference().peekResolvedMethod();
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
          bci = OptEncodedCallSiteTree.getByteCodeOffset(j, inlineEncoding);
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

  @Override
  @Interruptible
  public int size() {
    int size = TypeReference.ExceptionTable.peekType().asClass().getInstanceSize();
    size += _mcMap.size();
    if (eTable != null) size += RVMArray.IntArray.getInstanceSize(eTable.length);
    if (patchMap != null) size += RVMArray.IntArray.getInstanceSize(patchMap.length);
    return size;
  }

  //----------------//
  // implementation //
  //----------------//
  private static final ArchitectureSpecificOpt.OptExceptionDeliverer exceptionDeliverer =
      new ArchitectureSpecificOpt.OptExceptionDeliverer();

  private EncodedOSRMap _osrMap;

  @Interruptible
  public void createFinalOSRMap(IR ir) {
    this._osrMap = EncodedOSRMap.makeMap(ir.MIRInfo.osrVarMap);
  }

  public EncodedOSRMap getOSRMap() {
    return this._osrMap;
  }

  //////////////////////////////////////
  // Information the opt compiler needs to persistently associate
  // with a particular compiled method.

  /** The primary machine code maps */
  private OptMachineCodeMap _mcMap;
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
      return ArchitectureSpecific.RegisterConstants.NUM_GPRS - getFirstNonVolatileGPR();
    } else if (VM.BuildForIA32) {
      return ArchitectureSpecific.RegisterConstants.NUM_NONVOLATILE_GPRS - getFirstNonVolatileGPR();
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
      return ArchitectureSpecific.RegisterConstants.NUM_FPRS - getFirstNonVolatileFPR();
    } else if (VM.BuildForIA32) {
      return ArchitectureSpecific.RegisterConstants.NUM_NONVOLATILE_FPRS - getFirstNonVolatileFPR();
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
      setFirstNonVolatileGPR(ArchitectureSpecific.RegisterConstants.NUM_GPRS - n);
    } else if (VM.BuildForIA32) {
      setFirstNonVolatileGPR(ArchitectureSpecific.RegisterConstants.NUM_NONVOLATILE_GPRS - n);
    } else if (VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);
    }
  }

  /**
   * Set the number of non-volatile FPRs used by this method.
   */
  public void setNumberOfNonvolatileFPRs(short n) {
    if (VM.BuildForPowerPC) {
      setFirstNonVolatileFPR(ArchitectureSpecific.RegisterConstants.NUM_FPRS - n);
    } else if (VM.BuildForIA32) {
      setFirstNonVolatileFPR(ArchitectureSpecific.RegisterConstants.NUM_NONVOLATILE_FPRS - n);
    } else if (VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED);
    }
  }

  /**
   * Print the eTable
   */
  @Interruptible
  public void printExceptionTable() {
    if (eTable != null) ExceptionTable.printExceptionTable(eTable);
  }

  /**
   * @return the machine code map for the compiled method.
   */
  public OptMachineCodeMap getMCMap() {
    return _mcMap;
  }

  /**
   * Create the final machine code map for the compiled method.
   * Remember the offset for the end of prologue too for debugger.
   * @param ir the ir
   * @param machineCodeLength the number of machine code instructions.
   */
  @Interruptible
  public void createFinalMCMap(IR ir, int machineCodeLength) {
    _mcMap = OptMachineCodeMap.create(ir, machineCodeLength);
  }

  /**
   * Create the final exception table from the IR for the method.
   * @param ir the ir
   */
  @Interruptible
  public void createFinalExceptionTable(IR ir) {
    if (ir.hasReachableExceptionHandlers()) {
      eTable = OptExceptionTable.encode(ir);
    }
  }

  /**
   * Create the code patching maps from the IR for the method
   * @param ir the ir
   */
  @Interruptible
  public void createCodePatchMaps(IR ir) {
    // (1) count the patch points
    int patchPoints = 0;
    for (Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = s.nextInstructionInCodeOrder()) {
      if (s.operator() == IG_PATCH_POINT) {
        patchPoints++;
      }
    }
    // (2) if we have patch points, create the map.
    if (patchPoints != 0) {
      patchMap = new int[patchPoints * 2];
      int idx = 0;
      for (Instruction s = ir.firstInstructionInCodeOrder(); s != null; s = s.nextInstructionInCodeOrder()) {
        if (s.operator() == IG_PATCH_POINT) {
          int patchPoint = s.getmcOffset();
          int newTarget = InlineGuard.getTarget(s).target.getmcOffset();
          // A patch map is the offset of the last byte of the patch point
          // and the new branch immediate to lay down if the code is ever patched.
          if (VM.BuildForIA32) {
            patchMap[idx++] = patchPoint - 1;
            patchMap[idx++] = newTarget - patchPoint;
          } else if (VM.BuildForPowerPC) {

            // otherwise, it must be RFOR_POWERPC
            /* since currently we use only one NOP scheme, the offset
            * is adjusted for one word
            */
            patchMap[idx++] = (patchPoint >> ArchitectureSpecific.RegisterConstants.LG_INSTRUCTION_WIDTH) - 1;
            patchMap[idx++] =
                (newTarget - patchPoint + (1 << ArchitectureSpecific.RegisterConstants.LG_INSTRUCTION_WIDTH));
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
  public void applyCodePatches(CompiledMethod cm) {
    if (patchMap != null) {
      for (int idx = 0; idx < patchMap.length; idx += 2) {
        ArchitectureSpecific.CodeArray code = cm.codeArrayForOffset(Offset.fromIntZeroExtend(patchMap[idx]));
        if (VM.BuildForIA32) {
          ArchitectureSpecific.Assembler.patchCode(code, patchMap[idx], patchMap[idx + 1]);
        } else if (VM.BuildForPowerPC) {
          ArchitectureSpecificOpt.AssemblerOpt.patchCode(code, patchMap[idx], patchMap[idx + 1]);
        } else if (VM.VerifyAssertions) {
          VM._assert(VM.NOT_REACHED);
        }
      }

      if (VM.BuildForPowerPC) {
        // we need synchronization on PPC to handle the weak memory model
        // and its icache/dcache synchronization requirements.
        // Before the class loading finishes, other processors must get
        // synchronized.
        boolean DEBUG_CODE_PATCH = false;

        // let other processors see changes.
        Magic.sync();

        // All other processors now will see the patched code in their data cache.
        // We now need to force everyone's instruction caches to be in synch with their
        // data caches.  Some of the work of this call is redundant (since we already have
        // forced the data caches to be in synch), but we need the icbi instructions
        // to invalidate the instruction caches.
        Memory.sync(Magic.objectAsAddress(instructions),
                       instructions.length() << ArchitectureSpecific.RegisterConstants.LG_INSTRUCTION_WIDTH);
        // Force all other threads to execute isync at the next thread switch point
        // so that the icbi instructions take effect. Another effect is that
        // prefetched instructions are discarded.
        // Note: it would be sufficient to execute isync once for each
        // physical processor.
        RVMThread.softHandshake(codePatchSyncRequestVisitor);

        if (DEBUG_CODE_PATCH) {
          VM.sysWrite("all processors got synchronized!\n");
        }
      }

    }
  }

  private static RVMThread.SoftHandshakeVisitor codePatchSyncRequestVisitor =
    new RVMThread.SoftHandshakeVisitor() {
      @Override
      @Uninterruptible
      public boolean checkAndSignal(RVMThread t) {
        t.codePatchSyncRequested = true;
        return true; // handshake with everyone but ourselves.
      }
      @Override
      @Uninterruptible
      public boolean includeThread(RVMThread t) {
        // CollectorThreads will never be executing code that is subject to code patching.
        // (We don't allow speculative optimization of Uninterruptible code).  Therefore
        // it is safe to exempt collectors from the need to respond to the handshake.
        return !t.isCollectorThread();
      }
    };
}
