/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;
import java.lang.reflect.*;

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
public abstract class VM_JNIGenericEnvironment implements VM_RegisterConstants,
							  VM_SizeConstants {

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
   * Pointer to the JNIFunctions array 
   */
  protected VM_Address JNIEnvAddress; 

  /** 
   * For saving thread index register on entry to native, 
   * to be restored on JNI call from native
   */
  protected int savedTIreg;         

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
   * address of end (last entry) of JNIRefs array
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
   * Create a thread specific JNI environment.
   */
  public VM_JNIGenericEnvironment() {
    JNIRefs = VM_AddressArray.create(JNIREFS_ARRAY_LENGTH + JNIREFS_FUDGE_LENGTH);
    JNIRefsTop = 0;
    JNIRefsSavedFP = 0;
    JNIRefsMax = (JNIREFS_ARRAY_LENGTH - 1) << LOG_BYTES_IN_ADDRESS;
    alwaysHasNativeFrame = false;
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

  public final void setTopJavaFP(VM_Address topJavaFP) throws VM_PragmaUninterruptible  {
    JNITopJavaFP = topJavaFP;
  }

  public final void setSavedPRreg(VM_Processor vp) throws VM_PragmaUninterruptible  {
    savedPRreg = vp;
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
   * Get a reference from the JNIRefs stack
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
   * Remove a reference from the JNIRefs stack
   * @param offset in JNIRefs stack
   */
  public final void deleteJNIRef( int offset ) {
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
    if (pendingException==null || e==null)
      pendingException = e;
  }

  /** 
   * @return the pending exception
   */
  public final Throwable getException() {
    return pendingException;
  }

  /**
   * @return the address of the JNIFunctions array 
   */
  public final VM_Address getJNIenvAddress() {
    return JNIEnvAddress;
  }

  /*
   * Utility functions called from VM_JNIFunction
   * (cannot be placed in VM_JNIFunction because methods there are specially compiled
   * to be called from native)
   */


  /**
   * Given an address in C that points to a null-terminated string,
   * create a new Java byte[] with a copy of the string
   * @param stringAddress an address in C space for a string
   * @return a new Java byte[]
   */
  static byte[] createByteArrayFromC(VM_Address stringAddress) {
    // scan the memory for the null termination of the string
    int length = 0;
    for (VM_Address addr = stringAddress; true; addr = addr.add(4)) {
      int word = VM_Magic.getMemoryInt(addr);
      int byte0, byte1, byte2, byte3;
      if (VM.LittleEndian) {
	byte3 = ((word >> 24) & 0xFF);
	byte2 = ((word >> 16) & 0xFF);
	byte1 = ((word >> 8) & 0xFF);
	byte0 = (word & 0xFF);
      } else {
	byte0 = ((word >> 24) & 0xFF);
	byte1 = ((word >> 16) & 0xFF);
	byte2 = ((word >> 8) & 0xFF);
	byte3 = (word & 0xFF);
      }
      if (byte0==0)
	break;
      length++;
      if (byte1==0) 
	break;
      length++;
      if (byte2==0)
	break;
      length++;
      if (byte3==0)
	break;
      length++;
    }

   byte[] contents = new byte[length];
   VM_Memory.memcopy(VM_Magic.objectAsAddress(contents), stringAddress, length);
   
   return contents;
  }

  /**
   * Given an address in C that points to a null-terminated string,
   * create a new Java String with a copy of the string
   * @param stringAddress an address in C space for a string
   * @return a new Java String
   */
  static String createStringFromC(VM_Address stringAddress) {
    return new String(createByteArrayFromC(stringAddress));
  }
}
