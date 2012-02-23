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
import org.jikesrvm.Constants;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.compilers.common.CompiledMethod;
import org.jikesrvm.compilers.common.CompiledMethods;
import org.jikesrvm.mm.mminterface.Selected;
import org.jikesrvm.mm.mminterface.DebugUtil;
import org.jikesrvm.mm.mminterface.GCMapIterator;
import org.jikesrvm.mm.mminterface.GCMapIteratorGroup;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.scheduler.RVMThread;
import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.Log;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;
import org.jikesrvm.ArchitectureSpecific.Registers;

/**
 * Class that supports scanning thread stacks for references during
 * collections. References are located using GCMapIterators and are
 * inserted into a set of root locations.  Optionally, a set of
 * interior pointer locations associated with the object is created.<p>
 *
 * Threads, stacks, jni environments, and register objects have a
 * complex interaction in terms of scanning.  The operation of
 * scanning the stack reveals not only roots inside the stack but also
 * the state of the register objects's gprs and the JNI refs array.
 * They are all associated via the thread object, making it natural
 * for scanThread to be considered a single operation with the method
 * directly accessing these objects via the thread object's
 * fields. <p>
 *
 * One pitfall occurs when scanning the thread object (plus
 * dependents) when not all of the objects have been copied.  Then it
 * may be that the innards of the register object has not been copied
 * while the stack object has.  The result is that an inconsistent set
 * of slots is reported.  In this case, the copied register object may
 * not be correct if the copy occurs after the root locations are
 * discovered but before those locations are processed. In essence,
 * all of these objects form one logical unit but are physically
 * separated so that sometimes only part of it has been copied causing
 * the scan to be incorrect. <p>
 *
 * The caller of the stack scanning routine must ensure that all of
 * these components's descendants are consistent (all copied) when
 * this method is called. <p>
 *
 * <i>Code locations:</i> Identifying pointers <i>into</i> code
 * objects is essential if code objects are allowed to move (and if
 * the code objects were not otherwise kept alive, it would be
 * necessary to ensure the liveness of the code objects). A code
 * pointer is the only case in which we have interior pointers
 * (pointers into the inside of objects).  For such pointers, two
 * things must occur: first the pointed to object must be kept alive,
 * and second, if the pointed to object is moved by a copying
 * collector, the pointer into the object must be adjusted so it now
 * points into the newly copied object.<p>
 */
@Uninterruptible public final class ScanThread implements Constants {

  /***********************************************************************
   *
   * Class variables
   */

  /** quietly validates each ref reported by map iterators */
  static final boolean VALIDATE_REFS = VM.VerifyAssertions;

  /**
   * debugging options to produce printout during scanStack
   * MULTIPLE GC THREADS WILL PRODUCE SCRAMBLED OUTPUT so only
   * use these when running with PROCESSORS=1
   */
  private static final int DEFAULT_VERBOSITY = 0 /*0*/;
  private static final int FAILURE_VERBOSITY = 4;

  /***********************************************************************
   *
   * Instance variables
   */
  private final GCMapIteratorGroup iteratorGroup = new GCMapIteratorGroup();
  @Untraced
  private GCMapIterator iterator;
  @Untraced
  private TraceLocal trace;
  private boolean processCodeLocations;
  @Untraced
  private RVMThread thread;
  private Address ip, fp, prevFp, initialIPLoc, topFrame;
  @Untraced
  private CompiledMethod compiledMethod;
  private int compiledMethodType;
  private boolean failed;

  /***********************************************************************
   *
   * Thread scanning
   */

  /**
   * Scan a thread, placing the addresses of pointers into supplied buffers.
   *
   * @param thread The thread to be scanned
   * @param trace The trace instance to use for reporting references.
   * @param processCodeLocations Should code locations be processed?
   */
  public static void scanThread(RVMThread thread, TraceLocal trace,
                                boolean processCodeLocations) {
    if (DEFAULT_VERBOSITY>=1) {
      VM.sysWriteln("scanning ",thread.getThreadSlot());
    }

    /* get the gprs associated with this thread */
    Registers regs=thread.getContextRegisters();
    Address gprs = Magic.objectAsAddress(regs.gprs);

    Address ip=regs.getInnermostInstructionAddress();
    Address fp=regs.getInnermostFramePointer();
    regs.clear();
    regs.setInnermost(ip,fp);

    scanThread(thread, trace, processCodeLocations, gprs, Address.zero());
  }

  /**
   * Wrapper for {@link TraceLocal#reportDelayedRootEdge(Address)} that allows
   * sanity checking of the address.
   */
  private static void reportDelayedRootEdge(TraceLocal trace, Address addr) {
    if (VALIDATE_REFS) checkReference(addr);
    trace.reportDelayedRootEdge(addr);
  }

  /**
   * A more general interface to thread scanning, which permits the
   * scanning of stack segments which are dislocated from the thread
   * structure.
   *
   * @param thread The thread to be scanned
   * @param trace The trace instance to use for reporting references.
   * @param processCodeLocations Should code locations be processed?
   * @param gprs The general purpose registers associated with the
   * stack being scanned (normally extracted from the thread).
   * @param topFrame The top frame of the stack being scanned, or zero
   * if this is to be inferred from the thread (normally the case).
   */
  private static void scanThread(RVMThread thread, TraceLocal trace,
                                 boolean processCodeLocations,
                                 Address gprs, Address topFrame) {
    // figure out if the thread should be scanned at all; if not, exit
    if (thread.getExecStatus()==RVMThread.NEW || thread.getIsAboutToTerminate()) {
      return;
    }
    /* establish ip and fp for the stack to be scanned */
    Address ip, fp, initialIPLoc;
    if (topFrame.isZero()) { /* implicit top of stack, inferred from thread */
      ip = thread.getContextRegisters().getInnermostInstructionAddress();
      fp = thread.getContextRegisters().getInnermostFramePointer();
      initialIPLoc = thread.getContextRegisters().getIPLocation();
    } else {                 /* top frame explicitly defined */
      ip = Magic.getReturnAddress(topFrame);
      fp = Magic.getCallerFramePointer(topFrame);
      initialIPLoc = thread.getContextRegisters().getIPLocation(); // FIXME
    }

    /* Grab the ScanThread instance associated with this thread */
    ScanThread scanner = RVMThread.getCurrentThread().getCollectorThread().getThreadScanner();

    /* scan the stack */
    scanner.startScan(trace, processCodeLocations, thread, gprs, ip, fp, initialIPLoc, topFrame);
  }

  /**
   * Initializes a ScanThread instance, and then scans a stack
   * associated with a thread, and places references in deques (one for
   * object pointers, one for interior code pointers).  If the thread
   * scan fails, the thread is rescanned verbosely and a failure
   * occurs.<p>
   *
   * The various state associated with stack scanning is captured by
   * instance variables of this type, which are initialized here.
   *
   * @param trace The trace instance to use for reporting locations.
   * @param thread Thread for the thread whose stack is being scanned
   * @param gprs The general purpose registers associated with the
   * stack being scanned (normally extracted from the thread).
   * @param ip The instruction pointer for the top frame of the stack
   * we're about to scan.
   * @param fp The frame pointer for the top frame of the stack we're
   * about to scan.
   */
  private void startScan(TraceLocal trace,
                         boolean processCodeLocations,
                         RVMThread thread, Address gprs, Address ip,
                         Address fp, Address initialIPLoc, Address topFrame) {
    this.trace = trace;
    this.processCodeLocations = processCodeLocations;
    this.thread = thread;
    this.failed = false;
    this.ip = ip;
    this.fp = fp;
    this.initialIPLoc = initialIPLoc;
    this.topFrame = topFrame;
    scanThreadInternal(gprs, DEFAULT_VERBOSITY);
    if (failed) {
       /* reinitialize and rescan verbosly on failure */
      this.ip = ip;
      this.fp = fp;
      this.topFrame = topFrame;
      scanThreadInternal(gprs, FAILURE_VERBOSITY);
      VM.sysFail("Error encountered while scanning stack");
    }
  }

  /**
   * The main stack scanning loop.<p>
   *
   * Walk the stack one frame at a time, top (lo) to bottom (hi),<p>
   *
   * @param gprs The general purpose registers associated with the
   * stack being scanned (normally extracted from the thread).
   * @param verbosity The level of verbosity to be used when
   * performing the scan.
   */
  private void scanThreadInternal(Address gprs, int verbosity) {
    if (false) {
      VM.sysWriteln("Scanning thread ",thread.getThreadSlot()," from thread ",RVMThread.getCurrentThreadSlot());
    }
    if (verbosity >= 2) {
      Log.writeln("--- Start Of Stack Scan ---\n");
      Log.write("Thread #");
      Log.writeln(thread.getThreadSlot());
    }
    if (VM.VerifyAssertions) assertImmovableInCurrentCollection();

    /* first find any references to exception handlers in the registers */
    getHWExceptionRegisters();

    /* reinitialize the stack iterator group */
    iteratorGroup.newStackWalk(thread, gprs);

    if (verbosity >= 2) dumpTopFrameInfo(verbosity);

    /* scan each frame if a non-empty stack */
    if (fp.NE(ArchitectureSpecific.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP)) {
      prevFp = Address.zero();
      /* At start of loop:
         fp -> frame for method invocation being processed
         ip -> instruction pointer in the method (normally a call site) */
      while (Magic.getCallerFramePointer(fp).NE(ArchitectureSpecific.StackframeLayoutConstants.STACKFRAME_SENTINEL_FP)) {
        if (false) {
          VM.sysWriteln("Thread ",RVMThread.getCurrentThreadSlot()," at fp = ",fp);
        }
        prevFp = scanFrame(verbosity);
        ip = Magic.getReturnAddress(fp);
        fp = Magic.getCallerFramePointer(fp);
      }
    }

    /* If a thread started via createVM or attachVM, base may need scaning */
    checkJNIBase();

    if (verbosity >= 2) Log.writeln("--- End Of Stack Scan ---\n");
  }

  /**
   * When an exception occurs, registers are saved temporarily.  If
   * the stack being scanned is in this state, we need to scan those
   * registers for code pointers.  If the codeLocations deque is null,
   * then scanning for code pointers is not required, so we don't need
   * to do anything. (SB: Why only code pointers?).
   *
   * Dave G:  The contents of the GPRs of the exceptionRegisters
   * are handled during normal stack scanning
   * (@see org.jikesrvm.runtime.compilers.common.HardwareTrapCompiledMethod.
   * It looks to me like the main goal of this method is to ensure that the
   * method in which the trap happened isn't treated as dead code and collected
   * (if it's been marked as obsolete, we are setting its activeOnStackFlag below).
   *
   */
  private void getHWExceptionRegisters() {
    ArchitectureSpecific.Registers exReg = thread.getExceptionRegisters();
    if (processCodeLocations && exReg.inuse) {
      Address ip = exReg.ip;
      CompiledMethod compiledMethod = CompiledMethods.findMethodForInstruction(ip);
      if (VM.VerifyAssertions) {
        VM._assert(compiledMethod != null);
        VM._assert(compiledMethod.containsReturnAddress(ip));
      }
      compiledMethod.setActiveOnStack();
      ObjectReference code = ObjectReference.fromObject(compiledMethod.getEntryCodeArray());
      Address ipLoc = exReg.getIPLocation();
      if (VM.VerifyAssertions) VM._assert(ip == ipLoc.loadAddress());
      processCodeLocation(code, ipLoc);
    }
  }

  /**
   * Push a code pointer location onto the code locations deque,
   * optionally performing a sanity check first.<p>
   *
   * @param code The code object into which this interior pointer points
   * @param ipLoc The location of the pointer into this code object
   */
  @Inline
  private void processCodeLocation(ObjectReference code, Address ipLoc) {
    if (VALIDATE_REFS) {
      Address ip = ipLoc.loadAddress();
      Offset offset = ip.diff(code.toAddress());

      if (offset.sLT(Offset.zero()) ||
          offset.sGT(Offset.fromIntZeroExtend(ObjectModel.getObjectSize(code)))) {
        Log.writeln("ERROR: Suspiciously large offset of interior pointer from object base");
        Log.write("       object base = "); Log.writeln(code);
        Log.write("       interior reference = "); Log.writeln(ip);
        Log.write("       offset = "); Log.writeln(offset);
        Log.write("       interior ref loc = "); Log.writeln(ipLoc);
        if (!failed) failed = true;
      }
    }
    trace.processInteriorEdge(code, ipLoc, true);
  }

  /***********************************************************************
   *
   * Frame scanning methods
   */

  /**
   * Scan the current stack frame.<p>
   *
   * First the various iterators are set up, then the frame is scanned
   * for regular pointers, before scanning for code pointers.  The
   * iterators are then cleaned up, and native frames skipped if
   * necessary.
   *
   * @param verbosity The level of verbosity to be used when
   * performing the scan.
   */
  private Address scanFrame(int verbosity) {
    /* set up iterators etc, and skip the frame if appropriate */
    if (!setUpFrame(verbosity)) return fp;

    /* scan the frame for object pointers */
    scanFrameForObjects(verbosity);

    /* scan the frame for pointers to code */
    if (processCodeLocations && compiledMethodType != CompiledMethod.TRAP)
      processFrameForCode(verbosity);

    iterator.cleanupPointers();

    /* skip preceeding native frames if this frame is a native bridge */
    if (compiledMethodType != CompiledMethod.TRAP &&
        compiledMethod.getMethod().getDeclaringClass().hasBridgeFromNativeAnnotation()) {
      fp = RuntimeEntrypoints.unwindNativeStackFrameForGC(fp);
      if (verbosity >= 2) Log.write("scanFrame skipping native C frames\n");
    }
    return fp;
  }

  /**
   * Set up to scan the current stack frame.  This means examining the
   * frame to discover the method being invoked and then retrieving
   * the associated metadata (stack maps etc).  Certain frames should
   * not be scanned---these are identified and skipped.
   *
   * @param verbosity The level of verbosity to be used when
   * performing the scan.
   * @return True if the frame should be scanned, false if it should
   * be skipped.
   */
  private boolean setUpFrame(int verbosity) {
    /* get the compiled method ID for this frame */
    int compiledMethodId = Magic.getCompiledMethodID(fp);

    /* skip "invisible" transition frames generated by reflection and JNI) */
    if (compiledMethodId == ArchitectureSpecific.ArchConstants.INVISIBLE_METHOD_ID) {
      if (verbosity >= 2) Log.writeln("\n--- METHOD <invisible method>");
      return false;
    }

    /* establish the compiled method */
    compiledMethod = CompiledMethods.getCompiledMethod(compiledMethodId);
    compiledMethod.setActiveOnStack();  // prevents code from being collected

    compiledMethodType = compiledMethod.getCompilerType();

    if (verbosity >= 2) printMethodHeader();

    /* get the code associated with this frame */
    Offset offset = compiledMethod.getInstructionOffset(ip);

    /* initialize MapIterator for this frame */
    iterator = iteratorGroup.selectIterator(compiledMethod);
    iterator.setupIterator(compiledMethod, offset, fp);

    if (verbosity >= 3) dumpStackFrame(verbosity);
    if (verbosity >= 4) Log.writeln("--- Refs Reported By GCMap Iterator ---");

    return true;
  }

  /**
   * Identify all the object pointers stored as local variables
   * associated with (though not necessarily strictly within!) the
   * current frame.  Loop through the GC map iterator, getting the
   * address of each object pointer, adding them to the root locations
   * deque.<p>
   *
   * NOTE: Because of the callee save policy of the optimizing
   * compiler, references associated with a given frame may be in
   * callee stack frames (lower memory), <i>outside</i> the current
   * frame.  So the iterator may return locations that are outside the
   * frame being scanned.
   *
   * @param verbosity The level of verbosity to be used when
   * performing the scan.
   */
  private void scanFrameForObjects(int verbosity) {
    for (Address refaddr = iterator.getNextReferenceAddress();
         !refaddr.isZero();
         refaddr = iterator.getNextReferenceAddress()) {
      if (VALIDATE_REFS) checkReference(refaddr, verbosity);
      if (verbosity >= 4) dumpRef(refaddr, verbosity);
      reportDelayedRootEdge(trace, refaddr);
    }
  }

  /**
   * Identify all pointers into code pointers associated with a frame.
   * There are two cases to be considered: a) the instruction pointer
   * associated with each frame (stored in the thread's metadata for
   * the top frame and as a return address for all subsequent frames),
   * and b) local variables on the stack which happen to be pointers
   * to code.<p>
   *
   * FIXME: SB: Why is it that JNI frames are skipped when considering
   * top of stack frames, while boot image frames are skipped when
   * considering other frames.  Shouldn't they both be considered in
   * both cases?
   *
   * @param verbosity The level of verbosity to be used when
   * performing the scan.
   */
  private void processFrameForCode(int verbosity) {
    /* get the code object associated with this frame */
    ObjectReference code = ObjectReference.fromObject(compiledMethod.getEntryCodeArray());

    pushFrameIP(code, verbosity);
    scanFrameForCode(code);
  }

  /**
   * Push the instruction pointer associated with this frame onto the
   * code locations deque.<p>
   *
   * A stack frame represents an execution context, and thus has an
   * instruction pointer associated with it.  In the case of the top
   * frame, the instruction pointer is captured by the IP register,
   * which is preserved in the thread data structure at thread switch
   * time.  In the case of all non-top frames, the next instruction
   * pointer is stored as the return address for the <i>previous</i>
   * frame.<p>
   *
   * The address of the code pointer is pushed onto the code locations
   * deque along with the address of the code object into which it
   * points (both are required since the former is an internal
   * pointer).<p>
   *
   * The code pointers are updated later (after stack scanning) when
   * the code locations deque is processed. The pointer from RVMMethod
   * to the code object is not updated until after stack scanning, so
   * the pointer to the (uncopied) code object is available throughout
   * the stack scanning process, which enables interior pointer
   * offsets to be correctly computed.
   *
   * @param verbosity The level of verbosity to be used when
   * performing the scan.
   */
  private void pushFrameIP(ObjectReference code, int verbosity) {
    if (prevFp.isZero()) {  /* top of stack: IP in thread state */
      if (verbosity >= 3) {
        Log.write(" t.contextRegisters.ip    = ");
        Log.writeln(thread.getContextRegisters().ip);
        Log.write("*t.contextRegisters.iploc = ");
        Log.writeln(thread.getContextRegisters().getIPLocation().loadAddress());
      }
      /* skip native code, as it is not (cannot be) moved */
      if (compiledMethodType != CompiledMethod.JNI)
        processCodeLocation(code, initialIPLoc);
      else if (verbosity >= 4) {
        Log.writeln("GC Warning: SKIPPING return address for JNI code");
      }
    } else {  /* below top of stack: IP is return address, in prev frame */
      Address returnAddressLoc = Magic.getReturnAddressLocation(prevFp);
      Address returnAddress = returnAddressLoc.loadAddress();
      if (verbosity >= 4) {
        Log.write("--- Processing return address "); Log.write(returnAddress);
        Log.write(" located at "); Log.writeln(returnAddressLoc);
      }
      /* skip boot image code, as it is not (cannot be) moved */
      if (!DebugUtil.addrInBootImage(returnAddress))
        processCodeLocation(code, returnAddressLoc);
    }
  }

  /**
   * Scan this frame for internal code pointers.  The GC map iterator
   * is used to identify any local variables (stored on the stack)
   * which happen to be pointers into code.<p>
   *
   * @param code The code object associated with this frame.
   */
  private void scanFrameForCode(ObjectReference code) {
    iterator.reset();
    for (Address retaddrLoc = iterator.getNextReturnAddressAddress();
         !retaddrLoc.isZero();
         retaddrLoc = iterator.getNextReturnAddressAddress())
      processCodeLocation(code, retaddrLoc);
  }


  /**
   * AIX-specific code.<p>
   *
   * If we are scanning the stack of a thread that entered the VM via
   * a createVM or attachVM then the "bottom" of the stack had native
   * C frames instead of the usual java frames.  The JNIEnv for the
   * thread may still contain jniRefs that have been returned to the
   * native C code, but have not been reported for GC.  calling
   * getNextReferenceAddress without first calling setup... will
   * report the remaining jniRefs in the current "frame" of the
   * jniRefs stack.  (this should be the bottom frame)
   *
   * FIXME: SB: Why is this AIX specific?  Why depend on the
   * preprocessor?
   *
   */
  private void checkJNIBase() {
    if (VM.BuildForAix) {
      GCMapIterator iterator = iteratorGroup.getJniIterator();
      Address refaddr =  iterator.getNextReferenceAddress();
      while(!refaddr.isZero()) {
        reportDelayedRootEdge(trace, refaddr);
        refaddr = iterator.getNextReferenceAddress();
      }
    }
  }


  /***********************************************************************
   *
   * Debugging etc
   */

  /**
   * Assert that the stack is immovable.<p>
   *
   * Currently we do not allow stacks to be moved within the heap.  If
   * a stack contains native stack frames, then it is impossible for
   * us to safely move it.  Prior to the implementation of JNI, Jikes
   * RVM did allow the GC system to move thread stacks, and called a
   * special fixup routine, thread.fixupMovedStack to adjust all of
   * the special interior pointers (SP, FP).  If we implement split C
   * & Java stacks then we could allow the Java stacks to be moved,
   * but we can't move the native stack.
   */
  private void assertImmovableInCurrentCollection() {
    VM._assert(trace.willNotMoveInCurrentCollection(ObjectReference.fromObject(thread.getStack())));
    VM._assert(trace.willNotMoveInCurrentCollection(ObjectReference.fromObject(thread)));
    VM._assert(trace.willNotMoveInCurrentCollection(ObjectReference.fromObject(thread.getStack())));
    VM._assert(thread.getJNIEnv() == null || trace.willNotMoveInCurrentCollection(ObjectReference.fromObject(thread.getJNIEnv())));
    VM._assert(thread.getJNIEnv() == null || thread.getJNIEnv().refsArray() == null || trace.willNotMoveInCurrentCollection(ObjectReference.fromObject(thread.getJNIEnv().refsArray())));
    VM._assert(trace.willNotMoveInCurrentCollection(ObjectReference.fromObject(thread.getContextRegisters())));
    VM._assert(trace.willNotMoveInCurrentCollection(ObjectReference.fromObject(thread.getContextRegisters().gprs)));
    VM._assert(trace.willNotMoveInCurrentCollection(ObjectReference.fromObject(thread.getExceptionRegisters())));
    VM._assert(trace.willNotMoveInCurrentCollection(ObjectReference.fromObject(thread.getExceptionRegisters().gprs)));
  }

  /**
   * Print out the basic information associated with the top frame on
   * the stack.
   *
   * @param verbosity The level of verbosity to be used when
   * performing the scan.
   */
  private void dumpTopFrameInfo(int verbosity) {
    Log.write("   topFrame = "); Log.writeln(topFrame);
    Log.write("         ip = "); Log.writeln(ip);
    Log.write("         fp = "); Log.writeln(fp);
    Log.write("  registers.ip = "); Log.writeln(thread.getContextRegisters().ip);
    if (verbosity >= 3 && thread.getJNIEnv() != null)
      thread.getJNIEnv().dumpJniRefsStack();
  }

  /**
   * Print out information associated with a reference.
   *
   * @param refaddr The address of the reference in question.
   * @param verbosity The level of verbosity to be used when
   * performing the scan.
   */
  private void dumpRef(Address refaddr, int verbosity) {
    ObjectReference ref = refaddr.loadObjectReference();
    VM.sysWrite(refaddr);
    if (verbosity >= 5) {
      VM.sysWrite(":"); MemoryManager.dumpRef(ref);
    } else
      VM.sysWriteln();
  }

  /**
   * Check that a reference encountered during scanning is valid.  If
   * the reference is invalid, dump stack and die.
   *
   * @param refaddr The address of the reference in question.
   * @param verbosity The level of verbosity to be used when
   * performing the scan.
   */
  private void checkReference(Address refaddr, int verbosity) {
    ObjectReference ref = refaddr.loadObjectReference();
    if (!MemoryManager.validRef(ref)) {
      Log.writeln();
      Log.writeln("Invalid ref reported while scanning stack");
      printMethodHeader();
      Log.write(refaddr); Log.write(":"); Log.flush(); MemoryManager.dumpRef(ref);
      dumpStackFrame(verbosity);
      Log.writeln();
      Log.writeln("Dumping stack starting at frame with bad ref:");
      RVMThread.dumpStack(ip, fp);
      /* dump stack starting at top */
      Address top_ip = thread.getContextRegisters().getInnermostInstructionAddress();
      Address top_fp = thread.getContextRegisters().getInnermostFramePointer();
      RVMThread.dumpStack(top_ip, top_fp);
      Log.writeln("Failing iterators:");
      Offset offset = compiledMethod.getInstructionOffset(ip);
      iterator = iteratorGroup.selectIterator(compiledMethod);
      iterator.setupIterator(compiledMethod, offset, fp);
      int i=0;
      for (Address addr = iterator.getNextReferenceAddress();
           !addr.isZero();
           addr = iterator.getNextReferenceAddress()) {
        ObjectReference ref2 = addr.loadObjectReference();
        Log.write("Iterator "); Log.write(i++); Log.write(": "); Log.write(addr);
        Log.write(": "); Log.flush(); MemoryManager.dumpRef(ref2);
      }
      VM.sysFail("\n\nScanStack: Detected bad GC map; exiting RVM with fatal error");
    }
  }

  /**
   * Check that a reference encountered during scanning is valid.  If
   * the reference is invalid, dump stack and die.
   *
   * @param refaddr The address of the reference in question.
   */
  private static void checkReference(Address refaddr) {
    ObjectReference ref = refaddr.loadObjectReference();
    if (!MemoryManager.validRef(ref)) {
      Log.writeln();
      Log.writeln("Invalid ref reported while scanning stack");
      Log.write(refaddr); Log.write(":"); Log.flush(); MemoryManager.dumpRef(ref);
      Log.writeln();
      Log.writeln("Dumping stack:");
      RVMThread.dumpStack();
      VM.sysFail("\n\nScanStack: Detected bad GC map; exiting RVM with fatal error");
    }
  }
  /**
   * Print out the name of a method
   *
   * @param m The method to be printed
   */
  private void printMethod(RVMMethod m) {
    Log.write(m.getMemberRef().getType().getName().toByteArray()); Log.write(".");
    Log.write(m.getMemberRef().getName().toByteArray()); Log.write(" ");
    Log.write(m.getMemberRef().getDescriptor().toByteArray());
  }

  /**
   * Print out the method header for the method associated with the
   * current frame
   */
  private void printMethodHeader() {
    RVMMethod method = compiledMethod.getMethod();

    Log.write("\n--- METHOD (");
    Log.write(CompiledMethod.compilerTypeToString(compiledMethodType));
    Log.write(") ");
    if (method == null)
        Log.write("null method");
    else
        printMethod(method);
    Log.writeln();
    Log.write("--- fp = ");
    Log.write(fp);
    if (compiledMethod.isCompiled()) {
        ObjectReference codeBase = ObjectReference.fromObject(compiledMethod.getEntryCodeArray());
        Log.write("     code base = ");
        Log.write(codeBase);
        Log.write("     code offset = ");
        Log.writeln(ip.diff(codeBase.toAddress()));
        Log.write("     line number = ");
        Log.writeln(compiledMethod.findLineNumberForInstruction(ip.diff(codeBase.toAddress())));
    } else {
      Log.write("   Method is uncompiled - ip = ");
      Log.writeln(ip);
    }
  }

  /**
   * Dump the contents of a stack frame. Attempts to interpret each
   * word as an object reference
   *
   * @param verbosity The level of verbosity to be used when
   * performing the scan.
   */
  private void dumpStackFrame(int verbosity) {
    Address start,end;
    if (VM.BuildForIA32) {
      if (prevFp.isZero()) {
        start = fp.minus(20*BYTES_IN_ADDRESS);
        Log.writeln("--- 20 words of stack frame with fp = ", fp);
      } else {
        start = prevFp;    // start at callee fp
      }
      end = fp;            // end at fp
    } else {
      start = fp;                         // start at fp
      end = fp.loadAddress();   // stop at callers fp
    }

    for (Address loc = start; loc.LT(end); loc = loc.plus(BYTES_IN_ADDRESS)) {
      Log.write(loc); Log.write(" (");
      Log.write(loc.diff(start));
      Log.write("):   ");
      ObjectReference value = Selected.Plan.get().loadObjectReference(loc);
      Log.write(value);
      Log.write(" ");
      Log.flush();
      if (verbosity >= 4 && MemoryManager.objectInVM(value) && loc.NE(start) && loc.NE(end))
        MemoryManager.dumpRef(value);
      else
        Log.writeln();
    }
    Log.writeln();
  }
}
