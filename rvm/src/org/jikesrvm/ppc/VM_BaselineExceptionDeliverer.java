/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package org.jikesrvm.ppc;

import org.jikesrvm.VM;
import org.jikesrvm.VM_BaselineCompiledMethod;
import org.jikesrvm.VM_CompiledMethod;
import org.jikesrvm.VM_ExceptionDeliverer;
import org.jikesrvm.VM_Magic;
import org.jikesrvm.VM_ObjectModel;
import org.jikesrvm.ArchitectureSpecific;
import org.jikesrvm.classloader.*;

import org.vmmagic.unboxed.*;

/**
 *  Handle exception delivery and stack unwinding for methods compiled 
 * by baseline compiler.
 *
 * @author Derek Lieber
 * @date 18 Sep 1998 
 */
public abstract class VM_BaselineExceptionDeliverer extends VM_ExceptionDeliverer 
  implements VM_BaselineConstants {

  /**
   * Pass control to a catch block.
   */
  public void deliverException(VM_CompiledMethod compiledMethod,
                               Address        catchBlockInstructionAddress,
                               Throwable         exceptionObject,
                               ArchitectureSpecific.VM_Registers      registers) {
    Address fp    = registers.getInnermostFramePointer();
    VM_NormalMethod method = (VM_NormalMethod)compiledMethod.getMethod();

    // reset sp to "empty expression stack" state
    //
    Address sp = fp.plus(VM_Compiler.getEmptyStackOffset(method));

    // push exception object as argument to catch block
    //
    sp = sp.minus(BYTES_IN_ADDRESS);
    sp.store(VM_Magic.objectAsAddress(exceptionObject));

    // set address at which to resume executing frame
    //
    registers.ip = catchBlockInstructionAddress;

    // branch to catch block
    //
    VM.enableGC(); // disabled right before VM_Runtime.deliverException was called
    if (VM.VerifyAssertions) VM._assert(registers.inuse == true); 

    registers.inuse = false;
    VM_Magic.restoreHardwareExceptionState(registers);
    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
  }
   
  /**
   * Unwind a stackframe.
   */
  public void unwindStackFrame(VM_CompiledMethod compiledMethod, ArchitectureSpecific.VM_Registers registers) {
    VM_NormalMethod method = (VM_NormalMethod)compiledMethod.getMethod();
    VM_BaselineCompiledMethod bcm = (VM_BaselineCompiledMethod)compiledMethod;
    if (method.isSynchronized()) { 
      Address ip = registers.getInnermostInstructionAddress();
      Offset instr = compiledMethod.getInstructionOffset(ip);
      Offset lockOffset = ((VM_BaselineCompiledMethod)compiledMethod).getLockAcquisitionOffset();
      if (instr.sGT(lockOffset)) { // we actually have the lock, so must unlock it.
        Object lock;
        if (method.isStatic()) {
          lock = method.getDeclaringClass().getClassForType();
        } else {
          Address fp = registers.getInnermostFramePointer();
          int location = bcm.getGeneralLocalLocation(0);
          Address addr;
          if (VM_Compiler.isRegister(location)) 
            lock = VM_Magic.addressAsObject(registers.gprs.get(location).toAddress());
          else {
            addr = fp.plus(VM_Compiler.locationToOffset(location) - BYTES_IN_ADDRESS); //location offsets are positioned on top of their stackslot
            lock = VM_Magic.addressAsObject(addr.loadAddress());
          }
        }
        VM_ObjectModel.genericUnlock(lock);
      }
    }
    // restore non-volatile registers
    Address fp = registers.getInnermostFramePointer();
    Offset frameOffset = Offset.fromIntSignExtend(VM_Compiler.getFrameSize(bcm));

    for (int i = bcm.getLastFloatStackRegister(); i >= FIRST_FLOAT_LOCAL_REGISTER; --i) {    
      frameOffset = frameOffset.minus(BYTES_IN_DOUBLE);
      long temp = VM_Magic.getLongAtOffset(VM_Magic.addressAsObject(fp), frameOffset);
      registers.fprs[i] = VM_Magic.longBitsAsDouble(temp);
    }
   
    for (int i = bcm.getLastFixedStackRegister(); i >= FIRST_FIXED_LOCAL_REGISTER; --i) {
      frameOffset = frameOffset.minus(BYTES_IN_ADDRESS);
      registers.gprs.set(i, fp.loadWord(frameOffset));
    }
    
    registers.unwindStackFrame();
  }
}
