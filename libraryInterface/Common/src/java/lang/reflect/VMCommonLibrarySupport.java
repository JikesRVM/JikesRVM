/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package java.lang.reflect;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMArray;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMember;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.objectmodel.ObjectModel;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.runtime.ReflectionBase;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Pure;

/**
 * Common utilities for Jikes RVM implementations of the java.lang.reflect API
 */
final class VMCommonLibrarySupport {
  /* ---- Non-inlined Exception Throwing Methods --- */
  /**
   * Method just to throw an illegal argument exception without being inlined
   */
  @NoInline
  private static void throwNewIllegalArgumentException() {
    throw new IllegalArgumentException();
  }

  /**
   * Method just to throw an illegal argument exception without being inlined
   */
  @NoInline
  private static void throwNewIllegalArgumentException(String str) {
    throw new IllegalArgumentException(str);
  }
  /**
   * Method just to throw an illegal access exception without being inlined
   */
  @NoInline
  private static void throwNewIllegalAccessException() throws IllegalAccessException{
    throw new IllegalAccessException();
  }
  /**
   * Method just to throw an illegal access exception without being inlined
   */
  @NoInline
  private static void throwNewIllegalAccessException(RVMMember member, RVMClass accessingClass) throws IllegalAccessException{
    throw new IllegalAccessException("Access to " + member + " is denied to " + accessingClass);
  }
  /**
   * Method just to throw an instantiation exception without being inlined
   */
  @NoInline
  private static void throwNewInstantiationException(String str) throws InstantiationException{
    throw new InstantiationException(str);
  }
  /**
   * Method just to throw a negative array size exception without being inlined
   */
  @NoInline
  private static void throwNewNegativeArraySizeException() {
    throw new NegativeArraySizeException();
  }
  /**
   * Method just to throw an null pointer exception without being inlined
   */
  @NoInline
  private static void throwNewNullPointerException() {
    throw new NullPointerException();
  }
  /* ---- Array Methods ---- */
  /**
   * Dynamically create an array of objects.
   *
   * @param cls guaranteed to be a valid object type
   * @param length the length of the array
   * @return the new array
   * @throws NegativeArraySizeException if dim is negative
   * @throws OutOfMemoryError if memory allocation fails
   */
  static Object createArray(Class<?> cls, int length)
    throws OutOfMemoryError, NegativeArraySizeException {

    if (cls == Void.TYPE)
      throwNewIllegalArgumentException("Cannot create new array instance for the specified arguments");

    // will raise NPE
    RVMArray arrayType = java.lang.JikesRVMSupport.getTypeForClass(cls).getArrayTypeForElementType();
    if (!arrayType.isInitialized()) {
      arrayType.resolve();
      arrayType.instantiate();
      arrayType.initialize();
    }
    // will check -ve array size
    return RuntimeEntrypoints.resolvedNewArray(length, arrayType);
  }

  /**
   * Dynamically create a multi-dimensional array of objects.
   *
   * @param cls guaranteed to be a valid object type
   * @param dimensions the lengths for the dimensions of the array
   * @return the new array
   * @throws NegativeArraySizeException if any dimensions is negative
   * @throws OutOfMemoryError if memory allocation fails
   */
  static Object createArray(Class<?> cls, int[] dimensions)
    throws OutOfMemoryError, NegativeArraySizeException {

    if ((dimensions.length == 0)||(cls == Void.TYPE));
      throwNewIllegalArgumentException("Cannot create new array instance for the specified arguments");

    // will raise NPE
    RVMArray arrayType = java.lang.JikesRVMSupport.getTypeForClass(cls).getArrayTypeForElementType();
    for (int i=1; i < dimensions.length; i++) {
      arrayType = arrayType.getArrayTypeForElementType();
    }
    // will check -ve array sizes
    return RuntimeEntrypoints.buildMDAHelper(null, dimensions, 0, arrayType);
  }

  /* ---- General Reflection Support ---- */
  /**
   * Check to see if a method declared by the accessingClass
   * should be allowed to access the argument RVMMember.
   * Assumption: member is not public.  This trivial case should
   * be approved by the caller without needing to call this method.
   */
  private static void checkAccess(RVMMember member, RVMClass accessingClass) throws IllegalAccessException {
    RVMClass declaringClass = member.getDeclaringClass();
    if (member.isPrivate()) {
      // access from the declaringClass is allowed
      if (accessingClass == declaringClass) return;
    } else if (member.isProtected()) {
      // access within the package is allowed.
      if (declaringClass.getClassLoader() == accessingClass.getClassLoader() && declaringClass.getPackageName().equals(accessingClass.getPackageName())) return;

      // access by subclasses is allowed.
      for (RVMClass cls = accessingClass; cls != null; cls = cls.getSuperClass()) {
        if (accessingClass == declaringClass) return;
      }
    } else {
      // default: access within package is allowed
      if (declaringClass.getClassLoader() == accessingClass.getClassLoader() && declaringClass.getPackageName().equals(accessingClass.getPackageName())) return;
    }
    throwNewIllegalAccessException(member, accessingClass);
  }
  /* ---- Reflective Method Invocation Support ---- */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  static Object invoke(Object receiver, Object[] args, RVMMethod method, Method jlrMethod, RVMClass accessingClass, ReflectionBase invoker)
      throws IllegalAccessException, IllegalArgumentException,
      ExceptionInInitializerError, InvocationTargetException {
    // validate number and types of arguments
    if (!Reflection.needsCheckArgs(invoker) || checkArguments(args, method)) {
      if (method.isStatic()) {
        return invokeStatic(receiver, args, method, jlrMethod, accessingClass, invoker);
      } else {
        return invokeVirtual(receiver, args, method, jlrMethod, accessingClass, invoker);
      }
    } else {
      Object[] compatibleArgs = makeArgumentsCompatible(args, method);
      if (method.isStatic()) {
        return invokeStatic(receiver, compatibleArgs, method, jlrMethod, accessingClass, invoker);
      } else {
        return invokeVirtual(receiver, compatibleArgs, method, jlrMethod, accessingClass, invoker);
      }
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  private static Object invokeStatic(Object receiver, Object[] args, RVMMethod method, Method jlrMethod, RVMClass accessingClass, ReflectionBase invoker)
      throws IllegalAccessException, IllegalArgumentException,
      ExceptionInInitializerError, InvocationTargetException {
    // Accessibility checks
    if (!method.isPublic() && !jlrMethod.isAccessible()) {
      checkAccess(method, accessingClass);
    }

    // Forces initialization of declaring class
    RVMClass declaringClass = method.getDeclaringClass();
    if (!declaringClass.isInitialized()) {
      runClassInitializer(declaringClass);
    }

    // Invoke method
    try {
      return Reflection.invoke(method, invoker, receiver, args, true);
    } catch (Throwable t) {
      throw new InvocationTargetException(t);
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  private static Object invokeVirtual(Object receiver, Object[] args, RVMMethod method, Method jlrMethod, RVMClass accessingClass, ReflectionBase invoker)
      throws IllegalAccessException, IllegalArgumentException,
      ExceptionInInitializerError, InvocationTargetException {
    // validate "this" argument
    if (receiver == null) {
      throwNewNullPointerException();
    }
    RVMClass declaringClass = method.getDeclaringClass();
    if (!isArgumentCompatible(declaringClass, receiver)) {
      throwNewIllegalArgumentException();
    }

    // Accessibility checks
    if (!method.isPublic() && !jlrMethod.isAccessible()) {
      checkAccess(method, accessingClass);
    }

    // find the right method to call
    RVMClass C = Magic.getObjectType(receiver).asClass();
    method = C.findVirtualMethod(method.getName(), method.getDescriptor());

    // Invoke method
    try {
      return Reflection.invoke(method, invoker, receiver, args, false);
    } catch (Throwable t) {
      throw new InvocationTargetException(t);
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  private static boolean checkArguments(Object[] args, RVMMethod method) throws IllegalArgumentException {
    TypeReference[] parameterTypes = method.getParameterTypes();
    if (((args == null) && (parameterTypes.length != 0)) ||
        ((args != null) && (args.length != parameterTypes.length))) {
      throwNewIllegalArgumentException("argument count mismatch");
    }
    switch (parameterTypes.length) {
    case 6:
      if (!isArgumentCompatible(parameterTypes[5].resolve(), args[5])) {
        return false;
      }
    case 5:
      if (!isArgumentCompatible(parameterTypes[4].resolve(), args[4])) {
        return false;
      }
    case 4:
      if (!isArgumentCompatible(parameterTypes[3].resolve(), args[3])) {
        return false;
      }
    case 3:
      if (!isArgumentCompatible(parameterTypes[2].resolve(), args[2])) {
        return false;
      }
    case 2:
      if (!isArgumentCompatible(parameterTypes[1].resolve(), args[1])) {
        return false;
      }
    case 1:
      if (!isArgumentCompatible(parameterTypes[0].resolve(), args[0])) {
        return false;
      }
    case 0:
      return true;
    default:
      for (int i=0, n = parameterTypes.length; i < n; ++i) {
        if (!isArgumentCompatible(parameterTypes[i].resolve(), args[i])) {
          return false;
        }
      }
      return true;
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  private static Object[] makeArgumentsCompatible(Object[] args, RVMMethod method) {
    TypeReference[] parameterTypes = method.getParameterTypes();
    int length = parameterTypes.length;
    Object[] newArgs = new Object[length];
    switch(length) {
    case 6: newArgs[5] = makeArgumentCompatible(parameterTypes[5].peekType(), args[5]);
    case 5: newArgs[4] = makeArgumentCompatible(parameterTypes[4].peekType(), args[4]);
    case 4: newArgs[3] = makeArgumentCompatible(parameterTypes[3].peekType(), args[3]);
    case 3: newArgs[2] = makeArgumentCompatible(parameterTypes[2].peekType(), args[2]);
    case 2: newArgs[1] = makeArgumentCompatible(parameterTypes[1].peekType(), args[1]);
    case 1: newArgs[0] = makeArgumentCompatible(parameterTypes[0].peekType(), args[0]);
    case 0: break;
    default: {
      for (int i = 0; i < length; ++i) {
        newArgs[i] = makeArgumentCompatible(parameterTypes[i].peekType(), args[i]);
      }
      break;
    }
    }
    return newArgs;
  }

  @NoInline
  private static void runClassInitializer(RVMClass declaringClass) throws ExceptionInInitializerError {
    try {
      RuntimeEntrypoints.initializeClassForDynamicLink(declaringClass);
    } catch (Throwable e) {
      ExceptionInInitializerError ex = new ExceptionInInitializerError();
      ex.initCause(e);
      throw ex;
    }
  }

  /**
   * Make possibly wrapped method argument compatible with expected type
   */
  @SuppressWarnings({"UnnecessaryBoxing","PMD.IntegerInstantiation"})
  @NoInline
  @Pure
  private static Object makeArgumentCompatible(RVMType expectedType, Object arg) {
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
  private static boolean isArgumentCompatible(RVMType expectedType, Object arg) {
    if (expectedType.isPrimitiveType()) {
      return isPrimitiveArgumentCompatible(expectedType, arg);
    } else {
      if (arg == null) return true; // null is always ok
      RVMType actualType = ObjectModel.getObjectType(arg);
      if (expectedType == actualType ||
          expectedType == RVMType.JavaLangObjectType ||
          RuntimeEntrypoints.isAssignableWith(expectedType, actualType)) {
        return true;
      } else {
        throwNewIllegalArgumentException();
        return false;
      }
    }
  }

  /**
   * Is a boxed primitive argument compatible with the expected type
   */
  @Pure
  @Inline
  private static boolean isPrimitiveArgumentCompatible(RVMType expectedType, Object arg) {
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

  /* ---- Constructor Support ---- */
  /**
   * Construct an object from the given constructor args, called from the accessing class
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={0})
  static Object construct(RVMMethod constructor, Constructor<?> cons, Object[] args, RVMClass accessingClass, ReflectionBase invoker)
  throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    // Check accessibility
    if (!constructor.isPublic() && !cons.isAccessible()) {
      checkAccess(constructor, accessingClass);
    }
    // validate number and types of arguments to constructor
    if (Reflection.needsCheckArgs(invoker) && !checkArguments(args, constructor)) {
      args = makeArgumentsCompatible(args, constructor);
    }
    RVMClass cls = constructor.getDeclaringClass();
    if (cls.isAbstract()) {
      throwNewInstantiationException("Abstract class");
    }
    // Ensure that the class is initialized
    if (!cls.isInitialized()) {
      runClassInitializer(cls);
    }
    // Allocate an uninitialized instance;
    Object obj = RuntimeEntrypoints.resolvedNewScalar(cls);
    // Run the constructor on the instance.
    try {
      Reflection.invoke(constructor, invoker, obj, args, true);
    } catch (Throwable e) {
      throw new InvocationTargetException(e);
    }
    return obj;
  }
  /* ---- Constructor/Method Support ---- */
  /**
   * Convert from "vm" type system to "jdk" type system.
   */
  static Class<?>[] typesToClasses(TypeReference[] types) {
    Class<?>[] classes = new Class[types.length];
    for (int i = 0; i < types.length; i++) {
      classes[i] = types[i].resolve().getClassForType();
    }
    return classes;
  }
  /* ---- Field Support ---- */
  /**
   * Check the field in the given object can be read
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  private static void checkReadAccess(Object obj, RVMField field, Field jlrField, RVMClass accessingClass) throws IllegalAccessException,
  IllegalArgumentException,
  ExceptionInInitializerError {
    RVMClass declaringClass = field.getDeclaringClass();
    if (!field.isStatic()) {
      if (obj == null) {
        throwNewNullPointerException();
      }

      RVMType objType = ObjectModel.getObjectType(obj);
      if (objType != declaringClass && !RuntimeEntrypoints.isAssignableWith(declaringClass, objType)) {
        throwNewIllegalArgumentException();
      }
    }

    if (!field.isPublic() && !jlrField.isAccessible()) {
      checkAccess(field, accessingClass);
    }

    if (field.isStatic() && !declaringClass.isInitialized()) {
      runClassInitializer(declaringClass);
    }
  }

  /**
   * Check the field in the given object can be written to
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  private static void checkWriteAccess(Object obj, RVMField field, Field jlrField, RVMClass accessingClass) throws IllegalAccessException,
  IllegalArgumentException,
  ExceptionInInitializerError {

    RVMClass declaringClass = field.getDeclaringClass();
    if (!field.isStatic()) {
      if (obj == null) {
        throwNewNullPointerException();
      }

      RVMType objType = ObjectModel.getObjectType(obj);
      if (objType != declaringClass && !RuntimeEntrypoints.isAssignableWith(declaringClass, objType)) {
        throwNewIllegalArgumentException();
      }
    }

    if (!field.isPublic() && !jlrField.isAccessible()) {
      checkAccess(field, accessingClass);
    }

    if (field.isFinal() && (!jlrField.isAccessible() || field.isStatic()))
      throwNewIllegalAccessException();

    if (field.isStatic() && !declaringClass.isInitialized()) {
      runClassInitializer(declaringClass);
    }
  }

  /**
   * Read a field from an object
   * @param object to read from
   * @param field to be read
   * @param jlrField API version of field
   * @param accessingClass class performing access
   * @return the field's value
   * @throws IllegalAccessException
   * @throws IllegalArgumentException
   */
  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  static Object get(Object object, RVMField field, Field jlrField, RVMClass accessingClass) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object, field, jlrField, accessingClass);

    if (field.isReferenceType()) {
      return field.getObjectValueUnchecked(object);
    }
    TypeReference type = field.getType();
    if (type.isIntType()) {
      return field.getIntValueUnchecked(object);
    } else if (type.isCharType()) {
      return field.getCharValueUnchecked(object);
    } else if (type.isShortType()) {
      return field.getShortValueUnchecked(object);
    } else if (type.isLongType()) {
      return field.getLongValueUnchecked(object);
    } else if (type.isByteType()) {
      return field.getByteValueUnchecked(object);
    } else if (type.isBooleanType()) {
      return field.getBooleanValueUnchecked(object);
    } else if (type.isDoubleType()) {
      return field.getDoubleValueUnchecked(object);
    } else {
      if (VM.VerifyAssertions) VM._assert(type.isFloatType());
      return field.getFloatValueUnchecked(object);
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  static boolean getBoolean(Object object, RVMField field, Field jlrField, RVMClass accessingClass) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object, field, jlrField, accessingClass);
    TypeReference type = field.getType();
    if (!type.isBooleanType()) throwNewIllegalArgumentException("field type mismatch");
    return field.getBooleanValueUnchecked(object);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  static byte getByte(Object object, RVMField field, Field jlrField, RVMClass accessingClass) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object, field, jlrField, accessingClass);
    TypeReference type = field.getType();
    if (!type.isByteType()) throwNewIllegalArgumentException("field type mismatch");
    return field.getByteValueUnchecked(object);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  static char getChar(Object object, RVMField field, Field jlrField, RVMClass accessingClass) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object, field, jlrField, accessingClass);
    TypeReference type = field.getType();
    if (!type.isCharType()) throwNewIllegalArgumentException("field type mismatch");
    return field.getCharValueUnchecked(object);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  static double getDouble(Object object, RVMField field, Field jlrField, RVMClass accessingClass) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object, field, jlrField, accessingClass);
    TypeReference type = field.getType();
    if (type.isDoubleType()) {
      return field.getDoubleValueUnchecked(object);
    } else if (type.isFloatType()) {
      return field.getFloatValueUnchecked(object);
    } else if (type.isLongType()) {
      return field.getLongValueUnchecked(object);
    } else if (type.isIntType()) {
      return field.getIntValueUnchecked(object);
    } else if (type.isShortType()) {
      return field.getShortValueUnchecked(object);
    } else if (type.isCharType()) {
      return field.getCharValueUnchecked(object);
    } else if (type.isByteType()) {
      return field.getByteValueUnchecked(object);
    } else {
      throwNewIllegalArgumentException("field type mismatch");
      return 0.0d;
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  static float getFloat(Object object, RVMField field, Field jlrField, RVMClass accessingClass) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object, field, jlrField, accessingClass);
    TypeReference type = field.getType();
    if (type.isFloatType()) {
      return field.getFloatValueUnchecked(object);
    } else if (type.isLongType()) {
      return field.getLongValueUnchecked(object);
    } else if (type.isIntType()) {
      return field.getIntValueUnchecked(object);
    } else if (type.isShortType()) {
      return field.getShortValueUnchecked(object);
    } else if (type.isCharType()) {
      return field.getCharValueUnchecked(object);
    } else if (type.isByteType()) {
      return field.getByteValueUnchecked(object);
    } else {
      throwNewIllegalArgumentException("field type mismatch");
      return 0.0f;
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  static int getInt(Object object, RVMField field, Field jlrField, RVMClass accessingClass) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object, field, jlrField, accessingClass);
    TypeReference type = field.getType();
    if (type.isIntType()) {
      return field.getIntValueUnchecked(object);
    } else if (type.isShortType()) {
      return field.getShortValueUnchecked(object);
    } else if (type.isCharType()) {
      return field.getCharValueUnchecked(object);
    } else if (type.isByteType()) {
      return field.getByteValueUnchecked(object);
    } else {
      throwNewIllegalArgumentException("field type mismatch");
      return 0;
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  static long getLong(Object object, RVMField field, Field jlrField, RVMClass accessingClass) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object, field, jlrField, accessingClass);
    TypeReference type = field.getType();
    if (type.isLongType()) {
      return field.getLongValueUnchecked(object);
    } else if (type.isIntType()) {
      return field.getIntValueUnchecked(object);
    } else if (type.isShortType()) {
      return field.getShortValueUnchecked(object);
    } else if (type.isCharType()) {
      return field.getCharValueUnchecked(object);
    } else if (type.isByteType()) {
      return field.getByteValueUnchecked(object);
    } else {
      throwNewIllegalArgumentException("field type mismatch");
      return 0L;
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={1})
  static short getShort(Object object, RVMField field, Field jlrField, RVMClass accessingClass) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object, field, jlrField, accessingClass);
    TypeReference type = field.getType();
    if (type.isShortType()) {
      return field.getShortValueUnchecked(object);
    } else if (type.isByteType()) {
      return field.getByteValueUnchecked(object);
    } else {
      throwNewIllegalArgumentException("field type mismatch");
      return 0;
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  static void set(Object object, Object value, RVMField field, Field jlrField, RVMClass accessingClass)
  throws IllegalAccessException, IllegalArgumentException     {
    checkWriteAccess(object, field, jlrField, accessingClass);

    if (field.isReferenceType()) {
      if (value != null) {
        RVMType valueType = ObjectModel.getObjectType(value);
        RVMType fieldType = null;
        try {
          fieldType = field.getType().resolve();
        } catch (NoClassDefFoundError e) {
          throwNewIllegalArgumentException("field type mismatch");
        }
        if (fieldType != valueType &&
            !RuntimeEntrypoints.isAssignableWith(fieldType, valueType)) {
          throwNewIllegalArgumentException("field type mismatch");
        }
      }
      field.setObjectValueUnchecked(object, value);
    } else if (value instanceof Character) {
      setCharInternal(object, (Character) value, field);
    } else if (value instanceof Double) {
      setDoubleInternal(object, (Double) value, field);
    } else if (value instanceof Float) {
      setFloatInternal(object, (Float) value, field);
    } else if (value instanceof Long) {
      setLongInternal(object, (Long) value, field);
    } else if (value instanceof Integer) {
      setIntInternal(object, (Integer) value, field);
    } else if (value instanceof Short) {
      setShortInternal(object, (Short) value, field);
    } else if (value instanceof Byte) {
      setByteInternal(object, (Byte) value, field);
    } else if (value instanceof Boolean) {
      setBooleanInternal(object, (Boolean) value, field);
    } else {
      throw new IllegalArgumentException("field type mismatch");
    }
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  static void setBoolean(Object object, boolean value, RVMField field, Field jlrField, RVMClass accessingClass)
  throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object, field, jlrField, accessingClass);
    setBooleanInternal(object, value, field);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  static void setByte(Object object, byte value, RVMField field, Field jlrField, RVMClass accessingClass)
  throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object, field, jlrField, accessingClass);
    setByteInternal(object, value, field);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  static void setChar(Object object, char value, RVMField field, Field jlrField, RVMClass accessingClass)
  throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object, field, jlrField, accessingClass);
    setCharInternal(object, value, field);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  static void setDouble(Object object, double value, RVMField field, Field jlrField, RVMClass accessingClass)
  throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object, field, jlrField, accessingClass);
    setDoubleInternal(object, value, field);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  static void setFloat(Object object, float value, RVMField field, Field jlrField, RVMClass accessingClass)
  throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object, field, jlrField, accessingClass);
    setFloatInternal(object, value, field);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  static void setInt(Object object, int value, RVMField field, Field jlrField, RVMClass accessingClass)
  throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object, field, jlrField, accessingClass);
    setIntInternal(object, value, field);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  static void setLong(Object object, long value, RVMField field, Field jlrField, RVMClass accessingClass)
  throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object, field, jlrField, accessingClass);
    setLongInternal(object, value, field);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  static void setShort(Object object, short value, RVMField field, Field jlrField, RVMClass accessingClass)
  throws IllegalAccessException, IllegalArgumentException   {
    checkWriteAccess(object, field, jlrField, accessingClass);
    setShortInternal(object, value, field);
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  private static void setBooleanInternal(Object object, boolean value, RVMField field)
  throws IllegalArgumentException {
    TypeReference type = field.getType();
    if (type.isBooleanType())
      field.setBooleanValueUnchecked(object, value);
    else
      throwNewIllegalArgumentException("field type mismatch");
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  private static void setByteInternal(Object object, byte value, RVMField field) throws IllegalArgumentException {
    TypeReference type = field.getType();
    if (type.isByteType())
      field.setByteValueUnchecked(object, value);
    else if (type.isLongType())
      field.setLongValueUnchecked(object, value);
    else if (type.isIntType())
      field.setIntValueUnchecked(object, value);
    else if (type.isShortType())
      field.setShortValueUnchecked(object, value);
    else if (type.isCharType())
      field.setCharValueUnchecked(object, (char)value);
    else if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, value);
    else if (type.isFloatType())
      field.setFloatValueUnchecked(object, value);
    else
      throwNewIllegalArgumentException("field type mismatch");
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  private static void setCharInternal(Object object, char value, RVMField field) throws IllegalArgumentException {
    TypeReference type = field.getType();
    if (type.isCharType())
      field.setCharValueUnchecked(object, value);
    else if (type.isLongType())
      field.setLongValueUnchecked(object, value);
    else if (type.isIntType())
      field.setIntValueUnchecked(object, value);
    else if (type.isShortType())
      field.setShortValueUnchecked(object, (short)value);
    else if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, value);
    else if (type.isFloatType())
      field.setFloatValueUnchecked(object, value);
    else
      throwNewIllegalArgumentException("field type mismatch");
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  private static void setDoubleInternal(Object object, double value, RVMField field) throws IllegalArgumentException {
    TypeReference type = field.getType();
    if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, value);
    else
      throwNewIllegalArgumentException("field type mismatch");
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  private static void setFloatInternal(Object object, float value, RVMField field) throws IllegalArgumentException {
    TypeReference type = field.getType();
    if (type.isFloatType())
      field.setFloatValueUnchecked(object, value);
    else if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, value);
    else
      throwNewIllegalArgumentException("field type mismatch");
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  private static void setIntInternal(Object object, int value, RVMField field) throws IllegalArgumentException {
    TypeReference type = field.getType();
    if (type.isIntType())
      field.setIntValueUnchecked(object, value);
    else if (type.isLongType())
      field.setLongValueUnchecked(object, value);
    else if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, value);
    else if (type.isFloatType())
      field.setFloatValueUnchecked(object, value);
    else
      throwNewIllegalArgumentException("field type mismatch");
  }

  @Inline(value=Inline.When.ArgumentsAreConstant, arguments={2})
  private static void setLongInternal(Object object, long value, RVMField field) throws IllegalArgumentException {
    TypeReference type = field.getType();
    if (type.isLongType())
      field.setLongValueUnchecked(object, value);
    else if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, value);
    else if (type.isFloatType())
      field.setFloatValueUnchecked(object, value);
    else
      throwNewIllegalArgumentException("field type mismatch");
  }

  private static void setShortInternal(Object object, short value, RVMField field) throws IllegalArgumentException {
    TypeReference type = field.getType();
    if (type.isShortType())
      field.setShortValueUnchecked(object, value);
    else if (type.isLongType())
      field.setLongValueUnchecked(object, value);
    else if (type.isIntType())
      field.setIntValueUnchecked(object, value);
    else if (type.isDoubleType())
      field.setDoubleValueUnchecked(object, value);
    else if (type.isFloatType())
      field.setFloatValueUnchecked(object, value);
    else
      throwNewIllegalArgumentException("field type mismatch");
  }
}
