/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 * Arch-independent portion of reflective method invoker.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 * @date 15 Jul 1998
 */
public class VM_Reflection implements VM_Constants {

  //-----------//
  // interface //
  //-----------//
   
  // Call a method.
  // Taken:    method to be called
  //           "this" argument (ignored if method is static)
  //           remaining arguments
  //           isNonvirtual flag is false if the method of the real class of this object is
  //                        to be invoked; true if a method of a superclass may be invoked
  // Returned: return value (wrapped if primitive)
  // See also: java/lang/reflect/Method.invoke()
  //
  public static Object invoke(VM_Method method, Object thisArg, Object[] otherArgs) {
    return invoke(method, thisArg, otherArgs, false);
  }

  public static Object invoke(VM_Method method, Object thisArg, Object[] otherArgs, boolean isNonvirtual) {
    // choose actual method to be called
    //
    VM_Method targetMethod;
    if (method.isStatic() || method.isObjectInitializer() || isNonvirtual) {
      targetMethod = method;
    } else {
      int tibIndex = method.getOffset() >>> 2;
      targetMethod = VM_Magic.getObjectType(thisArg).asClass().getVirtualMethods()[tibIndex - TIB_FIRST_VIRTUAL_METHOD_INDEX];
    }
        
    // make sure it's been compiled
    //
    if (!targetMethod.isCompiled()) {
      synchronized (VM_ClassLoader.lock) {
	targetMethod.compile();
      }
    }
    INSTRUCTION[] code = targetMethod.getMostRecentlyGeneratedInstructions();
        
    // remember return type
    // Determine primitive type-ness early to avoid call (possible yield) 
    // later while refs are possibly being held in int arrays.
    //
    VM_Type returnType = method.getReturnType();
    boolean returnIsPrimitive = returnType.isPrimitiveType();  
     
    // decide how to pass parameters
    //
    int triple     = VM_MachineReflection.countParameters(method);
    int gprs       = triple & REFLECTION_GPRS_MASK;
    int[]    GPRs  = new int[gprs];
    int fprs       = (triple >> REFLECTION_GPRS_BITS) & 0x1F;
    double[] FPRs  = new double[fprs];

    int spills     = triple >> (REFLECTION_GPRS_BITS+REFLECTION_FPRS_BITS);
    int spillCount = spills;
     
    int[]   Spills = new int[spillCount];

    if (firstUse) { 
      // force dynamic link sites in unwrappers to get resolved, before disabling gc.
      // this is a bit silly, but I can't think of another way to do it [--DL]
      unwrapBoolean(wrapBoolean(0));
      unwrapByte(wrapByte((byte)0));
      unwrapChar(wrapChar((char)0));
      unwrapShort(wrapShort((short)0));
      unwrapInt(wrapInt((int)0));
      unwrapLong(wrapLong((long)0));
      unwrapFloat(wrapFloat((float)0));
      unwrapDouble(wrapDouble((double)0));
      firstUse = false;
    }

    // now for real...
    VM.disableGC();
    VM_MachineReflection.packageParameters(method, thisArg, otherArgs, GPRs, FPRs, Spills);
    VM.enableGC();
     
    if (!returnIsPrimitive) {
      return VM_Magic.invokeMethodReturningObject(code, GPRs, FPRs, Spills);
    }

    if (returnType == VM_Type.VoidType) {
      VM_Magic.invokeMethodReturningVoid(code, GPRs, FPRs, Spills);
      return null;
    }

    if (returnType == VM_Type.BooleanType) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, Spills);
      return new Boolean(x == 1);
    }

    if (returnType == VM_Type.ByteType) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, Spills);
      return new Byte((byte)x);
    }

    if (returnType == VM_Type.ShortType) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, Spills);
      return new Short((short)x);
    }

    if (returnType == VM_Type.CharType) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, Spills);
      return new Character((char)x);
    }

    if (returnType == VM_Type.IntType) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, Spills);
      return new Integer(x);
    }

    if (returnType == VM_Type.LongType) {
      long x = VM_Magic.invokeMethodReturningLong(code, GPRs, FPRs, Spills);
      return new Long(x);
    }

    if (returnType == VM_Type.FloatType) {
      float x = VM_Magic.invokeMethodReturningFloat(code, GPRs, FPRs, Spills);
      return new Float(x);
    }
        
    if (returnType == VM_Type.DoubleType) {
      double x = VM_Magic.invokeMethodReturningDouble(code, GPRs, FPRs, Spills);
      return new Double(x);
    }

    if (VM.VerifyAssertions) VM.assert(NOT_REACHED);
    return null;
  }

  // Method parameter wrappers.
  // 
  public static Object wrapBoolean(int b)     { VM_Magic.pragmaNoInline(); return new Boolean(b==1); }
  public static Object wrapByte(byte b)       { VM_Magic.pragmaNoInline(); return new Byte(b);       }
  public static Object wrapChar(char c)       { VM_Magic.pragmaNoInline(); return new Character(c);  }
  public static Object wrapShort(short s)     { VM_Magic.pragmaNoInline(); return new Short(s);      }
  public static Object wrapInt(int i)         { VM_Magic.pragmaNoInline(); return new Integer(i);    }
  public static Object wrapLong(long l)       { VM_Magic.pragmaNoInline(); return new Long(l);       }
  public static Object wrapFloat(float f)     { VM_Magic.pragmaNoInline(); return new Float(f);      }
  public static Object wrapDouble(double d)   { VM_Magic.pragmaNoInline(); return new Double(d);     }
   
  // Method parameter unwrappers.
  //
  public static int unwrapBooleanAsInt(Object o){ VM_Magic.pragmaNoInline(); if (unwrapBoolean(o)) return 1; else return 0; }
  public static boolean unwrapBoolean(Object o){VM_Magic.pragmaNoInline(); return ((Boolean)   o).booleanValue(); }
  public static byte   unwrapByte(Object o)   { VM_Magic.pragmaNoInline(); return ((Byte)      o).byteValue();    }
  public static char   unwrapChar(Object o)   { VM_Magic.pragmaNoInline(); return ((Character) o).charValue();    }
  public static short  unwrapShort(Object o)  { VM_Magic.pragmaNoInline(); return ((Short)     o).shortValue();   }
  public static int    unwrapInt(Object o)    { VM_Magic.pragmaNoInline(); return ((Integer)   o).intValue();     }
  public static long   unwrapLong(Object o)   { VM_Magic.pragmaNoInline(); return ((Long)      o).longValue();    }
  public static float  unwrapFloat(Object o)  { VM_Magic.pragmaNoInline(); return ((Float)     o).floatValue();   }
  public static double unwrapDouble(Object o) { VM_Magic.pragmaNoInline(); return ((Double)    o).doubleValue();  }
  public static int    unwrapObject(Object o) { VM_Magic.pragmaNoInline(); return VM_Magic.objectAsAddress(o);    }

  //----------------//
  // implementation //
  //----------------//
   
  private static boolean firstUse = true;
   
}
