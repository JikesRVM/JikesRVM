/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.mmInterface;

import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_BaselineGCMapIterator;
//-#if RVM_WITH_OPT_COMPILER
import com.ibm.JikesRVM.opt.VM_OptGCMapIterator;
//-#endif
//-#if RVM_WITH_QUICK_COMPILER
import com.ibm.JikesRVM.quick.VM_QuickGCMapIterator;
//-#endif
import com.ibm.JikesRVM.jni.VM_JNIGCMapIterator;
import com.ibm.JikesRVM.VM_HardwareTrapGCMapIterator;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_SizeConstants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;


/**
 * Maintains a collection of compiler specific VM_GCMapIterators that are used 
 * by collection threads when scanning thread stacks to locate object references
 * in those stacks. Each collector thread has its own VM_GCMapIteratorGroup.
 *
 * The group contains a VM_GCMapIterator for each type of stack frame that
 * may be found while scanning a stack during garbage collection, including
 * frames for baseline compiled methods, OPT compiled methods, and frames 
 * for transitions from Java into JNI native code. These iterators are
 * repsonsible for reporting the location of references in the stack or
 * register save areas.
 *
 * @see VM_GCMapIterator
 * @see VM_CompiledMethod
 * @see VM_CollectorThread
 * 
 * @author Janice Shepherd
 * @modified by Stephen Smith
 * @modified by anyone adding a new iterator
 */
public final class VM_GCMapIteratorGroup implements VM_SizeConstants {
  
  /** current location (memory address) of each gpr register */
  private final WordArray registerLocations;

  /** iterator for baseline compiled frames */
  private final VM_BaselineGCMapIterator baselineIterator;

  /** iterator for opt compiled frames */
  //-#if RVM_WITH_OPT_COMPILER
  private final VM_OptGCMapIterator optIterator;
  //-#else
  private final VM_GCMapIterator optIterator = null;
  //-#endif
  
  /** iterator for quick compiled frames */
  private VM_GCMapIterator     quickIterator;
  
  /** iterator for VM_HardwareTrap stackframes */
  private final VM_HardwareTrapGCMapIterator hardwareTrapIterator;
  
  /** iterator for JNI Java -> C  stackframes */
  private final VM_JNIGCMapIterator jniIterator;
  
  
  public VM_GCMapIteratorGroup() throws UninterruptiblePragma {
    registerLocations         = WordArray.create(VM_Constants.NUM_GPRS);
    
    baselineIterator = new VM_BaselineGCMapIterator(registerLocations);
    //-#if RVM_WITH_OPT_COMPILER
    optIterator = new VM_OptGCMapIterator(registerLocations);
    //-#endif
    //-#if RVM_WITH_QUICK_COMPILER
    quickIterator = new VM_QuickGCMapIterator(registerLocations);
    //-#endif
    jniIterator = new VM_JNIGCMapIterator(registerLocations);
    hardwareTrapIterator      = new VM_HardwareTrapGCMapIterator(registerLocations);
  }
  
  /**
   * Prepare to scan a thread's stack for object references.
   * Called by collector threads when beginning to scan a threads stack.
   * Calls newStackWalk for each of the contained VM_GCMapIterators.
   * <p>
   * Assumption:  the thread is currently suspended, ie. its saved gprs[]
   * contain the thread's full register state.
   * <p>
   * Side effect: registerLocations[] initialized with pointers to the
   * thread's saved gprs[] (in thread.contextRegisters.gprs)
   * <p>
   * @param thread  VM_Thread whose registers and stack are to be scanned
   */
  public void newStackWalk(VM_Thread thread) throws UninterruptiblePragma {
    Address registerLocation = VM_Magic.objectAsAddress(thread.contextRegisters.gprs);
    for (int i = 0; i < VM_Constants.NUM_GPRS; ++i) {
      registerLocations.set(i, registerLocation);
      registerLocation = registerLocation.add(BYTES_IN_ADDRESS);
    }
    baselineIterator.newStackWalk(thread);
    if (optIterator != null) optIterator.newStackWalk(thread);
    if (quickIterator != null) quickIterator.newStackWalk(thread);
    hardwareTrapIterator.newStackWalk(thread);
    jniIterator.newStackWalk(thread);
  }
  
  /**
   * Select iterator for scanning for object references in a stackframe.
   * Called by collector threads while scanning a threads stack.
   *
   * @param compiledMethod  VM_CompiledMethod for the method executing
   *                        in the stack frame
   *
   * @return VM_GCMapIterator to use
   */
  public VM_GCMapIterator selectIterator(VM_CompiledMethod compiledMethod) throws UninterruptiblePragma {
    switch (compiledMethod.getCompilerType()) {
    case VM_CompiledMethod.TRAP: return hardwareTrapIterator;
    case VM_CompiledMethod.BASELINE: return baselineIterator;
    case VM_CompiledMethod.OPT: return optIterator;
    case VM_CompiledMethod.QUICK: return quickIterator;
    case VM_CompiledMethod.JNI: return jniIterator;
    }
    if (VM.VerifyAssertions) {
      VM._assert(false, "VM_GCMapIteratorGroup.selectIterator: Unknown type of compiled method");
    }
    return null;
  }
  
  /**
   * get the VM_GCMapIterator used for scanning JNI native stack frames.
   *
   * @return jniIterator
   */
  public VM_GCMapIterator getJniIterator() throws UninterruptiblePragma {
    if (VM.VerifyAssertions) VM._assert(jniIterator!=null);
    return jniIterator;  
  }
}
