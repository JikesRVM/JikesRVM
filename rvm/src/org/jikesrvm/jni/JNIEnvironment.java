/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.jni;

import org.jikesrvm.VM;
import org.jikesrvm.SizeConstants;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.runtime.Entrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.scheduler.RVMThread;
import org.jikesrvm.scheduler.Synchronization;
import org.vmmagic.pragma.Entrypoint;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Unpreemptible;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.AddressArray;
import org.vmmagic.unboxed.ObjectReference;
import org.vmmagic.unboxed.Offset;

/**
 * A JNIEnvironment is created for each Java thread.
 */
public final class JNIEnvironment implements SizeConstants {

  /**
   * initial size for JNI refs, later grow as needed
   */
  protected static final int JNIREFS_ARRAY_LENGTH = 100;

  /**
   * sometimes we put stuff onto the jnirefs array bypassing the code
   * that makes sure that it does not overflow (evil assembly code in the
   * jni stubs that would be painful to fix).  So, we keep some space
   * between the max value in JNIRefsMax and the actual size of the
   * array.  How much is governed by this field.
   */
  protected static final int JNIREFS_FUDGE_LENGTH = 50;

  /**
   * This is the shared JNI function table used by native code
   * to invoke methods in @link{JNIFunctions}.
   */
  public static FunctionTable JNIFunctions;

  /**
   * For the PowerOpenABI we need a linkage triple instead of just
   * a function pointer.
   * This is an array of such triples that matches JNIFunctions.
   */
  public static LinkageTripletTable LinkageTriplets;

  /**
   * This is the pointer to the shared JNIFunction table.
   * When we invoke a native method, we adjust the pointer we
   * pass to the native code such that this field is at offset 0.
   * In other words, we turn a JNIEnvironment into a JNIEnv*
   * by handing the native code an interior pointer to
   * this object that points directly to this field.
   */
  @SuppressWarnings({"unused", "UnusedDeclaration"})
  // used by native code
  @Entrypoint
  private final Address externalJNIFunctions =
      VM.BuildForPowerOpenABI ? Magic.objectAsAddress(LinkageTriplets) : Magic.objectAsAddress(JNIFunctions);

  /**
   * For saving processor register on entry to native,
   * to be restored on JNI call from native
   */
  @Entrypoint
  @Untraced
  protected RVMThread savedTRreg;

  /**
   * For saving JTOC register on entry to native,
   * to be restored on JNI call from native (only used on PowerPC)
   */
  @Entrypoint
  @Untraced
  private final Address savedJTOC = VM.BuildForPowerPC ? Magic.getTocPointer() : Address.zero();

  /**
   * When native code doesn't maintain a base pointer we can't chain
   * through the base pointers when walking the stack. This field
   * holds the basePointer on entry to the native code in such a case,
   * and is pushed onto the stack if we re-enter Java code (e.g. to
   * handle a JNI function). This field is currently only used on IA32.
   */
  @Entrypoint
  private Address basePointerOnEntryToNative = Address.fromIntSignExtend(0xF00BAAA1);

  /**
   * When transitioning between Java and C and back, we may want to stop a thread
   * returning into Java and executing mutator code when a GC is in progress.
   * When in C code, the C code may never return. In these situations we need a
   * frame pointer at which to begin scanning the stack. This field holds this
   * value. NB. these fields don't chain together on the stack as we walk through
   * native frames by knowing their return addresses are outside of our heaps
   */
  @Entrypoint
  protected Address JNITopJavaFP;

  /**
   * Currently pending exception (null if none)
   */
  @Entrypoint
  @Untraced
  protected Throwable pendingException;

  /**
   * We allocate JNIEnvironments in the immortal heap (so we
   * can hand them directly to C code).  Therefore, we must do some
   * kind of pooling of JNIEnvironment instances.
   * This is the free list of unused instances.
   */
  protected JNIEnvironment next;

  /**
   * Pool of available JNIEnvironments.
   */
  protected static JNIEnvironment pool;

  /**
   * true if the bottom stack frame is native,
   * such as thread for CreateJVM or AttachCurrentThread
   */
  protected boolean alwaysHasNativeFrame;

  /**
   * references passed to native code
   */
  @Entrypoint
  @Untraced
  public AddressArray JNIRefs;

  /**
   * Offset of current top ref in JNIRefs array
   */
  @Entrypoint
  public int JNIRefsTop;

  /**
   * Offset of end (last entry) of JNIRefs array
   */
  @Entrypoint
  protected int JNIRefsMax;

  /**
   * Previous frame boundary in JNIRefs array.
   * NB unused on IA32
   */
  @Entrypoint
  public int JNIRefsSavedFP;

  /**
   * Initialize a thread specific JNI environment.
   */
  protected void initializeState() {
    JNIRefs = AddressArray.create(JNIREFS_ARRAY_LENGTH + JNIREFS_FUDGE_LENGTH);
    JNIRefsTop = 0;
    JNIRefsSavedFP = 0;
    JNIRefsMax = (JNIREFS_ARRAY_LENGTH - 1) << LOG_BYTES_IN_ADDRESS;
    alwaysHasNativeFrame = false;
  }

  /*
   * Allocation and pooling
   */

  /**
   * Create a thread specific JNI environment.
   */
  public static synchronized JNIEnvironment allocateEnvironment() {
    JNIEnvironment env;
    if (pool != null) {
      env = pool;
      pool = pool.next;
    } else {
      env = new JNIEnvironment();
    }
    env.initializeState();
    return env;
  }

  /**
   * Return a thread-specific JNI environment; must be called as part of
   * terminating a thread that has a JNI environment allocated to it.
   * @param env the JNIEnvironment to deallocate
   */
  @Unpreemptible("Deallocate environment but may contend with environment being allocated")
  public static synchronized void deallocateEnvironment(JNIEnvironment env) {
    env.savedTRreg = null; /* make sure that we don't have a reference back to
                              the thread, once the thread has died. */
    env.next = pool;
    pool = env;
  }

  /*
   * accessor methods
   */
  @Uninterruptible
  public boolean hasNativeStackFrame() {
    return alwaysHasNativeFrame || JNIRefsTop != 0;
  }

  @Uninterruptible
  public Address topJavaFP() {
    return JNITopJavaFP;
  }

  @Uninterruptible
  public AddressArray refsArray() {
    return JNIRefs;
  }

  @Uninterruptible
  public int refsTop() {
    return JNIRefsTop;
  }

  @Uninterruptible
  public int savedRefsFP() {
    return JNIRefsSavedFP;
  }

  /**
   * Check push of reference can succeed
   * @param ref object to be pushed
   * @param canGrow can the JNI reference array be grown?
   */
  @Uninterruptible("May be called from uninterruptible code")
  @NoInline
  private void checkPush(Object ref, boolean canGrow) {
    final boolean debug=true;
    VM._assert(MemoryManager.validRef(ObjectReference.fromObject(ref)));
    if (JNIRefsTop < 0) {
      if (debug) {
        VM.sysWriteln("JNIRefsTop=", JNIRefsTop);
        VM.sysWriteln("JNIRefs.length=", JNIRefs.length());
      }
      VM.sysFail("unchecked push to negative offset!");
    }
    if ((JNIRefsTop >> LOG_BYTES_IN_ADDRESS) >= JNIRefs.length()) {
      if (debug) {
        VM.sysWriteln("JNIRefsTop=", JNIRefsTop);
        VM.sysWriteln("JNIRefs.length=", JNIRefs.length());
      }
      VM.sysFail("unchecked pushes exceeded fudge length!");
    }
    if (!canGrow) {
      if ((JNIRefsTop+BYTES_IN_ADDRESS) >= JNIRefsMax) {
        if (debug) {
          VM.sysWriteln("JNIRefsTop=", JNIRefsTop);
          VM.sysWriteln("JNIRefsMax=", JNIRefsMax);
        }
        VM.sysFail("unchecked push can't grow JNI refs!");
      }
    }
  }

  /**
   * Push a reference onto thread local JNIRefs stack.
   * To be used by JNI functions when returning a reference
   * back to JNI native C code.
   * @param ref the object to put on stack
   * @return offset of entry in JNIRefs stack
   */
  public int pushJNIRef(Object ref) {
    if (ref == null) {
      return 0;
    } else {
      if (VM.VerifyAssertions) checkPush(ref, true);
      JNIRefsTop += BYTES_IN_ADDRESS;
      if (JNIRefsTop >= JNIRefsMax) {
        JNIRefsMax *= 2;
        AddressArray newrefs = AddressArray.create((JNIRefsMax >> LOG_BYTES_IN_ADDRESS) + JNIREFS_FUDGE_LENGTH);
        for (int i = 0; i < JNIRefs.length(); i++) {
          newrefs.set(i, JNIRefs.get(i));
        }
        JNIRefs = newrefs;
      }
      JNIRefs.set(JNIRefsTop >> LOG_BYTES_IN_ADDRESS, Magic.objectAsAddress(ref));
      return JNIRefsTop;
    }
  }

  /**
   * Push a JNI ref, used on entry to JNI
   * NB only used for Intel
   * @param ref reference to place on stack or value of saved frame pointer
   * @param isRef false if the reference isn't a frame pointer
   */
  @Uninterruptible("Encoding arguments on stack that won't be seen by GC")
  @Inline
  private int uninterruptiblePushJNIRef(Address ref, boolean isRef) {
    if (isRef && ref.isZero()) {
      return 0;
    } else {
      if (VM.VerifyAssertions) checkPush(isRef ? Magic.addressAsObject(ref) : null, false);
      // we count all slots so that releasing them is straight forward
      JNIRefsTop += BYTES_IN_ADDRESS;
      // ensure null is always seen as slot zero
      JNIRefs.set(JNIRefsTop >> LOG_BYTES_IN_ADDRESS, Magic.objectAsAddress(ref));
      return JNIRefsTop;
    }
  }

  /**
   * Save data and perform necessary conversions for entry into JNI.
   * NB only used for Intel.
   *
   * @param encodedReferenceOffsets
   *          bit mask marking which elements on the stack hold objects that need
   *          encoding as JNI ref identifiers
   */
  @Uninterruptible("Objects on the stack won't be recognized by GC, therefore don't allow GC")
  @Entrypoint
  public void entryToJNI(int encodedReferenceOffsets) {
    // Save processor
    savedTRreg = Magic.getThreadRegister();

    // Save frame pointer of calling routine, once so that native stack frames
    // are skipped and once for use by GC
    Address callersFP = Magic.getCallerFramePointer(Magic.getFramePointer());
    basePointerOnEntryToNative = callersFP; // NB old value saved on call stack
    JNITopJavaFP = callersFP;

    // Save current JNI ref stack pointer
    int oldJNIRefsSavedFP = JNIRefsSavedFP;
    JNIRefsSavedFP = JNIRefsTop;
    uninterruptiblePushJNIRef(Address.fromIntSignExtend(oldJNIRefsSavedFP), false);

    // Convert arguments on stack from objects to JNI references
    Address fp = Magic.getFramePointer();
    Offset argOffset = Offset.fromIntSignExtend(5*BYTES_IN_ADDRESS);
    fp.store(uninterruptiblePushJNIRef(fp.loadAddress(argOffset),true), argOffset);
    while (encodedReferenceOffsets != 0) {
      argOffset = argOffset.plus(BYTES_IN_ADDRESS);
      if ((encodedReferenceOffsets & 1) != 0) {
        fp.store(uninterruptiblePushJNIRef(fp.loadAddress(argOffset), true), argOffset);
      }
      encodedReferenceOffsets >>>= 1;
    }
    // Transition processor from IN_JAVA to IN_JNI
    if(!Synchronization.tryCompareAndSwap(Magic.getThreadRegister(),
        Entrypoints.execStatusField.getOffset(), RVMThread.IN_JAVA, RVMThread.IN_JNI)) {
      RVMThread.enterJNIBlockedFromCallIntoNative();
    }
  }

  /**
   * Restore data, throw pending exceptions or convert return value for exit
   * from JNI. NB only used for Intel.
   *
   * @param offset
   *          offset into JNI reference tables of result
   * @return Object encoded by offset or null if offset is 0
   */
  @Unpreemptible("Don't allow preemption when we're not in a sane state. " +
  "Code can throw exceptions so not uninterruptible.")
  @Entrypoint
  public Object exitFromJNI(int offset) {
    // Transition processor from IN_JNI to IN_JAVA
    if(!Synchronization.tryCompareAndSwap(Magic.getThreadRegister(),
        Entrypoints.execStatusField.getOffset(), RVMThread.IN_JNI, RVMThread.IN_JAVA)) {
      RVMThread.leaveJNIBlockedFromCallIntoNative();
      // NOTE: we can never assert IN_JAVA here as we would like, as requests to block, etc. cause
      // a state change!
    }
    // Restore JNI ref top and saved frame pointer
    JNIRefsTop = JNIRefsSavedFP;
    if (JNIRefsTop > 0) {
      JNIRefsSavedFP = JNIRefs.get((JNIRefsTop >> LOG_BYTES_IN_ADDRESS) + 1).toInt();
    }

    // Throw and clear any pending exceptions
    Throwable pe = pendingException;
    if (pe != null) {
      pendingException = null;
      RuntimeEntrypoints.athrow(pe);
      // NB. we will never reach here
    }
    // Lookup result
    Object result;
    if (offset == 0) {
      result = null;
    } else if (offset < 0) {
      result = JNIGlobalRefTable.ref(offset);
    } else {
      result = Magic.addressAsObject(JNIRefs.get(offset >> LOG_BYTES_IN_ADDRESS));
    }
    return result;
  }

  /**
   * Get a reference from the JNIRefs stack.
   * @param offset in JNIRefs stack
   * @return reference at that offset
   */
  public Object getJNIRef(int offset) {
    if (offset > JNIRefsTop) {
      VM.sysWrite("JNI ERROR: getJNIRef for illegal offset > TOP, ");
      VM.sysWrite(offset);
      VM.sysWrite("(top is ");
      VM.sysWrite(JNIRefsTop);
      VM.sysWrite(")\n");
      RVMThread.dumpStack();
      return null;
    }
    if (offset < 0) {
      return JNIGlobalRefTable.ref(offset);
    } else {
      return Magic.addressAsObject(JNIRefs.get(offset >> LOG_BYTES_IN_ADDRESS));
    }
  }

  /**
   * Remove a reference from the JNIRefs stack.
   * @param offset in JNIRefs stack
   */
  public void deleteJNIRef(int offset) {
    if (offset > JNIRefsTop) {
      VM.sysWrite("JNI ERROR: getJNIRef for illegal offset > TOP, ");
      VM.sysWrite(offset);
      VM.sysWrite("(top is ");
      VM.sysWrite(JNIRefsTop);
      VM.sysWrite(")\n");
    }

    JNIRefs.set(offset >> LOG_BYTES_IN_ADDRESS, Address.zero());

    if (offset == JNIRefsTop) JNIRefsTop -= BYTES_IN_ADDRESS;
  }

  /**
   * Dump the JNIRefs stack to the sysWrite stream
   */
  @Uninterruptible
  public void dumpJniRefsStack() {
    int jniRefOffset = JNIRefsTop;
    VM.sysWrite("\n* * dump of JNIEnvironment JniRefs Stack * *\n");
    VM.sysWrite("* JNIRefs = ");
    VM.sysWrite(Magic.objectAsAddress(JNIRefs));
    VM.sysWrite(" * JNIRefsTop = ");
    VM.sysWrite(JNIRefsTop);
    VM.sysWrite(" * JNIRefsSavedFP = ");
    VM.sysWrite(JNIRefsSavedFP);
    VM.sysWrite(".\n*\n");
    while (jniRefOffset >= 0) {
      VM.sysWrite(jniRefOffset);
      VM.sysWrite(" ");
      VM.sysWrite(Magic.objectAsAddress(JNIRefs).plus(jniRefOffset));
      VM.sysWrite(" ");
      MemoryManager.dumpRef(JNIRefs.get(jniRefOffset >> LOG_BYTES_IN_ADDRESS).toObjectReference());
      jniRefOffset -= BYTES_IN_ADDRESS;
    }
    VM.sysWrite("\n* * end of dump * *\n");
  }

  /**
   * Record an exception as pending so that it will be delivered on the return
   * to the Java caller;  clear the exception by recording null
   * @param e  An exception or error
   */
  public void recordException(Throwable e) {
    // don't overwrite the first exception except to clear it
    if (pendingException == null || e == null) {
      pendingException = e;
    }
  }

  /**
   * @return the pending exception
   */
  public Throwable getException() {
    return pendingException;
  }

  /**
   * Initialize the array of JNI functions.
   * This function is called during bootimage writing.
   */
  public static void initFunctionTable(FunctionTable functions) {
    JNIFunctions = functions;
    if (VM.BuildForPowerOpenABI) {
      // Allocate the linkage triplets in the bootimage too (so they won't move)
      LinkageTriplets = LinkageTripletTable.allocate(functions.length());
      for (int i = 0; i < functions.length(); i++) {
        LinkageTriplets.set(i, AddressArray.create(3));
      }
    }
  }

  /**
   * Initialization required during VM booting; only does something if
   * we are on a platform that needs linkage triplets.
   */
  public static void boot() {
    if (VM.BuildForPowerOpenABI) {
      // fill in the TOC and IP entries for each linkage triplet
      for (int i = 0; i < JNIFunctions.length(); i++) {
        AddressArray triplet = LinkageTriplets.get(i);
        triplet.set(1, Magic.getTocPointer());
        triplet.set(0, Magic.objectAsAddress(JNIFunctions.get(i)));
      }
    }
  }
}
