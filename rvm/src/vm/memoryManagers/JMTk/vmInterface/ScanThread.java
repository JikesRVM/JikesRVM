/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package org.mmtk.vm;

import org.mmtk.utility.deque.*;
import org.mmtk.utility.Constants;

import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.memoryManagers.mmInterface.DebugUtil;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_CollectorThread;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_GCMapIterator;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_GCMapIteratorGroup;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_CodeArray;

import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_CompiledMethods;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Runtime;
import com.ibm.JikesRVM.VM_Thread;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Class that supports scanning thread stacks for references during
 * collections. References are located using GCMapIterators and are
 * inserted into a set of root locations.  Optionally, a set of 
 * interior pointer locations paired with the object is created.
 *
 * @author Stephen Smith
 * @author Perry Cheng
 */  
public class ScanThread implements VM_Constants, Uninterruptible {

  /**
   * quietly validates each ref reported by map iterators
   */
  static final boolean VALIDATE_STACK_REFS = true;

  /**
   * debugging options to produce printout during scanStack
   * MULTIPLE GC THREADS WILL PRODUCE SCRAMBLED OUTPUT so only
   * use these when running with PROCESSORS=1
   */
  static int DUMP_STACK = 0;

  /**
   * Threads, stacks,  jni environments,  and register objects  have a
   * complex  interaction  in terms  of  scanning.   The operation  of
   * scanning the  stack reveals not  only roots inside the  stack but
   * also the  state of the register  objects's gprs and  the JNI refs
   * array.  They are all associated  via the thread object, making it
   * natural for  scanThread to be considered a  single operation with
   * the  method  directly  accessing  these objects  via  the  thread
   * object's fields. <p>
   *
   * One pitfall occurs when scanning the thread object (plus
   * dependents) when not all of the objects have been copied.  Then
   * it may be that the innards of the register object has not been
   * copied while the stack object has.  The result is that an
   * inconsistent set of slots is reported.  In this case, the copied
   * register object may not be correct if the copy occurs after the
   * root locations are discovered but before those locations are
   * processed. In essence, all of these objects form one logical unit
   * but are physically separated so that sometimes only part of it
   * has been copied causing the scan to be incorrect. <p>
   *
   * The caller of this routine must ensure that all of these
   * components's descendants are consistent (all copied) when this
   * method is called. <p>
   *
   *   t
   *   t.stack (if stack moving is enabled)
   *   t.jniEnv.jniRefs (t.jniEnv might be null)      
   *   t.contextRegisters 
   *   t.contextRegisters.gprs
   *   t.hardwareExceptionRegisters
   *   t.hardwareExceptionRegisters.gprs 
   */
  public static void scanThread(VM_Thread t, AddressDeque rootLocations, 
                                AddressPairDeque codeLocations) {
        
    Plan plan = Plan.getInstance();

    if (VM.VerifyAssertions) {
      /* Currently we do not allow stacks to be moved.  If a stack
       * contains native stack frames, then it is impossible for us to
       * safely move it.  Prior to the implementation of JNI, Jikes
       * RVM did allow the GC system to move thread stacks, and called
       * a special fixup routine, thread.fixupMovedStack to adjust all
       * of the special interior pointers (SP, FP).  If we implement
       * split C & Java stacks then we could allow the Java stacks to
       * be moved, but we can't move the native stack. */
      VM._assert(Plan.willNotMove(ObjectReference.fromObject(t.stack)));
    }                             

    if (VM.VerifyAssertions) {
      VM._assert(Plan.willNotMove(ObjectReference.fromObject(t)));
      VM._assert(Plan.willNotMove(ObjectReference.fromObject(t.stack)));
      VM._assert(t.jniEnv == null || Plan.willNotMove(ObjectReference.fromObject(t.jniEnv)));
      VM._assert(t.jniEnv == null || t.jniEnv.refsArray() == null || Plan.willNotMove(ObjectReference.fromObject(t.jniEnv.refsArray())));
      VM._assert(Plan.willNotMove(ObjectReference.fromObject(t.contextRegisters)));
      VM._assert(Plan.willNotMove(ObjectReference.fromObject(t.contextRegisters.gprs)));
      VM._assert(Plan.willNotMove(ObjectReference.fromObject(t.hardwareExceptionRegisters)));
      VM._assert(Plan.willNotMove(ObjectReference.fromObject(t.hardwareExceptionRegisters.gprs)));
    }

    // all threads in "unusual" states, such as running threads in
    // SIGWAIT (nativeIdleThreads, nativeDaemonThreads,
    // passiveCollectorThreads), set their ContextRegisters before
    // calling SIGWAIT so that scans of their stacks will start at
    // the caller of SIGWAIT
    // fp = -1 case, which we need to add support for again this
    // is for "attached" threads that have returned to C, but have
    // been given references which now reside in the JNIEnv
    // sidestack
    scanThreadInternal(rootLocations, codeLocations, t, Address.zero());
    if (failed == t) {
      VM.sysWriteln("RESCANNING with verbose on");
      DUMP_STACK = 3;
      scanThreadInternal(rootLocations, codeLocations, t, Address.zero());
      VM._assert(false);
    }
  }

  static VM_Thread failed = null;

  private static void codeLocationsPush (AddressPairDeque codeLocations,
                                         ObjectReference code, Address ipLoc, 
                                         int where, VM_Thread t) {
    Address ip = ipLoc.loadAddress();
    Offset offset = ip.diff(code.toAddress());
    if (offset.sLT(Offset.zero()) || offset.sGT(Offset.fromIntZeroExtend(1<<24))) {  // There is probably no object this large
        VM.sysWriteln("ERROR: Suspiciously large delta of interior pointer from object base");
        VM.sysWriteln("       object base = ", code);
        VM.sysWriteln("       interior reference = ", ip);
        VM.sysWriteln("       delta = ", offset);
        VM.sysWriteln("       interior ref loc = ", ipLoc);
        VM.sysWriteln("       where = ", where);
        if (failed == null) failed = t;
        // VM._assert(false);
    }
    codeLocations.push(code.toAddress(), ipLoc);
  }


  private static void printMethodHeader(VM_CompiledMethod compiledMethod,
                                        Address fp, Address ip) {
    VM_Method method = compiledMethod.getMethod();
    int compiledMethodType = compiledMethod.getCompilerType();

    VM.sysWrite("\n--- METHOD (",
                VM_CompiledMethod.compilerTypeToString(compiledMethodType), ") ");
    if (method == null)
        VM.sysWrite("null method");
    else
        VM.sysWrite(method);
    VM.sysWriteln();
    VM.sysWrite("--- fp = ", fp);
    if (compiledMethod.isCompiled()) {
        ObjectReference codeBase = ObjectReference.fromObject(compiledMethod.getInstructions());
        VM.sysWrite("     code base = ", codeBase);
        VM.sysWriteln("     code offset = ", ip.diff(codeBase.toAddress()));
    }
    else
      VM.sysWriteln("   Method is uncompiled - ip = ", ip);
  }

  static private Address sentinelFP = STACKFRAME_SENTINEL_FP;

  /**
   * Scans a threads stack during collection to find object references.
   * Locate and save locations containing roots and/or return addresses.
   * Include JNI native frames.
   * <p>
   *
   * @param rootLocations set in which to store addresses containing roots
   * @param codeLocations set in which to store addresses containing
   * return addresses (if null, skip)
   * @param t VM_Thread for the thread whose stack is being scanned
   * @param top_frame address of stack frame at which to begin the scan
   */
  private static void scanThreadInternal(AddressDeque rootLocations, 
                                         AddressPairDeque codeLocations,
                                         VM_Thread t, Address top_frame) {
    
    if (DUMP_STACK >= 1) VM.sysWriteln("Scanning thread ", t.getIndex());

    // Don't forget ip in hardwareExceptionRegisters, if it is in use.
    //
    if (codeLocations != null && t.hardwareExceptionRegisters.inuse) {
      Address ip = t.hardwareExceptionRegisters.ip;
      VM_CompiledMethod compiledMethod = VM_CompiledMethods.findMethodForInstruction(ip);
      if (VM.VerifyAssertions) VM._assert(compiledMethod != null);
      compiledMethod.setObsolete( false );
      ObjectReference code = ObjectReference.fromObject(compiledMethod.getInstructions());
      Address ipLoc = t.hardwareExceptionRegisters.getIPLocation();
      codeLocationsPush(codeLocations, code, ipLoc, 1, t);
    }

    // get gc thread local iterator group from our VM_CollectorThread object
    //
    VM_CollectorThread collector = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    VM_GCMapIteratorGroup iteratorGroup = collector.iteratorGroup;
    iteratorGroup.newStackWalk(t);

    Address             ip, fp, prevFp;
    if (top_frame.isZero()) {
      prevFp = Address.zero();
      // start scan using fp & ip in threads saved context registers
      ip = t.contextRegisters.getInnermostInstructionAddress();
      fp = t.contextRegisters.getInnermostFramePointer();
    } else {
      prevFp = top_frame;
      // start scan at caller of passed in fp
      ip = VM_Magic.getReturnAddress(top_frame);
      fp = VM_Magic.getCallerFramePointer(top_frame);
    }

    if (DUMP_STACK >= 1) {
      VM.sysWriteln("  top_frame = ", top_frame);
      VM.sysWriteln("         ip = ", ip);
      VM.sysWriteln("         fp = ", fp);
      VM.sysWriteln("  registers.ip = ", t.contextRegisters.ip);
    }    
    if (DUMP_STACK >= 2 && t.jniEnv != null) 
        t.jniEnv.dumpJniRefsStack();

    // It is possible to have a stack with only Native C frames, for a thread
    // that started in C, "attached" to the VM, made JNIFunction calls, and
    // has now returned back to C. In this case, scanStack should be called
    // with a "topJavaFrame" = 0. There may be references in the threads
    // JNIrefs side stack that need to be processed, below after the loop.

    if ( fp.NE(sentinelFP)) {

      // At start of loop:
      //   fp -> frame for method invocation being processed
      //   ip -> instruction pointer in the method (normally a call site)
      
      while (VM_Magic.getCallerFramePointer(fp).NE(sentinelFP)) {
        
        int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
        
        // Reflection and jni generate "invisible" transition frames with
        // method_id == 0.  Such frames are skipped here. For PowerPC
        // JNI C to java transitions frames exist, but should be skipped at the
        // end of this loop during incrementing to the "next" frame.
        //
        // Everything after this if-block is for normal Java (and JNI Java to C transition) frames
        //
        if (compiledMethodId == VM_Constants.INVISIBLE_METHOD_ID) {
          
          if (DUMP_STACK >= 1) VM.sysWrite("\n--- METHOD <invisible method>\n");
          
          prevFp = fp;
          ip = VM_Magic.getReturnAddress(fp);
          fp = VM_Magic.getCallerFramePointer(fp);
          continue;
        }
        
        VM_CompiledMethod compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
        compiledMethod.setObsolete(false);  // keeps code object alive
        VM_Method method = compiledMethod.getMethod();
        int compiledMethodType = compiledMethod.getCompilerType();

        if (DUMP_STACK >= 1) 
          printMethodHeader(compiledMethod, fp, ip);

        if (compiledMethodType == VM_CompiledMethod.TRAP) {
          ip = VM_Magic.getReturnAddress(fp);
          fp = VM_Magic.getCallerFramePointer(fp);
          continue;
        }

        // initialize MapIterator for this frame
        VM_CodeArray codeArray = compiledMethod.getInstructions();
        Offset offset = ip.diff(VM_Magic.objectAsAddress(codeArray));
        if (compiledMethodType != VM_CompiledMethod.JNI) {
          Offset possibleLen = Offset.fromIntZeroExtend(codeArray.length() << LG_INSTRUCTION_WIDTH);
          if (offset.sLT(Offset.zero()) || possibleLen.sLT(offset)) {
            // We have an invalid offset
            if (offset.sLT(Offset.zero())) {
              VM.sysWriteln("ScanThread: computed instruction offset is negative ", offset);
            } else {
              VM.sysWriteln("ScanThread: computed instruction offset is too big");
              VM.sysWrite("\toffset is", offset);
              VM.sysWriteln(" bytes of machine code for method ",possibleLen);
            }
            VM.sysWrite("\tSupposed method: ");
            VM.sysWrite(method);
            VM.sysWriteln("\n\tBase of its code array", ObjectReference.fromObject(codeArray));
            Address ra = VM_Magic.objectAsAddress(codeArray).add(offset);
            VM.sysWriteln("\tCalculated actual return address is ", ra);
            VM_CompiledMethod realCM = VM_CompiledMethods.findMethodForInstruction(ra);
            if (realCM == null) {
              VM.sysWriteln("\tUnable to find compiled method corresponding to this return address");
            } else {
              VM.sysWrite("\tFound compiled method ");
              VM.sysWrite(realCM.getMethod());
              VM.sysWriteln(" whose code contains this return address");
            }
            VM.sysWriteln("Attempting to dump suspect stack and then exit\n");
            VM_Scheduler.dumpStackAndDie(top_frame);
          }
        }
        VM_GCMapIterator iterator = iteratorGroup.selectIterator(compiledMethod);
        iterator.setupIterator(compiledMethod, offset.toInt(), fp);
        
        if (DUMP_STACK >= 2) dumpStackFrame( fp, prevFp );
        
        if (DUMP_STACK >= 3)
          VM.sysWrite("--- Refs Reported By GCMap Iterator ---\n");
        
        // scan the map for this frame and process each reference
        //
        for (Address refaddr = iterator.getNextReferenceAddress(); !refaddr.isZero();
             refaddr = iterator.getNextReferenceAddress()) {
          
          if (VM.VerifyAssertions && VALIDATE_STACK_REFS) {
            ObjectReference ref = refaddr.loadObjectReference();
            if (!MM_Interface.validRef(ref)) {
              VM.sysWrite("\nInvalid ref reported while scanning stack\n");
              printMethodHeader(compiledMethod, fp, ip);
              VM.sysWrite(refaddr); VM.sysWrite(":"); MM_Interface.dumpRef(ref);  
              dumpStackFrame( fp, prevFp );
              VM.sysWrite("\nDumping stack starting at frame with bad ref:\n");
              VM_Scheduler.dumpStack( ip, fp );
              // start stact starting at top
              Address top_ip = t.contextRegisters.getInnermostInstructionAddress();
              Address top_fp = t.contextRegisters.getInnermostFramePointer();
              VM_Scheduler.dumpStack( top_ip, top_fp );
              VM.sysFail("\n\nVM_ScanStack: Detected bad GC map; exiting RVM with fatal error");
            }
          }
          if (DUMP_STACK >= 3) {
            ObjectReference ref = refaddr.loadObjectReference();
            VM.sysWrite(refaddr); 
            if (DUMP_STACK >= 4) {
                VM.sysWrite(":"); MM_Interface.dumpRef(ref);
            }
            else
                VM.sysWriteln();
          }
          
          rootLocations.push(refaddr);
      }

      if (codeLocations != null) {
        // process the code object for the method, and if it is moved, relocate
        // the saved ip and any internal code pointers (JSR subroutine return
        // addresses. Note, the instructions field of the VM_Method object is
        // NOT updated at this time, so that other invocations of the same
        // method can use the original address to compute the relocation delta.
        //
        ObjectReference code = ObjectReference.fromObject(compiledMethod.getInstructions());

        if (prevFp.isZero()) {
          // top-most stack frame, ip saved in threads context regs
          if (DUMP_STACK >= 2) {
            VM.sysWriteln(" t.contextRegisters.ip    = ", t.contextRegisters.ip);
            VM.sysWriteln("*t.contextRegisters.iploc = ", 
                          t.contextRegisters.getIPLocation().loadAddress());
          }
          if (compiledMethodType != VM_CompiledMethod.JNI) {
            codeLocationsPush(codeLocations, code, t.contextRegisters.getIPLocation(), 2, t);
          } else {
            if (DUMP_STACK >= 3)
              VM.sysWriteln("GC Warning: SKIPPING return address for JNI code");
          }
        } else {
          Address returnAddressLoc = VM_Magic.getReturnAddressLocation(prevFp);
          Address returnAddress = returnAddressLoc.loadAddress();
          if (DUMP_STACK >= 3)
            VM.sysWriteln("--- Processing return address ", returnAddress,
                          " located at ", returnAddressLoc);
          if (!DebugUtil.addrInBootImage(returnAddress))
            codeLocationsPush(codeLocations, code, returnAddressLoc, 3, t);
        }
        
        // scan for internal code pointers in the stack frame and relocate
        iterator.reset();
        for (Address retaddrLoc = iterator.getNextReturnAddressAddress();  !retaddrLoc.isZero();
             retaddrLoc = iterator.getNextReturnAddressAddress()) {
          codeLocationsPush(codeLocations, code, retaddrLoc, 4, t);
        }
      }
      
      iterator.cleanupPointers();
      
      // if at a JNIFunction method, it is preceeded by native frames that must be skipped
      //
      if (compiledMethod.getMethod().getDeclaringClass().isBridgeFromNative()) {
        // skip native frames, stopping at last native frame PRECEEDING the
        // Java To C transition frame
        fp = VM_Runtime.unwindNativeStackFrameForGC(fp);
        
        if (DUMP_STACK >= 1) VM.sysWrite("scanStack skipping native C frames\n");
      }       
      
      // set fp & ip for next frame
      prevFp = fp;
      ip = VM_Magic.getReturnAddress(fp);
      fp = VM_Magic.getCallerFramePointer(fp);
      
      } // end of while != sentinel
      
    } // end of if (fp != STACKFRAME_SENTINEL_FP)
    
    // if we are scanning the stack of a thread that entered the VM
    // via a createVM or attachVM then the "bottom" of the stack had
    // native C frames instead of the usual java frames.  The JNIEnv
    // for the thread may still contain jniRefs that have been
    // returned to the native C code, but have not been reported for
    // GC.  calling getNextReferenceAddress without first calling
    // setup... will report the remaining jniRefs in the current
    // "frame" of the jniRefs stack.  (this should be the bottom
    // frame)
    //
    //-#if RVM_FOR_AIX
    VM_GCMapIterator iterator = iteratorGroup.getJniIterator();
    Address refaddr =  iterator.getNextReferenceAddress();
    while( !refaddr.isZero() ) {
      rootLocations.push(refaddr);
      refaddr =  iterator.getNextReferenceAddress();
    }
    //-#else
    // exclude PPC FOR LINUX
    //-#endif
    
    if (DUMP_STACK >= 1) VM.sysWrite("--- End Of Stack Scan ---\n");
    
  } // scanStack
  

  // dump contents of a stack frame. attempts to interpret each
  // word a an object reference
  //
  static void dumpStackFrame(Address fp, Address prevFp ) {
    Address start,end;
//-#if RVM_FOR_IA32
    if (prevFp.isZero()) {
      start = fp.sub(20*BYTES_IN_ADDRESS);
      VM.sysWriteln("--- 20 words of stack frame with fp = ", fp);
    }
    else {
      start = prevFp;    // start at callee fp
      // VM.sysWriteln("--- stack frame with fp = ", fp);
    }
    end = fp;            // end at fp
//-#endif
//-#if RVM_FOR_POWERPC
    // VM.sysWriteln("--- stack frame with fp = ", fp);
    start = fp;                         // start at fp
    end = fp.loadAddress();   // stop at callers fp
//-#endif

    for (Address loc = start; loc.LT(end); loc = loc.add(BYTES_IN_ADDRESS)) {
      VM.sysWrite(loc); VM.sysWrite(" (");
      VM.sysWrite(loc.diff(start).toInt(), "):  ");
      ObjectReference value = loc.loadObjectReference();
      VM.sysWrite(" ", value);
      VM.sysWrite(" ");
      if (DUMP_STACK >= 3 && MM_Interface.objectInVM(value) && loc.NE(start) && loc.NE(end) )
        MM_Interface.dumpRef(value);
      else
        VM.sysWrite("\n");
    }
    VM.sysWrite("\n");
  }


}
