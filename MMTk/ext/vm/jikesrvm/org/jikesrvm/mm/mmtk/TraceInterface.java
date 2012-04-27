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
package org.jikesrvm.mm.mmtk;

import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.VM;
import org.jikesrvm.Services;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.compilers.baseline.BaselineCompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.compilers.opt.runtimesupport.OptCompiledMethod;
import org.jikesrvm.compilers.opt.runtimesupport.OptEncodedCallSiteTree;
import org.jikesrvm.compilers.opt.runtimesupport.OptMachineCodeMap;
import org.jikesrvm.objectmodel.MiscHeader;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.scheduler.RVMThread;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Interruptible;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Class that supports scanning Objects or Arrays for references
 * during tracing, handling those references, and computing death times
 */
@Uninterruptible public final class TraceInterface extends org.mmtk.vm.TraceInterface implements ArchitectureSpecific.ArchConstants {

  /***********************************************************************
   *
   * Class variables
   */
  private static byte[][] allocCallMethods;

  static {
    /* Build the list of "own methods" */
    allocCallMethods = new byte[13][];
    allocCallMethods[0] = "postAlloc".getBytes();
    allocCallMethods[1] = "traceAlloc".getBytes();
    allocCallMethods[2] = "allocateScalar".getBytes();
    allocCallMethods[3] = "allocateArray".getBytes();
    allocCallMethods[4] = "clone".getBytes();
    allocCallMethods[5] = "alloc".getBytes();
    allocCallMethods[6] = "buildMultiDimensionalArray".getBytes();
    allocCallMethods[7] = "resolvedNewScalar".getBytes();
    allocCallMethods[8] = "resolvedNewArray".getBytes();
    allocCallMethods[9] = "unresolvedNewScalar".getBytes();
    allocCallMethods[10] = "unresolvedNewArray".getBytes();
    allocCallMethods[11] = "cloneScalar".getBytes();
    allocCallMethods[12] = "cloneArray".getBytes();
  }

  /***********************************************************************
   *
   * Public Methods
   */

  @Override
  public boolean gcEnabled() {
    return RVMThread.gcEnabled();
  }

  /**
   * Given a method name, determine if it is a "real" method or one
   * used for allocation/tracing.
   *
   * @param name The method name to test as an array of bytes
   * @return True if the method is a "real" method, false otherwise.
   */
  private boolean isAllocCall(byte[] name) {
    for (int i = 0; i < allocCallMethods.length; i++) {
      byte[] funcName = Services.getArrayNoBarrier(allocCallMethods, i);
      if (Magic.getArrayLength(name) == Magic.getArrayLength(funcName)) {
        /* Compare the letters in the allocCallMethod */
        int j = Magic.getArrayLength(funcName) - 1;
        while (j >= 0) {
          if (Services.getArrayNoBarrier(name, j) !=
              Services.getArrayNoBarrier(funcName, j))
            break;
          j--;
        }
        if (j == -1)
          return true;
      }
    }
    return false;
  }

  @Override
  public Offset adjustSlotOffset(boolean isScalar,
                                              ObjectReference src,
                                              Address slot) {
    /* Offset scalar objects so that the fields appear to begin at offset 0
       of the object. */
    Offset offset = slot.diff(src.toAddress());
    if (isScalar)
      return offset.minus(getHeaderEndOffset());
    else
      return offset;
  }

  @Override
  @NoInline
  @Interruptible // This can't be uninterruptible --- it is an IO routine
  public Address skipOwnFramesAndDump(ObjectReference typeRef) {
    TIB tib = Magic.addressAsTIB(typeRef.toAddress());
    RVMMethod m = null;
    int bci = -1;
    int compiledMethodID = 0;
    Offset ipOffset = Offset.zero();
    Address fp = Magic.getFramePointer();
    Address ip = Magic.getReturnAddress(fp);
    fp = Magic.getCallerFramePointer(fp);
    // This code borrows heavily from RVMThread.dumpStack
    while (Magic.getCallerFramePointer(fp).NE(STACKFRAME_SENTINEL_FP)) {
      compiledMethodID = Magic.getCompiledMethodID(fp);
      if (compiledMethodID != INVISIBLE_METHOD_ID) {
        // normal java frame(s)
        CompiledMethod compiledMethod =
          CompiledMethods.getCompiledMethod(compiledMethodID);
        if (compiledMethod.getCompilerType() != CompiledMethod.TRAP) {
          ipOffset = compiledMethod.getInstructionOffset(ip);
          m = compiledMethod.getMethod();
          if (VM.BuildForOptCompiler && compiledMethod.getCompilerType() == CompiledMethod.OPT) {
            OptCompiledMethod optInfo = (OptCompiledMethod)compiledMethod;
            /* Opt stack frames may contain multiple inlined methods. */
            OptMachineCodeMap map = optInfo.getMCMap();
            int iei = map.getInlineEncodingForMCOffset(ipOffset);
            if (iei >= 0) {
              int[] inlineEncoding = map.inlineEncoding;
              boolean allocCall = true;
              bci = map.getBytecodeIndexForMCOffset(ipOffset);
              for (int j = iei; j >= 0 && allocCall;
                   j = OptEncodedCallSiteTree.getParent(j,inlineEncoding)) {
                int mid = OptEncodedCallSiteTree.getMethodID(j, inlineEncoding);
                m = MemberReference.getMemberRef(mid).asMethodReference().getResolvedMember();
                if (!isAllocCall(m.getName().getBytes()))
                  allocCall = false;
                if (j > 0)
                  bci = OptEncodedCallSiteTree.getByteCodeOffset(j,
                                                                    inlineEncoding);
              }
              if (!allocCall)
                break;
            }
          } else {
            if (!isAllocCall(m.getName().getBytes())) {
              BaselineCompiledMethod baseInfo =
                (BaselineCompiledMethod)compiledMethod;
              bci = baseInfo.findBytecodeIndexForInstruction(ipOffset.toWord().lsh(INSTRUCTION_WIDTH).toOffset());
              break;
            }
          }
        }
      }
      ip = Magic.getReturnAddress(fp);
      fp = Magic.getCallerFramePointer(fp);
    }
    if (m != null) {
      int allocid = (((compiledMethodID & 0x0000ffff) << 15) ^
                     ((compiledMethodID & 0xffff0000) >> 16) ^
                     ipOffset.toInt()) & ~0x80000000;

      /* Now print the location string. */
      VM.sysWrite('\n');
      VM.writeHex(allocid);
      VM.sysWrite('-');
      VM.sysWrite('>');
      VM.sysWrite('[');
      VM.writeHex(compiledMethodID);
      VM.sysWrite(']');
      m.getDeclaringClass().getDescriptor().sysWrite();
      VM.sysWrite(':');
      m.getName().sysWrite();
      m.getDescriptor().sysWrite();
      VM.sysWrite(':');
      VM.writeHex(bci);
      VM.sysWrite('\t');
      RVMType type = tib.getType();
      type.getDescriptor().sysWrite();
      VM.sysWrite('\n');
    }
    return fp;
  }

  /***********************************************************************
   *
   * Wrapper methods
   */

  @Override
  @Inline
  public void updateDeathTime(ObjectReference obj) {
    MiscHeader.updateDeathTime(obj.toObject());
  }

  @Override
  @Inline
  public void setDeathTime(ObjectReference ref, Word time_) {
    MiscHeader.setDeathTime(ref.toObject(), time_);
  }

  @Override
  @Inline
  public void setLink(ObjectReference ref, ObjectReference link) {
    MiscHeader.setLink(ref.toObject(), link);
  }

  @Override
  @Inline
  public void updateTime(Word time_) {
    MiscHeader.updateTime(time_);
  }

  @Override
  @Inline
  public Word getOID(ObjectReference ref) {
    return MiscHeader.getOID(ref.toObject());
  }

  @Override
  @Inline
  public Word getDeathTime(ObjectReference ref) {
    return MiscHeader.getDeathTime(ref.toObject());
  }

  @Override
  @Inline
  public ObjectReference getLink(ObjectReference ref) {
    return MiscHeader.getLink(ref.toObject());
  }

  @Override
  @Inline
  public Address getBootImageLink() {
    return MiscHeader.getBootImageLink();
  }

  @Override
  @Inline
  public Word getOID() {
    return MiscHeader.getOID();
  }

  @Override
  @Inline
  public void setOID(Word oid) {
    MiscHeader.setOID(oid);
  }

  @Override
  @Inline
  public int getHeaderSize() {
    return MiscHeader.getHeaderSize();
  }

  @Override
  @Inline
  public int getHeaderEndOffset() {
    return ObjectModel.getHeaderEndOffset();
  }
}
