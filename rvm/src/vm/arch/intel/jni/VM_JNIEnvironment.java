/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.memoryManagers.vmInterface.MM_Interface;
import java.lang.reflect.*;

/**
 * Platform dependent aspects of the JNIEnvironment.
 *
 * @author Dave Grove
 * @author Ton Ngo
 * @author Steve Smith 
 */
public final class VM_JNIEnvironment extends VM_JNIGenericEnvironment implements VM_JNILinuxConstants {
  
  /**
   * This is the JNI function table, the address of this array will be
   * passed to the native code
   */
  private static INSTRUCTION[][] JNIFunctions;

  /**
   * This is a table of pointers to the shared JNI function table.  All entries 
   * point to the same function table.  Each thread uses the pointer at its thread id
   * offset to allow us to determine a threads id from the pointer it is using.
   * Needed when native calls Java (JNIFunctions) and passes its JNIEnv pointer.
   * Its offset into the JNIFunctionPts array is the same as the threads offset
   * in the Scheduler.threads array.
   */
  static int[] JNIFunctionPointers;        // made public so vpStatus could be set 11/16/00 SES
                                           // maybe need set & get functions ??

  public static void init() {
    // allocate the first dimension of the function array in the boot image so that
    // we have an address pointing to it.  This is necessary for thread creation
    // since the VM_JNIEnvironment object will contain a field pointing to this array

    // An extra entry is allocated, to hold the RVM JTOC 07/01 SES
    JNIFunctions = new INSTRUCTION[FUNCTIONCOUNT+1][];

    // First word is a pointer to the JNIFunction table
    // Second word is address of current processors vpStatus word
    // (JTOC is now stored at end of shared JNIFunctions array)
    JNIFunctionPointers = new int[VM_Scheduler.MAX_THREADS * 2];
  }

  /**
   *  Initialize the array of JNI functions.
   *  To be called from VM_DynamicLibrary.java when a library is loaded,
   *  expecting native calls to be made
   */
  public static void boot() {
    if (initialized)
      return;

    // fill an array of JNI names
    setNames();

    // fill in the IP entries for each AIX linkage triplet
    VM_TypeReference tRef = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), 
							  VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_JNIFunctions;"));
    VM_Class cls = (VM_Class)tRef.peekResolvedType();
    VM_Method[] mths = cls.getDeclaredMethods();
    for (int i=0; i<mths.length; i++) {
      String methodName = mths[i].getName().toString();
      int jniIndex = indexOf(methodName);
      if (jniIndex!=-1) {
	JNIFunctions[jniIndex] = mths[i].getCurrentCompiledMethod().getInstructions();
      } 
    }

    // store RVM JTOC address in last (extra) entry in JNIFunctions array
    // to be restored when native C invokes JNI functions implemented in java
    //
    // following causes exception in checkstore, so forced to setMemoryInt instead
    // JNIFunctions[FUNCTIONCOUNT+1] = VM_Magic.addressAsByteArray(VM_Magic.getTocPointer());
    VM_Magic.setMemoryAddress(VM_Magic.objectAsAddress(JNIFunctions).add(JNIFUNCTIONS_JTOC_OFFSET),
			      VM_Magic.getTocPointer());

    initialized = true;
  }

  /**
   * Create a thread specific JNI environment.
   * @param threadSlot index of creating thread in Schedule.threads array (thread id)
   */
  public VM_JNIEnvironment (int threadSlot) {
    super();
    JNIFunctionPointers[threadSlot * 2] = VM_Magic.objectAsAddress(JNIFunctions).toInt();
    JNIFunctionPointers[(threadSlot * 2)+1] = 0;  // later contains addr of processor vpStatus word
    JNIEnvAddress = VM_Magic.objectAsAddress(JNIFunctionPointers).add(threadSlot*8);
  }

  public INSTRUCTION[] getInstructions(int id) {    
    return JNIFunctions[id];
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
    throws Exception  {

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


    // Package the parameters for the constructor
    VM_Address varargAddress;
    if (isDotDotStyle) 
      // flag is false because this JNI function has 3 args before the var args
      varargAddress = getVarArgAddress(false);    
    else
      varargAddress = argAddress;

    Object argObjs[];
    if (isJvalue)
      argObjs = packageParameterFromJValue(mth, argAddress);
    else
      argObjs = packageParameterFromVarArg(mth, varargAddress);

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
  public static Object invokeWithDotDotVarArg(int methodID, VM_TypeReference expectReturnType)
    throws Exception, 
	   VM_PragmaNoInline, VM_PragmaNoOptCompile { // expect a certain stack frame structure
    VM_Address varargAddress = getVarArgAddress(false);    
    return packageAndInvoke(null, methodID, varargAddress, expectReturnType, false, true);

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
					      VM_TypeReference expectReturnType, 
					      boolean skip4Args)
    throws Exception,
	   VM_PragmaNoInline, VM_PragmaNoOptCompile { // expect a certain stack frame structure

    VM_Address varargAddress = getVarArgAddress(skip4Args);    
    return packageAndInvoke(obj, methodID, varargAddress, expectReturnType, skip4Args, true);

  }

  /**
   * This method supports var args passed from C
   *
   * In the Linux Intel C convention, the caller places the args immediately above the
   * saved return address, starting with the first arg
   *
   *
   *
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
   *  low address
   *
   *   |  fp  | <- VM_JNIEnvironment.getVarArgAddress
   *   | mid  |
   *   |      |
   *   |      |
   *   |------|   
   *   |  fp  | <- VM_JNIEnvironment.invokeWithDotDotVarArg frame
   *   | mid  |
   *   | ...  |
   *   |      |
   *   |      |
   *   |------|   
   *   |  fp  | <- JNI method frame
   *   | mid  |
   *   | ...  |
   *   | arg 0|    args copied by JNI prolog (3 for static, nonvirtual, 
   *   | arg 1|    or 4 for virtual)
   *   | arg 2|
   *   |      |
   *   |      |
   *   |------|
   *   | fp   | <- Native C caller frame
   *   |return|
   *   | arg 0|    
   *   | arg 1|    
   *   | arg 2|
   *   | arg 3|
   *   | arg 4|
   *   | arg 5|
   *   | arg 6|
   *   | arg 7|
   *   | arg 8|    
   *   | arg 9|
   *   |      |
   *   |      |
   *   |      |
   *
   *
   *   high address
   *
   *
   * @param skip4Args if true, the calling JNI function has 4 args before the vararg
   *                  if false, the calling JNI function has 3 args before the vararg
   * @return the starting address of the vararg in the caller stack frame
   */
  private static VM_Address getVarArgAddress(boolean skip4Args) {
    
    VM_Address fp = VM_Magic.getFramePointer();
    fp = VM_Magic.getMemoryAddress(fp);
    fp = VM_Magic.getMemoryAddress(fp);
    return (fp.add(2*4 + (skip4Args ? 4*4 : 3*4)));

  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodV
   * @param methodID the method ID
   * @param argAddress a raw address for the variable argument list
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithVarArg(int methodID, VM_Address argAddress, VM_TypeReference expectReturnType) 
    throws Exception {

    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, true);

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
					VM_TypeReference expectReturnType, boolean skip4Args) 
    throws Exception {

    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, true);

  }

  /**
   * Common code shared by the JNI functions CallStatic<type>MethodA
   * @param methodID id of VM_MemberReference
   * @param argAddress a raw address for the argument array
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithJValue(int methodID, VM_Address argAddress, 
					VM_TypeReference expectReturnType) 
    throws Exception {
    return packageAndInvoke(null, methodID, argAddress, expectReturnType, false, false);
  }

  /**
   * Common code shared by the JNI functions Call<type>MethodA
   * @param obj the object instance 
   * @param methodID id of VM_MemberReference
   * @param argAddress a raw address for the argument array
   * @param expectReturnType the return type for checking purpose
   * @param skip4Args received from the JNI function, passed on to VM_Reflection.invoke()
   * @return an object that may be the return object or a wrapper for the primitive return value 
   */
  public static Object invokeWithJValue(Object obj, int methodID, VM_Address argAddress, 
					VM_TypeReference expectReturnType, boolean skip4Args) 
    throws Exception {

    return packageAndInvoke(obj, methodID, argAddress, expectReturnType, skip4Args, false);

  }

  /**
   * Common code shared by invokeWithJValue, invokeWithVarArg and invokeWithDotDotVarArg
   * @param obj the object instance 
   * @param methodID id of VM_MemberReference
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
					VM_TypeReference expectReturnType, boolean skip4Args, 
					boolean isVarArg) 
    throws Exception,
	   VM_PragmaNoInline, VM_PragmaNoOptCompile { // expect a certain stack frame structure

    VM_Method targetMethod = VM_MemberReference.getMemberRef(methodID).asMethodReference().resolve();
    VM_TypeReference returnType = targetMethod.getReturnType();

    if (VM_JNIFunctions.traceJNI) 
	VM.sysWrite("JNI CallXXXMethod:  (mid " + 
		    methodID + 
		    ") " +
		    targetMethod.getDeclaringClass().toString() + 
		    "." + 
		    targetMethod.getName().toString() + "\n");
    
    if (expectReturnType==null) {   // for reference return type 
      if (!returnType.isReferenceType())
	throw new Exception("Wrong return type for method: expect reference type instead of " + returnType);      
    } else {    // for primitive return type
      if (!returnType.definitelySame(expectReturnType))
	throw new Exception("Wrong return type for method: expect " + expectReturnType + 
			    " instead of " + returnType);
    }  

    // Repackage the arguments into an array of objects based on the signature of this method
    Object[] argObjectArray;
    if (isVarArg) {
      argObjectArray = packageParameterFromVarArg(targetMethod, argAddress);
    } else {
      argObjectArray = packageParameterFromJValue(targetMethod, argAddress);
    }

    // now invoke the method
    Object returnObj = VM_Reflection.invoke(targetMethod, obj, argObjectArray, skip4Args);
    
    return returnObj;

  }

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

      int loword = VM_Magic.getMemoryInt(addr);
      int hiword;

      // VM.sysWrite("JNI packageParameterFromVarArg:  arg " + i + " = " + loword + 
      // " or " + VM.intAsHexString(loword) + "\n");

      addr = addr.add(4);

      // convert and wrap the argument according to the expected type

      if (argTypes[i].isFloatType()) {
	// NOTE:  in VarArg convention, C compiler will expand a float to a double that occupy 2 words
	// so we have to extract it as a double and convert it back to a float
	hiword = VM_Magic.getMemoryInt(addr);
	addr = addr.add(4);                       
	long doubleBits = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapFloat((float) (Double.longBitsToDouble(doubleBits)));
	
      } else if (argTypes[i].isDoubleType()) {
	hiword = VM_Magic.getMemoryInt(addr);
	addr = addr.add(4);
	long doubleBits = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));

      } else if (argTypes[i].isLongType()) { 
	hiword = VM_Magic.getMemoryInt(addr);
	addr = addr.add(4);
	long longValue = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapLong(longValue);

      } else if (argTypes[i].isBooleanType()) {
	// the 0/1 bit is stored in the high byte		
	argObjectArray[i] = VM_Reflection.wrapBoolean(loword);

      } else if (argTypes[i].isByteType()) {
	// the target byte is stored in the high byte
	argObjectArray[i] = VM_Reflection.wrapByte((byte) loword);

      } else if (argTypes[i].isCharType()) {
	// char is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapChar((char) loword);

      } else if (argTypes[i].isShortType()) {
	// short is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapShort((short) loword);

      } else if (argTypes[i].isReferenceType()) {
	// for object, the arg is a JREF index, dereference to get the real object
	argObjectArray[i] =  env.getJNIRef(loword);   

      } else if (argTypes[i].isIntType()) {
	argObjectArray[i] = VM_Reflection.wrapInt(loword);

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

    VM_Address addr = argAddress;
    for (int i=0; i<argCount; i++, addr = addr.add(8)) {

      int loword = VM_Magic.getMemoryInt(addr);
      int hiword;

      // VM.sysWrite("JNI packageParameterFromJValue:  arg " + i + " = " + loword + 
      //  " or " + VM.intAsHexString(loword) + ", at address " + 
      //	  VM.intAsHexString(addr) + "\n");

      // convert and wrap the argument according to the expected type

      if (argTypes[i].isFloatType()) {
	argObjectArray[i] = VM_Reflection.wrapFloat(Float.intBitsToFloat(loword));

      } else if (argTypes[i].isDoubleType()) {
	hiword = VM_Magic.getMemoryInt(addr.add(4));
	long doubleBits = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapDouble(Double.longBitsToDouble(doubleBits));

      } else if (argTypes[i].isLongType()) { 
	hiword = VM_Magic.getMemoryInt(addr.add(4));
	long longValue = (((long) hiword) << 32) | (loword & 0xFFFFFFFFL);
	argObjectArray[i] = VM_Reflection.wrapLong(longValue);

      } else if (argTypes[i].isBooleanType()) {
	// the 0/1 bit is stored in the high byte	
	argObjectArray[i] = VM_Reflection.wrapBoolean(loword & 0x000000FF);

      } else if (argTypes[i].isByteType()) {
	// the target byte is stored in the high byte
	argObjectArray[i] = VM_Reflection.wrapByte((byte) (loword & 0x000000FF));

      } else if (argTypes[i].isCharType()) {
	// char is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapChar((char) (loword & 0x0000FFFF));

      } else if (argTypes[i].isShortType()) {
	// short is stored in the high 2 bytes
	argObjectArray[i] = VM_Reflection.wrapShort((short) (loword & 0x0000FFFF));

      } else if (argTypes[i].isReferenceType()) {
	// for object, the arg is a JREF index, dereference to get the real object
	argObjectArray[i] =  env.getJNIRef(loword);   

      } else if (argTypes[i].isIntType()) {
	argObjectArray[i] = VM_Reflection.wrapInt(loword);

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
      int byte3 = ((word >> 24) & 0xFF);
      int byte2 = ((word >> 16) & 0xFF);
      int byte1 = ((word >> 8) & 0xFF);
      int byte0 = (word & 0xFF);
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
      MM_Interface.dumpRef(VM_Address.fromInt(JNIRefs[ jniRefOffset >> 2 ]));
      jniRefOffset -= 4;
    }
    VM.sysWrite("\n* * end of dump * *\n");
  }

}
