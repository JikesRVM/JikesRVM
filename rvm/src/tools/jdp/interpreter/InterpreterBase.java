/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
import com.ibm.JikesRVM.*;

/**
 * This interpreter uses a type-safe expression stack and local-variable 
 * storage implementation.
 * Each method call has its own local-variable storage object.
 * The call chain results in a LIFO stack of methods and a deeper and deeper 
 * expression stack
 *
 * @author John Barton
 */
import java.io.*;
import java.util.*;
import java.lang.reflect.*;

public abstract class InterpreterBase
extends VM_MagicNames
implements VM_Constants 
{

  abstract void invokeMagic(VM_Method m);
  abstract void invokeReflective(VM_Method m);
  abstract void invokeReflective(Class cls, VM_Method m);
  abstract boolean isNonPublicized(VM_Method called_method);
  abstract boolean isNonPublicized(Class cls);

  /**
   * These are the extensions for the bytecodes that operate on object
   * to handle object mapped to the JVM side.
   */
  abstract void X_ldc(VM_Class cls, int poolIndex);
  abstract void X_ldc2_w(VM_Class cls, int poolIndex); 	
  abstract void X_arrayload(int opcode, Object array, int index);	// cover all array load bytecodes
  abstract boolean X_if_acmpeq(Object val1, Object val2); 	
  abstract void X_getstatic(); 	
  abstract void X_getfield(Object ref); 	
  abstract void X_putfield(); 	
  abstract Object X_newarray(int count);
  abstract int X_arraylength(Object obj); 	
  abstract boolean X_checkcast(Object ref, Class lhsClass) throws VM_ResolutionException;	
  abstract boolean X_instanceof(Object ref, Class lhsClass);	
  abstract boolean X_ifnull(Object ref); 	
  abstract boolean X_invokestatic(VM_Class cls, VM_Method mth); 
  abstract void X_invokevirtual(Object ref, VM_Method mth); 

  /**
   *  Apply the Interpreter to a method taking String args and
   *  optionally return a String.
   *
   *  @param method : method to be interpreted.
   *  @param params : parameters to method, in stack order.
   *          (i.e. higher indices -> top of stack)
   *  @return resulting String from interpretation or null if the method is void.
   */
  public String interpretMethod(VM_Method method, String[] params) 
  {
    // get bytecodes for this method
    byte_codes = new ByteCodeContext(method);
    
    if (true || traceInterpreter >= 1) 
      {
	System.out.print("InterpreterBase: begin interpreting "+method+" using "+params.length+" params=");
	for (int i=0; i<params.length; ++i) System.out.print(params[i]+" ");
	System.out.println();
      }
    
    String result = null;
    VM_Type returnType         = method.getReturnType();
    
    // initialize our stack and our locals
    stack = new InterpreterStack();
    
    try
      {
	if ( !(returnType == VM_Type.VoidType || returnType == VM_Type.JavaLangStringType))
	  {
	    throw new IllegalArgumentException("Only string or void types can be returned!");
	  }
	
	VM_Type parameter_types[]  = method.getParameterTypes();
	
	if (params.length == 0) 
	  stack.push(params);
	else
	  {
	    if (parameter_types[0].getName().equals("[Ljava.lang.String;")) 
	      stack.push(params);
	    else 
	      throw new IllegalArgumentException(parameter_types[0]+"? Only String[] argument supported here!");
	  }
	
	stack.push(byte_codes, 1);
	
	interpret();
	
      }
    catch(Throwable t)
      {
	System.out.println(t);
	t.printStackTrace(System.out);
	debug();
      }
    if (returnType == VM_Type.JavaLangStringType) result = (String) stack.popObject();
    return result;
  }
  
  
  /** 
   *  Read one word of static data from jtoc at index.
   *
   */
  abstract protected int getStaticWord(int index);
  
  abstract protected Object _new(VM_Class klass);
  abstract protected Object _newarray(VM_Array array, int num);
  abstract protected VM_StackTrace[] extendStackTrace(VM_StackTrace base[]);
  
  static VM_Atom vm_stacktrace = null;
  static VM_Atom vm_stacktrace_create = null;
  
  
  /**
   * @param previous_callers is a stack of InterpreterBase objects representing
   * the interpreted call chain.
   */
  public InterpreterBase()
  {
    if (vm_stacktrace == null) initInterpreterBase();
    debug_after_return = null;
  }
  
  /**
   * Initialize constants for InterpreterBase
   */
  static void initInterpreterBase()
  {
    VM_Magic.setObjectAddressRemapper(new addressRemapper());
    try {
      VM.initForTool();

      // load and resolve some classes for reflective invocation in the code to 
      // handle mapped objects (this load/resolve is not done by initForTool)
      // they get loaded and resolved if not found
      // Disable code generation in the compiler
      VM.runningAsJDPRemoteInterpreter = true;
      VM_Class at = forName("com.ibm.JikesRVM.VM_Atom");
      at = forName("com.ibm.JikesRVM.VM_Field");    
      at = forName("com.ibm.JikesRVM.VM_Class");
      at = forName("com.ibm.JikesRVM.VM_Type");
      at = forName("com.ibm.JikesRVM.VM_Method");

      Platform.init();

    }
    catch (VM_ResolutionException e) {
      VM.sysWrite(e.getException() + "\n");
      e.printStackTrace();
      VM.sysExit(1);
    } 
    vm_stacktrace = VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/VM_StackTrace;");
    vm_stacktrace_create = VM_Atom.findOrCreateAsciiAtom("create");
  }
  
  /* return value of interpreted method */
  
  public int returnWord1;  // high order bits
  public int returnWord2;  // low order bits
  
  /***** implementation details below *****/
  
  /* stack and local variables */
  protected InterpreterStack  stack;
  /** bytecodes of method and current index */
  public ByteCodeContext byte_codes;   // need to make public to allow RemoteInterpreter to access
  
  
  private ByteCodeContext debug_after_return;     // restart single stepping when we hit "XXreturn" from this context.
  
  
  static int traceInterpreter = 0;      // output for each bytecode 

  // NOTE: turn off to not come up in debugger mode on start up
  static boolean debug = false;                  // single stepping
  
  static boolean traceExtension = false;        // message for bytecode operating on mapVM object

  static final boolean debug_checks_on = true; // assertion checking in intepreter.
  
  public static void println(String s)
  {
    System.out.println("InterpreterBase:"+s);
  }
  
  
  /*** magic helper functions ***/
  
  
  static final long twoIntsToLong(int hi, int lo)
  {
    long result = (((long) hi) << 32);
    result |= ((long) lo) & 0xFFFFFFFFL;
    return result;
  }
  
  
  /* isnull */
  private final boolean _isRefNull(int ref) {
    return (ref == VM_NULL);
  }
  
  /* ldc */
  private final void _ldc(VM_Class klass, int index) 
  {
    int offset = klass.getLiteralOffset(index) / 4;  // offset is into the JTOC
    byte desc = klass.getLiteralDescription(index);
    
    if      (desc == VM_Statics.INT_LITERAL)    
      stack.push( getStaticWord(offset) );
    
    else if (desc == VM_Statics.FLOAT_LITERAL)  
      // sigh: convert on input and inside too
      stack.push( Float.intBitsToFloat(getStaticWord(offset)));  
    
    // TODO:  cannot get the JTOC content and just convert from int to object, 
    // need to implement a hash table to hold object references in the JTOC
    else if (desc == VM_Statics.STRING_LITERAL) 
      stack.push(VM_Magic.addressAsObject(VM_Address.fromInt(getStaticWord(offset))));
    
    else    
      assert(NOT_IMPLEMENTED);
    
  }
  
  /* ldc2 */
  private final void _ldc2(VM_Class klass, int index) 
  {
    int offset = klass.getLiteralOffset(index) / 4;  // offset is into the JTOC
    int hi = getStaticWord(offset);  // hi ?? lo??
    int lo = getStaticWord(offset+1);
    int desc = klass.getLiteralDescription(index);
    if (desc == VM_Statics.LONG_LITERAL) 
      {
	stack.pushLongBits(hi, lo);
      }
    else if (desc == VM_Statics.DOUBLE_LITERAL)
      {
	stack.pushDoubleBits(hi, lo);
      }
    else assert(NOT_IMPLEMENTED);
  }
  
  /**
   *  Implements invokevirtual bytecode.
   *  On call stack top is last method argument, the next to last, 
   *  and so on, to the first argument.
   *  Then the stack has an object reference that will be the "this" object.
   *  The bytecode array has 2 bytes giving the constant pool index for the 
   *  method to be called.
   */
  void _invokevirtual(VM_Class sender, VM_Method called_method_prototype) 
  {
    // Use the "this" reference to find the type of the object.
    // The top of the stack is argN, eg arg2. 
    // At top-numberofargs, eg top-2, will be "this":
    // eg    this, arg1, arg2
    //        -2   -1    top
    //
    int number_of_args = called_method_prototype.getParameterWords();
    Object receiver = stack.getObjectDown(number_of_args);
    // VM_Type receiver_type = VM_Magic.getObjectType(receiver);  // This type must have been instantiated.
    Class receiverClass = receiver.getClass();    // java lang class, to be converted to VM_Class
    
    // If the receiver is a mapVM object, we need to unwrap to get the real 
    // receiver class
    if (mapVM.isMappedClass(receiverClass)) {
      mapVM mappedObj = (mapVM) receiver;
      mappedObj.handleAbstractClass();
      receiverClass = mappedObj.getJavaClass();
    }

    VM_Type receiver_type = getVMTypeForClass(receiverClass);

    // If the receiver is an array, what to do??  ALSO fix java/lang/Class.getMethods()
    // 
    VM_Class receiver_class = null;
    if (receiver_type.isClassType()) {
      if (traceInterpreter >= 2)    
	System.out.println("InterpreterBase: converted to class type");
      receiver_class = (VM_Class)receiver_type;
    }  else if (receiver_type.isArrayType()) {
      // KLUDGE: Array only use java.lang.Object methods so any Class will do.
      receiver_class = sender;
    } else  {
      assert(NOT_REACHED);
    }

    VM_Method correct_virtual_method = 
      receiver_class.findVirtualMethod(called_method_prototype.getName(), 
				       called_method_prototype.getDescriptor());
    if (mapVM.isMappedObject(receiver)) {
      if (traceExtension)
	System.out.println("X_invokevirtual: " + receiver_class + " " + 
			   called_method_prototype.getName() + " " + 
			   called_method_prototype.getDescriptor() + ", on " +
			   (mapVM) receiver);
    }
    
    if (traceInterpreter >= 2)    {
      System.out.println("InterpreterBase.getCalledVirtualMethodFromPoolIndex: called_method_prototype= "+called_method_prototype);
      System.out.println("InterpreterBase: correct_virtual_method= "+correct_virtual_method);
      System.out.println("InterpreterBase: receiver_class= "+receiver_class);
    }

    // todo , correct exception.
    if (correct_virtual_method == null)  {
      throw new NoSuchMethodError("TEMP");
      // _throwException(new NoSuchMethodError("Looking in "+receiver+" for "+called_method_prototype));
    }
    
    // See if we need to invoke this method reflectively
    // case (1):  the object instance is not publicized, we need to use the object's class
    if (isNonPublicized(receiverClass)) {
      invokeReflective(receiverClass, called_method_prototype);
      return;      
    }
    // case (2): the method belongs to a class that is not publicized
    else if (isNonPublicized(correct_virtual_method)) {
      invokeReflective(correct_virtual_method);
      return;
    }

    if (correct_virtual_method.getBytecodes()==null)
      System.out.println("InterpreterBase: ERROR, no bytecode");

    // Activate the new frame
    //
    byte_codes = new ByteCodeContext(correct_virtual_method);
    stack.push(byte_codes, number_of_args+1);
    
    // The "return" bytecode will pop the stack, 
    // push the return value, and reset the byte_codes member variable.
    
  }
  
  /* invokespecial */
  
  // Use reflection to pick up the work for the new bytecode
  // 1. allocate the object by picking up the special type put there by new
  // 2. pop the argument for initialization and invoke the right constructor
  // Class javaclass = klass.getClassForType();
  // Constructor constr = javaclass.getConstructor();
  // Object obj = constr.newInstance();
  
  private final void _invokespecial() 
  {
    VM_Class sender = getCurrentClass();
    VM_Method called_method_prototype = 
      getCalledMethodFromPoolIndex(byte_codes.fetch2BytesUnsigned(), sender);
    
    VM_Method called_method = VM_Class.findSpecialMethod(called_method_prototype);
    if (called_method == null) 
      throw new NoSuchMethodError(called_method_prototype.toString());
    
    int paramNum = called_method.getParameterWords();  // does not count "this"
    if (!called_method.isStatic()) 
      paramNum += 1;                 // for virtual methods, add one for "this" 
    
    
    if (traceInterpreter >= 1) 
      println("invokespecial (" + called_method_prototype + ") with " +
	      paramNum+" parameters, native=" + called_method.isNative());
    
    // for any method except initializer, push the stack and invoke the new bytecode
    if (!called_method.isObjectInitializer())  {
      // for native classes and those classes that the Publicizing class loader 
      // did not enable the public bit, invoke directly by reflection
      if (isNonPublicized(called_method) || called_method.isNative()) {
	invokeReflective(called_method);
	return;
      } else {
	byte_codes = new ByteCodeContext(called_method);
	stack.push(byte_codes, paramNum);
      }
    } 
    
    // for initializer method, intercept and use reflection to both create
    // and initialize the object
    else {
      Object newobj = stack.getObjectDown(paramNum-1);
      _invokeInitializer(called_method, newobj);    
    }
    
    
  }

  /**
   */
  private final void _invokeInitializer(VM_Method called_method, Object newobj) {
    Class unInitClass;
    VM_Class newclass;
    Object obj;
    Object [] paramObj;
    Constructor constr=null;

    try {
      unInitClass = Class.forName("unInitType");
    } catch (ClassNotFoundException e) {
      System.out.println("_invokeInitializer: unInitType ClassNotFoundException ");
      assert(NOT_REACHED);   // should always be able to find this type
      return;
    }

    // we expect the new object to be just a place holder object of type unInitType
    if (unInitClass.isInstance(newobj)) {     
      newclass = ((unInitType)newobj).getVMClass();
    } else {
      System.out.println("_invokeInitializer: expected unInitType object not on stack");
      assert(NOT_REACHED);   // shouldn't be here      
      return;
    }
   
    // we have the number of parameter, we need to get their types
    // as an array of Class objects (the array does not include "this" for 
    // virtual method)
    VM_Type [] paramType = called_method.getParameterTypes();
    Class [] paramClass = new Class[paramType.length];
    paramObj = new Object[paramType.length];
    // System.out.println("_invokeInitializer:  new instance of class " + newclass + ", " + 
    // 			  paramType.length + " parameters");
    
    // pop the parameters from the stack and 
    // wrap each primitive as object to create an array of parameter objects
    // (remember that the parameters on the stack are in reverse order relative
    // to the list)
    for (int k=paramType.length-1; k>=0; k--) {
      paramClass[k] = getClassForVMType(paramType[k]);
      // System.out.println("_invokespecial: parameter " + k + " is " + paramClass[k]);	    
      paramObj[k] = stack.popAsObject(paramType[k]);
    }
    
    // find the contructor for this class with the matching parameter types
    // progressing to the super class if none found in this class
    Class classToInstantiate = getClassForVMType(newclass);
    try {
      constr = classToInstantiate.getConstructor(paramClass);
    } 
    catch (NoSuchMethodException e1) {
      System.out.println("_invokeInitializer: NoSuchMethodException " + called_method);
      constr = null;
    }

    // System.out.println("\n_invokeInitializer: constructor is " + constr);

    // make sure the class for this constructor is loaded,
    // then invoke this constructor with the matching parameters
    try {
      newclass.load();
      newclass.resolve();
      newclass.instantiate();
      if (constr==null) {
	  // System.out.println("classToInstantiate.newInstance: " + called_method + " on " + newclass);
	obj = classToInstantiate.newInstance();
      }
      else {
	  // System.out.println("constr.newInstance: " + called_method + " on " + newclass);
	obj = constr.newInstance(paramObj);      
      }
    } catch (InstantiationException e2) {
      System.out.println("_invokeInitializer: InstantiationException, " + called_method + " on " + newclass);
      assert(NOT_REACHED);   // shouldn't be here 
      return;

    } catch (IllegalAccessException e3) {
      System.out.println("_invokeInitializer: IllegalAccessException, " + called_method + " on " + newclass);
      assert(NOT_REACHED);   // shouldn't be here 
      return;

    } catch (InvocationTargetException e4) {
      // the reflected initializer method throws an exception
      // unwrap the exception and pass it on to the program 
      if (traceInterpreter >= 1) {
	println("exception caught in initializer " + called_method + " of " + newclass + ", " + e4.getTargetException());
	if (constr==null)
	  println("constructor was null");
	else 
	  println("constructor was not null");
      }
      Throwable realEx = e4.getTargetException();
      _throwException(realEx);
      return;

    } catch (VM_ResolutionException e5) {
      System.out.println("_invokeInitializer: VM_ResolutionException, " + called_method + " on " + newclass);
      assert(NOT_REACHED);   // shouldn't be here 
      return;
    }	

    // finally replace all instances of unInitType with this new object
    Object ut = stack.popObject();
    stack.replaceUnInit((unInitType)ut,obj);	

  }

 
  /* invokestatic */
  private final void _invokestatic(VM_Class called_class, VM_Method called_method) 
  {      
    // The called_method's class has been loaded() to get here, 
    // but it need not have been instantiated().
    // We only need class initialization, so we could try to do this another way.

    // println("_invokestatic: " + called_class  + ", " + called_method);

    try {
      called_class.resolve();
      // Don't instanstiate if running under JDK because the VM compiler
      // will try to compile and generate code, and may invoke methods
      // in the local version java.lang classes that don't exist in the 
      // standard java.lang
      // called_class.instantiate();
    }
    catch(VM_ResolutionException e) {
      throw new ExceptionInInitializerError(e);  
    }
    
    // see if we need to invoke this reflectively
    if (isNonPublicized(called_method) || called_method.isNative()) {
      invokeReflective(called_method);
      return;
    }

    // see if this call is to be intercepted by the mapping mechanism
    if (mapVM.isMapped(called_class)) {
      if (X_invokestatic(called_class, called_method)) {
	return;   // already intercepted, nothing else to do here 
      }
    }
    // special handshaking between the debugger and the interpreter to know
    // when to refresh the cache of the pointers
    if (called_method.getName().toString().equals("cachePointers")) {
      mapVM.cachePointers();
      return;
    }


    // Push this method on to the stack to cause the frame to be activated and locals to be defined.
    //
    byte_codes = new ByteCodeContext(called_method);
    stack.push(byte_codes, called_method.getParameterWords());
    
    // The "return" bytecode will pop the stack, push the return value, 
    // and reset the byte_codes member variable.
  }
  
  /* new */
  protected final void _throwException(Throwable t) 
  {
    if (traceInterpreter >= 1)
      {
	System.out.println("InterpreterBase: Exception thrown:"+t);
	debug_methods_called();
	System.out.println("InterpreterBase: underlying stack is:");
	t.printStackTrace(System.out);
	debug();
      }
    
    while(byte_codes != null)
      {      
	int handler_pc = byte_codes.getHandlerOffsetForExceptionsMatching(t);
	if (byte_codes.getHandlerOffsetForExceptionsMatching(t) > 0)
	  {
	    if (traceInterpreter >= 1) System.out.println("InterpreterBase: found handler in "+byte_codes);
	    
	    // set up for continuation in handler.
	    //   -- move the instruction counter
	    //
	    byte_codes.jumpException(handler_pc);
	    
	    //   -- clear the expression stack
	    //
	    while(stack.expressionStackDepth() > 0) stack.popAny();
	    
	    //   -- push the exception for the handler
	    //
	    stack.push(t);
	    
	    if (traceInterpreter >= 1)
	      {
		System.out.println("InterpreterBase: jumpException done.");
		debug();
	      }
	    return;
	  }
	byte_codes = stack.popByteCodeContext();  // null if no frame is left.
      }
    // Here we should die, but we will give the interpreter-caller one chance
    //
    throw new RuntimeException("InterpreterBase: no catch for "+t.toString());
  }
  
  /* monitorenter */
  private final void _monitorEnter() {
    // VM_Runtime.monitorenter();
  }
  
  /* monitorexit */
  private final void _monitorExit() {
    // VM_Runtime.monitorexit();
  }
  
  
  private final boolean _boundsCheck(Object ref, int index) {
    if (index < 0) 
      {
	_throwException(new ArrayIndexOutOfBoundsException("negative index at "+byte_codes));
	return true;
      }
    int length = java.lang.reflect.Array.getLength(ref);
    if (index >= length) 
      {
	_throwException(new ArrayIndexOutOfBoundsException("index="+index+" >= "+length+" at "+byte_codes));
	return true;
      }
    return false;
  }
  
  /***** INTERPRETER BODY *****/
  
  private final void interpret() {
    
    // interpreter loop
    while (byte_codes.hasMoreElements()) 
      {
	int code = byte_codes.getNextCode();
	if (code < 0 || code > 255) 
	  {
	    System.out.println("InterpreterBase: encountered a bytecode out of range="+byte_codes);
	    debug();
	  }
	if (traceInterpreter >= 2 || debug) System.out.println("InterpreterBase: "+byte_codes+";  stack_top = "+stack.describeTop());
	if (debug) debug();
	switch (code) 
	  {
	  case 0x00: /* --- nop --- */    break;
	  case 0x01: /* --- aconst_null --- */ {  stack.push(null);   break;  }
	    
	  case 0x02: /* --- iconst_m1 --- */
	  case 0x03: /* --- iconst_0 --- */
	  case 0x04: /* --- iconst_1 --- */
	  case 0x05: /* --- iconst_2 --- */
	  case 0x06: /* --- iconst_3 --- */
	  case 0x07: /* --- iconst_4 --- */
	  case 0x08: /* --- iconst_5 --- */ {  stack.push(code - 0x03);   break; }
	    
	  case 0x09: /* --- lconst_0 --- */
	  case 0x0a: /* --- lconst_1 --- */ { long lc = code - 0x09;  stack.push(lc);  break;   }
	    
	  case 0x0b: /* --- fconst_0 --- */  {  stack.push(0.0f);    break;    }
	  case 0x0c: /* --- fconst_1 --- */  {  stack.push(1.0f);    break;    }
	  case 0x0d: /* --- fconst_2 --- */  {  stack.push(2.0f);    break;    }
	  case 0x0e: /* --- dconst_0 --- */  {  stack.push(0.0);     break;    }
	  case 0x0f: /* --- dconst_1 --- */  {  stack.push(1.0);     break;    }
	    
	  case 0x10: /* --- bipush --- */ 
	    {
	      int val = byte_codes.fetch1ByteSigned();
	      stack.push(val);
	      break;
	    }
	  case 0x11: /* --- sipush --- */ {
	    int val = byte_codes.fetch2BytesSigned();
	    stack.push(val);
	    break;
	  }
	  case 0x12: /* --- ldc --- */ {
	    int index = byte_codes.fetch1ByteUnsigned();
	    // look up this offset in the table, and push it.
	    VM_Class klass = getCurrentClass();  
	    if (mapVM.isMapped(klass))
	      X_ldc(klass, index);
	    else
	      _ldc(klass,index);
	    break;
	  }
	  case 0x13: /* --- ldc_w --- */ {
	    int index = byte_codes.fetch2BytesUnsigned();
	    // look up this offset in the table, and push it.
	    VM_Class klass = getCurrentClass(); 
	    if (mapVM.isMapped(klass))
	      X_ldc(klass, index);
	    else
	      _ldc(klass,index);
	    break;
	  }
	  case 0x14: /* --- ldc2_w --- */ {
	    int index = byte_codes.fetch2BytesUnsigned();
	    // look up this offset in the table, and push it.
	    VM_Class klass = getCurrentClass(); 
	    if (mapVM.isMapped(klass))
	      X_ldc2_w(klass, index);
	    else
	      _ldc2(klass,index);
	    break;
	  }
	  case 0x15: /* --- iload --- */
	    {
	      int index = byte_codes.fetch1ByteUnsigned();
	      int val = stack.getLocalInt(index);
	      stack.push(val);
	      break;
	    }
	  case 0x17: /* --- fload --- */
	    {
	      int index = byte_codes.fetch1ByteUnsigned();
	      float val = stack.getLocalFloat(index);
	      stack.push(val);
	      break;
	    }
	  case 0x19: /* --- aload --- */ 
	    {
	      int index = byte_codes.fetch1ByteUnsigned();
	      Object val = stack.getLocalObject(index);
	      stack.push(val);
	      break;
	    }
	  case 0x16: /* --- lload --- */
	    {
	      int index = byte_codes.fetch1ByteUnsigned();
	      long val = stack.getLocalLong(index);
	      stack.push(val);
	      break;
	    }
	  case 0x18: /* --- dload --- */ 
	    {
	      int index = byte_codes.fetch1ByteUnsigned();
	      double val = stack.getLocalDouble(index);
	      stack.push(val);
	      break;
	    }
	  case 0x1a: /* --- iload_0 --- */
	  case 0x1b: /* --- iload_1 --- */
	  case 0x1c: /* --- iload_2 --- */
	  case 0x1d: /* --- iload_3 --- */ {
	    int val = stack.getLocalInt(code-0x1a);
	    stack.push(val);
	    break;
	  }
	  case 0x1e: /* --- lload_0 --- */
	  case 0x1f: /* --- lload_1 --- */
	  case 0x20: /* --- lload_2 --- */
	  case 0x21: /* --- lload_3 --- */ {
	    long val = stack.getLocalLong(code-0x1e);
	    stack.push(val);
	    break;
	  }
	  case 0x22: /* --- fload_0 --- */
	  case 0x23: /* --- fload_1 --- */
	  case 0x24: /* --- fload_2 --- */
	  case 0x25: /* --- fload_3 --- */ {
	    float val = stack.getLocalFloat(code-0x22);
	    stack.push(val);
	    break;
	  }
	  case 0x26: /* --- dload_0 --- */
	  case 0x27: /* --- dload_1 --- */
	  case 0x28: /* --- dload_2 --- */
	  case 0x29: /* --- dload_3 --- */ {
	    double val = stack.getLocalDouble(code-0x26);
	    stack.push(val);
	    break;
	  }
	  case 0x2a: /* --- aload_0 --- */
	  case 0x2b: /* --- aload_1 --- */
	  case 0x2c: /* --- aload_2 --- */
	  case 0x2d: /* --- aload_3 --- */ {
	    Object ref = stack.getLocalObject(code-0x2a);
	    stack.push(ref);
	    break;
	  }
	  case 0x2e: /* --- iaload --- */  
	  case 0x2f: /* --- laload --- */
	  case 0x30: /* --- faload --- */
	  case 0x31: /* --- daload --- */
	  case 0x32: /* --- aaload --- */ 
	  case 0x33: /* --- baload --- */
	  case 0x34: /* --- caload --- */ 
	  case 0x35: /* --- saload --- */
	    {
	      int index = stack.popInt();
	      Object ref = stack.popObject();
	      if (mapVM.isMappedObject(ref))
		X_arrayload(code, ref, index);
	      else {
		if (ref == null) _throwException( new NullPointerException());
		_boundsCheck(ref, index);
		switch(code)
		  {
		  case 0x2e:  /* --- iaload --- */ 	     { int    a[] = (int[])ref;    stack.push(a[index]);	break; }
		  case 0x2f:  /* --- laload --- */ 	     { long   a[] = (long[])ref;   stack.push(a[index]);	break; }
		  case 0x30:  /* --- faload --- */ 	     { float  a[] = (float[])ref;  stack.push(a[index]);	break; }
		  case 0x32:  /* --- aaload --- */ 	     { Object a[] = (Object[])ref; stack.push(a[index]);	break; }
		  case 0x31:  /* --- daload --- */ 	     { double a[] = (double[])ref; stack.push(a[index]);	break; }
		  case 0x33:  /* --- baload --- */ 
		    {
		      if(ref.getClass().getName().equals("[Z")) { boolean   a[] = (boolean[])ref;   stack.push(a[index]?1:0);	break; }
		      else                                      { byte      a[] = (byte[])ref;      stack.push(a[index]);	break; }
		    }
		  case 0x34:  /* --- caload --- */ 	     { char   a[] = (char[])ref;   stack.push(a[index]);	break; }
		  case 0x35:  /* --- saload --- */ 	     { short  a[] = (short[])ref;  stack.push(a[index]);	break; }
		  }
	      }
	      break;
	    }
	  case 0x36: /* --- istore --- */	{  stack.setLocal(byte_codes.fetch1ByteUnsigned(), stack.popInt());    break;}
	  case 0x38: /* --- fstore --- */	{  stack.setLocal(byte_codes.fetch1ByteUnsigned(), stack.popFloat());  break;}
	  case 0x3a: /* --- astore --- */       {  stack.setLocal(byte_codes.fetch1ByteUnsigned(), stack.popObject());  break;}
	  case 0x37: /* --- lstore --- */       {  stack.setLocal(byte_codes.fetch1ByteUnsigned(), stack.popLong());  break;}
	  case 0x39: /* --- dstore --- */       {  stack.setLocal(byte_codes.fetch1ByteUnsigned(), stack.popDouble());  break;}
	  case 0x3b: /* --- istore_0 --- */
	  case 0x3c: /* --- istore_1 --- */
	  case 0x3d: /* --- istore_2 --- */
	  case 0x3e: /* --- istore_3 --- */     {  stack.setLocal(code-0x3b, stack.popInt());    break;}
	  case 0x3f: /* --- lstore_0 --- */
	  case 0x40: /* --- lstore_1 --- */
	  case 0x41: /* --- lstore_2 --- */
	  case 0x42: /* --- lstore_3 --- */     {  stack.setLocal(code-0x3f, stack.popLong());  break;}
	  case 0x43: /* --- fstore_0 --- */
	  case 0x44: /* --- fstore_1 --- */
	  case 0x45: /* --- fstore_2 --- */
	  case 0x46: /* --- fstore_3 --- */     {  stack.setLocal(code-0x43, stack.popFloat());  break;}
	  case 0x47: /* --- dstore_0 --- */
	  case 0x48: /* --- dstore_1 --- */
	  case 0x49: /* --- dstore_2 --- */
	  case 0x4a: /* --- dstore_3 --- */     {  stack.setLocal(code-0x47, stack.popDouble());  break;}
	  case 0x4b: /* --- astore_0 --- */
	  case 0x4c: /* --- astore_1 --- */
	  case 0x4d: /* --- astore_2 --- */
	  case 0x4e: /* --- astore_3 --- */     {  stack.setLocal(code-0x4b, stack.popObject());  break;}
	    
	  case 0x4f: /* --- iastore --- */
	    {
	      int val = stack.popInt();
	      int index = stack.popInt();
	      Object ref = stack.popObject();
	      if (mapVM.isMappedObject(ref)) {
		System.out.println("InterpreterBase: iastore not allowed to write to mapped VM space");
		debug();
	      } else {
		if (ref == null) _throwException( new NullPointerException());
		_boundsCheck(ref, index);
		if (traceInterpreter >= 2)
		  System.out.println("InterpreterBase: iastore gets val="+val+" index="+index+" and ref="+ref);
		int a[] = (int []) ref; 
		a[index] = val;
	      }
	      break;
	    }
	  case 0x51: /* --- fastore --- */
	    {
	      float val = stack.popFloat();
	      int index = stack.popInt();
	      Object ref = stack.popObject();
	      if (mapVM.isMappedObject(ref)) {
		System.out.println("InterpreterBase: fastore not allowed to write to mapped VM space");
		debug();
	      } else {
		if (ref == null) _throwException( new NullPointerException());
		_boundsCheck(ref, index);
		float a[] = (float []) ref; 
		a[index] = val;
	      }
	      break;
	    }
	  case 0x53: /* --- aastore --- */ 
	    {
	      Object val = stack.popObject();
	      int index = stack.popInt();
	      Object ref = stack.popObject();				   
	      if (mapVM.isMappedObject(ref)) {
		System.out.println("InterpreterBase: aastore not allowed to write to mapped VM space");
		debug();
	      } else {
		if (ref == null) _throwException( new NullPointerException());
		_boundsCheck(ref, index);
		Object a[] = (Object []) ref; 
		try {
		  a[index] = val;
		} catch (ArrayStoreException e) {
		  System.out.println("aastore: " + val + ", " + index + 
				     ", " + ref);
		  System.out.println("aastore: " + e.getMessage());
		  debug();
		}
	      }
	      break;
	    }
	  case 0x50: /* --- lastore --- */
	    {
	      long val = stack.popLong();
	      int index = stack.popInt();
	      Object ref = stack.popObject();
	      if (mapVM.isMappedObject(ref)) {
		System.out.println("InterpreterBase: lastore not allowed to write to mapped VM space");
		debug();
	      } else {
		if (ref == null) _throwException( new NullPointerException());
		_boundsCheck(ref, index);
		long a[] = (long []) ref;
		a[index] = val;
	      }
	      break;
	    }
	  case 0x52: /* --- dastore --- */ 
	    {
	      double val = stack.popDouble();
	      int index = stack.popInt();
	      Object ref = stack.popObject();
	      if (mapVM.isMappedObject(ref)) {
		System.out.println("InterpreterBase: dastore not allowed to write to mapped VM space");
		debug();
	      } else {
		if (ref == null) _throwException( new NullPointerException());
		_boundsCheck(ref, index);
		double a[] = (double []) ref;
		a[index] = val;
	      }
	      break;
	    }
	  case 0x54: /* --- bastore --- */ 
	    {
	      byte val = (byte)stack.popInt();
	      int index = stack.popInt();
	      Object ref = stack.popObject();
	      if (mapVM.isMappedObject(ref)) {
		System.out.println("InterpreterBase: bastore not allowed to write to mapped VM space");
		debug();
	      } else {
		if (ref == null) _throwException( new NullPointerException());
		_boundsCheck(ref, index);
		if (ref.getClass().getName().equals("[Z"))
		  {
		    boolean a[] = (boolean []) ref;  // Or we could use reflection...
		    a[index] = (val!=0);
		  }
		else
		  {
		    byte a[] = (byte []) ref;
		    a[index] = val;
		  }
	      }
	      break;
	    }
	  case 0x55: /* --- castore --- */ 
	    {
	      char val = (char)stack.popInt();
	      int index = stack.popInt();
	      Object ref = stack.popObject();
	      if (mapVM.isMappedObject(ref)) {
		System.out.println("InterpreterBase: iastore not allowed to write to mapped VM space");
		debug();
	      } else {
		if (ref == null) _throwException( new NullPointerException());
		_boundsCheck(ref, index);
		char a[] = (char []) ref;
		a[index] = val;
	      }
	      break;
	    }
	  case 0x56: /* --- sastore --- */ 
	    {
	      short val = (short)stack.popInt();
	      int index = stack.popInt();
	      Object ref = stack.popObject();
	      if (mapVM.isMappedObject(ref)) {
		System.out.println("InterpreterBase: iastore not allowed to write to mapped VM space");
		debug();
	      } else {
		if (ref == null) _throwException( new NullPointerException());
		_boundsCheck(ref, index);
		short a[] = (short []) ref;
		a[index] = val;
	      }
	      break;
	    }
	  case 0x57: /* --- pop --- */ {
	    stack.popAny();
	    break;
	  }
	  case 0x58: /* --- pop2 --- */ {
	    stack.pop2();
	    break;
	  }
	  case 0x59: /* --- dup --- */ {
	    stack.dup();
	    break;
	  }
	  case 0x5a: /* --- dup_x1 --- */ {
	    stack.dup_x1();
	    break;
	  }
	  case 0x5b: /* --- dup_x2 --- */ {
	    stack.dup_x2();
	    break;
	  }
	  case 0x5c: /* --- dup2 --- */ {
	    stack.dup2();
	    break;
	  }
	  case 0x5d: /* --- dup2_x1 --- */ {
	    stack.dup2_x1();
	    break;
	  }
	  case 0x5e: /* --- dup2_x2 --- */ {
	    stack.dup2_x2();
	    break;
	  }
	  case 0x5f: /* --- swap --- */ {
	    stack.swap();
	    break;
	  }
	  case 0x60: /* --- iadd --- */ {
	    int val1 = stack.popInt();
	    int val2 = stack.popInt();
	    int result = val2 + val1;
	    stack.push(result);
	    break;
	  }
	  case 0x61: /* --- ladd --- */ {
	    long val1 = stack.popLong();
	    long val2 = stack.popLong();
	    long result = val2 + val1;
	    stack.push(result);
	    break;
	  }
	  case 0x62: /* --- fadd --- */ {
	    float val1 = stack.popFloat();
	    float val2 = stack.popFloat();
	    float result = val2 + val1;
	    stack.push(result);
	    break;
	  }
	  case 0x63: /* --- dadd --- */ {
	    double val1 = stack.popDouble();
	    double val2 = stack.popDouble();
	    double result = val2 + val1;
	    stack.push(result);
	    break;
	  }
	  case 0x64: /* --- isub ---; */ {
	    int val1 = stack.popInt();
	    int val2 = stack.popInt();
	    int result = val2 - val1;
	    stack.push(result);
	    break;
	  }
	  case 0x65: /* --- lsub --- */ {
	    long val1 = stack.popLong();
	    long val2 = stack.popLong();
	    long result = val2 - val1;
	    stack.push(result);
	    break;
	  }
	  case 0x66: /* --- fsub --- */ {
	    float val1 = stack.popFloat();
	    float val2 = stack.popFloat();
	    float result = val2 - val1;
	    stack.push(result);
	    break;
	  }
	  case 0x67: /* --- dsub ---; */ {
	    double val1 = stack.popDouble();
	    double val2 = stack.popDouble();
	    double result = val2 - val1;
	    stack.push(result);
	    break;
	  }
	  case 0x68: /* --- imul --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    int result = val1 * val2;
	    stack.push(result);
	    break;
	  }
	  case 0x69: /* --- lmul --- */ {
	    long val2 = stack.popLong();
	    long val1 = stack.popLong();
	    long result = val1 * val2;
	    stack.push(result);
	    break;
	  }
	  case 0x6a: /* --- fmul --- */ {
	    float val2 = stack.popFloat();
	    float val1 = stack.popFloat();
	    float result = val1 * val2;
	    stack.push(result);
	    break;
	  }
	  case 0x6b: /* --- dmul --- */ {
	    double val2 = stack.popDouble();
	    double val1 = stack.popDouble();
	    double result = val1 * val2;
	    stack.push(result);
	    break;
	  }
	  case 0x6c: /* --- idiv --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    int result;
	    try {
	      result = val1 / val2;
	    } catch (ArithmeticException e) {
	      // throw ArithmeticException
	      _throwException(new ArithmeticException());
	      break;
	    }
	    stack.push(result);
	    break;
	  }
	  case 0x6d: /* --- ldiv --- */ {
	    long val2 = stack.popLong();
	    long val1 = stack.popLong();
	    long result;
	    try {
	      result = val1 / val2;
	    } catch (ArithmeticException e) {
	      // throw ArithmeticException
	      _throwException(new ArithmeticException());
	      break;
	    }
	    stack.push(result);
	    break;
	  }
	  case 0x6e: /* --- fdiv --- */ {
	    float val2 = stack.popFloat();
	    float val1 = stack.popFloat();
	    float result = val1 / val2;
	    stack.push(result);
	    break;
	  }
	  case 0x6f: /* --- ddiv --- */ {
	    double val2 = stack.popDouble();
	    double val1 = stack.popDouble();
	    double result = val1 / val2;
	    stack.push(result);
	    break;
	  }
	  case 0x70: /* --- irem --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    int result;
	    try {
	      result = val1 % val2;
	    } catch (ArithmeticException e) {
	      // throw ArithmeticException
	      _throwException(new ArithmeticException());
	      break;
	    }
	    stack.push(result);
	    break;
	  }
	  case 0x71: /* --- lrem --- */ {
	    long val2 = stack.popLong();
	    long val1 = stack.popLong();
	    long result;
	    try {
	      result = val1 % val2;
	    } catch (ArithmeticException e) {
	      // throw ArithmeticException
	      _throwException(new ArithmeticException());
	      break;
	    }
	    stack.push(result);
	    break;
	  }
	  case 0x72: /* --- frem --- */ {
	    float val2 = stack.popFloat();
	    float val1 = stack.popFloat();
	    float result = val1 % val2;
	    stack.push(result);
	    break;
	  }
	  case 0x73: /* --- drem --- */ {
	    double val2 = stack.popDouble();
	    double val1 = stack.popDouble();
	    double result = val1 % val2;
	    stack.push(result);
	    break;
	  }
	  case 0x74: /* --- ineg --- */ {
	    int val = stack.popInt();
	    int result = -val;
	    stack.push(result);
	    break;
	  }
	  case 0x75: /* --- lneg --- */ {
	    long val = stack.popLong();
	    long result = -val;
	    stack.push(result);
	    break;
	  }
	  case 0x76: /* --- fneg --- */ {
	    float val = stack.popFloat();
	    float result = -val;
	    stack.push(result);
	    break;
	  }
	  case 0x77: /* --- dneg --- */ {
	    double val = stack.popDouble();
	    double result = -val;
	    stack.push(result);
	    break;
	  }
	  case 0x78: /* --- ishl --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    int result = val1 << val2;
	    stack.push(result);
	    break;
	  }
	  case 0x79: /* --- lshl --- */ {
	    int val2 = stack.popInt();
	    long val1 = stack.popLong();
	    long result = val1 << val2;
	    stack.push(result);
	    break;
	  }
	  case 0x7a: /* --- ishr --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    int result = val1 >> val2;
	    stack.push(result);
	    break;
	  }
	  case 0x7b: /* --- lshr --- */ {
	    int val2 = stack.popInt();
	    long val1 = stack.popLong();
	    long result = val1 >> val2;
	    stack.push(result);
	    break;
	  }
	  case 0x7c: /* --- iushr --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    int result = val1 >>> val2;
	    stack.push(result);
	    break;
	  }
	  case 0x7d: /* --- lushr --- */ {
	    int val2 = stack.popInt();
	    long val1 = stack.popLong();
	    long result = val1 >>> val2;
	    stack.push(result);
	    break;
	  }
	  case 0x7e: /* --- iand --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    int result = val1 & val2;
	    stack.push(result);
	    break;
	  }
	  case 0x7f: /* --- land --- */ {
	    long val2 = stack.popLong();
	    long val1 = stack.popLong();
	    long result = val1 & val2;
	    stack.push(result);
	    break;
	  }
	  case 0x80: /* --- ior --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    int result = val1 | val2;
	    stack.push(result);
	    break;
	  }
	  case 0x81: /* --- lor --- */ {
	    long val2 = stack.popLong();
	    long val1 = stack.popLong();
	    long result = val1 | val2;
	    stack.push(result);
	    break;
	  }
	  case 0x82: /* --- ixor --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    int result = val1 ^ val2;
	    stack.push(result);
	    break;
	  }
	  case 0x83: /* --- lxor --- */ {
	    long val2 = stack.popLong();
	    long val1 = stack.popLong();
	    long result = val1 ^ val2;
	    stack.push(result);
	    break;
	  }
	  case 0x84: /* --- iinc --- */ {
	    int index = byte_codes.fetch1ByteUnsigned();
	    int val = byte_codes.fetch1ByteSigned();
	    int the_local = stack.getLocalInt(index);
	    stack.setLocal(index, the_local+val);
	    break;
	  }
	  case 0x85: /* --- i2l --- */ {
	    int val = stack.popInt();
	    long result = (long) val;
	    stack.push(result);
	    break;
	  }
	  case 0x86: /* --- i2f --- */ {
	    int val = stack.popInt();
	    float result = (float) val;
	    stack.push(result);
	    break;
	  }
	  case 0x87: /* --- i2d --- */ {
	    int val = stack.popInt();
	    double result = (double) val;
	    stack.push(result);
	    break;
	  }
	  case 0x88: /* --- l2i --- */ {
	    long val = stack.popLong();
	    int result = (int) val;
	    stack.push(result);
	    break;
	  }
	  case 0x89: /* --- l2f --- */ {
	    long val = stack.popLong();
	    float result = (float) val;
	    stack.push(result);
	    break;
	  }
	  case 0x8a: /* --- l2d --- */ {
	    long val = stack.popLong();
	    double result = (double) val;
	    stack.push(result);
	    break;
	  }
	  case 0x8b: /* --- f2i --- */ {
	    float val = stack.popFloat();
	    int result = (int) val;
	    stack.push(result);
	    break;
	  }
	  case 0x8c: /* --- f2l --- */ {
	    float val = stack.popFloat();
	    long result = (long) val;
	    stack.push(result);
	    break;
	  }
	  case 0x8d: /* --- f2d --- */ {
	    float val = stack.popFloat();
	    double result = (double) val;
	    stack.push(result);
	    break;
	  }
	  case 0x8e: /* --- d2i --- */ {
	    double val = stack.popDouble();
	    int result = (int) val;
	    stack.push(result);
	    break;
	  }
	  case 0x8f: /* --- d2l --- */ {
	    double val = stack.popDouble();
	    long result = (long) val;
	    stack.push(result);
	    break;
	  }
	  case 0x90: /* --- d2f --- */ {
	    double val = stack.popDouble();
	    float result = (float) val;
	    stack.push(result);
	    break;
	  }
	  case 0x91: /* --- i2b --- */ {
	    int val = stack.popInt();
	    byte result = (byte) val;
	    stack.push(result);
	    break;
	  }
	  case 0x92: /* --- i2c --- */ {
	    int val = stack.popInt();
	    char result = (char) val;
	    stack.push(result);
	    break;
	  }
	  case 0x93: /* --- i2s --- */ {
	    int val = stack.popInt();
	    short result = (short) val;
	    stack.push(result);
	    break;
	  }
	  case 0x94: /* --- lcmp --- */ {
	    long val2 = stack.popLong();
	    long val1 = stack.popLong();
	    int result = (val1 > val2) ? 1 : ((val1 == val2) ? 0 : -1);
	    stack.push(result);
	    break;
	  }
	  case 0x95: /* --- fcmpl --- */ {
	    // fcmpl and fcmpg are same except for NaN treatment
	    float val2 = stack.popFloat();
	    float val1 = stack.popFloat();
	    int result = (val1 > val2) ? 1 : ((val1 == val2) ? 0 : -1);
	    stack.push(result);
	    break;
	  }
	  case 0x96: /* --- fcmpg --- */ {
	    // fcmpl and fcmpg are same except for NaN treatment
	    float val2 = stack.popFloat();
	    float val1 = stack.popFloat();
	    int result = (val1 < val2) ? -1 : ((val1 == val2) ? 0 : 1);
	    stack.push(result);
	    break;
	  }
	  case 0x97: /* --- dcmpl --- */ {
	    // dcmpl and dcmpg are same except for NaN treatment
	    double val2 = stack.popDouble();
	    double val1 = stack.popDouble();
	    int result = (val1 > val2) ? 1 : ((val1 == val2) ? 0 : -1);
	    stack.push(result);
	    break;
	  }
	  case 0x98: /* --- dcmpg --- */ {
	    // dcmpl and dcmpg are same except for NaN treatment
	    double val2 = stack.popDouble();
	    double val1 = stack.popDouble();
	    int result = (val1 < val2) ? -1 : ((val1 == val2) ? 0 : 1);
	    stack.push(result);
	    break;
	  }
	  case 0x99: /* --- ifeq --- */ {
	    int val = stack.popInt();
	    if (val == 0) {
	      // take branch
	      byte_codes.takeBranch();
	    } else {
	      // don't take branch.
	      byte_codes.skipBranch();
	    }
	    continue;
	  }
	  case 0x9a: /* --- ifne --- */ {
	    int val = stack.popInt();
	    if (val != 0) byte_codes.takeBranch();
	    else          byte_codes.skipBranch();
	    continue;
	  }
	  case 0x9b: /* --- iflt --- */ {
	    int val = stack.popInt();
	    if (val < 0) byte_codes.takeBranch();
	    else         byte_codes.skipBranch(); 
	    continue;
	  }
	  case 0x9c: /* --- ifge --- */ {
	    int val = stack.popInt();
	    if (val >= 0) byte_codes.takeBranch();
	    else          byte_codes.skipBranch(); 
	    continue;	 
	  }
	  case 0x9d: /* --- ifgt --- */ {
	    int val = stack.popInt();
	    if (val > 0) byte_codes.takeBranch();
	    else         byte_codes.skipBranch(); 
	    continue;
	  }
	  case 0x9e: /* --- ifle --- */ {
	    int val = stack.popInt();
	    if (val <= 0) byte_codes.takeBranch();
	    else          byte_codes.skipBranch(); 
	    continue;
	  }
	  case 0x9f: /* --- if_icmpeq --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    if (val1 == val2) byte_codes.takeBranch();
	    else              byte_codes.skipBranch();  
	    continue;
	  }
	  case 0xa0: /* --- if_icmpne --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    if (val1 != val2)  byte_codes.takeBranch();
	    else               byte_codes.skipBranch();  
	    continue;
	  }
	  case 0xa1: /* --- if_icmplt --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    if (val1 < val2)   byte_codes.takeBranch();
	    else               byte_codes.skipBranch();  
	    continue;
	  }
	  case 0xa2: /* --- if_icmpge --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    if (val1 >= val2)  byte_codes.takeBranch();
	    else               byte_codes.skipBranch();  
	    continue;
	  }
	  case 0xa3: /* --- if_icmpgt --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    if (val1 > val2) byte_codes.takeBranch();
	    else             byte_codes.skipBranch();   
	    continue;
	  }
	  case 0xa4: /* --- if_icmple --- */ {
	    int val2 = stack.popInt();
	    int val1 = stack.popInt();
	    if (val1 <= val2) byte_codes.takeBranch();
	    else              byte_codes.skipBranch(); 
	    continue;
	  }
	  case 0xa5: /* --- if_acmpeq --- */ {
	    Object val2 = stack.popObject();
	    Object val1 = stack.popObject();
	    if (mapVM.isMappedObject(val1) && mapVM.isMappedObject(val2)) {
	      if (X_if_acmpeq(val1, val2))
		byte_codes.takeBranch();
	      else 
		byte_codes.skipBranch();
	    } else if (mapVM.isMappedObject(val1) || mapVM.isMappedObject(val2)) {
	      System.out.println("X_if_acmpeq: ERROR, cannot compare mapped and non-mapped object");
	      debug();
	    } else if (val1 == val2) {
	      byte_codes.takeBranch();
	    } else {             
	      byte_codes.skipBranch(); 
	    }
	    continue;
	  }
	  case 0xa6: /* --- if_acmpne --- */ {
	    Object val2 = stack.popObject();
	    Object val1 = stack.popObject();
	    if (mapVM.isMappedObject(val1) && mapVM.isMappedObject(val2)) {
	      if (X_if_acmpeq(val1, val2))
		byte_codes.skipBranch();
	      else 
		byte_codes.takeBranch();
	    } else if (mapVM.isMappedObject(val1) || mapVM.isMappedObject(val2)) {
	      System.out.println("X_if_acmpne: ERROR, cannot compare mapped and non-mapped object");
	      debug();
	    } else if (val1 != val2) {
	      byte_codes.takeBranch();
	    } else {
	      byte_codes.skipBranch();  
	    }
	    continue;
	  }
	  case 0xa7: /* --- goto --- */ {
	    byte_codes.takeBranch();
	    continue;
	  }
	  case 0xa8: /* --- jsr --- */ {
	    stack.push(byte_codes.jumpSubroutine());
	    continue;
	  }
	  case 0xa9: /* --- ret --- */ 
	    {
	      int index = byte_codes.fetch1ByteUnsigned();
	      byte_codes.returnSubroutine(stack.getLocalObject(index));
	      continue;
	    }
	  case 0xaa: /* --- tableswitch --- */ {
	    byte_codes.tableSwitch(stack.popInt());
	    continue;
	  }
	  case 0xab: /* --- lookupswitch --- */ {
	    byte_codes.lookupswitch(stack.popInt());
	    continue;
	  }
	  case 0xac: /* --- ireturn --- */ 
	  case 0xae: /* --- freturn --- */
	  case 0xb0: /* --- areturn --- */
	  case 0xad: /* --- lreturn --- */
	  case 0xaf: /* --- dreturn --- */
	  case 0xb1: /* --- return --- */ 
	    {
	      switch (code)
		{
		case 0xac: /* --- ireturn --- */ { int r    = stack.popInt();    stack.popByteCodeContext(); stack.push(r); break; }
		case 0xae: /* --- freturn --- */ { float r  = stack.popFloat();  stack.popByteCodeContext(); stack.push(r); break; }
		case 0xb0: /* --- areturn --- */ { Object r = stack.popObject(); stack.popByteCodeContext(); stack.push(r); break; }
		case 0xad: /* --- lreturn --- */ { long r   = stack.popLong();   stack.popByteCodeContext(); stack.push(r); break; }
		case 0xaf: /* --- dreturn --- */ { double r = stack.popDouble(); stack.popByteCodeContext(); stack.push(r); break; }
		case 0xb1: /* --- return --- */  {                               stack.popByteCodeContext();                break; }
		}
	      // Check the method we are returning from:  was it marked as the one to breakpoint at return?
	      //
	      if (debug_after_return == byte_codes) debug = true;
	      
	      byte_codes = stack.getCurrentByteCodeContext();
	      if (byte_codes == null) return; // stack empty, done interpreting, back to real life
	      break;
	    }
	  
	  case 0xb2: /* --- getstatic --- */ 
	    {
	      if (mapVM.isMapped(getCurrentClass())) {
		X_getstatic();
	      } else {
		VM_Field field = resolveField();
		pushFromHeapToStack(null, field);
	      }
	      break;
	    }
	  
	  case 0xb3: /* --- putstatic --- */ 
	    {
	      if (mapVM.isMapped(getCurrentClass())) {
		System.out.println("InterpreterBase: putstatic not allowed to write into VM space");
		debug();
	      } else {
		VM_Field field = resolveField();
		popFromStackToHeap(null, field);
	      }
	      break;
	    }
	  case 0xb4: /* --- getfield --- */ 
	    {
	      Object ref = stack.popObject();
	      if (mapVM.isMappedObject(ref)) {
		X_getfield(ref);
	      } else {
		VM_Field field = resolveField();
		// System.out.println("interpret: getfield on object " + ref + ", field " + field);
		pushFromHeapToStack(ref, field);
	      }
	      break;
	    }
	  
	  case 0xb5: /* --- putfield --- */ 
	    { 
	      VM_Field field = resolveField();
	      
	      // The stack is 
	      //    ... objectref, word  
	      //    ... objectref, wordhi, word lo.
	      // Here we copy the objectref off the stack
	      //
	      Object objectref = null;
	      if      (field.getType().getStackWords() == 1) objectref = stack.getObjectDown(1);
	      else if (field.getType().getStackWords() == 2) objectref = stack.getObjectDown(2);
	      else assert(NOT_REACHED);
	      
	      if (objectref==null) _throwException(new NullPointerException());
	      
	      if (mapVM.isMappedObject(objectref)) {
		System.out.println("InterpreterBase: putfield not allowed to write into VM space");
		debug();
	      } else {
		popFromStackToHeap(objectref,  field);
	      }
	      break;
	    }
	  
	  case 0xb6: /* --- invokevirtual --- */ 
	    {
	      // On the stack top is the last arg.  Below it is the next to last, etc. Then "this" appears.
	      VM_Class sender = getCurrentClass();
	      VM_Method calledMethod = getCalledMethodFromPoolIndex(byte_codes.fetch2BytesUnsigned(), sender);
	      if (traceInterpreter >= 1) println("invokevirtual (" + calledMethod+ ")");	      
	      VM_Class called_class = calledMethod.getDeclaringClass();
	      if (called_class.isAddressType()) 
		{
		  /* intercept Magic calls */
		  invokeMagic(calledMethod);
		}
	      else {
		_invokevirtual(sender, calledMethod);
	      }
	      break;
	    }
	  case 0xb7: /* --- invokespecial --- */ 
	    { 
	      _invokespecial();   
	      break;   
	    }
	  case 0xb8: /* --- invokestatic --- */ 
	    {
	      VM_Method called_method =  getCalledMethodFromPoolIndex(byte_codes.fetch2BytesUnsigned(), getCurrentClass());
	      if (traceInterpreter >= 1) println("invokestatic (" + called_method+ ")");
	      VM_Class called_class = called_method.getDeclaringClass();
	      // println("invokestatic (" + called_class+ ")");

	      if (called_class.isMagicType() || called_class.isAddressType()) 
		{
		  /* intercept Magic calls */
		  invokeMagic(called_method);
		}
	      // NOTE:  removed because of change in VM_StackTrace, don't think we need this anyway 
	// 	 else if (called_class.getDescriptor() == vm_stacktrace)
	// 	   {
	// 	     if (called_method.getName() == vm_stacktrace_create)
	// 	       {
	// 		 VM_StackTrace base[] = VM_StackTrace.create();
	// 		 
	// 		 // VM_StackTrace takes no arguments, so there are none to become locals of the method.
	// 		 // VM_StackTrace return one object, an array.
	// 		 // If we really interpreted a method we would push a frame, compute, then push a return array.
	// 		 // So here we compute the return array.
	// 		 stack.push(extendStackTrace(base));
	// 	       }
	// 	     else throw new InternalError("InterpreterBase: invoke fails for "+called_method);
	// 	   }
	      else 
		{
		  /* normal method invocation */
		  _invokestatic(called_class, called_method);
		}
	      break;
	    }
	  case 0xb9: /* --- invokeinterface --- */ {
	    VM_Class sender = getCurrentClass();
	    VM_Method called_method =  getCalledMethodFromPoolIndex(byte_codes.fetch2BytesUnsigned(), sender);
	    int nargs = byte_codes.fetch1ByteUnsigned();
	    byte_codes.fetch1ByteSigned(); // eat superfluous 0
	    if (traceInterpreter >= 1) println("invokeinterface (" + called_method+ ") " + nargs + " 0");
	    // same as invokevirtual
	    _invokevirtual(getCurrentClass(), called_method);
	    break;
	  }
	  case 0xba: /* --- unused --- */ {
	    if (traceInterpreter >= 2) println("unused");
	    assert(NOT_REACHED);
	    break;
	  }
	  case 0xbb: /* --- new --- */ {
	    int index = byte_codes.fetch2BytesUnsigned();
	    VM_Class klass = getClassFromPoolIndex(index, getCurrentClass()); 
	    if (traceInterpreter >= 2) println("new " + index + " (" + klass + ")");
	    Object obj = _new(klass);
	    stack.push(obj);
	    break;
	  }
	  case 0xbc: /* --- newarray --- */ {
	    int atype = byte_codes.fetch1ByteSigned();
	    VM_Array array = VM_Array.getPrimitiveArrayType(atype);
	    if (traceInterpreter >= 2) println("newarray " + atype + "(" + array + ")");
	    int count = stack.popInt();
	    Object obj = _newarray(array, count);
	    stack.push(obj);
	    break;
	  }
	  case 0xbd: /* --- anewarray --- */ {
	    int index = byte_codes.fetch2BytesUnsigned();
	    VM_Type array_element_type = getCurrentClass().getTypeRef(index);
	    VM_Array array = array_element_type.getArrayTypeForElementType();
	    int count = stack.popInt();
	    if (traceInterpreter >= 2) println("anewarray new " + index + " (" + array + ")");
	    if (mapVM.isMapped(array_element_type.asClass()))
	      stack.push(X_newarray(count));
	    else
	      stack.push(_newarray(array, count));
	    break;
	  }
	  case 0xbe: /* --- arraylength --- */ {
	    if (traceInterpreter >= 2) println("arraylength");
	    Object ref = stack.popObject();
	    int len;
	    if (mapVM.isMappedObject(ref))
	      len = X_arraylength(ref);
	    else
	      len = java.lang.reflect.Array.getLength(ref);
	    stack.push(len);
	    break;
	  }
	  case 0xbf: /* --- athrow --- */ {
	    if (traceInterpreter >= 2) println("athrow");
	    Object ref = stack.popObject();
	    Throwable t;
	    try 
	      {
		t = (Throwable) ref;
	      }
	    catch(ClassCastException e)
	      {
		t = new InternalError("InterpreterBase: athrow popped an object not derived from Throwable");
	      }
	    _throwException(t);
	    break;
	  }
	  case 0xc0: /* --- checkcast --- */ 
	    {
	      int index = byte_codes.fetch2BytesUnsigned();
	      int type_ref_id = getCurrentClass().getTypeRefId(index);
	      if (traceInterpreter >= 2) println("checkcast " + index + " (" + type_ref_id + ")");
	      Object ref = stack.popObject();
	      
	      // test object type
	      try {
		boolean rc;
		VM_Type lhsType = VM_TypeDictionary.getValue(type_ref_id);
		Class lhsClass = getClassForVMType(lhsType);
		Class rhsClass = ref.getClass();
		if (mapVM.isMappedClass(rhsClass)) {
		  rc = X_checkcast(ref, lhsClass);
		} else {
		  rc = lhsClass.isAssignableFrom(rhsClass);
		  // println("checkcast: "+lhsClass+" "+rhsClass+" = " + rc);
		}
		if (rc == false)
		  _throwException(new ClassCastException());	      
	      }
	      catch(Exception e) {
		_throwException(e);
	      }
	      
	      // passes.
	      stack.push(ref);
	      break;
	    } 
	  case 0xc1: /* --- instanceof --- */ 
	    {
	      int index = byte_codes.fetch2BytesUnsigned();
	      int type_ref_id = getCurrentClass().getTypeRefId(index);
	      if (traceInterpreter >= 2) println("instanceof " + index + " (" + type_ref_id + ")");
	      Object ref = stack.popObject();
	      
	      try {
		boolean rc;
		VM_Type lhsType = VM_TypeDictionary.getValue(type_ref_id);
		Class lhsClass = getClassForVMType(lhsType);
		Class rhsClass = ref.getClass();
		if (mapVM.isMappedClass(rhsClass)) {
		  rc = X_instanceof(ref, lhsClass);
		} else {
		  rc = lhsClass.isAssignableFrom(rhsClass);
		  // println("instanceof: "+lhsClass+" "+rhsClass+" = " + rc);
		}
		if (rc == true)
		  stack.push(1);	     // passes.
		else                                           
		  stack.push(0);	     // fails.
	      }
	      catch(Exception e) {
		_throwException(e);
	      }
	      
	      break;
	    }
	  case 0xc2: /* --- monitorenter ---  */ {
	    if (traceInterpreter >= 2) println("monitorenter -- not implemented");
	    Object ref = stack.popObject();
	    if (ref == null) _throwException(new NullPointerException());
	    break;
	  }
	  case 0xc3: /* --- monitorexit --- */ {
	    if (traceInterpreter >= 2) println("monitorexit -- not implemented");
	    Object ref = stack.popObject();
	    if (ref == null) _throwException(new NullPointerException());
	    break;
	  }
	  case 0xc4: /* --- wide --- */ {
	    if (traceInterpreter >= 2) println("wide");
	    int widecode = byte_codes.fetch1ByteUnsigned();
	    switch (widecode) {
            case 0x15: /* --- wide iload --- */
            case 0x17: /* --- wide fload --- */
            case 0x19: /* --- wide aload --- */ {
	      int index = byte_codes.fetch2BytesUnsigned();
	      int val = stack.getLocalInt(index);
	      stack.push(val);
              break;
            }
            case 0x16: /* --- wide lload --- */
            case 0x18: /* --- wide dload --- */ {
	      int index = byte_codes.fetch2BytesUnsigned();
	      long val = stack.getLocalLong(index);
	      stack.push(val);
	      break;
            }
            case 0x36: /* --- wide istore --- */
            case 0x38: /* --- wide fstore --- */
            case 0x3a: /* --- wide astore --- */ {
	      int index = byte_codes.fetch2BytesUnsigned();
	      int val = stack.popInt();
	      stack.setLocal(index, val);
              break;
            }
            case 0x37: /* --- wide lstore --- */
            case 0x39: /* --- wide dstore --- */ {
	      long val = stack.popLong();
	      int index = byte_codes.fetch2BytesUnsigned();
	      stack.setLocal(index, val);
              break;
            }
            case 0x84: /* --- wide iinc --- */ {
	      int index = byte_codes.fetch2BytesUnsigned();
              int val = byte_codes.fetch2BytesSigned();
	      int the_local = stack.getLocalInt(index);
	      stack.setLocal(index, the_local+val);
	      break;
            }
            case 0x9a: /* --- wide ret --- */ 
	      {
		int index = byte_codes.fetch2BytesUnsigned();
		byte_codes.returnSubroutine(stack.getLocalObject(index));
		continue;
	      }
	    default:
	      assert(NOT_REACHED);
	    }
	    break;
	  }
	  case 0xc5: /* --- multianewarray --- */ {
	    int index = byte_codes.fetch2BytesUnsigned();
	    VM_Type element_type = getCurrentClass().getTypeRef(index);
	    // VM_Array array_type = element_type.asArray();
	    int number_of_dimensions = byte_codes.fetch1ByteUnsigned();
	    int dims[] = new int[number_of_dimensions];
	    
	    // the number of dimension from the bytecode can be anything up to the 
	    // full dimension of the multi array, so to find the type indicated here,
	    // we have to unpack the specified number of dimension
	    // (can't just use getElementType() or getInnermostElementType()
	    for (int i=0; i<number_of_dimensions; i++)
	      element_type = element_type.asArray().getElementType();

	    // e.g. number_of_dimensions = 4
	    // -3 -2 -1 top
	    //  0  1  2   3
	    for(int i = 0; i < number_of_dimensions; i++) 
	      {
		int number_of_elements = stack.popInt();
		if (number_of_elements < 0) _throwException(new  NegativeArraySizeException());
		dims[number_of_dimensions - i - 1] = number_of_elements;
	      }
	    
	    if (traceInterpreter >= 2) println("multianewarray " + index + " dim=" + number_of_dimensions + " type="+element_type);
	    // Object multiarray  = VM_Runtime.buildMultiDimensionalArray(dims, 0, array);
	    Class aclass = getClassForVMType(element_type);
	    Object multiarray  = Array.newInstance(aclass, dims);
	    stack.push(multiarray);
	    break;
	  }
	  case 0xc6: /* --- ifnull --- */ {
	    Object val = stack.popObject();
	    if (mapVM.isMappedObject(val)) {
	      if (X_ifnull(val)) byte_codes.takeBranch();
	      else               byte_codes.skipBranch();
	    } else {
	      if (val == null)   byte_codes.takeBranch();
	      else               byte_codes.skipBranch();
	    }
	    continue;
	  }
	  case 0xc7: /* --- ifnonnull --- */ {
	    Object val = stack.popObject();
	    if (mapVM.isMappedObject(val)) {
	      if (X_ifnull(val)) byte_codes.skipBranch();
	      else               byte_codes.takeBranch();
	    } else {
	      if (val != null)   byte_codes.takeBranch();
	      else               byte_codes.skipBranch();
	    }
	    continue;
	  }
	  case 0xc8: /* --- goto_w --- */ {
	    byte_codes.takeWideBranch();
	    continue;
	  }
	  case 0xc9: /* --- jsr_w --- */ {
	    stack.push(byte_codes.jumpWideSubroutine());
	    continue;
	  }
	  default:
	    assert(NOT_REACHED);
	  } // switch
      } // for
    
    // off the end of the bytecodes
    assert(NOT_REACHED);
    
  } // interpreter
  
  
  VM_Field resolveField()
  {
    int index = byte_codes.fetch2BytesUnsigned();      
    int si = getCurrentClass().getMethodRefId(index);     // constant pool to method dictionary 
    VM_Field field = VM_FieldDictionary.getValue(si);

    if (traceInterpreter >= 2) System.out.println("InterpreterBase: resolved constant pool index="+index+" to "+si+" and found field "+field);
    return field;
  }
  
  /** 
   * NOTE:  change 2nd parameter to VM_Field, then call java.lang.reflect.Field 
   * from here
   * void pushFromHeapToStack(Object obj, java.lang.reflect.Field field)
   */
  void pushFromHeapToStack(Object obj, VM_Field vmfield)
  {
    // PROBLEM HERE! We shouldn't use toString() on object when it might not
    // be well-formed (e.g. we are interpreting the constructor)
    //if (traceInterpreter) println("get (" + field + ") from object="+obj);
    if (traceInterpreter >= 2) 
      println("get (" + vmfield + ") from object=" + ((obj==null)?"<null>":obj));
    
    // this is for the field we are interested in
    VM_Type field_type = vmfield.getType();

    // to get the java.lang.reflect.Field for this field, we look up using the 
    // declaring class for this bytecode and the name for this field
    Class currentClass = getClassForVMType(vmfield.getDeclaringClass());

    //     System.out.println("pushFromHeapToStack: in " + currentClass + 
    // 			  ", get " + vmfield + 
    // 			  ", " + field_type );


    try {
      // Field field = currentClass.getDeclaredField(vmfield.getName().toString());
      // Field field = currentClass.getField(vmfield.getName().toString());
      Field field = getFieldAnywhere(currentClass, vmfield.getName().toString());
      Object value = field.get(obj);
      stack.pushObjectUnwrapped(value, field_type);

    }
    catch(IllegalArgumentException e0)  {
      _throwException(e0);
    }
    catch(IllegalAccessException e1)  {
      _throwException(e1);
    }
    catch (NoSuchFieldException e2) {
      _throwException(e2);
    }
    return;
  }

  // NOTE:  replace 2nd argument from java.lang.reflect.Field to VM_Field
  // void popFromStackToHeap(Object objectref, java.lang.reflect.Field field)
  void popFromStackToHeap(Object objectref, VM_Field vmfield)
  {
    if (traceInterpreter >= 2) println(" put (" + vmfield + ") from "+stack.describeTop());
    // this is for the field we are interested in
    Class field_type = getClassForVMType(vmfield.getType());

    // to get the java.lang.reflect.Field for this field, we look up using the 
    // (1) the name for this field
    // (2) the declaring class of this bytecode for putstatic or
    //     the declaring class of the object for putfield
    Class currentClass;
    if (objectref==null) 
      currentClass = getClassForVMType(getCurrentClass());
    else
      currentClass = objectref.getClass();

    //     System.out.println("popFromStackToHeap: in class " + currentClass +
    // 			  ", get VM_Field " + vmfield + 
    // 			  ", class " + field_type );

    try {
      // System.out.println("popFromStackToHeap: field name is " + vmfield.getName().toString());
      // Field field = currentClass.getDeclaredField(vmfield.getName().toString());
      // Field field = currentClass.getField(vmfield.getName().toString());
      Field field = getFieldAnywhere(currentClass, vmfield.getName().toString());
      if (field_type.isPrimitive()) {
	if      (field_type == Character.TYPE)    { 
	  char value = (char)stack.popInt();  field.setChar(objectref,    value); }
	else if (field_type == Integer.TYPE)      { 
	  int  value = stack.popInt();        field.setInt(objectref,     value); }
	else if (field_type == Float.TYPE)        { 
	  float value = stack.popFloat();     field.setFloat(objectref,   value); }
	else if (field_type == Double.TYPE)       { 
	  double value = stack.popDouble();   field.setDouble(objectref,  value); }
	else if (field_type == Byte.TYPE)         { 
	  byte value = (byte)stack.popInt();  field.setByte(objectref,    value); }
	else if (field_type == Short.TYPE)        { 
	  short value = (short)stack.popInt(); field.setShort(objectref,  value); }
	else if (field_type == Long.TYPE)         { 
	  long value = stack.popLong();       field.setLong(objectref,    value); }
	else if (field_type == Boolean.TYPE)      { 
	  boolean value = (stack.popInt()!=0); field.setBoolean(objectref,value); }
	else throw new InternalError("?? getstatic of unknown type="+field);
      }
      /**** interface should work like any other ref
	else if(field_type.isInterface()) {
	throw new InternalError("? getstatic of interface?");
	}
	****/
      else { // Array or ref
	Object value = stack.popObject(); 
	field.set(objectref, value);
      }
    }
    catch (NoSuchFieldException e2) {
      _throwException(e2);
    }
    catch(Exception e) {
      _throwException(e);
    }
    finally {
      // If this is called by putfield, we need to pop once more since we stole the objectref off the stack.
      //
      if (objectref != null) stack.popObject();
    }
    return;
    
  }
  
  /**
   * Lookup function for Class object:  this used to be in the VM_Type class
   * but is recoded here for the interpreter.  The other method requires
   * a call to a locally modified version of Class.java, which would make
   * the interpreter non portable.  Here, we use the name to look up a class,
   * For primitive, we have to decode the primitive type and use getClass()
   * on a dummy wrapper object. For array, we use getClass() on a dummy array
   * Although the Java Class object found here is not directly linked to its 
   * corresponding VM_Type, there should be only one Class and VM_Type object 
   * for each type, so we should be able to find it.  The tradeoff is performance.
   * @param vmtype a VM_Type object
   * @return the corresponding Class for this type
   * @see VM_Type.getClassForType
   */
  public static Class getClassForVMType (VM_Type vmtype) {
    String classname = vmtype.getName();
    
    // for a regular object, we look up the class by name
    if (vmtype.isClassType()) {
      try {
	Class cls = Class.forName(classname);
	// System.out.println("getClassForVMType: found " + cls.getName());
	return cls;
      } catch (ClassNotFoundException e) {
	// System.out.println("getClassForVMType: not found " + classname);
	return null;
      }
    }

    // for primitive, we use the static Class already created
    if (vmtype.isPrimitiveType()) {
      if (vmtype.isBooleanType())
	return Boolean.TYPE;
      if (vmtype.isByteType())
	return Byte.TYPE;
      if (vmtype.isShortType())
	return Short.TYPE;
      if (vmtype.isIntType())
	return Integer.TYPE;
      if (vmtype.isLongType())
	return Long.TYPE;
      if (vmtype.isFloatType())
	return Float.TYPE;
      if (vmtype.isDoubleType())
	return Double.TYPE;
      if (vmtype.isCharType())
	return Character.TYPE;
    }

    // for array, lookup using the descriptor, like [I
    try {
      String arrayDesc =vmtype.getDescriptor().toString();       
      Class cls = Class.forName(arrayDesc.replace('/','.'));
      // System.out.println("getClassForVMType: found " + cls.getName());
      return cls;
    } catch (ClassNotFoundException e) {
      // System.out.println("getClassForVMType: not found " + classname);
      return null;
    }
  }

  /**
   * This is the converse of getClassForVMType, rewritten here for the same reason
   * @param Class a java/lang/Class object
   * @return the corresponding VM_Type for this class
   */  
  public static VM_Type getVMTypeForClass (Class javaClass) {
    // for primitive, use the static VM_Type already created
    if (javaClass.isPrimitive()) {      
      if (javaClass==Float.TYPE)
	return VM_Type.FloatType;
      if (javaClass==Double.TYPE)
	return VM_Type.DoubleType;
      if (javaClass==Long.TYPE)
	return VM_Type.LongType;
      if (javaClass==Boolean.TYPE)
	return VM_Type.BooleanType;
      if (javaClass==Byte.TYPE)
	return VM_Type.ByteType;
      if (javaClass==Character.TYPE)
	return VM_Type.CharType;
      if (javaClass==Short.TYPE)
	return VM_Type.ShortType;
      if (javaClass==Integer.TYPE)
	return VM_Type.IntType;
    }

    try {
      // for array, get the descriptor like [I and look up via the class loader
      if (javaClass.isArray()) {
    	VM_Atom classDescriptor = VM_Atom.findOrCreateAsciiAtom(javaClass.getName());
    	return VM_ClassLoader.findOrCreateType(classDescriptor,VM_SystemClassLoader.getVMClassLoader()).asArray();
      }
  
      // for normal class, look up using the class name via VM_Class
      // System.out.println("getVMTypeForClass: " + javaClass);
      return (VM_Type) forName(javaClass.getName());
    }
    catch (VM_ResolutionException e) {      
      System.out.println("getVMTypeForClass: not found " + javaClass);
      return null;
    }

  }

  /**
   * Convert from VM_Method to java.lang.method, matching the name and
   * the argument types
   */
  public static java.lang.reflect.Method getMethodForVMMethod(VM_Method vmmethod) {
    VM_Class vmclass = vmmethod.getDeclaringClass();
    Class javaclass = getClassForVMType(vmclass);
    VM_Type vmtypes[] = vmmethod.getParameterTypes();
    Class javatypes[] = new Class[vmtypes.length];

    // convert the type for the parameter list
    for (int i=0; i<vmtypes.length; i++) 
      javatypes[i] = getClassForVMType(vmtypes[i]);


    return getMethodAnywhere(javaclass, vmmethod.getName().toString(), javatypes);

    // try {
    // 	 //java.lang.reflect.Method javamethod = javaclass.getMethod(vmmethod.getName().toString(), javatypes);
    // 	 java.lang.reflect.Method javamethod = javaclass.getDeclaredMethod(vmmethod.getName().toString(), javatypes);
    // 	 javamethod.setAccessible(true);
    // 	 return javamethod;
    // } catch (NoSuchMethodException e) {
    // 	 return null;
    // }
  }

  
  protected VM_Method getCalledMethodFromPoolIndex(int index, VM_Class caller) 
  {
    VM_Method called_method = caller.getMethodRef(index); // constant pool to method dictionary 
    VM_Class callee = called_method.getDeclaringClass();
    try
      {
	callee.load();
      }
    catch (VM_ResolutionException e)
      {
	System.out.println(e);
	e.printStackTrace(System.err);
	assert(NOT_IMPLEMENTED);
      }
    return called_method;
  }
  
  
  protected VM_Class getClassFromPoolIndex(int index, VM_Class caller)  
  {
    VM_Type callee_type = caller.getTypeRef(index);
    if (!callee_type.isClassType())
      {
	throw new ClassCastException(callee_type.toString() + "is not a class type");
      }
    ////// skip until we have exceptions      assert(callee_type.isClassType()); // should be some java.lang error
    VM_Class callee = (VM_Class) callee_type;
    try
      {
	callee.load();
      }
    catch (VM_ResolutionException e)
      {
	assert(NOT_IMPLEMENTED);
      }
    return callee;
  }
  
  public ByteCodeContext getByteCodeContext()
  {
    return byte_codes;
  }
  
  public VM_Class getCurrentClass()
  {
    return byte_codes.getMethod().getDeclaringClass();  // could be cached.
  }
  
  private DataInputStream debug_in = null;
  private boolean single_step = true;
  
  /** 
   * Built in debugger for the interpreter
   */
  public void debug() 
  {
    
    if (debug_in == null) debug_in = new DataInputStream(System.in);
    
    try
      {
	while(true)
	  {
	    System.out.print("jid>");
	    String line = debug_in.readLine();
	    if (line == null) System.exit(0);
	    
	    StringTokenizer tokens = new StringTokenizer(line);
	    while (tokens.hasMoreTokens())
	      {
		String token = tokens.nextToken();
		if (token.equals("?"))
		  {
		    debug_usage();
		  }
		else if (token.equals("f"))
		  {
		    System.out.println(stack);
		  }
		else if (token.equals("w"))
		  {
		    System.out.println(debug_methods_called());
		  }
		else if (token.equals("s"))
		  {
		    return;
		  }
		else if (token.equals("t"))
		  {
		    if (traceInterpreter > 0) {
		      System.out.println("InterpreterBase: disable trace mode");
		      traceInterpreter = 0;
		      ByteCodeContext.traceByteCodes = false;
		      InterpreterStack.trace = false;
		    } else {
		      System.out.println("InterpreterBase: enable trace mode");
		      traceInterpreter = 10;
		      ByteCodeContext.traceByteCodes = true;
		      InterpreterStack.trace = true;
		    }
		  }
		else if (token.equals("tx"))
		  {
		    if (traceExtension) {
		      System.out.println("InterpreterBase: disable trace for mapped bytecode extension");
		      traceExtension = true;		      
		    } else {
		      System.out.println("InterpreterBase: enable trace for mapped bytecode extension");
		      traceExtension = true;
		    }
		  }
		else if (token.equals("c")) 
		  {
		    debug = false;
		    return;    // stop single step.
		  }
		else if (token.equals("cr"))
		  {
		    // Continue-to-return is implemented by turning off
		    // "debug" flag until a "return" bytecode is hit and
		    // the ByteCodeContext is the same as the current one.
		    // See "return" bytecodes in interpreter switch.
		    //
		    debug_after_return = byte_codes;
		    debug = false;  // stop single step.
		    return;    
		  }
		else if (token.equals("v"))
		  {
		    String addr;
		    if (tokens.hasMoreTokens())
		      {
			try
			  {
			    VM_Address address = VM_Address.fromInt(Integer.parseInt(tokens.nextToken(),16));
			    Object o = VM_Magic.addressAsObject(address);
			    System.out.println("Object at address "+address+" toString():");
			    System.out.println(o);
			    /////////////// hack
			    char ca[] = (char []) o;
			    System.out.print("char ["+ca.length+"]=");
			    for (int i = 0; i < ca.length && i < 30; i++) System.out.print(ca[i]);
			    System.out.print("\n");
			  }
			catch(NumberFormatException e)
			  {
			    System.out.println(e);
			    // try again turkey.
			  }
		      }
		    else
		      {
			System.out.println("give an address in hex after command v");
		      }
		  }
		else if (token.equals("m")) 
		  {
		    // mapVM.cacheMethodDictionary();
		  }
		else if (token.equals("d")) 
		  {
		    mapVM.dumpCache();
		  }
		else if (token.equals("q") || token.equals("quit"))
		  {
		    VM.sysExit(-1);
		  }
		else if (token.equals("jt"))
		  {
		    if (tokens.hasMoreTokens())
		      {
			try
			  {
			    int slot = Integer.parseInt(tokens.nextToken(),10);
			    int num = VM_Statics.getSlotContentsAsInt(slot);
			    if (VM_Statics.isReference(slot))
			      System.out.println("Object = " + num);
			    else 
			      System.out.println("Constant = " + num);
			  }
			catch(NumberFormatException e)
			  {
			    System.out.println(e);
			  }
		      }
		  }
		else
		  {
		  System.out.println(token+"...not implemented (use ? for help and q for quit)");	
		  }
	      }	 
	  }
      }
    catch(IOException e)
      {
	System.out.println("InterpreterBase: debug() continuing after "+e);
      }
  }
  
  void debug_usage()
  {
    System.out.println("? - print this message");
    System.out.println("    You are in the special debug-the-debugger mode");
    System.out.println("f - print current stack frame");
    System.out.println("w - print call stack ");
    System.out.println("s - single step");
    System.out.println("t - start tracing");
    System.out.println("o - stop tracing");
    System.out.println("cr - continue execution up to a return, then single stepping");
    System.out.println("c - continue execution, stop single stepping");
    System.out.println("c - continue execution, stop single stepping");
    System.out.println("jt - print the static table");
    System.out.println("v - view an object; give address in hex, cross you fingers, we're going in");
  }
  
  String debug_methods_called()
  {
    System.out.println("********************************");
    StringBuffer result = new StringBuffer("InterpreterBase, methods called:\n");
    if (stack == null) result.append("** Stack is null! **");
    else  
      {
	InterpreterStack stack_clone = (InterpreterStack) stack.deepClone();
	ByteCodeContext context = stack_clone.getCurrentByteCodeContext();
	while(context != null)
	  { 
	    result.append(context.toString());
	    result.append("\n");
	    stack_clone.popByteCodeContext();
	    context = stack_clone.getCurrentByteCodeContext();
	  }
      }
    System.out.println(new String(result));
    System.out.println("********************************");
    return new String(result);
  }


  /**
   * same as VM_Class.forName() except we don't call VM_Class.initialize()
   * which contains VM_Magic calls
   */
  static VM_Class forName(String className)
    throws VM_ResolutionException
  {
    VM_Atom classDescriptor = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
    VM_Class cls = VM_ClassLoader.findOrCreateType(classDescriptor,VM_SystemClassLoader.getVMClassLoader()).asClass();

    cls.load();
    cls.resolve();
    cls.instantiate();
    return cls;
  }

  static void assert(boolean b) {
    if (b)
      return;
    throw new RuntimeException();
  }

  static java.lang.reflect.Field getFieldAnywhere(Class currentClass, String fieldName) 
    throws NoSuchFieldException {
    
    // Field[] field = currentClass.getDeclaredField(vmfield.getName().toString());
    // field.setAccessible(true);
    // return field;

    for (Class cls = currentClass; cls != null; cls = cls.getSuperclass()) {
      Field[] fields = cls.getDeclaredFields();
      for (int i = 0; i < fields.length; ++i) {
	Field f = fields[i];
	if (f.getName().equals(fieldName)) {
	  f.setAccessible(true);
	  return f;
	}
      }
    }

    throw new NoSuchFieldException();

  }

  static java.lang.reflect.Method getMethodAnywhere(Class currentClass, String methodName, Class[] argTypes) {
    
    // Field[] field = currentClass.getDeclaredField(vmfield.getName().toString());
    // field.setAccessible(true);
    // return field;

    for (Class cls = currentClass; cls != null; cls = cls.getSuperclass()) {
      Method[] methods = cls.getDeclaredMethods();
      for (int i = 0; i < methods.length; ++i) {
	Method m = methods[i];
	if (m.getName().equals(methodName) && checkMethodArgumentType(m, argTypes)) {
	  m.setAccessible(true);
	  return m;
	}
      }
    }

    return null;

  }


  static boolean checkMethodArgumentType(Method currentMethod, Class[] argTypes) {

    Class[] actualTypes = currentMethod.getParameterTypes();
    if (actualTypes.length != argTypes.length)
      return false;
    for (int i=0; i<actualTypes.length; i++) {
      if (actualTypes[i] != argTypes[i])
	return false;
    }
    return true;
  }


  
} // class InterpreterBase



