/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 *   This class extends the InterpreterBase by adding methods that are specific
 * to environment, e.g. access methods through ptrace for remote process or 
 * methods to read coredump.
 * @author John Barton
 *
 */

import com.ibm.JikesRVM.*;
import java.lang.reflect.*;

class RemoteInterpreter extends InterpreterBase implements JDPServiceInterface
{
  static String jbiFileName = "~/jvmBuild/RVM.map";

  /**
   * Dummy constructor for the PublicizingClassLoader
   */
  public RemoteInterpreter() {
    String buildDir = System.getProperty("buildDir");
    if (buildDir!=null) {
      jbiFileName = buildDir + "/RVM.map";
    }
  }

  /**
   * Interpret a "main()" method for a class.
   * @param args: class_name arg0, arg1, ...
   */
  public static void main(String args[]) throws Exception  {
    if (args.length <= 0) usage();  // does not return.

    RemoteInterpreter c = new RemoteInterpreter();
    
    // Define the address mapper in VM_Magic 
    // (to redefine objectAsAddress and addressAsObject)
    // VM_Magic.setObjectAddressRemapper(new addressRemapper());
    
    // pick up any command line argument for the interpreter
    for (int i = 0; i < args.length; i++)  {      
      if (args[i].equals("-i")) {
        jbiFileName = args[i+1].substring(0,args[i+1].indexOf(".image")) + ".map";
      }
    }

    // initialize the mapVM class if we are doing remote reflection for jdp:
    // this needs to be done after the class load/resolve in InterpreterBase.init()
    // because of reflective calls to compute offset constants
    if (args[0].equals("jdp")) 
      mapVM.init();

    
    // look for the main method to start
    VM_Class cls = null;
    try
      {
	cls = InterpreterBase.forName(args[0]);
      }
    catch (VM_ResolutionException e)
      { // no such class
	e.printStackTrace();
	VM.sysWrite(e.getException() + "\n");
	VM.sysExit(1);
      }
    
    VM_Method mainMethod     =  cls.findMainMethod();
    
    if ( mainMethod == null )
      { // no such method
	VM.sysWrite(cls.getName() + " doesn't have a \"public static void main(String[])\" method to execute");
	VM.sysExit(1);
      }
    
    // create "main" argument list
    //
    String[] mainArgs = new String[args.length - 1];
    for (int i = 0, n = mainArgs.length; i < n; ++i)
      mainArgs[i] = args[i + 1];
    
    RemoteInterpreter interpreter = new RemoteInterpreter();
    
    if (traceInterpreter >= 1) System.out.println("ReflectiveInterpreter: interpreting "+mainMethod);
    
    interpreter.interpretMethod(mainMethod, mainArgs);
    
  }

  /**
   * Print usage message and exit.
   */
  
  public static void usage() {
    System.err.println("RemoteInterpreter.main: class_with_main, args for main");
    System.exit(-1);
  }


  //
  // -- implemention of abstract methods of VM_InterperterBase
  //
  protected int getStaticWord(int index) {
    if (traceInterpreter >= 1) 
      System.out.println("RemoteInterpreter: VM_Statics.getSlotContentsAsInt("+index+") (" +
			 VM_Statics.getSlotDescriptionAsString(index)+")");
    return VM_Statics.getSlotContentsAsInt(index);
  }

 
  protected Object _new(VM_Class klass) {
    // Original code:
    // try {
    // 	 klass.load();
    // 	 klass.resolve();
    // 	 klass.instantiate();
    // }
    // catch (VM_ResolutionException e) {
    // 	 _throwException(e);
    // }
    // 
    // int size = klass.getInstanceSize();
    // Object[] tib = klass.getTypeInformationBlock();
    // Object obj = VM_Runtime.quickNewScalar(size, tib);
    
    // Save on the stack a marker type containing the bytecode index 
    // and the class to create.
    int bcIndex = getByteCodeContext().getCurrentIndex();
    unInitType obj = new unInitType(bcIndex, klass);
    // System.out.println("  new for " + klass + " at bytecode " + bcIndex);

    return (Object) obj;
  }

  // Moved to java/lang/reflect, so now invoke there
  protected Object _newarray(VM_Array array, int num)   {
    VM_Type elementType = array.getElementType();
    Class eclass = InterpreterBase.getClassForVMType(elementType);
    return java.lang.reflect.Array.newInstance(eclass, num);   
  }
   

  /**
   *  Create an array of reference that will later contain
   * mapped object.  The correct element type will be stored in 
   * these mapped object 
   * (we may have problem if some bytecode try to get the type for 
   * this array)
   */
  Object X_newarray(int num) {
    if (traceExtension)
      System.out.println("X_newarray:  array of mapped object, " + num);
    return java.lang.reflect.Array.newInstance(mapVM.mapClass, num);   
  }


  /**
   * See if this method belongs to the classes that are not 
   * publicized by the Publicizing class loader.  If so, we need to invoke
   * the method directly by reflection because it may later invoke
   * private method in the class that would not be found by 
   * reflection.
   * @param called_method the method to be invoked
   * @return true if the method has been invoked reflectively,
   *         false otherwise
   * @see PublicizingClassLoader
   */
  protected boolean isNonPublicized(VM_Method called_method) {
    Class cls = getClassForVMType(called_method.getDeclaringClass());
    return isNonPublicized(cls);
  }

  protected boolean isNonPublicized(Class cls) {

    String called_method_classname = cls.getName().toString();      
    if (called_method_classname.startsWith("java.lang.") ||
	called_method_classname.startsWith("java.io.") ||
	called_method_classname.startsWith("java.net.") ||
	called_method_classname.startsWith("java.util") ||
	called_method_classname.startsWith("Platform")) {
	  return true;
    } else {
    	 return false;
    }


    // This works if we are using the class loader
    // If the class for this method was not loaded with the publicizing class loader
    // then the fields are not made all public
    // ClassLoader clsLoader = cls.getClassLoader();
    // if (clsLoader==null) {
    // 	 return true;    
    // } else {
    // 	 return false;
    // }
  }
    

  /**
   * Invoke a method directly by reflection:  this version is used in most case
   * <ul>
   * <li> find the reflected method
   * <li> pop the parameters from the stack and wrap them
   * <li> invoke
   * <li> unwrap the result and push on the stack
   * </ul>
   * 
   */
  protected void invokeReflective(VM_Method calledMethod) {
    VM_Class calledClass = calledMethod.getDeclaringClass();
    Class calledJavaClass = InterpreterBase.getClassForVMType(calledClass);
    invokeReflective(calledJavaClass, calledMethod);
  }

  /**
   * Invoke a method directly by reflection:  this version is used for invokevirtual 
   * or invokespecial when the object
   * is an instance of a class that was not loaded with the PublicizingClassLoader
   * This includes the classes java.lang, java.io, java.util.zip, and also any
   * static objects in these classes.  We cannot use the class retrieved from the method
   * because the class loader will be different between the object and any reflective
   * info on the object:  for the object, the class loader will be null and for the 
   * reflection (e.g. Field, ...) the class loader will be PublicizingClassLoader.
   * The JDK will fail with a message that the types are different.
   */
  protected void invokeReflective(Class calledJavaClass, VM_Method calledMethod) {
    String calledMethodName = calledMethod.getName().toString();
 
    // Convert the list of argument from VM_Types to Java classes 
    VM_Type callArgTypes[] = calledMethod.getParameterTypes();
    Class javaTypes[] = new Class[callArgTypes.length];
    for (int i=0; i<callArgTypes.length; i++) 
      javaTypes[i] = InterpreterBase.getClassForVMType(callArgTypes[i]);

    try {
      // Get the Java reflection method from VM_Method by lookup
      //java.lang.reflect.Method javaMethod = calledJavaClass.getMethod(calledMethodName, javaTypes);
      // java.lang.reflect.Method javaMethod = calledJavaClass.getDeclaredMethod(calledMethodName, javaTypes);
      // javaMethod.setAccessible(true);
      java.lang.reflect.Method javaMethod = getMethodAnywhere(calledJavaClass, calledMethodName, javaTypes);
      if (javaMethod==null)
	throw new NoSuchMethodException();
      // System.out.println("invokeReflective: method " + javaMethod);
      
      // build the parameter array from the stack
      Object invokeArgs[] = new Object[callArgTypes.length];
      for (int i=callArgTypes.length-1; i>=0; i--) {
	invokeArgs[i] = stack.popAsObject(callArgTypes[i]); 
      }
       
      // get the object instance from the stack if the method is not static
      Object objectInstance;
      if (calledMethod.isStatic())
	objectInstance = null;        // don't need the object instance
      else {             
	objectInstance = stack.popObject();
      }

      // invoke the method by reflection
      Object result = javaMethod.invoke(objectInstance, invokeArgs);

      // if we have value returned, put it back on the stack
      // System.out.println("invokeReflective: " + javaMethod + " returns " + result);
      VM_Type vmReturnType = calledMethod.getReturnType();
      if (result!=null) {
	stack.pushObjectUnwrapped(result, vmReturnType);
      } else if (!vmReturnType.isPrimitiveType()) {
	stack.push(null);
      }

      return;

    } catch (IllegalAccessException e1) {
      System.out.println("invokeReflective: fail to invoke method " + calledMethodName + ", IllegalAccessException " + e1.getMessage());

    } catch (InvocationTargetException e2) {
      // the reflected method throws an exception
      // unwrap the exception and pass it on to the program 
      if (traceInterpreter >= 1)
	println("exception caught in invokeReflective " + calledMethodName + ", " + e2.getTargetException());
      Throwable realEx = e2.getTargetException();
      _throwException(realEx);

    } catch (NoSuchMethodException e3) {
      System.out.println("invokeReflective: no method matching " + calledMethod + ", NoSuchMethodException " + e3.getMessage());
    }

  }

  /**
   * Check for the recognized magic method, 
   * pop the stack to get the required parameters,
   * call the appropriate VM_Magic method directly,
   * then push the result back on the stack
   */
  protected void invokeMagic(VM_Method called_method)    {
      if (sysCall1 == null) super.initInterpreterBase();  /// TODO: convince VM to do this

      if (traceInterpreter >= 1) System.out.println("RemoteInterpreter: invokeMagic on "+called_method);
      
      VM_Atom methodName = called_method.getName();
     
// 	 if (methodName == sysCall0)
// 	    {
// 	    int toc = stack.popInt();
// 	    int ip = stack.popInt();
// 	    if (traceInterpreter) System.out.println("RemoteInterpreter.sysCall0: ip="+ip+" toc="+toc);
// 	    int result = VM_Magic.sysCall0(ip, toc);  // this call will be inlined by the compiler.
// 	    stack.push(result);
// 	    }
// 	 else if (methodName == sysCall1) 
// 	   {
// 	    int parameter = stack.popInt();  // args to sysCall1 are pushed left to right, so pop right to left.
// 	    int toc = stack.popInt();
// 	    int ip = stack.popInt();
// 	    if (traceInterpreter) System.out.println("RemoteInterpreter.sysCall1: ip="+ip+" toc="+toc+" parameter="+parameter);
// 	    int result = VM_Magic.sysCall1(ip, toc, parameter);  // this call will be inlined by the compiler.
// 	    stack.push(result);
// 	    }
// 	 else if (methodName == sysCall2) 
// 	    {
// 	    int parameter2 = stack.popInt();  // args to sysCall1 are pushed left to right, so pop right to left.
// 	    int parameter1 = stack.popInt();  // args to sysCall1 are pushed left to right, so pop right to left.
// 	    int toc = stack.popInt();
// 	    int ip = stack.popInt();
// 	    if (traceInterpreter) System.out.println("RemoteInterpreter.sysCall2: ip="+ip+" toc="+toc+" parameters="+parameter1+","+parameter2);
// 	    int result = VM_Magic.sysCall2(ip, toc, parameter1, parameter2);  // this call will be inlined by the compiler.
// 	    stack.push(result);
// 	    }
// 	 else if (methodName == sysCall3) 
// 	    {
// 	    int parameter3 = stack.popInt(); 
// 	    int parameter2 = stack.popInt(); 
// 	    int parameter1 = stack.popInt(); 
// 	    int toc = stack.popInt();
// 	    int ip = stack.popInt();
// 	    if (traceInterpreter) System.out.println("RemoteInterpreter.sysCall3: ip="+ip+" toc="+toc+" parameters= "+
// 						     parameter1+", "+parameter2+", "+parameter3);
// 	    int result = VM_Magic.sysCall3(ip, toc, parameter1, parameter2, parameter3);  // this call will be inlined by the compiler.
// 	    stack.push(result);
// 	    }
      if (methodName == floatAsIntBits)
	 {
	 stack.push(Float.floatToIntBits(stack.popFloat()));
	 }
      else if (methodName == longBitsAsDouble)
	 {
	 stack.push(Double.longBitsToDouble(stack.popLong()));
	 }
      else if (methodName == doubleAsLongBits)
	 {
	 stack.push(Double.doubleToLongBits(stack.popDouble()));
	 }
      else if (methodName == objectAsAddress)
	 {
	 stack.push(VM_Magic.objectAsAddress(stack.popObject())); 
	 }
      else if (methodName == getFramePointer)
	 {
	 System.out.println("RemoteInterpreter: getFramePointer cannot be implemented");
	 System.out.println(debug_methods_called());
	 InterpreterBase.assert(NOT_IMPLEMENTED);
	 }
      else if (methodName == getMemoryWord)
	 {
	 VM_Address address = VM_Address.fromInt(stack.popInt());
	 System.out.print("RemoteInterpreter: VM.Magic.getMemoryWord("+Integer.toHexString(address.toInt())+")=");
	 int word = VM_Magic.getMemoryWord(address);
	 System.out.println(Integer.toHexString(word));
	 stack.push(word);
	 }
      else if (methodName == getObjectType)
	 {
	 // VM_Type getObjectType(Object object);
	 Object obj = stack.popObject();
	 InterpreterBase.assert(obj != null);
	 // System.out.print("RemoteInterpreter: VM.Magic.getObjectType("+obj+")=");
	 VM_Type the_type = VM_Magic.getObjectType(obj);
	 stack.push(the_type);
	 // System.out.println(the_type);
	 }
      else if (methodName == isync)
	{
	// Nothing to do 
	  //  System.out.println("isync is being skipped");
	}
      else if (methodName == addressFromInt)
	{
	  stack.push(VM_Address.fromInt(stack.popInt())); 
	}
      else if (methodName == addressToInt)
	{
	  Object obj = stack.popObject();
	  stack.push(((VM_Address)obj).toInt());
	}
      else if (methodName == addressAdd)
	{
	  int value = stack.popInt();
	  Object obj = stack.popObject();
	  InterpreterBase.assert(obj != null);
	  stack.push(((VM_Address)(obj)).add(value));
	}
      else
	 {
	 System.out.println(called_method+" not implemented");
	 InterpreterBase.assert(NOT_IMPLEMENTED);
	 }
  }

  protected VM_StackTrace[] extendStackTrace(VM_StackTrace base[])  {
    int size = base.length;
    int extension = 1;    // TEMP!!
    VM_StackTrace[] extended = new VM_StackTrace[size + extension];
    for (int i = 0; i < base.length; i++)
      {
	extended[i] = base[i];
      }
    
    // TEMP
    extended[size] = new VM_StackTrace();
    
    // NOTE:  removed because of change in VM_StackTrace
    // extended[size].method = extended[size-1].method;
    extended[size].instructionOffset = extended[size-1].instructionOffset;
    return extended;
  }

  /**
   * If current class is mapped, get the constant pool index from the bytecode and 
   * the mapped VM_Class for the current class (class declaring this method).  
   * Read the entry in the constant pool which should be a JTOC index for an int, 
   * float or String object.  For int and float, read the constant value from the JVM 
   * and push onto the stack.  For String, create a remote object and push onto the stack
   *   .. -> item 
   *    The sequence:
   *  (1) mapVM.manualTypeSearch(className)          -> VM_Class object   
   *  (2) VM_Class object + constantPoolOffset       -> constantPoolArray
   *  (3) constantPoolArray [ constantPoolIndex ]    -> JTOC_offset for long or double
   *  (4) VM_Statics.slots + JTOC_offset             -> address of long or double value
   *  (5) find the type using the data structure in the interpreter
   */
  void X_ldc(VM_Class cls, int poolIndex) {
    if (traceInterpreter >= 2) 
      System.out.println("X_ldc: load from constant pool index " + poolIndex);

    VM_Class currentClass = getCurrentClass();

    // (1) Find the VM_Class from the dictionary
    String currentClassName = currentClass.getDescriptor().toString();
    if (traceExtension)	
      System.out.println("X_ldc: " + currentClassName);
    int addr = mapVM.manualTypeSearch(currentClassName);
    // System.out.println("X_ldc: dictionary look up, VM_Class @" + Integer.toHexString(addr));

    // (2) Compute pointer to the class constant pool array
    addr = Platform.readmem(addr + mapVM.VMClassConstantPool_offset);
    // System.out.println("X_ldc: class constant pool @ " + Integer.toHexString(addr) +
    // 		       ", index " + poolIndex);

    // (3) Read the constant pool entry: get an offset into the JTOC
    addr = Platform.readmem(addr + (poolIndex<<2));
    // System.out.println("X_ldc:  JTOC offset " + Integer.toHexString(addr));

    // (4) Get the address of the VM_Statics.slots that holds this constant
    addr = mapVM.getJTOC() + (addr<<2);
    // System.out.println("X_ldc:  JTOC entry @ " + Integer.toHexString(addr));
    int mappedFieldValue  = Platform.readmem(addr);

    int desc = cls.getLiteralDescription(poolIndex);
    if (desc == VM_Statics.INT_LITERAL) {
      stack.push(mappedFieldValue);
    } else if (desc == VM_Statics.FLOAT_LITERAL) {
      stack.push( Float.intBitsToFloat(mappedFieldValue));
    } else if (desc == VM_Statics.STRING_LITERAL) {
      // System.out.println("X_ldc: string literal @ " + Integer.toHexString(mappedFieldValue));
      stack.push( new mapVM(VM_Type.JavaLangStringType, mappedFieldValue, 4));
    } else {
      System.out.println("X_ldc: unexpected type for constant, should be int, float or String");
      debug();
    }
  }

  /**
   * If current class is mapped, get the constant pool index from the bytecode and 
   * the mapped VM_Class for the current class (class declaring this method).  
   * Read the entry in the constant pool which should be a JTOC index for a long
   * or double.  Read the constant value from the JVM and push onto the stack
   *   .. -> item 
   *    The sequence:
   *  (1) mapVM.manualTypeSearch(className)          -> VM_Class object   
   *  (2) VM_Class object + constantPoolOffset       -> constantPoolArray
   *  (3) constantPoolArray [ constantPoolIndex ]    -> JTOC_offset for long or double
   *  (4) VM_Statics.slots + JTOC_offset             -> address of long or double value
   *  (5) find the type using the data structure in the interpreter
   */
  void X_ldc2_w(VM_Class cls, int poolIndex) {
    if (traceInterpreter >= 2) 
      System.out.println("X_ldc2_w: new implementation");

    VM_Class currentClass = getCurrentClass();

    // (1) Find the VM_Class from the dictionary
    String currentClassName = currentClass.getDescriptor().toString();
    if (traceExtension)	
      System.out.println("X_ldc2_w: " + currentClassName);
    int addr = mapVM.manualTypeSearch(currentClassName);
    // System.out.println("X_ldc2_w: dictionary look up, VM_Class @" + Integer.toHexString(addr));

    // (2) Compute pointer to the class constant pool array
    addr = Platform.readmem(addr + mapVM.VMClassConstantPool_offset);
    // System.out.println("X_ldc2_w: class constant pool @ " + Integer.toHexString(addr) +
    //		       ", index " + poolIndex);

    // (3) Read the constant pool entry: get an offset into the JTOC
    addr = Platform.readmem(addr + (poolIndex<<2));
    // System.out.println("X_ldc2_w:  JTOC offset " + Integer.toHexString(addr));

    // (4) Get the address of the VM_Statics.slots that holds this constant
    addr = mapVM.getJTOC() + (addr<<2);
    // System.out.println("X_ldc2_w:  JTOC entry @ " + Integer.toHexString(addr));

    
    //int mappedFieldValue  = Platform.readmem(addr);
    //int mappedFieldValue1 = Platform.readmem(addr+4);

    int desc = cls.getLiteralDescription(poolIndex);
    if (desc == VM_Statics.DOUBLE_LITERAL) {
      double mappedFieldValue = Platform.readDouble(addr);
      if (InterpreterBase.traceExtension)
	System.out.println("X_ldc2_w: double ");
      stack.push(mappedFieldValue);
      // stack.pushDoubleBits(mappedFieldValue, mappedFieldValue1);
    } else if (desc == VM_Statics.LONG_LITERAL) {
      long mappedFieldValue = Platform.readLong(addr);
      if (InterpreterBase.traceExtension)
	System.out.println("X_ldc2_w: long ");
      stack.push(mappedFieldValue);
      // stack.pushLongBits(mappedFieldValue, mappedFieldValue1);
    } else {
      System.out.println("X_ldc2_w: unexpected type for constant, should be long or double");
      debug();
    }

  } 

  /**
   * Load array element
   *
   *
   */
  
  void X_arrayload(int opcode, Object mArray, int index) {
    mapVM mappedArray = (mapVM) mArray;
    // if (traceExtension)
    // System.out.println("X_arrayload: " + mappedArray + " at index " + index);

    if (mappedArray.getAddress() == 0) 
      _throwException( new NullPointerException());
    
    // Check bounds
    if (index<0)
      _throwException(new ArrayIndexOutOfBoundsException("negative index"));    
    int realArrayLength = Platform.readmem(mappedArray.getAddress() + VM_ObjectModel.getArrayLengthOffset() );
    if (index >= realArrayLength)
      _throwException(new ArrayIndexOutOfBoundsException("index="+index+" >= "+realArrayLength));

    // Get element from the VM side
    
    switch(opcode) 
      {
      case 0x2e:  /* --- iaload --- */ { 
	// array of int:  get the 4-bytes integer value
	if (traceExtension)	
	  System.out.println("X_arrayload: iaload " + mappedArray + " at index " + index);
	int   element = Platform.readmem(mappedArray.getAddress() + index*4);
	stack.push(element);	       
	break; 			       
      }				       
      case 0x2f:  /* --- laload --- */ { 
	// array of long:  get the 8-bytes value and convert to long
	if (traceExtension)	
	  System.out.println("X_arrayload: laload " + mappedArray + " at index " + index);
	int loword = Platform.readmem(mappedArray.getAddress() + index*8);
	int hiword = Platform.readmem(mappedArray.getAddress() + index*8 + 4);
	long element = twoIntsToLong(hiword, loword);
	stack.push(element);	       
	break; 			       
      }				       
      case 0x30:  /* --- faload --- */ { 
	// array of float:  get the 4-bytes value and convert to float
	if (traceExtension)	
	  System.out.println("X_arrayload: faload " + mappedArray + " at index " + index);
	int   data = Platform.readmem(mappedArray.getAddress() + index*4);
	float element = Float.intBitsToFloat(data);
	stack.push(element);	       
	break; 			       
      }				       
      case 0x32:  /* --- aaload --- */ { 
	// array of object:  update the mapVM wrapper and put it back on the stack
	if (traceExtension)	
	  System.out.println("X_arrayload: aaload " + mappedArray + " at index " + index);
	mapVM newMapped = new mapVM(mappedArray.getType().asArray().getElementType(),
				    Platform.readmem(mappedArray.getAddress() + index*4),
				    mapVM.PointerSize);
	stack.push(newMapped);	       
	break; }		       
      case 0x31:  /* --- daload --- */ { 
	// array of double:  get the 8-bytes value and convert to double
	if (traceExtension)	
	  System.out.println("X_arrayload: daload " + mappedArray + " at index " + index);
	int loword = Platform.readmem(mappedArray.getAddress() + index*8);
	int hiword = Platform.readmem(mappedArray.getAddress() + index*8 + 4);
	double element = Double.longBitsToDouble(twoIntsToLong(hiword, loword));
	stack.push(element);	
	break; 
      }
      case 0x33:  /* --- baload --- */ {
	// array of byte or boolean:  get one byte value
	if (traceExtension)	
	  System.out.println("X_arrayload: baload " + mappedArray + " at index " + index);
	byte element = Platform.readByte(mappedArray.getAddress() + index);
	if (mappedArray.getType().asArray().getElementType().isBooleanType()) { 
	  stack.push(element==0?0:1);	
	} else { 
	  stack.push(element);	
	}
	break;
      }
      case 0x34:  /* --- caload --- */ { 
	// array of char:  get one byte value
	if (traceExtension)	
	  System.out.println("X_arrayload: caload " + mappedArray + " at index " + index);
	char element = (char) Platform.readByte(mappedArray.getAddress() + index);
	stack.push(element);	
	break; 
      }
      case 0x35:  /* --- saload --- */ { 
	// array of short:  get 2-bytes value
	if (traceExtension)	
	  System.out.println("X_arrayload: saload " + mappedArray + " at index " + index);
	short element = Platform.readShort(mappedArray.getAddress() + index*2);
	stack.push(element);	
	break; 
      }
      }
  }


  /**
   * Unwrap mapped object to compare
   */
  boolean X_if_acmpeq(Object val1, Object val2) {
    if (traceExtension)	
      System.out.println("X_if_acmpeq: " + val1 + ", " + val2);    
    if (((mapVM) val1).getAddress() == ((mapVM) val2).getAddress())
      return true;
    else
      return false;
  }

  /**
   * Check if primitive, get from JVM 
   * 	 .. -> value 
   *    Get the constant pool index from the bytecode and the mapped VM_Class for the 
   * current class (class declaring this method).  Read the entry in the constant pool
   * and access the specified static field.
   *    For this case, we find the mapped current class through the method ID of the 
   * method being executed (on the stack).  The drawback is that the method ID may not
   * be valid if we are single stepping in the debugger and we stop at a bad point
   * in the prolog code.  The sequence is similar to that for X_getfield except that
   * -we have to look up the VM_Clas manually since we don't have a handle to the class
   * -the offset is for JTOC register
   * -the field type can be found externally since it's static
   *    The sequence
   *  (1) mapVM.manualTypeSearch(className)          -> VM_Class object   
   *  (2) VM_Class object + constantPoolOffset       -> constantPoolArray
   *  (3) constantPoolArray [ constantPoolIndex ]    -> VM_Field_index
   *  (4) VM_Field_index + VM_FieldDictionary.values -> VM_Field pointer
   *
   *  (5a) VM_Field pointer + VM_FieldOffset_offset  -> offset value
   *  (5b) VM_Statics.slots + offset value           -> field value or pointer to object
   *
   *  (6a) find the type using the data structure in the interpreter
   *
   *
   */
  void X_getstatic() {
    // if (traceInterpreter >= 2) 
    VM_Class currentClass = getCurrentClass();
    int index = byte_codes.fetch2BytesUnsigned();      

    // (1) Find the VM_Class from the dictionary
    String currentClassName = currentClass.getDescriptor().toString();
    if (traceExtension)	
      System.out.println("X_getstatic: " + currentClassName);
    int addr = mapVM.manualTypeSearch(currentClassName);
    // System.out.println("X_getstatic: dictionary look up, VM_Class @" + Integer.toHexString(addr));

    // (2) Compute pointer to the class constant pool array
    addr = Platform.readmem(addr + mapVM.VMClassConstantPool_offset);
    // System.out.println("X_getstatic: class constant pool @ " + Integer.toHexString(addr));

    // (3) Read the constant pool entry: get an index to the VM_Field dictionary
    addr = Platform.readmem(addr + index*4);
    // System.out.println("X_getstatic: VM_Field dictionary index " + addr);

    // (4) Read the VM_Field dictionary entry: get a VM_field object
    addr = Platform.readmem(addr*4 + mapVM.FieldDictionary_values);
    // System.out.println("X_getstatic: VM_Field @ " + Integer.toHexString(addr));

    // (5a) Get the byte offset to the desired field of the mapped object
    int mappedFieldOffset = Platform.readmem(addr + mapVM.VMFieldOffset_offset);
    // System.out.println("X_getstatic: VM_Statics offset "  + mappedFieldOffset);


    // (5b) Get the address of the VM_Statics.slots that holds this static field
    addr = mapVM.getJTOC() + mappedFieldOffset;
    
    // (6) For the type, we can just refer to the data structure in the interpreter
    // since this is a static field
    int si = getCurrentClass().getMethodRefId(index);     // constant pool to method dictionary 
    VM_Field field = VM_FieldDictionary.getValue(si);
    VM_Type fieldType = field.getType();
    
    // Finally, handle the field depending on its type
    
    // (1) if the static field is a primitive value, find its size, 
    // read the actual value from the JTOC on the JVM side,
    // convert it to the correct type and put it on the stack
    // To find the field size, we refer back to the VM_Field in the external side
    if (fieldType.isPrimitiveType()) {
      int size = field.getSize();
      mapVM newMappedPrimitive = new mapVM(fieldType, addr, size);
      try {
        stack.pushMappedPrimitiveToStack(newMappedPrimitive);
      } catch (Exception e) {
        // do we have a problem extracting from the mapped JVM side?
        System.out.println("X_getstatic: " + e.getMessage());
        debug();
      }
      return;
      
    } 
    // (2) If the static field is an array or reference, create a mapped object 
    // to represent it
    else {
      addr = Platform.readmem(addr);
      mapVM mappedField = new mapVM(field.getType(), addr, 4);
      stack.push(mappedField);
      // System.out.println("X_getstatic: " + mappedField);
    }

  } 


  /**
   *   Given a mapped object, get the constant pool index from the bytecode
   * and access the specified field of the object.  If the field is primitive, 
   * read the value and put it on the stack in the appropriate type. If the field
   * is an object, wrap the object with its address as a mapVM object and put it
   * on the stack.
   *   Steps for accessing the field:
   *  (1) objAddress + TIB_offset                    -> pointer to VM_Class object 
   *  (2) VM_Class object + constantPoolOffset       -> constantPoolArray
   *  (3) constantPoolArray + constantPoolIndex      -> VM_Field_index
   *  (4) VM_Field_index + VM_FieldDictionary.values -> VM_Field pointer
   *  (5) VM_Field pointer + VM_FieldOffset_offset   -> offset value
   *  (6) VM_Field pointer + VM_FieldType_offset     -> VM_Type object
   *  (7) VM_Type object + VM_TypeDescriptor_offset  -> VM_Atom object
   *  (8) VM_Atom object + VM_AtomVal_offset         -> type descriptor as byte array of char
   *  (9) objAddress + offset value                  -> field value or pointer to object
   * 
   *    Action on the stack: an updated mapVM object for a reference, 
   * or the actual value for a primitive value 
   * 	object -> value  
   */
  void X_getfield(Object ref) {
    int index = byte_codes.fetch2BytesUnsigned();      
    mapVM mappedObject = (mapVM)ref;
    // if (traceInterpreter >= 2) 
    // System.out.println("X_getfield: constant pool index " + index + " of current class " + getCurrentClass() + ", for mapped object " + mappedObject);

    // (1) Compute pointer to the VM_Type, which should be VM_Class since we expect an object
    int addr = mapVM.manualTypeSearch(getCurrentClass().getDescriptor().toString());
    // System.out.println("X_getfield: candidate object VM_Class @ " + Integer.toHexString(addr));

    // (1a) This may not be the right VM_Class because the method may belong to the superclass
    // so check the class name and traverse up the class hierarchy if necessary
    String declaringClassName = getCurrentClass().getDescriptor().toString();    
    // System.out.println("DeclaringClassName " + declaringClassName);

    // This should be an infinite loop, but use a count for now to be sure
    for (int i=0; i<20; i++) {  
      int nameAddr =  Platform.readmem(addr + mapVM.VMClassName_offset);
      String realName = mapVM.getVMAtomString(nameAddr);
      // System.out.println("realName " + realName);
      if (declaringClassName.equals(realName)) {
	break;
      } else {
	addr = Platform.readmem(addr + mapVM.VMClassSuper_offset);
	if (addr==0) {
	  // System.out.println("X_getfield: ERROR, cannot find class " + declaringClassName + " in the class hierarchy for this object");
	  break;
	}
      }
    }
    // System.out.println("X_getfield: actual object VM_Class @ " + Integer.toHexString(addr));

    // (2) Compute pointer to the class constant pool array
    addr = Platform.readmem(addr + mapVM.VMClassConstantPool_offset);
    // System.out.println("X_getfield: class constant pool @ " + Integer.toHexString(addr));

    // (3) Read the constant pool entry: get an index to the VM_Field dictionary
    addr = Platform.readmem(addr + index*4);
    // System.out.println("X_getfield: VM_Field dictionary index " + addr);

    // (4) Read the VM_Field dictionary entry: get a VM_field object
    addr = Platform.readmem(addr*4 + mapVM.FieldDictionary_values);
    // System.out.println("X_getfield: VM_Field @ " + Integer.toHexString(addr));

    // (5) Get the offset to the desired field of the mapped object
    int mappedFieldOffset = Platform.readmem(addr + mapVM.VMFieldOffset_offset);
    // System.out.println("X_getfield: field offset "  + mappedFieldOffset);

    // (6) Get the type of the desired field of the mapped object
    int mappedFieldTypeAddress = Platform.readmem(addr + mapVM.VMFieldType_offset);
    // System.out.println("X_getfield: VM_Type of field @ " + Integer.toHexString(mappedFieldTypeAddress));

    // (7) VM_Type object + VM_TypeDescriptor_offset  -> VM_Atom object
    int typeAtomAddress = Platform.readmem(mappedFieldTypeAddress + mapVM.VMTypeDescriptor_offset);

    // (8) get the type descriptor for this field
    String mappedFieldType = mapVM.getVMAtomString(typeAtomAddress);
    // System.out.println("X_getfield: field type " + mappedFieldType);


    // convert the type string to the VM_ structure in the external world
    VM_Atom fieldDescriptor = VM_Atom.findOrCreateAsciiAtom(mappedFieldType);
    VM_Type fieldType = VM_ClassLoader.findOrCreateType(fieldDescriptor,VM_SystemClassLoader.getVMClassLoader());
    
    // Decode the field type to decide what to do, we have three cases:
    int mappedFieldValue, mappedFieldValue1;

    // (1) if it's a reference, create a new mapVM object and put it on the stack
    if (fieldType.isClassType()) {
      mappedFieldValue = Platform.readmem(mappedObject.getAddress() + mappedFieldOffset);
      mapVM newMappedReference = new mapVM(fieldType, mappedFieldValue, mapVM.PointerSize);
      stack.push(newMappedReference);
      if (traceExtension)	
	System.out.println("X_getfield: reference @ " + Integer.toHexString(mappedFieldValue));
      return;
    } 

    // (2) if it's an array, create a new mapVM object with the right element size and put on stack
    if (fieldType.isArrayType()) {
      int logsize = fieldType.asArray().getLogElementSize();
      int size=1;
      for (int i=0; i<logsize; i++)   // do this because we don't have 2^n
	size *= 2;
      mappedFieldValue = Platform.readmem(mappedObject.getAddress() + mappedFieldOffset);
      mapVM newMappedReference = new mapVM(fieldType, mappedFieldValue, size);
      stack.push(newMappedReference);
      if (traceExtension)	
        System.out.println("X_getfield: array @ " + Integer.toHexString(mappedFieldValue));
      return;
    }

    // (3) if it's a primitive value, find its size, 
    // read the actual value from the JVM memory, 
    // convert it to the correct type and put it on the stack
    // To find the field size, we refer back to the VM_Field in the external side
    // System.out.println("X_getfield: primitive at index " + index);
    int si = getCurrentClass().getMethodRefId(index);    // constant pool entry
    VM_Field ifield = VM_FieldDictionary.getValue(si);   // index into the field dictionary
    int size = ifield.getSize();

    mapVM newMappedPrimitive = new mapVM(fieldType, 
					 mappedObject.getAddress() + mappedFieldOffset,
					 size);
    try {
      stack.pushMappedPrimitiveToStack(newMappedPrimitive);
    } catch (Exception e) {
      // do we have a problem extracting from the mapped JVM side?
      System.out.println("X_getfield: " + e.getMessage());
      debug();
    }
  }


  /**
   * Currently we don't allow store into the JVM space
   */
  void X_putfield() {
    // if (traceInterpreter >= 2) 
    System.out.println("X_putfield: not implemented yet");
    debug();
  }

  /**
   * 	array -> length
   */
  int X_arraylength(Object obj) {
    mapVM mappedObj = (mapVM) obj;
    int length = Platform.readmem(mappedObj.getAddress() + VM_ObjectModel.getArrayLengthOffset() );
    if (traceExtension)	
      System.out.println("X_arraylength: array length for " + mappedObj + ", " + length);
    return length;
  } 

  /**
   * unwrap mapped object before checking
   */
  boolean X_checkcast(Object ref, VM_Type lhsType) throws VM_ResolutionException {

    // compute the address of the class object
    int addr = ((mapVM)ref).getAddress();
    int typeAddress = VM_ObjectModel.getTIB(this,addr);
    // int typeAddress = JDPObjectModel.getTIBFromPlatform(addr);
    typeAddress = Platform.readmem(typeAddress);           

    // read the class name for the object from the JVM side
    int nameAddr =  Platform.readmem(typeAddress + mapVM.VMClassName_offset);
    String realName = mapVM.getVMAtomString(nameAddr);
    // System.out.println("X_checkcast: rhs is " + realName);
    
    // checkcast against the lhs using the name for the rhs
    VM_Type rhsType = (VM_Type) forName(realName.substring(1,(realName.length()-1)));
    
    return lhsType.isAssignableWith(rhsType);
  }

  /**
   * may need to do something here
   */
  boolean X_instanceof(Object ref, Class lhsClass) {
    System.out.println("X_instanceof: not implemented yet");
    debug();
    return false;
  } 

  /**
   * Read address from JVM, check for null	
   * 	object -> ..  
   */
  boolean X_ifnull(Object ref) {
    mapVM mappedObj = (mapVM) ref;
    if (traceExtension)	
      System.out.println("X_ifnull: " + mappedObj);
    // int pointer = Platform.readmem(mappedObj.getAddress());
    // checking the address itself is good enough 
    int pointer = mappedObj.getAddress();
    if (pointer==0)
      return true;
    else
      return false;
  } 


  /**
   * (currently we don't need to intercept here, it's done in the main
   * code InterpreterBase._invokevirtual)
   * Unwrap object, intercept the call
   * For now, only a few virtual access methods are recognized and
   * intercepted.  We don't actually create a new stack frame to
   * run the method
   */
  void X_invokevirtual(Object ref, VM_Method mth) {
    mapVM mappedObj = (mapVM) ref;
    String mthName = mth.getName().toString();
    if (traceExtension)     
      System.out.println("X_invokevirtual: " + mthName + " on " + mappedObj);
    if (mappedObj.getType().getName().equals("com.ibm.JikesRVM.VM_Atom") && 
	mthName.equals("getBytes"))
       
    debug();
  }

  /**
   *  This is the entry point for most mapped objects:  invoking a static
   * VM_* method to obtain a VM_* object
   *  Watch for these base address lookup and consult the class mapVM
   * for the address values
   * VM_AtomDictionary.getKeysPointer()
   * VM_AtomDictionary.getChainsPointer()
   * VM_Atom.getValue()
   * @param calledClass  the class of the method
   * @param calledMethod the method to be invoked
   * @return true is the method has been intercepted and the appropriate value
   *         has been put on the stack, nothing else to do
   *         false if this method is not intercepted, so handle it normally
   * @see mapVM.getMapBase()
   */
  boolean X_invokestatic(VM_Class calledClass, VM_Method calledMethod) {
    if (traceExtension)     
      System.out.println("X_invokestatic: " + calledClass.getName() + 
		       " " + calledMethod.getName());

    Object mappedObj = mapVM.getMapBase(calledClass, calledMethod);

    if (mappedObj==null) {      
      return false;
    } else {      
      stack.push(mappedObj);
      return true;
    }
  }

  /**
   * Return the contents of a JTOC slot in the debuggee
   *
   * @param slot 
   */
  public int readJTOCSlot(int slot) {
    int ptr = mapVM.getJTOC() + (slot << 2);
    return Platform.readmem(ptr);
  }

  /**
   * Return the contents of a memory location in the debuggee
   *
   * @param ptr the memory location
   */
  public int readMemory(ADDRESS ptr) {
    return Platform.readmem(ptr);
  }
}


