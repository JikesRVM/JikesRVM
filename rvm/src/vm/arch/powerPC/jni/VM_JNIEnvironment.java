/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import java.lang.reflect.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;
import com.ibm.JikesRVM.classloader.*;

/**
 *   This class implements the JNI environment, it includes:
 * -The array of JNI function pointers accessible from C
 * -Implementation of all the JNI functions
 *
 * @author Ton Ngo 
 * @author Steve Smith
 */
public class VM_JNIEnvironment implements VM_JNIAIXConstants, VM_RegisterConstants, VM_SizeConstants
{
  private static boolean initialized = false;
  private static String[] names;

  /**
   * This is the JNI function table, the address of this array will be
   * passed to the native code
   */
  //-#if RVM_FOR_LINUX
  private static INSTRUCTION[][]   JNIFunctions;
  //-#endif
  //-#if RVM_FOR_AIX
  private static INSTRUCTION[][][] JNIFunctions;
  //-#endif
  
  /**
   * This is a table of pointers to the shared JNI function table.  All entries 
   * point to the same function table.  Each thread uses the pointer at its thread id
   * offset to allow us to determine a threads id from the pointer it is using.
   * Needed when native calls Java (JNIFunctions) and passes its JNIEnv pointer.
   * Its offset into the JNIFunctionPts array is the same as the threads offset
   * in the Scheduler.threads array.
   */
  //  private static int[] JNIFunctionPointers;
  static int[] JNIFunctionPointers;        // made public so vpStatus could be set 11/16/00 SES
                                             // maybe need set & get functions ??

  /**
   * These are thread specific information, such as:
   *  -the list of references passed to native code, for GC purpose
   *  -saved RVM system registers
   */
  VM_Address   JNIEnvAddress;      // contain a pointer to the JNIFunctions array
  int          savedTIreg;         // for saving thread index register on entry to native, to be restored on JNI call from native
  VM_Processor savedPRreg;         // for saving processor register on entry to native, to be restored on JNI call from native
  boolean      alwaysHasNativeFrame;  // true if the bottom stack frame is native, such as thread for CreateJVM or AttachCurrentThread

  public int[] JNIRefs;          // references passed to native code
  int         JNIRefsTop;       // -> address of current top ref in JNIRefs array 
  int         JNIRefsMax;       // -> address of end (last entry) of JNIRefs array
  int         JNIRefsSavedFP;   // -> previous frame boundary in JNIRefs array
  public VM_Address  JNITopJavaFP;     // -> Top java frame when in C frames on top of the stack

  Throwable pendingException = null;

  /*
   * accessor methods
   */
  public boolean hasNativeStackFrame() throws VM_PragmaUninterruptible  {
    return alwaysHasNativeFrame || JNIRefsTop != 0;
  }

  public VM_Address topJavaFP() throws VM_PragmaUninterruptible  {
      return JNITopJavaFP;
  }

  public int[] refsArray() throws VM_PragmaUninterruptible {
      return JNIRefs;
  }

  public int refsTop() throws VM_PragmaUninterruptible  {
      return JNIRefsTop;
  }
   
  public int savedRefsFP() throws VM_PragmaUninterruptible  {
      return JNIRefsSavedFP;
  }

  public void setTopJavaFP(VM_Address topJavaFP) throws VM_PragmaUninterruptible  {
      JNITopJavaFP = topJavaFP;
  }

  public void setSavedPRreg(VM_Processor vp) throws VM_PragmaUninterruptible  {
      savedPRreg = vp;
  }

  public void setSavedTerminationContext(VM_Registers regs) throws VM_PragmaUninterruptible  {
      savedContextForTermination = regs;
  }

  public VM_Registers savedTerminationContext() throws VM_PragmaUninterruptible  {
      return savedContextForTermination;
  }

  public void setFromNative(VM_Address topJavaFP, VM_Processor nativeVP, int threadId) throws VM_PragmaUninterruptible  {
      alwaysHasNativeFrame = true;
      JNITopJavaFP = topJavaFP;
      savedPRreg = nativeVP;
      savedTIreg = threadId;
  }

  // Saved context for thread attached to external pthread.  This context is
  // saved by the JNIService thread and points to the point in JNIStartUp thread
  // where it yields to the queue in the native VM_Processor.
  // When DetachCurrentThread is called, the JNIService thread restores this context 
  // to allow the thread to run VM_Thread.terminate on its original stack.
  VM_Registers savedContextForTermination;

  // temporarily use a fixed size array for JNI refs, later grow as needed
  static final int JNIREFS_ARRAY_LENGTH = 400;

  // allocate the first dimension of the function array in the boot image so that
  // we have an address pointing to it.  This is necessary for thread creation
  // since the VM_JNIEnvironment object will contain a field pointing to this array
  public static void init() {
    //-#if RVM_FOR_AIX
    JNIFunctions = new int[FUNCTIONCOUNT][][];
    //-#endif
    //-#if RVM_FOR_LINUX
    JNIFunctions = new int[FUNCTIONCOUNT+1][];
    //-#endif
	
    // 2 words for each thread
    JNIFunctionPointers = new int[VM_Scheduler.MAX_THREADS * 2];
  }

  /**
   *  Initialize the array of JNI functions
   *  To be called from VM_DynamicLibrary.java when a library is loaded,
   *  expecting native calls to be made
   *
   */
  public static void boot() {

    if (initialized)
      return;

    // fill an array of JNI names
    setNames();

    //-#if RVM_FOR_AIX
    // fill in the TOC entries for each AIX linkage triplet
    for (int i=0; i<JNIFunctions.length; i++) {
      JNIFunctions[i] = new int[3][];
      JNIFunctions[i][TOC] = VM_Statics.getSlots();   // the JTOC value: address of TOC
    }
    //-#endif

    // fill in the IP entries for each AIX linkage triplet
    VM_TypeReference tRef = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), 
							  VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_JNIFunctions;"));
    VM_Class cls = (VM_Class)tRef.peekResolvedType();
    VM_Method[] mths = cls.getDeclaredMethods();
    // VM.sysWrite("VM_JNIEnvironment:  scanning " + mths.length + " methods\n");
    for (int i=0; i<mths.length; i++) {
      String methodName = mths[i].getName().toString();
      int jniIndex = indexOf(methodName);
      if (jniIndex!=-1) {
	//-#if RVM_FOR_AIX
	JNIFunctions[jniIndex][IP] = mths[i].getCurrentInstructions();
	//-#endif
	//-#if RVM_FOR_LINUX
	JNIFunctions[jniIndex]     = mths[i].getCurrentInstructions();
	//-#endif

	// VM.sysWrite("   " + methodName + "=" + VM.intAsHexString(JNIFunctions[jniIndex][IP]));
      } 
      // else {
      //   VM.sysWrite("   " + methodName + " skipped\n");
      // }
    }

    //-#if RVM_FOR_AIX
    VM_Address functionAddress = VM_Magic.objectAsAddress(JNIFunctions[NEWINTARRAY][IP]);
    // VM.sysWrite("   NewIntArray is at " + VM.intAsHexString(functionAddress) + "\n");
    functionAddress = VM_Magic.objectAsAddress(JNIFunctions[NEWINTARRAY][TOC]);
    // VM.sysWrite("   TOC is stored at " + VM.intAsHexString(functionAddress) + "\n");
    //-#endif

    //-#if RVM_FOR_LINUX
    // set JTOC content, how about GC ? will it move JTOC ?
    VM_Magic.setMemoryAddress(VM_Magic.objectAsAddress(JNIFunctions).add(JNIFUNCTIONS_JTOC_OFFSET),
			      VM_Magic.getTocPointer());
    //-#endif
    
    initialized = true;
  }

  // Instance:  create a thread specific JNI environment.  threadSlot = creating threads
  // thread id == index of its entry in Scheduler.threads array
  //
  public VM_JNIEnvironment(int threadSlot) {
    // VM_Field functions = (VM_Field) VM.getMember("VM_JNIEnvironment", "JNIFunctions", "[[I");
    // int addr = VM_Magic.getTocPointer() + functions.getOffset();

    // as of 8/22 SES - let JNIEnvAddress be the address of the JNIFunctionPtr to be
    // used by the creating thread.  Passed as first arg (JNIEnv) to native C functions.

    // uses 2 words for each thread, the first is the function pointer
    // to be used when making native calls
    JNIFunctionPointers[threadSlot * 2] = VM_Magic.objectAsAddress(JNIFunctions).toInt();
    JNIFunctionPointers[(threadSlot * 2)+1] = 0;  // later contains addr of processor vpStatus word
    JNIEnvAddress = VM_Magic.objectAsAddress(JNIFunctionPointers).add(threadSlot*8);

    JNIRefs = new int[JNIREFS_ARRAY_LENGTH];
    JNIRefs[0] = 0;                                        // 0 entry for bottom of stack
    JNIRefsTop = 0;
    JNIRefsSavedFP = 0;
    JNIRefsMax = (JNIRefs.length - 1) * BYTES_IN_ADDRESS;   // byte offset to last entry

    // initially TOP and SavedFP -> entry 0 containing 0

    alwaysHasNativeFrame = false;

  }

  // push a reference onto thread local JNIRefs stack.  To be used by JNI
  // Functions when returning a reference back to JNI native C code
  // Taken:    Object to put on stack
  // Returned: offset of entry in JNIRefs stack
  // 
  public int pushJNIRef( Object ref ) {
    JNIRefsTop += BYTES_IN_ADDRESS;
    JNIRefs[JNIRefsTop >> LOG_BYTES_IN_ADDRESS] = VM_Magic.objectAsAddress(ref).toInt();
    return JNIRefsTop;
  }

  // get a reference from the JNIRefs stack
  // Taken:    offset in JNIRefs stack
  // Returned: reference at that offset
  public Object getJNIRef( int offset ) {
    if (offset > JNIRefsTop) {
      VM.sysWrite("JNI ERROR: getJNIRef for illegal offset > TOP\n");
      return null;
    }
    return VM_Magic.addressAsObject( VM_Address.fromInt(JNIRefs[ offset>>2 ]) );
  }
	
  // remove a reference from the JNIRefs stack
  // Taken:    offset in JNIRefs stack
  public void deleteJNIRef( int offset ) {
    if (offset > JNIRefsTop) {
      VM.sysWrite("JNI ERROR: getJNIRef for illegal offset > TOP, ");
      VM.sysWrite(offset); 
      VM.sysWrite("(top is ");
      VM.sysWrite(JNIRefsTop);
      VM.sysWrite(")\n");
    }
    
    JNIRefs[ offset>>2 ] = 0;

    if (offset == JNIRefsTop) JNIRefsTop -= 4;
  }

  // record an exception as pending so that it will be delivered on the return
  // to the Java caller;  clear the exception by recording null
  // Taken:  an exception or error
  // Returned:  nothing
  //
  public void recordException(Throwable e) {
    // don't overwrite the first exception except to clear it
    if (pendingException==null || e==null)
      pendingException = e;
  }

  // return the pending exception
  // Taken:  nothing
  // Returned:  an exception or error
  //
  public Throwable getException() {
    return pendingException;
  }

  //
  // get the address of the JNIFunctions array, which should be in the JTOC
  // Taken:    nothing 
  // Returned: the address of the JNIFunctions array 
  // 
  public VM_Address getJNIenvAddress() {
    return JNIEnvAddress;
  }

  public INSTRUCTION[] getInstructions(int id) {    
    //-#if RVM_FOR_AIX
    return JNIFunctions[id][IP];
    //-#endif
    //-#if RVM_FOR_LINUX
    return JNIFunctions[id];
    //-#endif
  }

  //
  // get the JNI index for a function name
  // Taken:    a JNI function name
  // Returned: the index for this function, -1 if not found
  //
  private static int indexOf(String functionName) {
    for (int i=0; i<FUNCTIONCOUNT; i++) {
      if (names[i].equals(functionName))
	return i;
    }
    return -1;
  }





  private static String[] setNames() {
    names = new String[FUNCTIONCOUNT];
    names[0]                             = new String("undefined");
    names[RESERVED0]                     = new String("reserved0")                     ;	  
    names[RESERVED1]                     = new String("reserved1")                     ;	  
    names[RESERVED2]                     = new String("reserved2")                     ;	  
    names[RESERVED3]                     = new String("reserved3")                     ;	  
    names[GETVERSION]                    = new String("GetVersion")                    ;	  
    names[DEFINECLASS]                   = new String("DefineClass")                   ;	  
    names[FINDCLASS]                     = new String("FindClass")                     ;	  
    names[FROMREFLECTEDMETHOD]         	 = new String("FromReflectedMethod"); //  JDK1.2, #7      
    names[FROMREFLECTEDFIELD]          	 = new String("FromReflectedField");  //  JDK1.2, #8      
    names[TOREFLECTEDMETHOD]           	 = new String("ToReflectedMethod");   //  JDK1.2, #9      
    names[GETSUPERCLASS]                 = new String("GetSuperclass")                 ;	  
    names[ISASSIGNABLEFROM]              = new String("IsAssignableFrom")              ;	  
    names[TOREFLECTEDFIELD]            	 = new String("ToReflectedField");    //  JDK1.2, #12      
    names[THROW]                         = new String("Throw")                         ;	  
    names[THROWNEW]                      = new String("ThrowNew")                      ;	  
    names[EXCEPTIONOCCURRED]             = new String("ExceptionOccurred")             ;	  
    names[EXCEPTIONDESCRIBE]             = new String("ExceptionDescribe")             ;	  
    names[EXCEPTIONCLEAR]                = new String("ExceptionClear")                ;	  
    names[FATALERROR]                    = new String("FatalError")                    ;	  
    names[PUSHLOCALFRAME]              	 = new String("PushLocalFrame");      //  JDK1.2, #19      
    names[POPLOCALFRAME]               	 = new String("PopLocalFrame");       //  JDK1.2, #20      
    names[NEWGLOBALREF]                  = new String("NewGlobalRef")                  ;	  
    names[DELETEGLOBALREF]               = new String("DeleteGlobalRef")               ;	  
    names[DELETELOCALREF]                = new String("DeleteLocalRef")                ;	  
    names[ISSAMEOBJECT]                  = new String("IsSameObject")                  ;	  
    names[NEWLOCALREF]                 	 = new String("NewLocalRef");         //  JDK1.2, #25      
    names[ENSURELOCALCAPACITY]         	 = new String("EnsureLocalCapacity"); //  JDK1.2, #26   
    names[ALLOCOBJECT]                   = new String("AllocObject")                   ;	  
    names[NEWOBJECT]                     = new String("NewObject")                     ;	  
    names[NEWOBJECTV]                    = new String("NewObjectV")                    ;	  
    names[NEWOBJECTA]                    = new String("NewObjectA")                    ;	  
    names[GETOBJECTCLASS]                = new String("GetObjectClass")                ;	  
    names[ISINSTANCEOF]                  = new String("IsInstanceOf")                  ;	  
    names[GETMETHODID]                   = new String("GetMethodID")                   ;	  
    names[CALLOBJECTMETHOD]              = new String("CallObjectMethod")              ;	  
    names[CALLOBJECTMETHODV]             = new String("CallObjectMethodV")             ;	  
    names[CALLOBJECTMETHODA]             = new String("CallObjectMethodA")             ;	  
    names[CALLBOOLEANMETHOD]             = new String("CallBooleanMethod")             ;	  
    names[CALLBOOLEANMETHODV]            = new String("CallBooleanMethodV")            ;	  
    names[CALLBOOLEANMETHODA]            = new String("CallBooleanMethodA")            ;	  
    names[CALLBYTEMETHOD]                = new String("CallByteMethod")                ;	  
    names[CALLBYTEMETHODV]               = new String("CallByteMethodV")               ;	  
    names[CALLBYTEMETHODA]               = new String("CallByteMethodA")               ;	  
    names[CALLCHARMETHOD]                = new String("CallCharMethod")                ;	  
    names[CALLCHARMETHODV]               = new String("CallCharMethodV")               ;	  
    names[CALLCHARMETHODA]               = new String("CallCharMethodA")               ;	  
    names[CALLSHORTMETHOD]               = new String("CallShortMethod")               ;	  
    names[CALLSHORTMETHODV]              = new String("CallShortMethodV")              ;	  
    names[CALLSHORTMETHODA]              = new String("CallShortMethodA")              ;	  
    names[CALLINTMETHOD]                 = new String("CallIntMethod")                 ;	  
    names[CALLINTMETHODV]                = new String("CallIntMethodV")                ;	  
    names[CALLINTMETHODA]                = new String("CallIntMethodA")                ;	  
    names[CALLLONGMETHOD]                = new String("CallLongMethod")                ;	  
    names[CALLLONGMETHODV]               = new String("CallLongMethodV")               ;	  
    names[CALLLONGMETHODA]               = new String("CallLongMethodA")               ;	  
    names[CALLFLOATMETHOD]               = new String("CallFloatMethod")               ;	  
    names[CALLFLOATMETHODV]              = new String("CallFloatMethodV")              ;	  
    names[CALLFLOATMETHODA]              = new String("CallFloatMethodA")              ;	  
    names[CALLDOUBLEMETHOD]              = new String("CallDoubleMethod")              ;	  
    names[CALLDOUBLEMETHODV]             = new String("CallDoubleMethodV")             ;	  
    names[CALLDOUBLEMETHODA]             = new String("CallDoubleMethodA")             ;	  
    names[CALLVOIDMETHOD]                = new String("CallVoidMethod")                ;	  
    names[CALLVOIDMETHODV]               = new String("CallVoidMethodV")               ;	  
    names[CALLVOIDMETHODA]               = new String("CallVoidMethodA")               ;	  
    names[CALLNONVIRTUALOBJECTMETHOD]    = new String("CallNonvirtualObjectMethod")    ;	  
    names[CALLNONVIRTUALOBJECTMETHODV]   = new String("CallNonvirtualObjectMethodV")   ;	  
    names[CALLNONVIRTUALOBJECTMETHODA]   = new String("CallNonvirtualObjectMethodA")   ;	  
    names[CALLNONVIRTUALBOOLEANMETHOD]   = new String("CallNonvirtualBooleanMethod")   ;	  
    names[CALLNONVIRTUALBOOLEANMETHODV]  = new String("CallNonvirtualBooleanMethodV")  ;	  
    names[CALLNONVIRTUALBOOLEANMETHODA]  = new String("CallNonvirtualBooleanMethodA")  ;	  
    names[CALLNONVIRTUALBYTEMETHOD]      = new String("CallNonvirtualByteMethod")      ;	  
    names[CALLNONVIRTUALBYTEMETHODV]     = new String("CallNonvirtualByteMethodV")     ;	  
    names[CALLNONVIRTUALBYTEMETHODA]     = new String("CallNonvirtualByteMethodA")     ;	  
    names[CALLNONVIRTUALCHARMETHOD]      = new String("CallNonvirtualCharMethod")      ;	  
    names[CALLNONVIRTUALCHARMETHODV]     = new String("CallNonvirtualCharMethodV")     ;	  
    names[CALLNONVIRTUALCHARMETHODA]     = new String("CallNonvirtualCharMethodA")     ;	  
    names[CALLNONVIRTUALSHORTMETHOD]     = new String("CallNonvirtualShortMethod")     ;	  
    names[CALLNONVIRTUALSHORTMETHODV]    = new String("CallNonvirtualShortMethodV")    ;	  
    names[CALLNONVIRTUALSHORTMETHODA]    = new String("CallNonvirtualShortMethodA")    ;	  
    names[CALLNONVIRTUALINTMETHOD]       = new String("CallNonvirtualIntMethod")       ;	  
    names[CALLNONVIRTUALINTMETHODV]      = new String("CallNonvirtualIntMethodV")      ;	  
    names[CALLNONVIRTUALINTMETHODA]      = new String("CallNonvirtualIntMethodA")      ;	  
    names[CALLNONVIRTUALLONGMETHOD]      = new String("CallNonvirtualLongMethod")      ;	  
    names[CALLNONVIRTUALLONGMETHODV]     = new String("CallNonvirtualLongMethodV")     ;	  
    names[CALLNONVIRTUALLONGMETHODA]     = new String("CallNonvirtualLongMethodA")     ;	  
    names[CALLNONVIRTUALFLOATMETHOD]     = new String("CallNonvirtualFloatMethod")     ;	  
    names[CALLNONVIRTUALFLOATMETHODV]    = new String("CallNonvirtualFloatMethodV")    ;	  
    names[CALLNONVIRTUALFLOATMETHODA]    = new String("CallNonvirtualFloatMethodA")    ;	  
    names[CALLNONVIRTUALDOUBLEMETHOD]    = new String("CallNonvirtualDoubleMethod")    ;	  
    names[CALLNONVIRTUALDOUBLEMETHODV]   = new String("CallNonvirtualDoubleMethodV")   ;	  
    names[CALLNONVIRTUALDOUBLEMETHODA]   = new String("CallNonvirtualDoubleMethodA")   ;	  
    names[CALLNONVIRTUALVOIDMETHOD]      = new String("CallNonvirtualVoidMethod")      ;	  
    names[CALLNONVIRTUALVOIDMETHODV]     = new String("CallNonvirtualVoidMethodV")     ;	  
    names[CALLNONVIRTUALVOIDMETHODA]     = new String("CallNonvirtualVoidMethodA")     ;	  
    names[GETFIELDID]                    = new String("GetFieldID")                    ;	  
    names[GETOBJECTFIELD]                = new String("GetObjectField")                ;	  
    names[GETBOOLEANFIELD]               = new String("GetBooleanField")               ;	  
    names[GETBYTEFIELD]                  = new String("GetByteField")                  ;	  
    names[GETCHARFIELD]                  = new String("GetCharField")                  ;	  
    names[GETSHORTFIELD]                 = new String("GetShortField")                 ;	  
    names[GETINTFIELD]                   = new String("GetIntField")                   ;	  
    names[GETLONGFIELD]                  = new String("GetLongField")                  ;	  
    names[GETFLOATFIELD]                 = new String("GetFloatField")                 ;	  
    names[GETDOUBLEFIELD]                = new String("GetDoubleField")                ;	  
    names[SETOBJECTFIELD]                = new String("SetObjectField")                ;	  
    names[SETBOOLEANFIELD]               = new String("SetBooleanField")               ;	  
    names[SETBYTEFIELD]                  = new String("SetByteField")                  ;	  
    names[SETCHARFIELD]                  = new String("SetCharField")                  ;	  
    names[SETSHORTFIELD]                 = new String("SetShortField")                 ;	  
    names[SETINTFIELD]                   = new String("SetIntField")                   ;	  
    names[SETLONGFIELD]                  = new String("SetLongField")                  ;	  
    names[SETFLOATFIELD]                 = new String("SetFloatField")                 ;	  
    names[SETDOUBLEFIELD]                = new String("SetDoubleField")                ;	  
    names[GETSTATICMETHODID]             = new String("GetStaticMethodID")             ;	  
    names[CALLSTATICOBJECTMETHOD]        = new String("CallStaticObjectMethod")        ;	  
    names[CALLSTATICOBJECTMETHODV]       = new String("CallStaticObjectMethodV")       ;	  
    names[CALLSTATICOBJECTMETHODA]       = new String("CallStaticObjectMethodA")       ;	  
    names[CALLSTATICBOOLEANMETHOD]       = new String("CallStaticBooleanMethod")       ;	  
    names[CALLSTATICBOOLEANMETHODV]      = new String("CallStaticBooleanMethodV")      ;	  
    names[CALLSTATICBOOLEANMETHODA]      = new String("CallStaticBooleanMethodA")      ;	  
    names[CALLSTATICBYTEMETHOD]          = new String("CallStaticByteMethod")          ;	  
    names[CALLSTATICBYTEMETHODV]         = new String("CallStaticByteMethodV")         ;	  
    names[CALLSTATICBYTEMETHODA]         = new String("CallStaticByteMethodA")         ;	  
    names[CALLSTATICCHARMETHOD]          = new String("CallStaticCharMethod")          ;	  
    names[CALLSTATICCHARMETHODV]         = new String("CallStaticCharMethodV")         ;	  
    names[CALLSTATICCHARMETHODA]         = new String("CallStaticCharMethodA")         ;	  
    names[CALLSTATICSHORTMETHOD]         = new String("CallStaticShortMethod")         ;	  
    names[CALLSTATICSHORTMETHODV]        = new String("CallStaticShortMethodV")        ;	  
    names[CALLSTATICSHORTMETHODA]        = new String("CallStaticShortMethodA")        ;	  
    names[CALLSTATICINTMETHOD]           = new String("CallStaticIntMethod")           ;	  
    names[CALLSTATICINTMETHODV]          = new String("CallStaticIntMethodV")          ;	  
    names[CALLSTATICINTMETHODA]          = new String("CallStaticIntMethodA")          ;	  
    names[CALLSTATICLONGMETHOD]          = new String("CallStaticLongMethod")          ;	  
    names[CALLSTATICLONGMETHODV]         = new String("CallStaticLongMethodV")         ;	  
    names[CALLSTATICLONGMETHODA]         = new String("CallStaticLongMethodA")         ;	  
    names[CALLSTATICFLOATMETHOD]         = new String("CallStaticFloatMethod")         ;	  
    names[CALLSTATICFLOATMETHODV]        = new String("CallStaticFloatMethodV")        ;	  
    names[CALLSTATICFLOATMETHODA]        = new String("CallStaticFloatMethodA")        ;	  
    names[CALLSTATICDOUBLEMETHOD]        = new String("CallStaticDoubleMethod")        ;	  
    names[CALLSTATICDOUBLEMETHODV]       = new String("CallStaticDoubleMethodV")       ;	  
    names[CALLSTATICDOUBLEMETHODA]       = new String("CallStaticDoubleMethodA")       ;	  
    names[CALLSTATICVOIDMETHOD]          = new String("CallStaticVoidMethod")          ;	  
    names[CALLSTATICVOIDMETHODV]         = new String("CallStaticVoidMethodV")         ;	  
    names[CALLSTATICVOIDMETHODA]         = new String("CallStaticVoidMethodA")         ;	  
    names[GETSTATICFIELDID]              = new String("GetStaticFieldID")              ;	  
    names[GETSTATICOBJECTFIELD]          = new String("GetStaticObjectField")          ;	  
    names[GETSTATICBOOLEANFIELD]         = new String("GetStaticBooleanField")         ;	  
    names[GETSTATICBYTEFIELD]            = new String("GetStaticByteField")            ;	  
    names[GETSTATICCHARFIELD]            = new String("GetStaticCharField")            ;	  
    names[GETSTATICSHORTFIELD]           = new String("GetStaticShortField")           ;	  
    names[GETSTATICINTFIELD]             = new String("GetStaticIntField")             ;	  
    names[GETSTATICLONGFIELD]            = new String("GetStaticLongField")            ;	  
    names[GETSTATICFLOATFIELD]           = new String("GetStaticFloatField")           ;	  
    names[GETSTATICDOUBLEFIELD]          = new String("GetStaticDoubleField")          ;	  
    names[SETSTATICOBJECTFIELD]          = new String("SetStaticObjectField")          ;	  
    names[SETSTATICBOOLEANFIELD]         = new String("SetStaticBooleanField")         ;	  
    names[SETSTATICBYTEFIELD]            = new String("SetStaticByteField")            ;	  
    names[SETSTATICCHARFIELD]            = new String("SetStaticCharField")            ;	  
    names[SETSTATICSHORTFIELD]           = new String("SetStaticShortField")           ;	  
    names[SETSTATICINTFIELD]             = new String("SetStaticIntField")             ;	  
    names[SETSTATICLONGFIELD]            = new String("SetStaticLongField")            ;	  
    names[SETSTATICFLOATFIELD]           = new String("SetStaticFloatField")           ;	  
    names[SETSTATICDOUBLEFIELD]          = new String("SetStaticDoubleField")          ;	  
    names[NEWSTRING]                     = new String("NewString")                     ;	  
    names[GETSTRINGLENGTH]               = new String("GetStringLength")               ;	  
    names[GETSTRINGCHARS]                = new String("GetStringChars")                ;	  
    names[RELEASESTRINGCHARS]            = new String("ReleaseStringChars")            ;	  
    names[NEWSTRINGUTF]                  = new String("NewStringUTF")                  ;	  
    names[GETSTRINGUTFLENGTH]            = new String("GetStringUTFLength")            ;	  
    names[GETSTRINGUTFCHARS]             = new String("GetStringUTFChars")             ;	  
    names[RELEASESTRINGUTFCHARS]         = new String("ReleaseStringUTFChars")         ;	  
    names[GETARRAYLENGTH]                = new String("GetArrayLength")                ;	  
    names[NEWOBJECTARRAY]                = new String("NewObjectArray")                ;	  
    names[GETOBJECTARRAYELEMENT]         = new String("GetObjectArrayElement")         ;	  
    names[SETOBJECTARRAYELEMENT]         = new String("SetObjectArrayElement")         ;	  
    names[NEWBOOLEANARRAY]               = new String("NewBooleanArray")               ;	  
    names[NEWBYTEARRAY]                  = new String("NewByteArray")                  ;	  
    names[NEWCHARARRAY]                  = new String("NewCharArray")                  ;	  
    names[NEWSHORTARRAY]                 = new String("NewShortArray")                 ;	  
    names[NEWINTARRAY]                   = new String("NewIntArray")                   ;	  
    names[NEWLONGARRAY]                  = new String("NewLongArray")                  ;	  
    names[NEWFLOATARRAY]                 = new String("NewFloatArray")                 ;	  
    names[NEWDOUBLEARRAY]                = new String("NewDoubleArray")                ;	  
    names[GETBOOLEANARRAYELEMENTS]       = new String("GetBooleanArrayElements")       ;	  
    names[GETBYTEARRAYELEMENTS]          = new String("GetByteArrayElements")          ;	  
    names[GETCHARARRAYELEMENTS]          = new String("GetCharArrayElements")          ;	  
    names[GETSHORTARRAYELEMENTS]         = new String("GetShortArrayElements")         ;	  
    names[GETINTARRAYELEMENTS]           = new String("GetIntArrayElements")           ;	  
    names[GETLONGARRAYELEMENTS]          = new String("GetLongArrayElements")          ;	  
    names[GETFLOATARRAYELEMENTS]         = new String("GetFloatArrayElements")         ;	  
    names[GETDOUBLEARRAYELEMENTS]        = new String("GetDoubleArrayElements")        ;	  
    names[RELEASEBOOLEANARRAYELEMENTS]   = new String("ReleaseBooleanArrayElements")   ;	  
    names[RELEASEBYTEARRAYELEMENTS]      = new String("ReleaseByteArrayElements")      ;	  
    names[RELEASECHARARRAYELEMENTS]      = new String("ReleaseCharArrayElements")      ;	  
    names[RELEASESHORTARRAYELEMENTS]     = new String("ReleaseShortArrayElements")     ;	  
    names[RELEASEINTARRAYELEMENTS]       = new String("ReleaseIntArrayElements")       ;	  
    names[RELEASELONGARRAYELEMENTS]      = new String("ReleaseLongArrayElements")      ;	  
    names[RELEASEFLOATARRAYELEMENTS]     = new String("ReleaseFloatArrayElements")     ;	  
    names[RELEASEDOUBLEARRAYELEMENTS]    = new String("ReleaseDoubleArrayElements")    ;	  
    names[GETBOOLEANARRAYREGION]         = new String("GetBooleanArrayRegion")         ;	  
    names[GETBYTEARRAYREGION]            = new String("GetByteArrayRegion")            ;	  
    names[GETCHARARRAYREGION]            = new String("GetCharArrayRegion")            ;	  
    names[GETSHORTARRAYREGION]           = new String("GetShortArrayRegion")           ;	  
    names[GETINTARRAYREGION]             = new String("GetIntArrayRegion")             ;	  
    names[GETLONGARRAYREGION]            = new String("GetLongArrayRegion")            ;	  
    names[GETFLOATARRAYREGION]           = new String("GetFloatArrayRegion")           ;	  
    names[GETDOUBLEARRAYREGION]          = new String("GetDoubleArrayRegion")          ;	  
    names[SETBOOLEANARRAYREGION]         = new String("SetBooleanArrayRegion")         ;	  
    names[SETBYTEARRAYREGION]            = new String("SetByteArrayRegion")            ;	  
    names[SETCHARARRAYREGION]            = new String("SetCharArrayRegion")            ;	  
    names[SETSHORTARRAYREGION]           = new String("SetShortArrayRegion")           ;	  
    names[SETINTARRAYREGION]             = new String("SetIntArrayRegion")             ;	  
    names[SETLONGARRAYREGION]            = new String("SetLongArrayRegion")            ;	  
    names[SETFLOATARRAYREGION]           = new String("SetFloatArrayRegion")           ;	  
    names[SETDOUBLEARRAYREGION]          = new String("SetDoubleArrayRegion")          ;	  
    names[REGISTERNATIVES]               = new String("RegisterNatives")               ;	  
    names[UNREGISTERNATIVES]             = new String("UnregisterNatives")             ;	  
    names[MONITORENTER]                  = new String("MonitorEnter")                  ;	  
    names[MONITOREXIT]                   = new String("MonitorExit")                   ;	  
    names[GETJAVAVM]                     = new String("GetJavaVM")                     ;	  
    names[GETSTRINGREGION]             	 = new String("GetStringRegion");           // JDK 1.2, #220
    names[GETSTRINGUTFREGION]         	 = new String("GetStringUTFRegion");        // JDK 1.2, #221
    names[GETPRIMITIVEARRAYCRITICAL]   	 = new String("GetPrimitiveArrayCritical"); // JDK 1.2, #222
    names[RELEASEPRIMITIVEARRAYCRITICAL] = new String("ReleasePrimitiveArrayCritical"); // JDK 1.2, #223
    names[GETSTRINGCRITICAL]           	 = new String("GetStringCritical");         // JDK 1.2, # 224
    names[RELEASESTRINGCRITICAL]       	 = new String("ReleaseStringCritical");     // JDK 1.2, #225
    names[NEWWEAKGLOBALREF]            	 = new String("NewWeakGlobalRef");    	    // JDK 1.2, #226
    names[DELETEWEAKGLOBALREF]         	 = new String("DeleteWeakGlobalRef"); 	    // JDK 1.2, #227
    names[EXCEPTIONCHECK]              	 = new String("ExceptionCheck");      	    // JDK 1.2, #228

    return names;

  }


  /*****************************************************************************
   * Utility function called from VM_JNIFunction
   * (cannot be placed in VM_JNIFunction because methods there are specially compiled
   * to be called from native)
   *****************************************************************************/


  /**
   * Get a VM_Field of an object given the index for this field
   * @param obj an Object
   * @param fieldIndex an index into the VM_Field array that describes the fields of this object
   * @return the VM_Field pointed to by the index, or null if the index or the object is invalid
   *
   */
  public static VM_Field getFieldAtIndex (Object obj, int fieldIndex) {   
    // VM.sysWrite("GetObjectField: field at index " + fieldIndex + "\n");

    VM_Type objType = VM_Magic.getObjectType(obj);
    if (objType.isClassType()) {
      VM_Field[] fields = objType.asClass().getInstanceFields();
      if (fieldIndex>=fields.length) {
	return null;                                      // invalid field index
      } else {
	return fields[fieldIndex];
      } 
    } else {
      // object is not a class type, probably an array or an invalid reference
      return null;
    }
  }



  /**
   * Common code shared by the JNI functions NewObjectA, NewObjectV, NewObject
   * (object creation)
   * @param methodID the method ID for a constructor
   * @return a new object created by the specified constructor
   */
  public static Object invokeInitializer(Class cls, int methodID, VM_Address argAddress, 
					 boolean isJvalue, boolean isDotDotStyle) 
    throws Exception {

    // get the parameter list as Java class
    VM_Method mth = VM_MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    VM_TypeReference[] argTypes = mth.getParameterTypes();
    Class[]   argClasses = new Class[argTypes.length];
    for (int i=0; i<argClasses.length; i++) {
      argClasses[i] = argTypes[i].resolve().getClassForType();
    }

    Constructor constMethod = cls.getConstructor(argClasses);
    if (constMethod==null)
      throw new Exception("Constructor not found");


    Object argObjs[];

    if (isJvalue) {
      argObjs = packageParameterFromJValue(mth, argAddress);
    } else {
      if (isDotDotStyle) {
	//-#if RVM_FOR_AIX
	VM_Address varargAddress = pushVarArgToSpillArea(methodID, false);
	argObjs = packageParameterFromVarArg(mth, varargAddress);
	//-#endif
	
	//-#if RVM_FOR_LINUX
	// pass in the frame pointer of glue stack frames
	// stack frame looks as following:
	//      this method -> 
	//
	//      native to java method ->
	//
	//      glue frame ->
	//
	//      native C method ->
	VM_Address gluefp = VM_Magic.getCallerFramePointer(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer())); 
	argObjs = packageParameterFromDotArgSVR4(mth, gluefp, false);
	//-#endif
      } else {
	// var arg
	//-#if RVM_FOR_AIX
	argObjs = packageParameterFromVarArg(mth, argAddress);
	//-#endif
	//-#if RVM_FOR_LINUX
	argObjs = packageParameterFromVarArgSVR4(mth, argAddress);
	//-#endif
      }
    }

    // construct the new object
    Object newobj = constMethod.newInstance(argObjs);
    
    return newobj;

  }


  /**
   * Common code shared by the JNI functions CallStatic<type>Method
   * (static method invocation)
   * @param methodID the method ID
   * @param expectReturnType the return type of the method to be invoked
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithDotDotVarArg(int methodID, 
					      VM_TypeReference expectReturnType)
    throws Exception {

    //-#if RVM_FOR_AIX
    VM_Address varargAddress = pushVarArgToSpillArea(methodID, false);    
    return packageAndInvoke(null, methodID, varargAddress, expectReturnType, false, AIX_VARARG);
    //-#endif

    //-#if RVM_FOR_LINUX
    VM_Address glueFP = VM_Magic.getCallerFramePointer(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    return packageAndInvoke(null, methodID, glueFP, expectReturnType, false, SVR4_DOTARG);
    //-#endif
  }

  /**
   * Common code shared by the JNI functions Call<type>Method
   * (virtual method invocation)
   * @param obj the object instance 
   * @param methodID the method ID
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args  true if the calling JNI Function takes 4 args before the vararg
   *                   false if the calling JNI Function takes 3 args before the vararg
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithDotDotVarArg(Object obj, int methodID, 
					      VM_TypeReference expectReturnType, boolean skip4Args)
    throws Exception {

    //-#if RVM_FOR_AIX
    VM_Address varargAddress = pushVarArgToSpillArea(methodID, skip4Args);    
    return packageAndInvoke(obj, methodID, varargAddress, expectReturnType, skip4Args, AIX_VARARG);
    //-#endif

    //-#if RVM_FOR_LINUX
    VM_Address glueFP = VM_Magic.getCallerFramePointer(VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()));
    return packageAndInvoke(obj, methodID, glueFP, expectReturnType, skip4Args, SVR4_DOTARG);
    //-#endif
  }


  /**
   * This method supports var args passed from C
   *
   * In the AIX C convention, the caller keeps the first 8 words in registers and 
   * the rest in the spill area in the caller frame.  The callee will push the values
   * in registers out to the spill area of the caller frame and use the beginning 
   * address of this spill area as the var arg address
   *
   * For the JNI functions that takes var args, their prolog code will save the
   * var arg in the glue frame because the values in the register may be lost by 
   * subsequent calls.
   *
   * This method copies the var arg values that were saved earlier in glue frame into
   * the spill area of the original caller, thereby doing the work that the callee
   * normally performs in the AIX C convention.
   *
   * NOTE: This method contains internal stack pointer.
   * For now we assume that the stack will not be relocatable while native code is running
   * because native code can hold an address into the stack, so this code is OK,
   * but this is an issue to be resolved later
   *
   * NOTE:  this method assumes that it is immediately above the 
   * invokeWithDotDotVarArg frame, the JNI frame, the glue frame and 
   * the C caller frame in the respective order.  
   * Therefore, this method will not work if called from anywhere else
   *
   *
   *
   *   |  fp  | <- VM_JNIEnvironment.pushVarArgToSpillArea
   *   | mid  |
   *   | xxx  |
   *   |      |
   *   |      |
   *   |------|   
   *   |  fp  | <- VM_JNIEnvironment.invokeWithDotDotVarArg frame
   *   | mid  |
   *   | xxx  |
   *   |      |
   *   |      |
   *   |      |
   *   |------|   
   *   |  fp  | <- JNI method frame
   *   | mid  |
   *   | xxx  |
   *   |      |
   *   |      |
   *   |      |
   *   |------|
   *   |  fp  | <- glue frame
   *   | mid  |
   *   + xxx  +
   *   | r3   |   volatile save area
   *   | r4   |
   *   | r5   |
   *   | r6   |   vararg GPR[6-10]save area   <- VARARG_AREA_OFFSET
   *   | r7   |
   *   | r8   |
   *   | r9   |
   *   | r10  |
   *   | fpr1 |   vararg FPR[1-3] save area (also used as volatile FPR[1-6] save area)
   *   | fpr2 |
   *   | fpr3 |
   *   | fpr4 |
   *   | fpr5 |
   *   + fpr6 +
   *   | r13  |   nonvolatile GPR[13-31] save area
   *   | ...  |
   *   + r31  +
   *   | fpr14|   nonvolatile FPR[14-31] save area
   *   | ...  |
   *   | fpr31|
   *   |topjav|   offset to preceding Java to C glue frame
   *   |------|  
   *   | fp   | <- Native C caller frame
   *   | cr   |
   *   | lr   |
   *   | resv |
   *   | resv |
   *   + toc  +
   *   |   0  |    spill area initially not filled
   *   |   1  |    to be filled by this method
   *   |   2  |
   *   |   3  |
   *   |   4  |
   *   |   5  |
   *   |   6  |
   *   |   7  |
   *   |   8  |    spill area already filled by caller
   *   |   9  |
   *   |      |
   *   |      |
   *   |      |
   *
   * @param methodID a VM_MemberReference id
   * @param skip4Args if true, the calling JNI function has 4 args before the vararg
   *                  if false, the calling JNI function has 3 args before the vararg
   * @return the starting address of the vararg in the caller stack frame
   */
  private static VM_Address pushVarArgToSpillArea(int methodID, boolean skip4Args) throws Exception {

    int glueFrameSize = JNI_GLUE_FRAME_SIZE;

    // get the FP for this stack frame and traverse 2 frames to get to the glue frame
    VM_Address gluefp = VM_Magic.getMemoryAddress(VM_Magic.getFramePointer().add(VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET));
    gluefp = VM_Magic.getMemoryAddress(gluefp.add(VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET));
    gluefp = VM_Magic.getMemoryAddress(gluefp.add(VM_Constants.STACKFRAME_FRAME_POINTER_OFFSET));

    // compute the offset into the area where the vararg GPR[6-10] and FPR[1-3] are saved
    // skipping the args which are not part of the arguments for the target method
    // For Call<type>Method functions and NewObject, skip 3 args
    // For CallNonvirtual<type>Method functions, skip 4 args
    int varargGPROffset = VARARG_AREA_OFFSET + (skip4Args ? 4 : 0);
    int varargFPROffset = varargGPROffset + 5*4 ;

    // compute the offset into the spill area of the native caller frame, 
    // skipping the args which are not part of the arguments for the target method
    // For Call<type>Method functions, skip 3 args
    // For CallNonvirtual<type>Method functions, skip 4 args
    int spillAreaLimit  = glueFrameSize + NATIVE_FRAME_HEADER_SIZE + 8*4;
    int spillAreaOffset = glueFrameSize + NATIVE_FRAME_HEADER_SIZE + 
                          (skip4Args ? 4*4 : 3*4);

    // address to return pointing to the var arg list
    VM_Address varargAddress = gluefp.add(spillAreaOffset);

    // VM.sysWrite("pushVarArgToSpillArea:  var arg at " + 
    // 		   VM.intAsHexString(varargAddress) + "\n");
 
    VM_Method targetMethod = VM_MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;

    for (int i=0; i<argCount && spillAreaOffset<spillAreaLimit ; i++) {
      int hiword, loword;

      if (argTypes[i].isFloatType() || argTypes[i].isDoubleType()) {
	// move 2 words from the vararg FPR save area into the spill area of the caller
	hiword = VM_Magic.getMemoryInt(gluefp.add(varargFPROffset));
	varargFPROffset+=4;
	loword = VM_Magic.getMemoryInt(gluefp.add(varargFPROffset));
	varargFPROffset+=4;
	VM_Magic.setMemoryInt(gluefp.add(spillAreaOffset), hiword);
	spillAreaOffset+=4;
	VM_Magic.setMemoryInt(gluefp.add(spillAreaOffset), loword);
	spillAreaOffset+=4;
      } 

      else if (argTypes[i].isLongType()) {
	// move 2 words from the vararg GPR save area into the spill area of the caller
	hiword = VM_Magic.getMemoryInt(gluefp.add(varargGPROffset));
	varargGPROffset+=4;
	VM_Magic.setMemoryInt(gluefp.add(spillAreaOffset), hiword);
	spillAreaOffset+=4;
	// this covers the case when the long value straddles the spill boundary
	if (spillAreaOffset<spillAreaLimit) {
	  loword = VM_Magic.getMemoryInt(gluefp.add(varargGPROffset));
	  varargGPROffset+=4;
	  VM_Magic.setMemoryInt(gluefp.add(spillAreaOffset), loword);
	  spillAreaOffset+=4;
	}
      }

      else {
	hiword = VM_Magic.getMemoryInt(gluefp.add(varargGPROffset));
	varargGPROffset+=4;
	VM_Magic.setMemoryInt(gluefp.add(spillAreaOffset), hiword);
	spillAreaOffset+=4;
      }

    }

    // At this point, all the vararg values should be in the spill area in the caller frame
    // return the address of the beginning of the vararg to use in invoking the target method
    return varargAddress;

  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodV
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithVarArg(int methodID, VM_Address argAddress, 
					VM_TypeReference expectReturnType) 
    throws Exception {
    //-#if RVM_FOR_AIX
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, AIX_VARARG);
    //-#endif

    //-#if RVM_FOR_LINUX
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, SVR4_VARARG);
    //-#endif
  }

  /**
   * Common code shared by the JNI functions Call<type>MethodV
   * @param obj the object instance 
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to VM_Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithVarArg(Object obj, int methodID, VM_Address argAddress, 
					VM_TypeReference expectReturnType, 
					boolean skip4Args) 
    throws Exception {

    //-#if RVM_FOR_AIX
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, AIX_VARARG);
    //-#endif

    //-#if RVM_FOR_LINUX
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, SVR4_VARARG);
    //-#endif
  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodA
   * @param methodID a VM_MemberReference id
   * @param argAddress a raw address for the argument array
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithJValue(int methodID, VM_Address argAddress, 
					VM_TypeReference expectReturnType) 
    throws Exception {
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, JVALUE_ARG);
  }

  /**
   * Common code shared by the JNI functions Call<type>MethodA
   * @param obj the object instance 
   * @param methodID a VM_MemberReference id
   * @param argAddress a raw address for the argument array
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to VM_Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithJValue(Object obj, int methodID, VM_Address argAddress, 
					VM_TypeReference expectReturnType, 
					boolean skip4Args) 
    throws Exception {
    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, JVALUE_ARG);
  }

  public static final int SVR4_DOTARG = 0;         // Linux/PPC SVR4 normal
  public static final int AIX_DOTARG  = 1;         // AIX normal 
  public static final int JVALUE_ARG  = 2;         // javlue
  public static final int SVR4_VARARG = 3;         // Linux/PPC SVR4 vararg
  public static final int AIX_VARARG  = 4;         // AIX vararg

  /**
   * Common code shared by invokeWithJValue, invokeWithVarArg and invokeWithDotDotVarArg
   * @param obj the object instance 
   * @param methodID a VM_MemberReference id
   * @param argAddress a raw address for the argument array
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args This flag is received from the JNI function and passed directly to 
   *                     VM_Reflection.invoke().  
   *                     It is true if the actual method is to be invoked, which could be
   *                     from the superclass.
   *                     It is false if the method from the real class of the object 
   *                     is to be invoked, which may not be the actual method specified by methodID
   * @param isVarArg  This flag describes whether the array of parameters is in var arg format or
   *                  jvalue format
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object packageAndInvoke(Object obj, int methodID, VM_Address argAddress, 
					VM_TypeReference expectReturnType, 
					boolean skip4Args, int argtype) 
    throws Exception {
  
    // VM.sysWrite("JNI CallXXXMethod:  method ID " + methodID + " with args at " + 
    // 		   VM.intAsHexString(argAddress) + "\n");
    
    VM_Method targetMethod = VM_MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    VM_TypeReference returnType = targetMethod.getReturnType();

    // VM.sysWrite("JNI CallXXXMethod:  " + targetMethod.getDeclaringClass().toString() +
    //		"." + targetMethod.getName().toString() + "\n");

    if (expectReturnType==null) {   // for reference return type 
      if (!returnType.isReferenceType())
	throw new Exception("Wrong return type for method: expect reference type instead of " + returnType);      
    } 
    else {    // for primitive return type
      if (returnType!=expectReturnType) 
	throw new Exception("Wrong return type for method: expect " + expectReturnType + 
			    " instead of " + returnType);
    }  

    // Repackage the arguments into an array of objects based on the signature of this method
    Object[] argObjectArray;
    
    switch (argtype) {
      //-#if RVM_FOR_LINUX
    case SVR4_DOTARG:
      // argAddress is the glue frame pointer
      argObjectArray = packageParameterFromDotArgSVR4(targetMethod, argAddress, skip4Args);
      break;
      //-#endif
    case JVALUE_ARG:
      argObjectArray = packageParameterFromJValue(targetMethod, argAddress);
      break;
      //-#if RVM_FOR_LINUX
    case SVR4_VARARG:
      argObjectArray = packageParameterFromVarArgSVR4(targetMethod, argAddress);
      break;
      //-#endif
    case AIX_VARARG:
      argObjectArray = packageParameterFromVarArg(targetMethod, argAddress);
      break;
    default:
      argObjectArray = null;
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }

    // now invoke the method
    Object returnObj = VM_Reflection.invoke(targetMethod, obj, argObjectArray, skip4Args);
    
    return returnObj;
  }


  //-#if RVM_FOR_LINUX 
  /* The method reads out parameters from registers saved in native->java glue stack frame (glueFP)
   * and the spill area of native stack frame (caller of glueFP).
   * 
   * NOTE: assuming the stack frame won't get moved, (see pushVarArgToSpillArea)
   *       the row address glueFP can be replaced by offset to the stack.
   *
   * @param targetMethod, the call target
   * @param glueFP, the glue stack frame pointer
   */
  static Object[] packageParameterFromDotArgSVR4(VM_Method targetMethod, VM_Address glueFP, boolean skip4Args) {

//    VM_Magic.breakpoint();

    // native method's stack frame
    VM_Address nativeFP = VM_Magic.getCallerFramePointer(glueFP);
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();

    // GPR r3 - r10 and FPR f1 - f8 are saved in glue stack frame
    VM_Address regsavearea = glueFP.add(VM_StackframeLayoutConstants.STACKFRAME_HEADER_SIZE);

    // spill area offset
    VM_Address overflowarea = nativeFP.add(NATIVE_FRAME_HEADER_SIZE);
    
    // overflowarea is aligned to 8 bytes
    if (VM.VerifyAssertions) VM._assert((overflowarea.toInt() & 0x07) == 0);
    
    // adjust gpr and fpr to normal numbering, make life easier
    int gpr = (skip4Args) ? 7:6;       // r3 - env, r4 - cls, r5 - method id
    int fpr = 1;
    
    // not set the starting gprs array address 
    // and fpr starting array address, so we can use gpr and fpr to 
    // calculate the right position to get values
    // GPR starts with r3;
    VM_Address gprarray = regsavearea.add(-3*4); 
    VM_Address fprarray = regsavearea.add(8*4-2*4);

    // call the common function for SVR4
    packageArgumentForSVR4(argTypes, argObjectArray, gprarray, fprarray, overflowarea, gpr, fpr, env); 
    
    return argObjectArray;
  }

  // linux has totally different layout of va_list
  // see /usr/lib/gcc-lib/powerpc-linux/xxxx/include/va-ppc.h
  //
  // va_list is defined as following
  //
  // struct {
  //   char unsigned gpr;    // compiled to 1 byte, index of gprs in saved area
  //                         // 0 -> r3, 1 -> r4, ....
  //   char unsigned fpr;    // compiled to 1 byte, index to fprs in saved area
  //                         // 0 -> fr1, 1 -> fr2, ....
  //   char * over_flow_area;
  //   char * reg_save_area;
  // }
  //
  // The interpretation of data can be found in PowerPC Processor ABI Supplement
  //
  // The reg_save area lays out r3 - r10, f1 - f8
  // 
  // I am not sure if GCC understand the ABI in a right way, it saves GPRs 1 - 10
  // in the area, while only gprs starting from r3 are used.
  //
  // -- Feng
  // 
  static Object[] packageParameterFromVarArgSVR4(VM_Method targetMethod, VM_Address argAddress) {
//  VM_Magic.breakpoint();
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];
    
    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();
    
    // the va_list has following layout on PPC/Linux
    // GPR FPR 0 0   (4 bytes)
    // overflowarea  (pointer)
    // reg_save_area (pointer)
    VM_Address va_list_addr = argAddress;
    int word1 = VM_Magic.getMemoryInt(va_list_addr);
    int gpr = word1 >> 24;
    int fpr = (word1 >> 16) & 0x0FF;
    va_list_addr = va_list_addr.add(4);
    VM_Address overflowarea = VM_Magic.getMemoryAddress(va_list_addr);
    va_list_addr = va_list_addr.add(4);
    VM_Address regsavearea = VM_Magic.getMemoryAddress(va_list_addr);
    
    // overflowarea is aligned to 8 bytes
    if (VM.VerifyAssertions) VM._assert((overflowarea.toInt() & 0x07) == 0);
    
    // adjust gpr and fpr to normal numbering, make life easier
    gpr += 3;
    fpr += 1;
    
    // not set the starting gprs array address 
    // and fpr starting array address, so we can use gpr and fpr to 
    // calculate the right position to get values
    // GPR starts with r3;
    VM_Address gprarray = regsavearea.add(-3*4); 
    VM_Address fprarray = regsavearea.add(8*4-2*4);
    
    // call the common function for SVR4
    packageArgumentForSVR4(argTypes, argObjectArray, gprarray, fprarray, overflowarea, gpr, fpr, env); 

    return argObjectArray;
  }

  static void packageArgumentForSVR4(VM_TypeReference[] argTypes, Object[] argObjectArray,
				     VM_Address gprarray, VM_Address fprarray,
				     VM_Address overflowarea, int gpr, int fpr,
				     VM_JNIEnvironment env) {
    // also make overflow offset, we may need to round it
    int overflowoffset = 0;
    int argCount = argTypes.length;

    // now interpret values by types, see PPC ABI
    for (int i=0; i<argCount; i++) {
      if (argTypes[i].isFloatType()
	  || argTypes[i].isDoubleType()) {
	int loword, hiword;
	if (fpr > LAST_OS_PARAMETER_FPR) {
	  // overflow, OTHER
	  // round it, bytes are saved from lowest to highest one, regardless endian
	  overflowoffset = (overflowoffset + 7) & -8;
	  hiword = VM_Magic.getMemoryInt(overflowarea.add(overflowoffset));
	  overflowoffset += BYTES_IN_INT;
	  loword = VM_Magic.getMemoryInt(overflowarea.add(overflowoffset));
	  overflowoffset += BYTES_IN_INT;
	} else {
	  // get value from fpr, increase fpr by 1
	  hiword = VM_Magic.getMemoryInt(fprarray.add(fpr*BYTES_IN_DOUBLE));
	  loword = VM_Magic.getMemoryInt(fprarray.add(fpr*BYTES_IN_DOUBLE + BYTES_IN_INT));
	  fpr += 1;
	}
	long doubleBits = (((long)hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	if (argTypes[i].isFloatType()) {
	  argObjectArray[i] = VM_Reflection.wrapFloat((float)(Double.longBitsToDouble(doubleBits)));
	} else { // double type
	  argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));
	}
	
	//		VM.sysWriteln("double "+Double.longBitsToDouble(doubleBits));
	
      } else if (argTypes[i].isLongType()) {
	int loword, hiword;
	if (gpr > LAST_OS_PARAMETER_GPR-1) {
	  // overflow, OTHER
	  // round overflowoffset, assuming overflowarea is aligned to 8 bytes
	  overflowoffset = (overflowoffset + 7) & -8;
	  hiword = VM_Magic.getMemoryInt(overflowarea.add(overflowoffset));
	  overflowoffset += BYTES_IN_INT;
	  loword = VM_Magic.getMemoryInt(overflowarea.add(overflowoffset));
	  overflowoffset += BYTES_IN_INT;
	  
	  // va-ppc.h makes last gpr useless
	  gpr = 11;
	} else {
	  gpr += (gpr + 1) & 0x01;  // if gpr is even, gpr += 1
	  hiword = VM_Magic.getMemoryInt(gprarray.add(gpr*4));
	  loword = VM_Magic.getMemoryInt(gprarray.add((gpr+1)*4));
	  gpr += 2;
	}
	long longBits = (((long)hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapLong(longBits);
	
	//		VM.sysWriteln("long 0x"+Long.toHexString(longBits));
      } else {
	// int type left now
	int ivalue;
	if (gpr > LAST_OS_PARAMETER_GPR) {
	  // overflow, OTHER
	  ivalue = VM_Magic.getMemoryInt(overflowarea.add(overflowoffset));
	  overflowoffset += 4;
	} else {
	  ivalue = VM_Magic.getMemoryInt(gprarray.add(gpr*4));
	  gpr += 1;
	} 
	
	//		VM.sysWriteln("int "+ivalue);
	
	if (argTypes[i].isBooleanType()) {
	  argObjectArray[i] = VM_Reflection.wrapBoolean(ivalue);
	} else if (argTypes[i].isByteType()) {
	  argObjectArray[i] = VM_Reflection.wrapByte((byte)ivalue);
	} else if (argTypes[i].isShortType()) {
	  argObjectArray[i] = VM_Reflection.wrapShort((short)ivalue);
	} else if (argTypes[i].isCharType()) {
	  argObjectArray[i] = VM_Reflection.wrapChar((char)ivalue);
	} else if (argTypes[i].isIntType()) {
	  argObjectArray[i] = VM_Reflection.wrapInt(ivalue);
	} else if (argTypes[i].isReferenceType()) {
	  argObjectArray[i] = env.getJNIRef(ivalue);
	} else {
	  if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
	}
      }
    }
  }
  //-#endif  RVM_FOR_LINUX


  /**
   * Repackage the arguments passed as a variable argument list into an array of Object,
   * used by the JNI functions CallStatic<type>MethodV
   * @param mth the target VM_Method
   * @param argAddress an address into the C space for the array of jvalue unions;  
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromVarArg(VM_Method targetMethod, VM_Address argAddress) {
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the VM_JNIEnvironment for this thread in case we need to dereference any object arg
    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();

    // VM.sysWrite("JNI packageParameterFromVarArg: packaging " + argCount + " arguments\n");

    VM_Address addr = argAddress;
    for (int i=0; i<argCount; i++) {
      int loword, hiword;
      hiword = VM_Magic.getMemoryInt(addr);

      // VM.sysWrite("JNI packageParameterFromVarArg:  arg " + i + " = " + hiword + 
      // " or " + VM.intAsHexString(hiword) + "\n");

      addr = addr.add(4);

      // convert and wrap the argument according to the expected type

      if (argTypes[i].isFloatType()) {
	// NOTE:  in VarArg convention, C compiler will expand a float to a double that occupy 2 words
	// so we have to extract it as a double and convert it back to a float
	loword = VM_Magic.getMemoryInt(addr);
	addr = addr.add(4);                       
	long doubleBits = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapFloat((float) (Double.longBitsToDouble(doubleBits)));
	
      } else if (argTypes[i].isDoubleType()) {
	loword = VM_Magic.getMemoryInt(addr);
	addr = addr.add(4);
	long doubleBits = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));

      } else if (argTypes[i].isLongType()) { 
	loword = VM_Magic.getMemoryInt(addr);
	addr = addr.add(4);
	long longValue = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapLong(longValue);

      } else if (argTypes[i].isBooleanType()) {
	// the 0/1 bit is stored in the high byte	
	argObjectArray[i] = VM_Reflection.wrapBoolean(hiword);

      } else if (argTypes[i].isByteType()) {
	// the target byte is stored in the high byte
	argObjectArray[i] = VM_Reflection.wrapByte((byte) hiword);

      } else if (argTypes[i].isCharType()) {
	// char is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapChar((char) hiword);

      } else if (argTypes[i].isShortType()) {
	// short is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapShort((short) hiword);

      } else if (argTypes[i].isReferenceType()) {
	// for object, the arg is a JREF index, dereference to get the real object
	argObjectArray[i] =  env.getJNIRef(hiword);   

      } else if (argTypes[i].isIntType()) {
	argObjectArray[i] = VM_Reflection.wrapInt(hiword);

      } else {
	return null;
      }

    }

    return argObjectArray;
    

  }

  /**
   * Repackage the arguments passed as an array of jvalue into an array of Object,
   * used by the JNI functions CallStatic<type>MethodA
   * @param mth the target VM_Method
   * @param argAddress an address into the C space for the array of jvalue unions;  
   *                   each element is 2-word and holds the argument of the appropriate type
   * @return an Object array holding the arguments wrapped at Objects
   */
  static Object[] packageParameterFromJValue(VM_Method targetMethod, VM_Address argAddress) {
    VM_TypeReference[] argTypes = targetMethod.getParameterTypes();
    int argCount = argTypes.length;
    Object[] argObjectArray = new Object[argCount];

    // get the VM_JNIEnvironment for this thread in case we need to dereference any object arg
    VM_JNIEnvironment env = VM_Thread.getCurrentThread().getJNIEnv();

    // VM.sysWrite("JNI packageParameterFromJValue: packaging " + argCount + " arguments\n");

    for (int i=0; i<argCount; i++) {
	
      VM_Address addr = argAddress.add(8*i);
      int hiword = VM_Magic.getMemoryInt(addr);
      int loword = VM_Magic.getMemoryInt(addr.add(4));

      // VM.sysWrite("JNI packageParameterFromJValue:  arg " + i + " = " + hiword + 
      //	  " or " + VM.intAsHexString(hiword) + "\n");

      // convert and wrap the argument according to the expected type

      if (argTypes[i].isFloatType()) {
	argObjectArray[i] = VM_Reflection.wrapFloat(Float.intBitsToFloat(hiword));

      } else if (argTypes[i].isDoubleType()) {
	long doubleBits = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));

      } else if (argTypes[i].isLongType()) { 
	long longValue = (((long) hiword) << BITS_IN_INT) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapLong(longValue);

      } else if (argTypes[i].isBooleanType()) {
	// the 0/1 bit is stored in the high byte	
	argObjectArray[i] = VM_Reflection.wrapBoolean((hiword & 0xFF000000) >>> 24);

      } else if (argTypes[i].isByteType()) {
	// the target byte is stored in the high byte
	argObjectArray[i] = VM_Reflection.wrapByte((byte) ((hiword & 0xFF000000) >>> 24));

      } else if (argTypes[i].isCharType()) {
	// char is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapChar((char) ((hiword & 0xFFFF0000) >>> 16));

      } else if (argTypes[i].isShortType()) {
	// short is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapShort((short) ((hiword & 0xFFFF0000) >>> 16));

      } else if (argTypes[i].isReferenceType()) {
	// for object, the arg is a JREF index, dereference to get the real object
	argObjectArray[i] =  env.getJNIRef(hiword);   

      } else if (argTypes[i].isIntType()) {
	argObjectArray[i] = VM_Reflection.wrapInt(hiword);

      } else {
	return null;
      }

    }

    return argObjectArray;
    
  }


  /**
   * Given an address in C that points to a null-terminated string,
   * create a new Java byte[] with a copy of the string
   * @param stringAddress an address in C space for a string
   * @return a new Java byte[]
   */
  static byte[] createByteArrayFromC(VM_Address stringAddress) {
    int word;
    int length = 0;
    VM_Address addr = stringAddress;

    // scan the memory for the null termination of the string
    while (true) {
      word = VM_Magic.getMemoryInt(addr);
      int byte0 = ((word >> 24) & 0xFF);
      int byte1 = ((word >> 16) & 0xFF);
      int byte2 = ((word >> 8) & 0xFF);
      int byte3 = (word & 0xFF);
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
      addr = addr.add(4);
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

    byte[] contents = createByteArrayFromC( stringAddress );
    return new String(contents);

  }

  public void dumpJniRefsStack () throws VM_PragmaUninterruptible {
    int jniRefOffset = JNIRefsTop;
    VM.sysWrite("\n* * dump of JNIEnvironment JniRefs Stack * *\n");
    VM.sysWrite("* JNIRefs = ");
    VM.sysWrite(VM_Magic.objectAsAddress(JNIRefs));
    VM.sysWrite(" * JNIRefsTop = ");
    VM.sysWrite(JNIRefsTop);
    VM.sysWrite(" * JNIRefsSavedFP = ");
    VM.sysWrite(JNIRefsSavedFP);
    VM.sysWrite(".\n*\n");
    while ( jniRefOffset >= 0 ) {
      VM.sysWrite(jniRefOffset);
      VM.sysWrite(" ");
      VM.sysWrite(VM_Magic.objectAsAddress(JNIRefs).add(jniRefOffset));
      VM.sysWrite(" ");
      MM_Interface.dumpRef(VM_Address.fromInt(JNIRefs[jniRefOffset >> 2]));
      jniRefOffset -= 4;
    }
    VM.sysWrite("\n* * end of dump * *\n");
  }

}
