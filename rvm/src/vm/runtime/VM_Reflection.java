/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
import org.vmmagic.pragma.*;
import org.vmmagic.unboxed.*;

/**
 * Arch-independent portion of reflective method invoker.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 * @date 15 Jul 1998
 */
public class VM_Reflection implements VM_Constants {

  /**
   * Call a method.
   * @param method method to be called
   * @param thisArg "this" argument (ignored if method is static)
   * @param otherArgs remaining arguments
   * 
   * isNonvirtual flag is false if the method of the real class of this 
   * object is to be invoked; true if a method of a superclass may be invoked
   * @return return value (wrapped if primitive)
   * See also: java/lang/reflect/Method.invoke()
   */ 
  public static Object invoke(VM_Method method, Object thisArg, Object[] otherArgs) {
    return invoke(method, thisArg, otherArgs, false);
  }

  public static Object invoke(VM_Method method, Object thisArg, 
                              Object[] otherArgs, boolean isNonvirtual) {

    // the class must be initialized before we can invoke a method
    //
    VM_Class klass = method.getDeclaringClass();
    if (!klass.isInitialized()) {
      VM_Runtime.initializeClassForDynamicLink(klass);
    }

    // remember return type
    // Determine primitive type-ness early to avoid call (possible yield) 
    // later while refs are possibly being held in int arrays.
    //
    VM_TypeReference returnType = method.getReturnType();
    boolean returnIsPrimitive = returnType.isPrimitiveType();  
     
    // decide how to pass parameters
    //
    int triple     = VM_MachineReflection.countParameters(method);
    int gprs       = triple & REFLECTION_GPRS_MASK;
    WordArray GPRs = WordArray.create(gprs);
    int fprs       = (triple >> REFLECTION_GPRS_BITS) & 0x1F;
    double[] FPRs  = new double[fprs];

    int spills     = triple >> (REFLECTION_GPRS_BITS+REFLECTION_FPRS_BITS);
    int spillCount = spills;
     
    WordArray Spills = WordArray.create(spillCount);

    if (firstUse) { 
      // force dynamic link sites in unwrappers to get resolved, 
      // before disabling gc.
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

    // choose actual method to be called
    //
    VM_Method targetMethod;
    if (method.isStatic() || method.isObjectInitializer() || isNonvirtual) {
      targetMethod = method;
    } else {
      int tibIndex = method.getOffsetAsInt() >>> LOG_BYTES_IN_ADDRESS;
      targetMethod = VM_Magic.getObjectType(thisArg).asClass().getVirtualMethods()[tibIndex - TIB_FIRST_VIRTUAL_METHOD_INDEX];
    }

    // There's a nasty race condition here that we just hope doesn't happen.
    // The issue is that we can't get the CompiledMethod from targetMethod while
    // GC is disabled because all accesses to it must be synchronized.  But,
    // it's theoretically possible that we take the epilogue yieldpoint in getCurrentCompiledMethod
    // and lose control before we can disable threadswitching and thus invoke an obsolete 
    // and already GC'ed instruction array.
    targetMethod.compile();
    VM_CompiledMethod cm = targetMethod.getCurrentCompiledMethod();
    while (cm == null) {
      targetMethod.compile();
      cm = targetMethod.getCurrentCompiledMethod();
    }
    
    VM_Processor.getCurrentProcessor().disableThreadSwitching();

    VM_CodeArray code = cm.getInstructions();
    VM_MachineReflection.packageParameters(method, thisArg, otherArgs, GPRs, 
                                           FPRs, Spills);
    
    // critical: no threadswitch/GCpoints between here and the invoke of code!
    //           We may have references hidden in the GPRs and Spills arrays!!!
    VM_Processor.getCurrentProcessor().enableThreadSwitching();

    if (!returnIsPrimitive) {
      return VM_Magic.invokeMethodReturningObject(code, GPRs, FPRs, Spills);
    }

    if (returnType.isVoidType()) {
      VM_Magic.invokeMethodReturningVoid(code, GPRs, FPRs, Spills);
      return null;
    }

    if (returnType.isBooleanType()) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, Spills);
      return new Boolean(x == 1);
    }

    if (returnType.isByteType()) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, Spills);
      return new Byte((byte)x);
    }

    if (returnType.isShortType()) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, Spills);
      return new Short((short)x);
    }

    if (returnType.isCharType()) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, Spills);
      return new Character((char)x);
    }

    if (returnType.isIntType()) {
      int x = VM_Magic.invokeMethodReturningInt(code, GPRs, FPRs, Spills);
      return new Integer(x);
    }

    if (returnType.isLongType()) {
      long x = VM_Magic.invokeMethodReturningLong(code, GPRs, FPRs, Spills);
      return new Long(x);
    }

    if (returnType.isFloatType()) {
      float x = VM_Magic.invokeMethodReturningFloat(code, GPRs, FPRs, Spills);
      return new Float(x);
    }
        
    if (returnType.isDoubleType()) {
      double x = VM_Magic.invokeMethodReturningDouble(code, GPRs, FPRs, Spills);
      return new Double(x);
    }

    if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
    return null;
  }

  // Method parameter wrappers.
  // 
  public static Object wrapBoolean(int b) throws NoInlinePragma     { return new Boolean(b==1); }
  public static Object wrapByte(byte b) throws NoInlinePragma       { return new Byte(b);       }
  public static Object wrapChar(char c) throws NoInlinePragma       { return new Character(c);  }
  public static Object wrapShort(short s) throws NoInlinePragma     { return new Short(s);      }
  public static Object wrapInt(int i) throws NoInlinePragma         { return new Integer(i);    }
  public static Object wrapLong(long l) throws NoInlinePragma       { return new Long(l);       }
  public static Object wrapFloat(float f) throws NoInlinePragma     { return new Float(f);      }
  public static Object wrapDouble(double d) throws NoInlinePragma   { return new Double(d);     }
   
  // Method parameter unwrappers.
  //
  public static int unwrapBooleanAsInt(Object o) throws NoInlinePragma  { if (unwrapBoolean(o)) return 1; else return 0; }
  public static boolean unwrapBoolean(Object o) throws NoInlinePragma   { return ((Boolean)   o).booleanValue(); }
  public static byte   unwrapByte(Object o) throws NoInlinePragma       { return ((Byte)      o).byteValue();    }
  public static char   unwrapChar(Object o) throws NoInlinePragma       { return ((Character) o).charValue();    }
  public static short  unwrapShort(Object o) throws NoInlinePragma      { return ((Short)     o).shortValue();   }
  public static int    unwrapInt(Object o) throws NoInlinePragma        { return ((Integer)   o).intValue();     }
  public static long   unwrapLong(Object o) throws NoInlinePragma       { return ((Long)      o).longValue();    }
  public static float  unwrapFloat(Object o) throws NoInlinePragma      { return ((Float)     o).floatValue();   }
  public static double unwrapDouble(Object o) throws NoInlinePragma     { return ((Double)    o).doubleValue();  }
  public static Address unwrapObject(Object o) throws NoInlinePragma { return VM_Magic.objectAsAddress(o);    }

  private static boolean firstUse = true;
}
