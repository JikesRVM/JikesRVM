/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package java.lang.reflect;

import org.jikesrvm.objectmodel.VM_ObjectModel;
import org.jikesrvm.runtime.VM_Runtime;
import org.jikesrvm.classloader.*;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Inline;

/**
 * Library support interface of Jikes RVM
 */
public class JikesRVMSupport {

  /**
   * Convert from "vm" type system to "jdk" type system.
   */
  static Class<?>[] typesToClasses(VM_TypeReference[] types) {
    Class<?>[] classes = new Class[types.length];
    for (int i = 0; i < types.length; i++) {
      classes[i] = types[i].resolve().getClassForType();
    }
    return classes;
  }

  /**
   * Make possibly wrapped method argument compatible with expected type
   */
  @SuppressWarnings({"UnnecessaryBoxing","PMD.IntegerInstantiation"})
  @NoInline
  @Pure
  static Object makeArgumentCompatible(VM_Type expectedType, Object arg) {
    if (expectedType.isPrimitiveType()) {
      if (arg instanceof java.lang.Byte) {
        if (expectedType.isByteType()) return arg;
        if (expectedType.isShortType()) return Short.valueOf((Byte) arg);
        if (expectedType.isIntType()) return Integer.valueOf((Byte) arg);
        if (expectedType.isLongType()) return Long.valueOf((Byte) arg);
      } else if (arg instanceof java.lang.Short) {
        if (expectedType.isShortType()) return arg;
        if (expectedType.isIntType()) return Integer.valueOf((Short) arg);
        if (expectedType.isLongType()) return Long.valueOf((Short) arg);
      } else if (arg instanceof java.lang.Character) {
        if (expectedType.isCharType()) return arg;
        if (expectedType.isIntType()) return Integer.valueOf((Character) arg);
        if (expectedType.isLongType()) return Long.valueOf((Character) arg);
      } else if (arg instanceof java.lang.Integer) {
        if (expectedType.isIntType()) return arg;
        if (expectedType.isLongType()) return Long.valueOf((Integer) arg);
      } else if (arg instanceof java.lang.Float) {
        if (expectedType.isDoubleType()) return Double.valueOf((Float) arg);
      }
    }
    return arg;
  }

  /**
   * Are the 2 arguments compatible? Throw IllegalArgumentException if they
   * can't be made compatible.
   */
  @Pure
  @Inline
  static boolean isArgumentCompatible(VM_Type expectedType, Object arg) {
    if (expectedType.isPrimitiveType()) {
      return isPrimitiveArgumentCompatible(expectedType, arg);
    } else {
      if (arg == null) return true; // null is always ok
      VM_Type actualType = VM_ObjectModel.getObjectType(arg);
      if (expectedType == actualType ||
          expectedType == VM_Type.JavaLangObjectType ||
          VM_Runtime.isAssignableWith(expectedType, actualType)) {
        return true;
      } else {
        throwNewIllegalArgumentException();
        return false;
      }
    }
  }

  @NoInline
  private static void throwNewIllegalArgumentException() {
    throw new IllegalArgumentException();
  }
  @Pure
  @Inline
  private static boolean isPrimitiveArgumentCompatible(VM_Type expectedType, Object arg) {
    if (arg instanceof java.lang.Void) {
      if (expectedType.isVoidType()) return true;
    } else if (arg instanceof java.lang.Boolean) {
      if (expectedType.isBooleanType()) return true;
    } else if (arg instanceof java.lang.Byte) {
      if (expectedType.isByteType()) return true;
      if (expectedType.isShortType()) return false;
      if (expectedType.isIntType()) return false;
      if (expectedType.isLongType()) return false;
    } else if (arg instanceof java.lang.Short) {
      if (expectedType.isShortType()) return true;
      if (expectedType.isIntType()) return false;
      if (expectedType.isLongType()) return false;
    } else if (arg instanceof java.lang.Character) {
      if (expectedType.isCharType()) return true;
      if (expectedType.isIntType()) return false;
      if (expectedType.isLongType()) return false;
    } else if (arg instanceof java.lang.Integer) {
      if (expectedType.isIntType()) return true;
      if (expectedType.isLongType()) return false;
    } else if (arg instanceof java.lang.Long) {
      if (expectedType.isLongType()) return true;
    } else if (arg instanceof java.lang.Float) {
      if (expectedType.isFloatType()) return true;
      if (expectedType.isDoubleType()) return false;
    } else if (arg instanceof java.lang.Double) {
      if (expectedType.isDoubleType()) return true;
    }
    throwNewIllegalArgumentException();
    return false;
  }

  public static Field createField(VM_Field f) {
    return new Field(new VMField(f));
  }

  public static Method createMethod(VM_Method m) {
    return new Method(new VMMethod(m));
  }

  @SuppressWarnings("unchecked") // Can't type-check this without <T> type<T>, which breaks javac
  public static <T> Constructor<T> createConstructor(VM_Method m) {
    return new Constructor(new VMConstructor(m));
  }

  public static VMConstructor createVMConstructor(VM_Method m) {
    return new VMConstructor(m);
  }

  public static VM_Field getFieldOf(Field f) {
    return f.f.field;
  }

  public static VM_Method getMethodOf(Method m) {
    return m.m.method;
  }

  public static VM_Method getMethodOf(Constructor cons) {
    return cons.cons.constructor;
  }

  public static VM_Field getFieldOf(VMField f) {
    return f.field;
  }

  public static VM_Method getMethodOf(VMMethod m) {
    return m.method;
  }

  public static VM_Method getMethodOf(VMConstructor cons) {
    return cons.constructor;
  }


  /**
   * Check to see if a method declared by the accessingClass
   * should be allowed to access the argument VM_Member.
   * Assumption: member is not public.  This trivial case should
   * be approved by the caller without needing to call this method.
   */
  public static void checkAccess(VM_Member member, VM_Class accessingClass) throws IllegalAccessException {
    VM_Class declaringClass = member.getDeclaringClass();
    if (member.isPrivate()) {
      // access from the declaringClass is allowed
      if (accessingClass == declaringClass) return;
    } else if (member.isProtected()) {
      // access within the package is allowed.
      if (declaringClass.getClassLoader() == accessingClass.getClassLoader() && declaringClass.getPackageName().equals(accessingClass.getPackageName())) return;

      // access by subclasses is allowed.
      for (VM_Class cls = accessingClass; cls != null; cls = cls.getSuperClass()) {
        if (accessingClass == declaringClass) return;
      }
    } else {
      // default: access within package is allowed
      if (declaringClass.getClassLoader() == accessingClass.getClassLoader() && declaringClass.getPackageName().equals(accessingClass.getPackageName())) return;
    }

    throw new IllegalAccessException("Access to "+member+" is denied to "+accessingClass);
  }

}
