/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.jni;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;

/**
 * Aspects of the JNIEnvironment that are platform independent.
 * An JNIEnvironment is created for each Java thread.
 * 
 * Platform dependent portions of the environment are found in the
 * subclass VM_JNIEnvironment.
 *
 * @author Dave Grove
 * @author Ton Ngo
 * @author Steve Smith 
 */
public abstract class VM_JNIGenericEnvironment implements VM_SizeConstants {

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
   * For saving processor register on entry to native, 
   * to be restored on JNI call from native
   */
  protected VM_Processor savedPRreg; 

  /**
   * true if the bottom stack frame is native, 
   * such as thread for CreateJVM or AttachCurrentThread
   */
  protected boolean alwaysHasNativeFrame;  

  /**
   * references passed to native code
   */
  protected VM_AddressArray JNIRefs; 

  /**
   * address of current top ref in JNIRefs array   
   */
  protected int JNIRefsTop;

  /**
   * offset of end (last entry) of JNIRefs array
   */
  protected int JNIRefsMax;   

  /**
   * previous frame boundary in JNIRefs array
   */
  protected int JNIRefsSavedFP;

  /**
   * Top java frame when in C frames on top of the stack
   */
  protected VM_Address JNITopJavaFP;

  /**
   * Currently pending exception (null if none)
   */
  protected Throwable pendingException;

  /**
   * We allocate VM_JNIEnvironments in the immortal heap (so we 
   * can hand them directly to C code).  Therefore, we must do some
   * kind of pooling of VM_JNIEnvironment instances.
   * This is the free list of unused instances
   */
  protected VM_JNIEnvironment next;

  /**
   * Pool of available VM_JNIEnvironments
   */
  protected static VM_JNIEnvironment pool;

  /**
   * Initialize a thread specific JNI environment.
   */
  protected void initializeState() {
    JNIRefs = VM_AddressArray.create(JNIREFS_ARRAY_LENGTH + JNIREFS_FUDGE_LENGTH);
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
  public static synchronized VM_JNIEnvironment allocateEnvironment() {
    VM_JNIEnvironment env;
    if (pool != null) {
      env = pool;
      pool = pool.next;
    } else {
      env = new VM_JNIEnvironment();
    }
    env.initializeState();
    return env;
  }

  public static synchronized void deallocateEnvironment(VM_JNIEnvironment env) {
    env.next = pool;
    pool = env;
  }

  /*
   * accessor methods
   */
  public final boolean hasNativeStackFrame() throws VM_PragmaUninterruptible  {
    return alwaysHasNativeFrame || JNIRefsTop != 0;
  }

  public final VM_Address topJavaFP() throws VM_PragmaUninterruptible  {
    return JNITopJavaFP;
  }

  public final VM_AddressArray refsArray() throws VM_PragmaUninterruptible {
    return JNIRefs;
  }

  public final int refsTop() throws VM_PragmaUninterruptible  {
    return JNIRefsTop;
  }
   
  public final int savedRefsFP() throws VM_PragmaUninterruptible  {
    return JNIRefsSavedFP;
  }

  /**
   * Push a reference onto thread local JNIRefs stack.   
   * To be used by JNI functions when returning a reference 
   * back to JNI native C code.
   * @param ref the object to put on stack
   * @return offset of entry in JNIRefs stack
   */
  public final int pushJNIRef(Object ref) {
    if (ref == null)
      return 0;

    if (VM.VerifyAssertions) {
      VM._assert(MM_Interface.validRef(VM_Magic.objectAsAddress(ref)));
    }

    if ((JNIRefsTop >>> LOG_BYTES_IN_ADDRESS) >= JNIRefs.length()) {
      VM.sysFail("unchecked pushes exceeded fudge length!");
    }

    JNIRefsTop += BYTES_IN_ADDRESS;

    if (JNIRefsTop >= JNIRefsMax) {
      JNIRefsMax *= 2;
      VM_AddressArray newrefs = VM_AddressArray.create((JNIRefsMax>>>LOG_BYTES_IN_ADDRESS) + JNIREFS_FUDGE_LENGTH);
      for(int i = 0; i<JNIRefs.length(); i++) {
	newrefs.set(i, JNIRefs.get(i));
      }
      JNIRefs = newrefs;
    }

    JNIRefs.set(JNIRefsTop >>> LOG_BYTES_IN_ADDRESS, VM_Magic.objectAsAddress(ref));
    return JNIRefsTop;
  }

  /**
   * Get a reference from the JNIRefs stack.
   * @param offset in JNIRefs stack
   * @return reference at that offset
   */
  public final Object getJNIRef(int offset) {
    if (offset > JNIRefsTop) {
      VM.sysWrite("JNI ERROR: getJNIRef for illegal offset > TOP, ");
      VM.sysWrite(offset); 
      VM.sysWrite("(top is ");
      VM.sysWrite(JNIRefsTop);
      VM.sysWrite(")\n");
      return null;
    }
    if (offset < 0)
      return VM_JNIGlobalRefTable.ref(offset);
    else
      return VM_Magic.addressAsObject(JNIRefs.get(offset >>> LOG_BYTES_IN_ADDRESS));
  }

  /**
   * Remove a reference from the JNIRefs stack.
   * @param offset in JNIRefs stack
   */
  public final void deleteJNIRef(int offset) {
    if (offset > JNIRefsTop) {
      VM.sysWrite("JNI ERROR: getJNIRef for illegal offset > TOP, ");
      VM.sysWrite(offset); 
      VM.sysWrite("(top is ");
      VM.sysWrite(JNIRefsTop);
      VM.sysWrite(")\n");
    }
    
    JNIRefs.set(offset >>> LOG_BYTES_IN_ADDRESS, VM_Address.zero());

    if (offset == JNIRefsTop) JNIRefsTop -= BYTES_IN_ADDRESS;
  }

  /**
   * Dump the JNIRefs stack to the sysWrite stream
   */
  public final void dumpJniRefsStack () throws VM_PragmaUninterruptible {
    int jniRefOffset = JNIRefsTop;
    VM.sysWrite("\n* * dump of JNIEnvironment JniRefs Stack * *\n");
    VM.sysWrite("* JNIRefs = ");
    VM.sysWrite(VM_Magic.objectAsAddress(JNIRefs));
    VM.sysWrite(" * JNIRefsTop = ");
    VM.sysWrite(JNIRefsTop);
    VM.sysWrite(" * JNIRefsSavedFP = ");
    VM.sysWrite(JNIRefsSavedFP);
    VM.sysWrite(".\n*\n");
    while (jniRefOffset>= 0) {
      VM.sysWrite(jniRefOffset);
      VM.sysWrite(" ");
      VM.sysWrite(VM_Magic.objectAsAddress(JNIRefs).add(jniRefOffset));
      VM.sysWrite(" ");
      MM_Interface.dumpRef(JNIRefs.get(jniRefOffset >>> LOG_BYTES_IN_ADDRESS));
      jniRefOffset -= BYTES_IN_ADDRESS;
    }
    VM.sysWrite("\n* * end of dump * *\n");
  }

  /**
   * Record an exception as pending so that it will be delivered on the return
   * to the Java caller;  clear the exception by recording null
   * @param an exception or error
   */
  public final void recordException(Throwable e) {
    // don't overwrite the first exception except to clear it
    if (pendingException==null || e==null) {
      pendingException = e;
    }
  }

  /** 
   * @return the pending exception
   */
  public final Throwable getException() {
    return pendingException;
  }
}
