/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
import com.ibm.JikesRVM.*;

/**
 * @author John Barton
 * 
 *  Java VM Stack implemented as three parallel stacks,
 *   primitives, objects, and type
 *  
 *  Each active method has a region of the stack called its 
 *  "stack frame".  Each stack frame starts with the
 *  local variables of the method followed by an object 
 *  reference to the VM_Method and an integer giving the
 *  stack top of the caller of this frame.  The comes the
 *  expression stack for the method.  At a function call,
 *  the expression stack of the caller becomes the locals
 *  of the callee:
 *
 * 
 * -------------------------------------
 *       L0                          <-- on call "locals" is here
 * 	 L1
 * 	 L2
 * 	 L3
 * 	 ByteCodeContext
 *       0                         <-- old locals
 *       -1 <---------------------+ <-- on call frame_index is here
 *       T0                       |
 * 	 T1        L0             |
 *       T2        L1             |
 * 	 T3        L2             | <-- on call top is here
 *                 L3             |
 *                 ByteCodeContext|
 *                 5 -------------+  
 *                 0            
 *                 T0
 * ...
 * --------------------------------------
 *
 * 
 * AT first byte code of callee
 * -------------------------------------
 * 0     L0                    
 * 1	 L1
 * 2	 L2
 * 3	 L3
 * 4	 ByteCodeContext
 * 5      0                         <-- callers locals
 * 6	 -1 <---------------------+ 
 * 7     T0                       | <-- callers stack starts after its frame_index
 * 8	 T1        L0             | <-- locals is here
 * 9     T2        L1             |
 * 10	 T3        L2             | 
 * 11              L3             |
 * 12              ByteCodeContext|
 * 13              5              | <-- new callers locals 
 * 14              6 -------------+ <-- frame_index and top before first push
 * 15              T0               <-- first entry in callee expresson stack
 * ...
 * --------------------------------------
 *
 *
 *
 *
 */

class InterpreterStack
   implements VM_Constants 
   {

     /**
      * Replace any occurences of this unInitType object in the
      * current frame (the range from locals to frame_index)
      */
     void replaceUnInit(unInitType ut, Object newobj) {
       Class unInitClass;
       try {
	 unInitClass = Class.forName("unInitType");
         for (int i=locals; i<=top; i++) {
           if (descriptors[i] == reference_type) {
             if (objects[i]!=null) {
               Class objclass = objects[i].getClass();
               if (objclass==unInitClass) {
         	 if (((unInitType) objects[i]).equals(ut)) {
         	   objects[i] = (Object) newobj;
         	 }
               }
             }
           }
         }
       } catch (ClassNotFoundException e) {
	 InterpreterBase.assert(NOT_REACHED);   // shouldn't be here
       }

     }


// ints

    void push(int v)     
      {
      _push (v, int_type);
      }
     /*
       void push(char c)
       {
       if (checking) 
       {
       if (c < '\u0000' || c > '\uffff') throw new InternalError("InterpreterStack pushed out of range char");
       }
       else _push(c, int_type);
       }
       */

     int popInt()  
      {
      if (checking)
	 {
	 checkStackUnderflow();
	 checkType(top, int_type);
	 }
      return primitives[top--];
      }
   
   void setLocal(int i, int v)
      {
      if (checking) checkLocalRange(i);
      objects    [locals+i]   = null;
      primitives [locals+i]   = v;
      descriptors[locals+i]   = int_type;
      }

   int getLocalInt(int i)
      {
      if (checking)
	 {
	 checkLocalRange(i);
	 checkType(locals+i, int_type);
	 }
      return primitives[locals+i];
      }

// Objects

     void push(Object ref)     
  {
    incrementTop();
    objects[top] = ref;
    primitives[top] = 0;
    descriptors[top] = reference_type;
  }


     /**
      * Given a Java Class, pop the stack and wrap the value
      * and return it as an object of this class
      * (copied from InterpreterBase._invokespecial,
      * remember to use this there once working)
      * @param objClass  the Java Class that the top stack entry is expected to be
      * @return an object wrapping the top stack entry, stack is popped
      */
     Object popAsObject(Class objClass) {
       VM_Type paramType = InterpreterBase.getVMTypeForClass(objClass);
       return popAsObject(paramType);
     }

     /**
      * Given a VM_Type, pop the stack and wrap the value
      * and return it as an object of this type
      * We have to handle the mapped case because this method is intended for
      * Java reflection and it does not know about mapped object:
      * <p>
      * <ul>
      * <li> For mapped primitive or array of primitive, copy the values from 
      * the JVM side (push it onto the stack in the appropriate type and
      * pop it back out wrapped as an object)
      * <li> We don't try to copy mapped object, hopefully we don't to.
      * </ul>
      * 
      *
      * @param paramType the VM_Type that the top stack entry is expected to be
      * @return an object wrapping the top stack entry, stack is popped
      * @see InterpreterBase._invokespecial
      */
     Object popAsObject(VM_Type paramType) {
       // System.out.println("popAsObject: " + paramType);

       // if this is a mapped object, we have to unmap it first and
       // replace it with a copy of the primitive value on the stack
       // before popping and wrapping it.
       if (descriptors[top] == reference_type) {
	 if (mapVM.isMappedObject(objects[top])) {
	   Object popped = null;
	   try {
	     popped = popObject();
	     pushMappedPrimitiveToStack((mapVM) popped);
	   } catch (Exception e) {
	     System.out.println("popAsObject: ERROR, " + e.getMessage() + ", popped=" + popped);
	     System.out.println(this);
	     System.out.println("--------------------------------------------------");
	     e.printStackTrace();
	   }
	 }
       }

       if (paramType.isFloatType()) {                                 // Float
	 if (checking) 
	   checkType(top, float_type);
         return VM_Reflection.wrapFloat(popFloat());
       } else if (paramType.isDoubleType()) {                         // Double
	 if (checking) {
	   checkType(top, double_type_lo);
	   checkType(top-1, double_type_hi);
	 }
         return VM_Reflection.wrapDouble(popDouble());
       } else if (paramType.isLongType()) {                           // Long
	 if (checking) {
	   checkType(top, long_type_lo);
	   checkType(top-1,long_type_hi);
	 }
         return VM_Reflection.wrapLong(popLong());
       } else if (paramType.isBooleanType()) {                        // Boolean
	 if (checking) 
	   checkType(top, int_type);
         return VM_Reflection.wrapBoolean(popInt());
       } else if (paramType.isByteType()) {                           // Byte
	 if (checking) 
	   checkType(top, int_type);
         return VM_Reflection.wrapByte((byte) popInt());
       } else if (paramType.isCharType()) {                           // Char
	 if (checking) 
	   checkType(top, int_type);
         return VM_Reflection.wrapChar((char) popInt());
       } else if (paramType.isShortType()) {                          // Short
	 if (checking) 
	   checkType(top, int_type);
         return VM_Reflection.wrapShort((short) popInt());
       } else if (paramType.isReferenceType()) {                      // Object
	 if (checking) 
	   checkType(top, reference_type);
         return popObject();
       } else {                                                  // for the rest, treat as int
	 if (checking) 
	   checkType(top, int_type);
	 // System.out.println("popAsObject: wrapped as Integer");		
         return VM_Reflection.wrapInt(popInt());
       }
     }

     /** 
      * Given an object, unwrap it and push on the stack
      * according to its type: primitive Class as primitive,
      * Object Class as reference
      */
     void pushObjectUnwrapped(Object obj, VM_Type paramType) {
       // System.out.println("pushObjectUnwrapped: unwrapping " + paramType);

       if (paramType.isFloatType()) {                                 // Float
         push(VM_Reflection.unwrapFloat(obj));
       } else if (paramType.isDoubleType()) {                         // Double
         push(VM_Reflection.unwrapDouble(obj));
       } else if (paramType.isLongType()) {                           // Long
         push(VM_Reflection.unwrapLong(obj));
       } else if (paramType.isBooleanType()) {                        // Boolean
         push(VM_Reflection.unwrapBooleanAsInt(obj));
       } else if (paramType.isByteType()) {                           // Byte
         push( (int) VM_Reflection.unwrapByte(obj));
       } else if (paramType.isCharType()) {                           // Char
         push( (int) VM_Reflection.unwrapChar(obj));
       } else if (paramType.isShortType()) {                          // Short
         push( (int) VM_Reflection.unwrapShort(obj));
       } else if (paramType.isReferenceType()) {                      // Object
	 // System.out.println("pushObjectUnwrapped: unwrapped as object");
         push(obj);
       } else {                                                  // for the rest, treat as int
         push(VM_Reflection.unwrapInt(obj));
       }       
     }

  /**
   *  Given a mapped primitive and its size, read its value and put it on the
   * stack as the appropriate type.  This also handles array of primitive, but
   * not array of objects
   */

  public void pushMappedPrimitiveToStack(mapVM mappedObject) throws Exception {
    int mappedFieldAddress = mappedObject.getAddress();
    int size = mappedObject.getSize();
    int mappedFieldValue;
    switch (size) {
    case 1: mappedFieldValue = Platform.readByte(mappedFieldAddress); break;
    case 2: mappedFieldValue = Platform.readShort(mappedFieldAddress); break;
    case 4: 
    case 8: mappedFieldValue = Platform.readmem(mappedFieldAddress); break;
    default: 
      System.out.println("pushMappedPrimitiveToStack: unknown size for primitive type"); 
      return;
    }
    int mappedFieldValue1 = Platform.readmem(mappedFieldAddress+4); 

    VM_Type fieldType = mappedObject.getType();
    if (fieldType.isBooleanType()) {
      if (InterpreterBase.traceExtension)
        InterpreterBase.log("pushMappedPrimitiveToStack: boolean, size " + size);
      push(mappedFieldValue==0?0:1);
    } else if (fieldType.isByteType()) {
      if (InterpreterBase.traceExtension)
        InterpreterBase.log("pushMappedPrimitiveToStack: byte, size " + size);
      push((byte) mappedFieldValue);
    } else if (fieldType.isCharType()) {
      if (InterpreterBase.traceExtension)
        InterpreterBase.log("pushMappedPrimitiveToStack: char, size " + size);
      push((char) mappedFieldValue);
    } else if (fieldType.isDoubleType()) {
      if (InterpreterBase.traceExtension)
        InterpreterBase.log("pushMappedPrimitiveToStack: double, size " + size);
      double element = Double.longBitsToDouble(twoIntsToLong(mappedFieldValue, mappedFieldValue1));
      push(element);
    } else if (fieldType.isFloatType()) {
      if (InterpreterBase.traceExtension)
        InterpreterBase.log("pushMappedPrimitiveToStack: float, size " + size);
      float element = Float.intBitsToFloat(mappedFieldValue);
      push(element);
    } else if (fieldType.isIntType()) {
      if (InterpreterBase.traceExtension)
        InterpreterBase.log("pushMappedPrimitiveToStack: int, size " + size);
      push(mappedFieldValue);
    } else if (fieldType.isLongType()) {
      if (InterpreterBase.traceExtension)
        InterpreterBase.log("pushMappedPrimitiveToStack: long, size " + size);
      long element = twoIntsToLong(mappedFieldValue, mappedFieldValue1);
      push(element);
    } else if (fieldType.isShortType()) {
      if (InterpreterBase.traceExtension)
        InterpreterBase.log("pushMappedPrimitiveToStack: short, size " + size);
      push((short) mappedFieldValue);
    } 

    else if (fieldType.isArrayType()) {      
      VM_Type elementType = fieldType.asArray().getInnermostElementType();
      if (!elementType.isPrimitiveType()) {
	throw new Exception("don't know how to clone an array of object yet");
      }
      push(mapVM.getMappedArray(mappedObject));
    } 

    else if (fieldType.isJavaLangStringType()) {
      if (InterpreterBase.traceExtension)
	InterpreterBase.log("pushMappedPrimitiveToStack: cloning String");
      push(mapVM.getMappedString(mappedObject));
    }

//      else if (fieldType.toString().equals("VM_Type")) {
//        if (InterpreterBase.traceExtension)
//  	System.out.println("pushMappedPrimitiveToStack: cloning VM_Type");
//        //XXX TODO: put getMappedVM_Type in mapVM push(mapVM.getMappedVM_Type(mappedObject));
//        push(mapVM_getMappedVM_Type(mappedObject));
//      }

//      else if (fieldType.toString().equals("VM_LineNumberMap")) {
//        if (InterpreterBase.traceExtension)
//  	System.out.println("pushMappedPrimitiveToStack: cloning VM_LineNumberMap");
//        //XXX TODO: put getMappedVM_LineNumberMap in mapVM push(mapVM.getMappedVM_LineNumberMap(mappedObject));
//        push(mapVM_getMappedVM_LineNumberMap(mappedObject));
//      }

    else {
      System.err.println("this type is currently not being handled, " + fieldType); 
      InterpreterBase.jid(); //new
      throw new Exception("this type is currently not being handled, " + fieldType); 
    } 
  }   
     //XXX
     // for now put it here so we don't have to
     // recompile mapVM
  //palm
  static VM_LineNumberMap mapVM_getMappedVM_LineNumberMap(mapVM mapped) {
    
    // address of the object
    final int address = mapped.getAddress();

    // we need the two int[] arrays: startPCs and lineNumbers
    int startPCAddress = getAddress("LVM_LineNumberMap;", "startPCs", address);
    int lineNumbersAddress = getAddress("LVM_LineNumberMap;", "lineNumbers", address);

    // read in the arrays
    int[] startPCs = readIntArray(startPCAddress);
    int[] lineNumbers = readIntArray(lineNumbersAddress);

    // Construct the new map
    VM_LineNumberMap map = new VM_LineNumberMap(startPCs, lineNumbers);
    return map;
  }

  static int getAddress(String clsName, String fieldName, int address) {
    try {
      VM_Field field = BootMap.findVMField(clsName, fieldName);
      return Platform.readmem(address + field.getOffset());
    } catch (BmapNotFoundException e2) {
      VM.sysWrite("Trouble with " + clsName + "." + fieldName + " @ " + Integer.toHexString(address));
      VM.sysWrite(e2 + "\n");
      e2.printStackTrace();
      VM.sysExit(1);            
    }
    return 0;
  }     

  static int[] readIntArray(int address) {
    final int count = Platform.readmem(address + VM_ObjectModel.getArrayLengthOffset());
    int[] v = new int[count];
    for (int i = 0; i < v.length; i++) {
      v[i] = Platform.readmem(address + i*4);
    }
    return v;
  }

  static VM_Type mapVM_getMappedVM_Type(mapVM mappedVM_Type) {

    // The address of the mapped object
    int address = mappedVM_Type.getAddress();

    // Get the VM_Atom descriptor of class VM_Type
    try {
      VM_Field descriptorField = BootMap.findVMField("LVM_Type;", "descriptor");
      address = Platform.readmem(address + descriptorField.getOffset());
    } catch (BmapNotFoundException e2) {
      VM.sysWrite(e2 + "\n");
      e2.printStackTrace();
      VM.sysExit(1);            
    }

    // Get the byte[] val field of class VM_Atom
    try {
      VM_Field valField = BootMap.findVMField("LVM_Atom;", "val");
      address = Platform.readmem(address + valField.getOffset());
    } catch (BmapNotFoundException e2) {
      VM.sysWrite(e2 + "\n");
      e2.printStackTrace();
      VM.sysExit(1);            
    }

    // val.length
    final int count = Platform.readmem(address + VM_ObjectModel.getArrayLengthOffset());

    // Clone the byte array val
    byte[] bytes = new byte[count];
    for (int i = 0; i < count; i++) {
      bytes[i] = Platform.readByte(address + i);
    }
    
    // Create a possibly new type in this VM
    String keyString = new String(bytes);
    VM_Atom descriptor = VM_Atom.findOrCreateUnicodeAtom(keyString);
    ClassLoader classLoader = null;
    VM_Type type = VM_ClassLoader.findOrCreateType(descriptor, classLoader);
    return type;
  }
  //end-palm




    Object popObject () 
      {
      if (checking)
	 {
	 checkStackUnderflow();
	 checkType(top, reference_type);
	 }
      return objects[top--];
      }

   void setLocal(int i, Object ref)
      {
      if (checking) checkLocalRange(i);
      objects    [locals+i] = ref;
      primitives [locals+i] = 0;
      descriptors[locals+i] = reference_type;
      }

   Object getLocalObject(int i)
      {
      if (checking)
	 {
	 checkLocalRange(i);
	 checkType(locals+i, reference_type);
	 }
      return objects[locals+i];
      }

// Floats

    void push(float v)   
      {
      _push (Float.floatToIntBits(v), float_type);
      }

    float  popFloat ()  
      {
      if (checking)
	 {
	 checkStackUnderflow();
	 checkType(top, float_type);
	 }
      return Float.intBitsToFloat(primitives[top--]);
      }

   void setLocal(int i, float f)
      {
      if (checking) checkLocalRange(i);
      objects    [locals+i] = null;
      primitives [locals+i] = Float.floatToIntBits(f);
      descriptors[locals+i] = float_type;
      }

   float getLocalFloat(int i)
      {
      if (checking)
	 {
	 checkLocalRange(i);
	 checkType(locals+i, float_type);
	 }
      return  Float.intBitsToFloat(primitives[locals+i]);
      }

// Doubles

    void push(double v)  
      {
      long l = Double.doubleToLongBits(v);
      _push ((int)(l>>>32), double_type_hi); 
      _push ((int)l, double_type_lo);
      }

   void pushDoubleBits(int hi, int lo)
      {
      _push(hi, double_type_hi);
      _push(lo, double_type_lo);
      }

   double popDouble()  
      {
      if (checking)
	 {
	 checkStackUnderflowLong();
	 checkType(top, double_type_lo);
	 checkType(top-1, double_type_hi);
	 }
      double val = Double.longBitsToDouble(twoIntsToLong(primitives[top-1], primitives[top]));
      top -= 2;
      return val;
      }

    void setLocal(int i, double d)
      {
      if (checking) checkLocalRange(i);
      long l = Double.doubleToLongBits(d);
      objects    [locals+i]   = null;
      primitives [locals+i]   = (int) (l >>> 32);
      descriptors[locals+i]   = double_type_hi;
      objects    [locals+i+1] = null;
      primitives [locals+i+1] = (int) l;
      descriptors[locals+i+1] = double_type_lo;
      }

   double getLocalDouble(int i)
      {
      if (checking)
	 {
	 checkLocalRange(i);
	 checkLocalRange(i+1);
	 checkType(locals+i, double_type_hi);
	 checkType(locals+i+1, double_type_lo);
	 }
      return Double.longBitsToDouble(twoIntsToLong(primitives[locals+i], primitives[locals+i+1]));
      }

// Long

    void push(long v)    
      {
      _push ((int)(v>>>32), long_type_hi); 
      _push ((int)v,       long_type_lo);
      }

   void pushLongBits(int hi, int lo)
      {
      _push(hi, long_type_hi);
      _push(lo, long_type_lo);
      }
   
   long  popLong()  
      {
      if (checking)
	 {
	 checkStackUnderflowLong();
	 checkType(top, long_type_lo);
	 checkType(top-1,long_type_hi);
	 }
      long result = twoIntsToLong(primitives[top-1], primitives[top]);
      top -= 2;
      //XXX
      //XXX This is a horrible hack around loading longs.
      //XXX It's temporary, but without it, you don't know what
      //XXX will be returned when popping a long
      //XXX
      String s = ""+result;
      return result;
      }

   void setLocal(int i, long v)
      {
      if (checking) checkLocalRange(i);
      objects    [locals+i]   = null;
      primitives [locals+i]   = (int) (v >>> 32);
      descriptors[locals+i]   = long_type_hi;
      objects    [locals+i+1] = null;
      primitives [locals+i+1] = (int) v;
      descriptors[locals+i+1] = long_type_lo;
      }

    long getLocalLong(int i)
      {
      if (checking)
	 {
	 checkLocalRange(i);
	 checkLocalRange(i+1);
	 checkType(locals+i, long_type_hi);
	 checkType(locals+i+1, long_type_lo);
	 }
      return twoIntsToLong(primitives[locals+i], primitives[locals+i+1]);
      }

   void popAny()
      {
      checkStackUnderflow();
      top--;
      }

   void swap()
      {
      if (checking) checkStackUnderflowLong(); // must be at least two words.
      int p = primitives[top];
      primitives[top] = primitives[top-1];
      primitives[top-1] = p;
      Object o = objects[top];
      objects[top] = objects[top-1];
      objects[top-1] = o;
      int d = descriptors[top];
      descriptors[top] = descriptors[top-1];
      descriptors[top-1] = d;
      }

    void pop2()     
      {
      checkStackUnderflow();
      top--;
      checkStackUnderflow();
      top--;
      }

    void dup()      
      {
      incrementTop();
      //   -2 -1 -0 top
      //       w  X
      //       w  w
      copyTop(-0, -1);
      }

    void dup_x1()   
      {
      incrementTop();
      //   -2 -1 -0 top
      //   w2 w1  X  
      //   w1 w2 w1
      copyTop(-0, -1);  
      copyTop(-1, -2);  
      copyTop(-2, -0);  // -0 has w1
      }

    void dup_x2()   
      {
      incrementTop();
      //   -3 -2 -1 -0 top
      //   w3 w2 w1  X
      //   w1 w3 w2 w1
      copyTop(-0, -1);
      copyTop(-1, -2);
      copyTop(-2, -3);
      copyTop(-3, -0);  // -0 has w1 now
      }

    void dup2()     
      {
      incrementTop();
      incrementTop();
      //   -3 -2 -1 -0 top
      //   w2 w1  X  X  ->
      //   w2 w1 w2 w1
      copyTop(-0, -2);
      copyTop(-1, -3);
      }

    void dup2_x1()  
      {
      incrementTop();
      incrementTop();
      //   -4 -3 -2 -1 -0 top
      //   w3 w2 w1  X  X  ->
      //   w2 w1 w3 w2 w1
      copyTop(-0, -2);
      copyTop(-1, -3);
      copyTop(-2, -4);
      copyTop(-3, -0);  // -0 has w1 now
      copyTop(-4, -1);
      }

    void dup2_x2()  
      {
      incrementTop();
      incrementTop();
      //   -5 -4 -3 -2 -1 -0 top
      //   w4 w3 w2 w1  X  X
      //   w2 w1 w4 w3 w2 w1
      copyTop(-0, -2);
      copyTop(-1, -3);
      copyTop(-2, -4);
      copyTop(-3, -5); 
      copyTop(-4, -0);
      copyTop(-5, -1);
      }

   /**
    *  Copy an object reference out of the stack a given number of words from the top.
    *
    * @param i the number of words from the top
    */
   Object getObjectDown(int i)
      {
      if (checking) 
	 {
	 checkType(top-i, reference_type);
	 checkStackUnderflow();
	 }
      return objects[top-i];
      }

   /** 
    * Create a new stack frame for a new method invocation.
    * First is local words with the first of these being arguments;
    * Next is the ByteCodeContext object reference;
    * Then the frame index of the caller;
    * Then the expression stack of the callee.
    *
    * The first method should be a static method without arguments.
    */
   void push(ByteCodeContext callee, int number_of_parameter_words_counting_this)
      {
      // Save for push
      //
      int callers_locals = locals;

      // The top of the stack now holds the callee arguments and, for
      // virtual functions, "this".  The locals overlap these words.
      //
      locals = top - number_of_parameter_words_counting_this + 1;  // eg 2 - 0 + 1 = 3

      // This method's expression stack begins after all locals and
      // after words used to stack info about the callee
      //
      int num_locals =  callee.getMethod().getLocalWords();
      
      int prev_top = top;
      top = locals + num_locals - 1;  // The new expression stack grows from last local; eg 3 + 1 - 1 = 3
      while (top >= primitives.length) extendStacks();

      // Clear words from old pops between the args and the end of local storage.
      //
      for(int unset_locals = prev_top+1; unset_locals < descriptors.length; unset_locals++) 
	 {
	 objects[unset_locals] = null;
	 descriptors[unset_locals] = unset_type;
	 }

      // Push down the context and previous stack frame information
      //
      push(callee);
      push(callers_locals);
      push(frame_index);   // top now three words beyond locals, eg 7

      if (trace) 
	System.out.println("InterpreterStack: push ByteCodeContext: frame_index from " +
			   frame_index + " to " + top + 
			   "; num_locals=" + num_locals + 
			   "; locals offset=" + locals + 
			   " top=" + top);

      frame_index = top;
      }
   
   ByteCodeContext getCurrentByteCodeContext()
      {
      int mi = contextIndex();
      ///////////System.out.println("InterpreterStack: getCurrentByteCodeContext="+mi+" objects[mi]="+objects[mi]);
      if (mi > 0) return (ByteCodeContext)objects[mi];
      else return null;
      }


   /**
    *  Remove current stack frame and restore caller's frame
    */
   
   ByteCodeContext popByteCodeContext()
      {

      // recover the frame_index from where we stored it on the stack.
      //
      int callers_frame_index = primitives[frame_index];

      // recover the beginning of the callers frame from where we stored it on the stack.
      //
      int callers_locals = primitives[frame_index - 1];

      //  roll back the stack to caller's top, popping the callee's args
      //
      int prev_top = top;
      top = locals - 1; 
      
      // Clear words from old pops between the args and the end of local storage.
      //
      for(int unset_locals = top+1; unset_locals < prev_top; unset_locals++) 
	 {
	 objects[unset_locals] = null;
	 descriptors[unset_locals] = unset_type;
	 }

      // rest to callers
      //
      locals = callers_locals;
      if (trace) System.out.println("InterpreterStack: pop ByteCodeContext: frame_index from "+frame_index+" to "+callers_frame_index+"; new locals="+locals+" top="+top);
      frame_index = callers_frame_index;

      // we should now be ready to push the return value
      return getCurrentByteCodeContext();
      }

   protected Object deepClone()
      {
      InterpreterStack copy = new InterpreterStack();
      copy.primitives = new int[primitives.length];
      copy.objects = new Object[objects.length];
      copy.descriptors = new int[descriptors.length];
      for (int i = 0; i < primitives.length; i++)
	 {
	 copy.primitives[i] = primitives[i];
	 copy.objects[i] = objects[i];
	 copy.descriptors[i] = descriptors[i];
	 }
      copy.top = top;
      copy.frame_index = frame_index;
      copy.locals = locals;
      return copy;
      }

   int expressionStackDepth()
      {
      return top - frame_index;
      }

    InterpreterStack() {
      _init();
    }

   static boolean trace = false;
   static boolean traceTop = false;
   static final boolean checking = true;
   
    void traceStack(String s) {
      System.out.println(s+describeEntry(top));
    }


   // ----------- implementation ----------------------
   // --- NOTE see clone() above ----------------------
   //
   private int primitives[];
   private Object objects[];
   private int descriptors[];
   /** array index of last pushed entry; -1 means empty stack. */
   private int top;
   
   /** Index of center of top frame; at this stack entry is the callers frame_index or -1 */
   private int frame_index; 
   /** Index of the zeroth local variable for the top frame; also the entry of the first argument */
   private int locals; 

    private static final int INITIAL_STACK_SIZE = 16;

   /** Constant values for descriptors */
    private final static int int_type = 1;
    private final static int long_type_hi = 2;
    private final static int long_type_lo = 3;
    private final static int float_type =4;
    private final static int double_type_hi = 5;
    private final static int double_type_lo = 6;
    private final static int return_type = 7;
    private final static int reference_type = 8;
    private final static int unset_type = 9;
   
    private final static int any_type = 99;
  

   private void _init()
      {
      primitives = new int [INITIAL_STACK_SIZE];
      objects = new Object [INITIAL_STACK_SIZE];
      descriptors = new int [INITIAL_STACK_SIZE];
      top = -1;
      locals = 0;
      frame_index = -1;
      }
    
    private void _push(int val, int type)  
      {
      incrementTop();
      primitives[top] = val;
      objects[top]    = null;
      descriptors[top]      = type;
      }

   private final void incrementTop() 
      {
      ++top;
      if (traceTop) System.out.println("InterpreterStack: IncrementTop top="+top);
      if (top >= primitives.length) extendStacks();
      InterpreterBase.assert(top < primitives.length);
      }

   private void extendStacks()
      {
      int original_length = primitives.length;
      int extended_length = original_length*2;
      if (trace) System.out.println("InterpreterStack: extending stacks from "+original_length+" to "+extended_length+"; top="+top);
      int [] p2 = new int [extended_length];
      System.arraycopy(primitives, 0, p2, 0, original_length);
      primitives = p2;

      Object [] o2 = new Object [extended_length];
      System.arraycopy(objects, 0, o2, 0, original_length);
      objects = o2;

      int [] t2 = new int [extended_length];
      System.arraycopy(descriptors, 0, t2, 0, original_length);
      descriptors = t2;
      }

   private final void checkType(int index, int type)
      {
      if ((type != any_type) && descriptors[index] != type) 
	 throw new InternalError("Pop wrong type at index="+index+", had="+description(descriptors[index])+" wanted: "+description(type));
      }

   private final void checkStackUnderflow()
      {
      if (top - 1 < frame_index) throw new InternalError("Pop on Stack Empty");
      }

   private final void checkStackUnderflowLong()
      {
      if (top - 2 < frame_index) throw new InternalError("Pop on Stack Empty");
      }

   private final void checkLocalRange(int i)
      {
      if (i < 0 || i > (frame_index - locals - 2)) throw new InternalError("Attempt to get local "+i+" with frame_index="+frame_index+" and locals="+locals);
      }

   /**
    *  Copy the "src" entry on the stack to the "dst" entry.
    *  The args are always non-positive
    */
   private final void copyTop(int dst, int src)
      {
      primitives[top + dst]  = primitives[top + src]; 
      objects[top + dst]     = objects[top + src]; 
      descriptors[top + dst] = descriptors[top + src];
      }
  
   private String description(int descriptor)
      {
      switch(descriptor)
	 {
	 case int_type:      return "int";
	 case long_type_hi:   return "long_type_hi";
	 case long_type_lo:   return "long_type_lo";
	 case float_type:    return "float_type";
	 case double_type_hi: return "double_type_hi";
	 case double_type_lo: return "double_type_lo";
	 case return_type:   return "return_type";
	 case reference_type:   return "reference_type";
	 case any_type:         return "any_type";
	 default: return "ILLEGAL DESCRIPTOR";
	 }
      }

  public String describeEntry(int entry)
      {
      if (entry < 0) return "stack is empty";

      StringBuffer result = new StringBuffer();
      switch(descriptors[entry])
	 {
	 case int_type:       result.append("int       : "); result.append(primitives[entry]);  break;
	 case long_type_hi:    
	    {
	    result.append("long      : "); 
	    result.append(twoIntsToLong(primitives[entry], primitives[entry+1])); 
	    break;
	    }
	 case long_type_lo:
	    {
	    result.append("long (lo order)");
	    break;
	    }
	 case double_type_hi:  
	    {
	    result.append("double    : "); 
	    result.append(Double.longBitsToDouble(twoIntsToLong(primitives[entry], primitives[entry+1]))); 
	    break;
	    }
	 case double_type_lo:
	    {
	    result.append("double (lo order)");
	    break;
	    }
	 case float_type:     result.append("float     : "); result.append(Float.intBitsToFloat(primitives[entry]));  break;
	 case return_type:    result.append("return    : "); result.append(Integer.toHexString(primitives[entry]));   break;
	 case reference_type: 
	    {
	    result.append("reference : "); 
	    if(objects[entry] == null) result.append("<null>");
            // PROBLEM HERE! We shouldn't use toString() on object when it might not
            // be well-formed (e.g. we are interpreting the constructor)
            //else                       result.append(objects[entry]);
            // else result.append(VM_Magic.getObjectType(objects[entry]));
	    else {
	      String className = objects[entry].getClass().toString();
	      if (className.equals("class ByteCodeContext") ||
		  className.equals("class mapVM") )
		result.append(objects[entry].toString());
	      else
		// for other, don't dump the whole object
		result.append(className);
	    }
	    break;
	    }
	 case unset_type:     result.append("unset_type: ");                                                          break;
	 case any_type:       result.append("any_type  : ");                                                          break;
	 default:             result.append("?? descriptor for entry "+entry+" is "+descriptors[entry]);              break;
	 }
      return new String(result);
      }

   public String describeTop()
      {
      if (expressionStackDepth() < 1) return "<empty expression stack>";
      return describeEntry(top);
      }

   public String toString()
      {
      StringBuffer result = new StringBuffer("InterpreterStack has "+(top+1)+" entries, frame_index="+frame_index+" locals="+locals+" top="+top+"\n");
      for (int i = 0; i <= top; i++)
	 {
	 result.append(i);
	 result.append("  "); 
	 result.append(describeEntry(i));
	 result.append("\n");
	 }
      return new String(result);
      }

   static final long twoIntsToLong(int hi, int lo)
      {
      long result = (((long) hi) << 32);
      result |= ((long) lo) & 0xFFFFFFFFL;
      return result;
      }

   private final int contextIndex()
      {
      return frame_index - 2; // one is locals index and next is context
      }

   /**
    * Test code
    */
   public static void main(String args[])
      {
      InterpreterStack stack = new InterpreterStack();
      System.out.println(stack);
      
      int itest = 12;
      System.out.println("InterpreterStack: push int"+itest);
      stack.push(itest);
      System.out.println(stack);

      System.out.println("InterpreterStack: popInt = "+stack.popInt());
      System.out.println(stack);

      long l = 9223372036854775807L;
      System.out.println("InterpreterStack: push long "+l);
      stack.push(l);
      System.out.println(stack);

      System.out.println("InterpreterStack: popLong = "+stack.popLong());
      System.out.println(stack);

      double d = 3.1415926;
      System.out.println("InterpreterStack: push double "+d);
      stack.push(d);
      System.out.println(stack);

      System.out.println("InterpreterStack: popDouble = "+stack.popDouble());
      System.out.println(stack);

      float f = 3.1415926F;
      System.out.println("InterpreterStack: push float "+f);
      stack.push(f);
      System.out.println(stack);

      System.out.println("InterpreterStack: popFloat = "+stack.popFloat());
      System.out.println(stack);

      String s = new String("Hello");
      System.out.println("InterpreterStack: push ref "+s);
      stack.push(s);      System.out.println(stack);

      System.out.println("InterpreterStack: popObject = "+stack.popObject());
      System.out.println(stack);

      int i1 = 1;
      int i2 = 2;
      int i3 = 3;
      stack.push(i3);
      stack.push(i2);
      stack.push(i1);
      System.out.println(stack);

      System.out.println("InterpreterStack: dup");
      stack.dup();
      System.out.println(stack);

      System.out.println("InterpreterStack: pop2");
      stack.pop2();
      System.out.println(stack);

      stack.push(i1);
      System.out.println("InterpreterStack: setup for dup_x1");
      System.out.println(stack);
      

      System.out.println("InterpreterStack: dup_x1");
      stack.dup_x1();
      System.out.println(stack);

      stack.pop2();
      stack.pop2();
      stack.push(i3);
      stack.push(i2);
      stack.push(i1);
      System.out.println("InterpreterStack: setup for dup_x2");
      System.out.println(stack);
     

      System.out.println("InterpreterStack: dup_x2");
      stack.dup_x2();
      System.out.println(stack);

      stack.pop2();
      stack.pop2();
      stack.push(i3);
      stack.push(i2);
      stack.push(i1);
      System.out.println("InterpreterStack: setup");
      System.out.println(stack);
     
      System.out.println("InterpreterStack: dup2");
      stack.dup2();
      System.out.println(stack);

      stack.pop2();
      stack.pop2();
      stack.popInt();
      stack.push(i3);
      stack.push(i2);
      stack.push(i1);
      System.out.println("InterpreterStack: setup");
      System.out.println(stack);
     
      System.out.println("InterpreterStack: dup2_x1");
      stack.dup2_x1();
      System.out.println(stack);

      stack.pop2();
      stack.pop2();
      stack.popInt();
      stack.push(4);
      stack.push(i3);
      stack.push(i2);
      stack.push(i1);
      System.out.println("InterpreterStack: setup");
      System.out.println(stack);
     
      System.out.println("InterpreterStack: dup2_x2");
      stack.dup2_x2();
      System.out.println(stack);
      
      System.out.println("InterpreterStack: large stack test");
      stack.dup2_x2();
      stack.dup2_x2();
      stack.dup2_x2();
      stack.dup2_x2();
      stack.dup2_x2();
      stack.dup2_x2();
      stack.dup2_x2();
      stack.dup2_x2();
      System.out.println(stack);

      stack.pop2();
      stack.pop2();
      stack.pop2();
      stack.pop2();
      stack.pop2();
      stack.pop2();
      stack.pop2();
      stack.pop2();
      stack.pop2();
      stack.pop2();
      stack.pop2();
      
      stack.push(i3);
      stack.push(i2);
      stack.push(i1);
      System.out.println("InterpreterStack: setup");
      System.out.println(stack);

      VM_Atom typeDescriptor = VM_Atom.findOrCreateAsciiAtom("Ljava/lang/Object;");
      VM_Type type           = VM_ClassLoader.findOrCreateType(typeDescriptor,VM_SystemClassLoader.getVMClassLoader());
      VM.sysWrite("inspecting " + type + "\n");
      
      if (type.isClassType() == false)
         return;
      
      VM_Class cls = type.asClass();
      
      VM_Method methods[] = cls.getStaticMethods();
      VM.sysWrite("\n"+methods.length+" static methods:\n\n");
      for (int im = 0, n = methods.length; im < n; ++im)
         {
         VM_Method method = methods[im];
         VM.sysWrite(method + "\n");
         }

      stack.push(new ByteCodeContext(methods[0]), 0);

      System.out.println("InterpreterStack: pushByteCodeContext");
      System.out.println(stack);

      stack.push(new ByteCodeContext(methods[0]), 0);

      System.out.println("InterpreterStack: pushByteCodeContext");
      System.out.println(stack);

      System.out.println("InterpreterStack: testing local variables-------------------------------");

      stack.setLocal(0,itest);
      System.out.println(stack);      
      System.out.println("InterpreterStack: getLocalInt="+stack.getLocalInt(0));

      stack.setLocal(0,f);
      System.out.println(stack);      
      System.out.println("InterpreterStack: getLocalFloat="+stack.getLocalFloat(0));

      stack.setLocal(0,d);
      System.out.println(stack);      
      System.out.println("InterpreterStack: getLocalDouble="+stack.getLocalDouble(0));

      stack.setLocal(0,l);
      System.out.println(stack);      
      System.out.println("InterpreterStack: getLocalLong="+stack.getLocalLong(0));

      stack.popByteCodeContext();
      System.out.println("InterpreterStack: popByteCodeContext");
      System.out.println(stack);
      }


   }
class  InterpreterStackException
   extends Exception
   {
    InterpreterStackException(String m)
      {
      super(m);
      }
   }
