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
public abstract class VM_JNIGenericEnvironment implements VM_JNIConstants,
							  VM_RegisterConstants,
							  VM_SizeConstants {

  protected static boolean initialized = false;
  protected static String[] names;

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
  protected int[] JNIRefs; 

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
   * Saved context for thread attached to external pthread.  This context is
   * saved by the JNIService thread and points to the point in JNIStartUp thread
   * where it yields to the queue in the native VM_Processor.
   * When DetachCurrentThread is called, the JNIService thread restores this context 
   * to allow the thread to run VM_Thread.terminate on its original stack.
   */
  protected VM_Registers savedContextForTermination;

  /**
   * Create a thread specific JNI environment.
   */
  public VM_JNIGenericEnvironment() {
    JNIRefs = new int[JNIREFS_ARRAY_LENGTH + JNIREFS_FUDGE_LENGTH];
    JNIRefs[0] = 0;                       // 0 entry for bottom of stack
    JNIRefsTop = 0;
    JNIRefsSavedFP = 0;
    JNIRefsMax = (JNIREFS_ARRAY_LENGTH - 1) * 4;   // byte offset to last entry
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

  public final int[] refsArray() throws VM_PragmaUninterruptible {
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

  public final void setSavedTerminationContext(VM_Registers regs) throws VM_PragmaUninterruptible  {
    savedContextForTermination = regs;
  }

  public final VM_Registers savedTerminationContext() throws VM_PragmaUninterruptible  {
    return savedContextForTermination;
  }

  public final void setFromNative(VM_Address topJavaFP, 
				  VM_Processor nativeVP, 
				  int threadId) throws VM_PragmaUninterruptible  {
    alwaysHasNativeFrame = true;
    JNITopJavaFP = topJavaFP;
    savedPRreg = nativeVP;
    savedTIreg = threadId;
  }

  /**
   * Push a reference onto thread local JNIRefs stack.   
   * To be used by JNI functions when returning a reference 
   * back to JNI native C code.
   * @param ref the object to put on stack
   * @return offset of entry in JNIRefs stack
   */
  public final int pushJNIRef( Object ref ) {
    if (ref == null)
      return 0;

    if (VM.VerifyAssertions) {
      VM._assert(MM_Interface.validRef( VM_Magic.objectAsAddress(ref)));
    }

    if (JNIRefsTop>>2 >= JNIRefs.length) {
      VM.sysFail("unchecked pushes exceeded fudge length!");
    }

    JNIRefsTop += 4;
    if (JNIRefsTop >= JNIRefsMax) {
      JNIRefsMax *= 2;
      int[] newrefs = new int[ (JNIRefsMax>>2) + JNIREFS_FUDGE_LENGTH ];
      for(int i = 0; i < JNIRefs.length; i++) {
	newrefs[i] = JNIRefs[i];
      }
      for(int i = JNIRefs.length; i < newrefs.length; i++) {
	newrefs[i] = 0;
      }
      JNIRefs = newrefs;
    }
    JNIRefs[JNIRefsTop >> 2] = VM_Magic.objectAsAddress(ref).toInt();
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
      return VM_Magic.addressAsObject(VM_Address.fromInt(JNIRefs[ offset>>2 ]));
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
    
    JNIRefs[offset>>2] = 0;

    if (offset == JNIRefsTop) JNIRefsTop -= 4;
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
    while (jniRefOffset >= 0) {
      VM.sysWrite(jniRefOffset);
      VM.sysWrite(" ");
      VM.sysWrite(VM_Magic.objectAsAddress(JNIRefs).add(jniRefOffset));
      VM.sysWrite(" ");
      MM_Interface.dumpRef(VM_Address.fromInt(JNIRefs[jniRefOffset >> 2]));
      jniRefOffset -= 4;
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

  /**
   * Get the JNI index for a function name.
   * @param functionName a JNI function name
   * @return the index for this function, -1 if not found
   */
  protected static int indexOf(String functionName) {
    for (int i=0; i<FUNCTIONCOUNT; i++) {
      if (names[i].equals(functionName))
	return i;
    }
    return -1;
  }

  protected static String[] setNames() {
    names = new String[FUNCTIONCOUNT];
    names[0]                             = "undefined";
    names[RESERVED0]                     = "reserved0";
    names[RESERVED1]                     = "reserved1";	  
    names[RESERVED2]                     = "reserved2";	  
    names[RESERVED3]                     = "reserved3";
    names[GETVERSION]                    = "GetVersion";
    names[DEFINECLASS]                   = "DefineClass";
    names[FINDCLASS]                     = "FindClass";
    names[FROMREFLECTEDMETHOD]         	 = "FromReflectedMethod"; //  JDK1.2, #7      
    names[FROMREFLECTEDFIELD]          	 = "FromReflectedField";  //  JDK1.2, #8      
    names[TOREFLECTEDMETHOD]           	 = "ToReflectedMethod";   //  JDK1.2, #9      
    names[GETSUPERCLASS]                 = "GetSuperclass";
    names[ISASSIGNABLEFROM]              = "IsAssignableFrom";
    names[TOREFLECTEDFIELD]            	 = "ToReflectedField";    //  JDK1.2, #12      
    names[THROW]                         = "Throw";
    names[THROWNEW]                      = "ThrowNew";
    names[EXCEPTIONOCCURRED]             = "ExceptionOccurred";
    names[EXCEPTIONDESCRIBE]             = "ExceptionDescribe";
    names[EXCEPTIONCLEAR]                = "ExceptionClear";
    names[FATALERROR]                    = "FatalError";
    names[PUSHLOCALFRAME]              	 = "PushLocalFrame";      //  JDK1.2, #19      
    names[POPLOCALFRAME]               	 = "PopLocalFrame";       //  JDK1.2, #20      
    names[NEWGLOBALREF]                  = "NewGlobalRef";
    names[DELETEGLOBALREF]               = "DeleteGlobalRef";
    names[DELETELOCALREF]                = "DeleteLocalRef";
    names[ISSAMEOBJECT]                  = "IsSameObject";
    names[NEWLOCALREF]                 	 = "NewLocalRef";         //  JDK1.2, #25      
    names[ENSURELOCALCAPACITY]         	 = "EnsureLocalCapacity"; //  JDK1.2, #26   
    names[ALLOCOBJECT]                   = "AllocObject";
    names[NEWOBJECT]                     = "NewObject";	  
    names[NEWOBJECTV]                    = "NewObjectV";	  
    names[NEWOBJECTA]                    = "NewObjectA";	  
    names[GETOBJECTCLASS]                = "GetObjectClass";	  
    names[ISINSTANCEOF]                  = "IsInstanceOf";	  
    names[GETMETHODID]                   = "GetMethodID";	  
    names[CALLOBJECTMETHOD]              = "CallObjectMethod";	  
    names[CALLOBJECTMETHODV]             = "CallObjectMethodV";	  
    names[CALLOBJECTMETHODA]             = "CallObjectMethodA";	  
    names[CALLBOOLEANMETHOD]             = "CallBooleanMethod";	  
    names[CALLBOOLEANMETHODV]            = "CallBooleanMethodV";	  
    names[CALLBOOLEANMETHODA]            = "CallBooleanMethodA";	  
    names[CALLBYTEMETHOD]                = "CallByteMethod";	  
    names[CALLBYTEMETHODV]               = "CallByteMethodV";	  
    names[CALLBYTEMETHODA]               = "CallByteMethodA";	  
    names[CALLCHARMETHOD]                = "CallCharMethod";	  
    names[CALLCHARMETHODV]               = "CallCharMethodV";	  
    names[CALLCHARMETHODA]               = "CallCharMethodA";	  
    names[CALLSHORTMETHOD]               = "CallShortMethod";	  
    names[CALLSHORTMETHODV]              = "CallShortMethodV";	  
    names[CALLSHORTMETHODA]              = "CallShortMethodA";	  
    names[CALLINTMETHOD]                 = "CallIntMethod";	  
    names[CALLINTMETHODV]                = "CallIntMethodV";	  
    names[CALLINTMETHODA]                = "CallIntMethodA";	  
    names[CALLLONGMETHOD]                = "CallLongMethod";  
    names[CALLLONGMETHODV]               = "CallLongMethodV";	  
    names[CALLLONGMETHODA]               = "CallLongMethodA";	  
    names[CALLFLOATMETHOD]               = "CallFloatMethod";	  
    names[CALLFLOATMETHODV]              = "CallFloatMethodV";	  
    names[CALLFLOATMETHODA]              = "CallFloatMethodA";	  
    names[CALLDOUBLEMETHOD]              = "CallDoubleMethod";	  
    names[CALLDOUBLEMETHODV]             = "CallDoubleMethodV";	  
    names[CALLDOUBLEMETHODA]             = "CallDoubleMethodA";	  
    names[CALLVOIDMETHOD]                = "CallVoidMethod";  
    names[CALLVOIDMETHODV]               = "CallVoidMethodV";	  
    names[CALLVOIDMETHODA]               = "CallVoidMethodA";
    names[CALLNONVIRTUALOBJECTMETHOD]    = "CallNonvirtualObjectMethod";
    names[CALLNONVIRTUALOBJECTMETHODV]   = "CallNonvirtualObjectMethodV";
    names[CALLNONVIRTUALOBJECTMETHODA]   = "CallNonvirtualObjectMethodA";
    names[CALLNONVIRTUALBOOLEANMETHOD]   = "CallNonvirtualBooleanMethod";
    names[CALLNONVIRTUALBOOLEANMETHODV]  = "CallNonvirtualBooleanMethodV";
    names[CALLNONVIRTUALBOOLEANMETHODA]  = "CallNonvirtualBooleanMethodA";
    names[CALLNONVIRTUALBYTEMETHOD]      = "CallNonvirtualByteMethod";
    names[CALLNONVIRTUALBYTEMETHODV]     = "CallNonvirtualByteMethodV";
    names[CALLNONVIRTUALBYTEMETHODA]     = "CallNonvirtualByteMethodA";
    names[CALLNONVIRTUALCHARMETHOD]      = "CallNonvirtualCharMethod";
    names[CALLNONVIRTUALCHARMETHODV]     = "CallNonvirtualCharMethodV";
    names[CALLNONVIRTUALCHARMETHODA]     = "CallNonvirtualCharMethodA";
    names[CALLNONVIRTUALSHORTMETHOD]     = "CallNonvirtualShortMethod";	  
    names[CALLNONVIRTUALSHORTMETHODV]    = "CallNonvirtualShortMethodV";	  
    names[CALLNONVIRTUALSHORTMETHODA]    = "CallNonvirtualShortMethodA";	  
    names[CALLNONVIRTUALINTMETHOD]       = "CallNonvirtualIntMethod";	  
    names[CALLNONVIRTUALINTMETHODV]      = "CallNonvirtualIntMethodV";	  
    names[CALLNONVIRTUALINTMETHODA]      = "CallNonvirtualIntMethodA";	  
    names[CALLNONVIRTUALLONGMETHOD]      = "CallNonvirtualLongMethod";	  
    names[CALLNONVIRTUALLONGMETHODV]     = "CallNonvirtualLongMethodV";	  
    names[CALLNONVIRTUALLONGMETHODA]     = "CallNonvirtualLongMethodA";	  
    names[CALLNONVIRTUALFLOATMETHOD]     = "CallNonvirtualFloatMethod";	  
    names[CALLNONVIRTUALFLOATMETHODV]    = "CallNonvirtualFloatMethodV";	  
    names[CALLNONVIRTUALFLOATMETHODA]    = "CallNonvirtualFloatMethodA";	  
    names[CALLNONVIRTUALDOUBLEMETHOD]    = "CallNonvirtualDoubleMethod";	  
    names[CALLNONVIRTUALDOUBLEMETHODV]   = "CallNonvirtualDoubleMethodV";	  
    names[CALLNONVIRTUALDOUBLEMETHODA]   = "CallNonvirtualDoubleMethodA";	  
    names[CALLNONVIRTUALVOIDMETHOD]      = "CallNonvirtualVoidMethod";	  
    names[CALLNONVIRTUALVOIDMETHODV]     = "CallNonvirtualVoidMethodV";	  
    names[CALLNONVIRTUALVOIDMETHODA]     = "CallNonvirtualVoidMethodA";	  
    names[GETFIELDID]                    = "GetFieldID";
    names[GETOBJECTFIELD]                = "GetObjectField";
    names[GETBOOLEANFIELD]               = "GetBooleanField";
    names[GETBYTEFIELD]                  = "GetByteField";
    names[GETCHARFIELD]                  = "GetCharField";
    names[GETSHORTFIELD]                 = "GetShortField";
    names[GETINTFIELD]                   = "GetIntField";
    names[GETLONGFIELD]                  = "GetLongField";
    names[GETFLOATFIELD]                 = "GetFloatField";
    names[GETDOUBLEFIELD]                = "GetDoubleField";
    names[SETOBJECTFIELD]                = "SetObjectField";
    names[SETBOOLEANFIELD]               = "SetBooleanField";
    names[SETBYTEFIELD]                  = "SetByteField";
    names[SETCHARFIELD]                  = "SetCharField";
    names[SETSHORTFIELD]                 = "SetShortField";
    names[SETINTFIELD]                   = "SetIntField";
    names[SETLONGFIELD]                  = "SetLongField";
    names[SETFLOATFIELD]                 = "SetFloatField";
    names[SETDOUBLEFIELD]                = "SetDoubleField";
    names[GETSTATICMETHODID]             = "GetStaticMethodID";
    names[CALLSTATICOBJECTMETHOD]        = "CallStaticObjectMethod";
    names[CALLSTATICOBJECTMETHODV]       = "CallStaticObjectMethodV";
    names[CALLSTATICOBJECTMETHODA]       = "CallStaticObjectMethodA";
    names[CALLSTATICBOOLEANMETHOD]       = "CallStaticBooleanMethod";	  
    names[CALLSTATICBOOLEANMETHODV]      = "CallStaticBooleanMethodV";	  
    names[CALLSTATICBOOLEANMETHODA]      = "CallStaticBooleanMethodA";	  
    names[CALLSTATICBYTEMETHOD]          = "CallStaticByteMethod";	  
    names[CALLSTATICBYTEMETHODV]         = "CallStaticByteMethodV";	  
    names[CALLSTATICBYTEMETHODA]         = "CallStaticByteMethodA";	  
    names[CALLSTATICCHARMETHOD]          = "CallStaticCharMethod";	  
    names[CALLSTATICCHARMETHODV]         = "CallStaticCharMethodV";	  
    names[CALLSTATICCHARMETHODA]         = "CallStaticCharMethodA";	  
    names[CALLSTATICSHORTMETHOD]         = "CallStaticShortMethod";	  
    names[CALLSTATICSHORTMETHODV]        = "CallStaticShortMethodV";
    names[CALLSTATICSHORTMETHODA]        = "CallStaticShortMethodA";
    names[CALLSTATICINTMETHOD]           = "CallStaticIntMethod";
    names[CALLSTATICINTMETHODV]          = "CallStaticIntMethodV";
    names[CALLSTATICINTMETHODA]          = "CallStaticIntMethodA";
    names[CALLSTATICLONGMETHOD]          = "CallStaticLongMethod";
    names[CALLSTATICLONGMETHODV]         = "CallStaticLongMethodV";
    names[CALLSTATICLONGMETHODA]         = "CallStaticLongMethodA";
    names[CALLSTATICFLOATMETHOD]         = "CallStaticFloatMethod";
    names[CALLSTATICFLOATMETHODV]        = "CallStaticFloatMethodV";
    names[CALLSTATICFLOATMETHODA]        = "CallStaticFloatMethodA";
    names[CALLSTATICDOUBLEMETHOD]        = "CallStaticDoubleMethod";
    names[CALLSTATICDOUBLEMETHODV]       = "CallStaticDoubleMethodV";	  
    names[CALLSTATICDOUBLEMETHODA]       = "CallStaticDoubleMethodA";	  
    names[CALLSTATICVOIDMETHOD]          = "CallStaticVoidMethod";
    names[CALLSTATICVOIDMETHODV]         = "CallStaticVoidMethodV";
    names[CALLSTATICVOIDMETHODA]         = "CallStaticVoidMethodA";	  
    names[GETSTATICFIELDID]              = "GetStaticFieldID";
    names[GETSTATICOBJECTFIELD]          = "GetStaticObjectField";
    names[GETSTATICBOOLEANFIELD]         = "GetStaticBooleanField";
    names[GETSTATICBYTEFIELD]            = "GetStaticByteField";
    names[GETSTATICCHARFIELD]            = "GetStaticCharField";
    names[GETSTATICSHORTFIELD]           = "GetStaticShortField";
    names[GETSTATICINTFIELD]             = "GetStaticIntField";
    names[GETSTATICLONGFIELD]            = "GetStaticLongField";
    names[GETSTATICFLOATFIELD]           = "GetStaticFloatField";
    names[GETSTATICDOUBLEFIELD]          = "GetStaticDoubleField";
    names[SETSTATICOBJECTFIELD]          = "SetStaticObjectField";
    names[SETSTATICBOOLEANFIELD]         = "SetStaticBooleanField";
    names[SETSTATICBYTEFIELD]            = "SetStaticByteField";
    names[SETSTATICCHARFIELD]            = "SetStaticCharField";
    names[SETSTATICSHORTFIELD]           = "SetStaticShortField";
    names[SETSTATICINTFIELD]             = "SetStaticIntField";
    names[SETSTATICLONGFIELD]            = "SetStaticLongField";	  
    names[SETSTATICFLOATFIELD]           = "SetStaticFloatField";	  
    names[SETSTATICDOUBLEFIELD]          = "SetStaticDoubleField";	  
    names[NEWSTRING]                     = "NewString";
    names[GETSTRINGLENGTH]               = "GetStringLength";
    names[GETSTRINGCHARS]                = "GetStringChars";
    names[RELEASESTRINGCHARS]            = "ReleaseStringChars";
    names[NEWSTRINGUTF]                  = "NewStringUTF";
    names[GETSTRINGUTFLENGTH]            = "GetStringUTFLength";
    names[GETSTRINGUTFCHARS]             = "GetStringUTFChars";
    names[RELEASESTRINGUTFCHARS]         = "ReleaseStringUTFChars";
    names[GETARRAYLENGTH]                = "GetArrayLength";
    names[NEWOBJECTARRAY]                = "NewObjectArray";
    names[GETOBJECTARRAYELEMENT]         = "GetObjectArrayElement";
    names[SETOBJECTARRAYELEMENT]         = "SetObjectArrayElement";
    names[NEWBOOLEANARRAY]               = "NewBooleanArray";
    names[NEWBYTEARRAY]                  = "NewByteArray";
    names[NEWCHARARRAY]                  = "NewCharArray";
    names[NEWSHORTARRAY]                 = "NewShortArray";
    names[NEWINTARRAY]                   = "NewIntArray";
    names[NEWLONGARRAY]                  = "NewLongArray";
    names[NEWFLOATARRAY]                 = "NewFloatArray";
    names[NEWDOUBLEARRAY]                = "NewDoubleArray";
    names[GETBOOLEANARRAYELEMENTS]       = "GetBooleanArrayElements";
    names[GETBYTEARRAYELEMENTS]          = "GetByteArrayElements";
    names[GETCHARARRAYELEMENTS]          = "GetCharArrayElements";
    names[GETSHORTARRAYELEMENTS]         = "GetShortArrayElements";
    names[GETINTARRAYELEMENTS]           = "GetIntArrayElements";
    names[GETLONGARRAYELEMENTS]          = "GetLongArrayElements";
    names[GETFLOATARRAYELEMENTS]         = "GetFloatArrayElements";
    names[GETDOUBLEARRAYELEMENTS]        = "GetDoubleArrayElements";
    names[RELEASEBOOLEANARRAYELEMENTS]   = "ReleaseBooleanArrayElements";
    names[RELEASEBYTEARRAYELEMENTS]      = "ReleaseByteArrayElements";
    names[RELEASECHARARRAYELEMENTS]      = "ReleaseCharArrayElements";
    names[RELEASESHORTARRAYELEMENTS]     = "ReleaseShortArrayElements";
    names[RELEASEINTARRAYELEMENTS]       = "ReleaseIntArrayElements";
    names[RELEASELONGARRAYELEMENTS]      = "ReleaseLongArrayElements";
    names[RELEASEFLOATARRAYELEMENTS]     = "ReleaseFloatArrayElements";
    names[RELEASEDOUBLEARRAYELEMENTS]    = "ReleaseDoubleArrayElements";
    names[GETBOOLEANARRAYREGION]         = "GetBooleanArrayRegion";
    names[GETBYTEARRAYREGION]            = "GetByteArrayRegion";
    names[GETCHARARRAYREGION]            = "GetCharArrayRegion";
    names[GETSHORTARRAYREGION]           = "GetShortArrayRegion";
    names[GETINTARRAYREGION]             = "GetIntArrayRegion";
    names[GETLONGARRAYREGION]            = "GetLongArrayRegion";
    names[GETFLOATARRAYREGION]           = "GetFloatArrayRegion";
    names[GETDOUBLEARRAYREGION]          = "GetDoubleArrayRegion";
    names[SETBOOLEANARRAYREGION]         = "SetBooleanArrayRegion";
    names[SETBYTEARRAYREGION]            = "SetByteArrayRegion";
    names[SETCHARARRAYREGION]            = "SetCharArrayRegion";
    names[SETSHORTARRAYREGION]           = "SetShortArrayRegion";
    names[SETINTARRAYREGION]             = "SetIntArrayRegion";
    names[SETLONGARRAYREGION]            = "SetLongArrayRegion";
    names[SETFLOATARRAYREGION]           = "SetFloatArrayRegion";
    names[SETDOUBLEARRAYREGION]          = "SetDoubleArrayRegion";
    names[REGISTERNATIVES]               = "RegisterNatives";
    names[UNREGISTERNATIVES]             = "UnregisterNatives";
    names[MONITORENTER]                  = "MonitorEnter";
    names[MONITOREXIT]                   = "MonitorExit";
    names[GETJAVAVM]                     = "GetJavaVM";
    names[GETSTRINGREGION]             	 = "GetStringRegion";           // JDK 1.2, #220
    names[GETSTRINGUTFREGION]         	 = "GetStringUTFRegion";        // JDK 1.2, #221
    names[GETPRIMITIVEARRAYCRITICAL]   	 = "GetPrimitiveArrayCritical"; // JDK 1.2, #222
    names[RELEASEPRIMITIVEARRAYCRITICAL] = "ReleasePrimitiveArrayCritical"; // JDK 1.2, #223
    names[GETSTRINGCRITICAL]           	 = "GetStringCritical";         // JDK 1.2, # 224
    names[RELEASESTRINGCRITICAL]       	 = "ReleaseStringCritical";     // JDK 1.2, #225
    names[NEWWEAKGLOBALREF]            	 = "NewWeakGlobalRef";    	    // JDK 1.2, #226
    names[DELETEWEAKGLOBALREF]         	 = "DeleteWeakGlobalRef"; 	    // JDK 1.2, #227
    names[EXCEPTIONCHECK]              	 = "ExceptionCheck";      	    // JDK 1.2, #228

    return names;
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
