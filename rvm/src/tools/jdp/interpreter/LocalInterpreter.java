/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 *    This class extends the InterpreterBase by adding methods that are specific
 * to environment, e.g. access methods through ptrace for remote process or 
 * methods to read coredump.
 * @author John Barton
 *
 */

import java.lang.reflect.*;

class LocalInterpreter extends InterpreterBase
   {
   /**
    * Interpret a "main()" method for a class.
    * @param args: class_name arg0, arg1, ...
    */
   public static void main(String args[]) throws Exception
      {
      if (args.length <= 0) usage();  // does not return.

      VM_Class cls = null;
      try
         {
	 cls = InterpreterBase.forName(args[0]);
         }
      catch (VM_ResolutionException e)
         { // no such class
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
      
      LocalInterpreter interpreter = new LocalInterpreter();

      if (traceInterpreter) System.out.println("ReflectiveInterpreter: interpreting "+mainMethod);

      interpreter.interpretMethod(mainMethod, mainArgs);

      }

   /**
    * Print usage message and exit.
    */

   public static void usage()
      {
      System.err.println("LocalInterpreter.main: class_with_main, args for main");
      System.exit(-1);
      }


     //
     // -- implemention of abstract methods of VM_InterperterBase
     //
  protected int getStaticWord(int index) 
  {
    if (traceInterpreter) 
      System.out.println("LocalInterpreter: VM_Statics.getSlotContentsAsInt("+index+") (" +
			 VM_Statics.getSlotDescriptionAsString(index)+")");
    return VM_Statics.getSlotContentsAsInt(index);
  }

 
  protected Object _new(VM_Class klass) 
  {
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
   protected Object _newarray(VM_Array array, int num) 
      {
	Class aclass = array.getClassForType();
	return java.lang.reflect.Array.newArray(aclass, num);

      // array.load();
      // array.resolve();
      // array.instantiate();
      // Object[] tib = array.getTypeInformationBlock();
      // Object obj = VM_Runtime.quickNewArray(num,
      // 				  array.getInstanceSize(num),
      // 				  tib);
      // return obj;
      }
   
   protected void invokeMagic(VM_Method called_method)
      {
      if (sysCall1 == null) super.init();  /// TODO: convince VM to do this

      if (traceInterpreter) System.out.println("LocalInterpreter: invokeMagic on "+called_method);
      
      VM_Atom methodName = called_method.getName();
     
      if (methodName == sysCall0)
         {
         int toc = stack.popInt();
         int ip = stack.popInt();
         if (traceInterpreter) System.out.println("LocalInterpreter.sysCall0: ip="+ip+" toc="+toc);
         int result = VM_Magic.sysCall0(ip, toc);  // this call will be inlined by the compiler.
         stack.push(result);
         }
      else if (methodName == sysCall1) 
	 {
	 int parameter = stack.popInt();  // args to sysCall1 are pushed left to right, so pop right to left.
	 int toc = stack.popInt();
	 int ip = stack.popInt();
	 if (traceInterpreter) System.out.println("LocalInterpreter.sysCall1: ip="+ip+" toc="+toc+" parameter="+parameter);
	 int result = VM_Magic.sysCall1(ip, toc, parameter);  // this call will be inlined by the compiler.
	 stack.push(result);
	 }
      else if (methodName == sysCall2) 
	 {
	 int parameter2 = stack.popInt();  // args to sysCall1 are pushed left to right, so pop right to left.
	 int parameter1 = stack.popInt();  // args to sysCall1 are pushed left to right, so pop right to left.
	 int toc = stack.popInt();
	 int ip = stack.popInt();
	 if (traceInterpreter) System.out.println("LocalInterpreter.sysCall2: ip="+ip+" toc="+toc+" parameters="+parameter1+","+parameter2);
	 int result = VM_Magic.sysCall2(ip, toc, parameter1, parameter2);  // this call will be inlined by the compiler.
	 stack.push(result);
	 }
      else if (methodName == sysCall3) 
	 {
	 int parameter3 = stack.popInt(); 
	 int parameter2 = stack.popInt(); 
	 int parameter1 = stack.popInt(); 
	 int toc = stack.popInt();
	 int ip = stack.popInt();
	 if (traceInterpreter) System.out.println("LocalInterpreter.sysCall3: ip="+ip+" toc="+toc+" parameters= "+
						  parameter1+", "+parameter2+", "+parameter3);
	 int result = VM_Magic.sysCall3(ip, toc, parameter1, parameter2, parameter3);  // this call will be inlined by the compiler.
	 stack.push(result);
	 }
      else if (methodName == floatAsIntBits)
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
	 System.out.println("LocalInterpreter: getFramePointer cannot be implemented");
	 System.out.println(debug_methods_called());
	 InterpreterBase.assert(NOT_IMPLEMENTED);
	 }
      else if (methodName == getMemoryWord)
	 {
	 int address = stack.popInt();
	 System.out.print("LocalInterpreter: VM.Magic.getMemoryWord("+Integer.toHexString(address)+")=");
	 int word = VM_Magic.getMemoryWord(address);
	 System.out.println(Integer.toHexString(word));
	 stack.push(word);
	 }
      else if (methodName == getObjectType)
	 {
	 // VM_Type getObjectType(Object object);
	 Object obj = stack.popObject();
	 InterpreterBase.assert(obj != null);
	 System.out.print("LocalInterpreter: VM.Magic.getObjectType("+obj+")=");
	 VM_Type the_type = VM_Magic.getObjectType(obj);
	 stack.push(the_type);
	 if (traceInterpreter) System.out.println(the_type);
	 }
      else if (methodName == isync) 
	{ // nothing to do
	  // System.out.println("isync is being skipped");
	}
      else
	 {
	 System.out.println(called_method+" not implemented");
	 InterpreterBase.assert(NOT_IMPLEMENTED);
	 }
      }
   
   protected VM_StackTrace[] extendStackTrace(VM_StackTrace base[])
      {
      int size = base.length;
      int extension = 1;    // TEMP!!
      VM_StackTrace[] extended = new VM_StackTrace[size + extension];
      for (int i = 0; i < base.length; i++)
	 {
	 extended[i] = base[i];
	 }

      // TEMP
      extended[size] = new VM_StackTrace();

      extended[size].method = extended[size-1].method;
      extended[size].instructionOffset = extended[size-1].instructionOffset;
      return extended;
      }
   
   }


