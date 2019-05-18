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
import org.jikesrvm.classlibrary.ClassLibraryHelpers;
import org.jikesrvm.classlibrary.JavaLangReflectSupport;
import org.jikesrvm.classloader.*;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.runtime.ReflectionBase;

import static java.lang.reflect.JikesRVMHelpers.*;

import java.util.LinkedList;
import java.util.List;

/**
 * Library support interface of Jikes RVM
 */
public class JikesRVMSupport {

  private static final ReflectAccess ra = new ReflectAccess();

  /**
   * The implementations of {@link Constructor}, {@link Method} and {@link Field}
   * have a field with name {@code slot} that has an implementation defined
   * meaning. It's not useful for us because we currently don't use shared tables
   * for fields and methods. Therefore, save some defined value into that we'll possibly
   * recognize if it appears somewhere.
   */
  public static final int SLOT_CONTENTS = 0xFADEDEAD;

  public static Field createField(RVMField f) {
    RVMClass declaringRvmClass = f.getDeclaringClass();
    Class<?> declaringClass = declaringRvmClass.getClassForType();
    String name = atomToInternedStringOrError(f.getName());
    Class<?> type = f.getType().resolve().getClassForType();
    int modifiers = f.getModifiers();
    int slot = SLOT_CONTENTS;
    String signature = convertAtomToInternedStringOrNull(f.getSignature());
    byte[] annotations = f.getAnnotationsData().getRawAnnotations();
    Field newField = ra.newField(declaringClass, name, type, modifiers, slot, signature, annotations);
    Magic.setObjectAtOffset(newField, ClassLibraryHelpers.javaLangReflectField_rvmFieldField.getOffset(), f);
    return newField;
  }

  public static Method createMethod(RVMMethod m) {
    Class<?> declaringClass = m.getDeclaringClass().getClassForType();
    String name = atomToInternedStringOrError(m.getName());
    Class[] parameterTypes = convertMethodParametersTypesToClasses(m);
    Class<?> returnType = m.getReturnType().resolve().getClassForType();
    Class[] checkedExceptions = determineCheckedExceptionTypes(m.getExceptionTypes());
    int modifiers = m.getModifiers();
    int slot = SLOT_CONTENTS;
    String signature = convertAtomToInternedStringOrNull(m.getSignature());
    MethodAnnotations annotationsData = (MethodAnnotations) m.getAnnotationsData();
    byte[] annotations = annotationsData.getRawAnnotations();
    byte[] parameterAnnotations = annotationsData.getRawParameterAnnotations();
    byte[] annotationDefault = annotationsData.getRawAnnotationDefault();
    Method newMethod = ra.newMethod(declaringClass, name, parameterTypes, returnType, checkedExceptions, modifiers, slot, signature, annotations, parameterAnnotations, annotationDefault);
    Magic.setObjectAtOffset(newMethod, ClassLibraryHelpers.javaLangReflectMethod_rvmMethodField.getOffset(), m);
    ReflectionBase invoker = m.getInvoker();
    if (Reflection.cacheInvokerInJavaLangReflect) {
      Magic.setObjectAtOffset(newMethod, ClassLibraryHelpers.javaLangReflectMethod_invokerField.getOffset(), invoker);
    } else {
      Magic.setObjectAtOffset(newMethod, ClassLibraryHelpers.javaLangReflectMethod_invokerField.getOffset(), null);
    }
    return newMethod;
  }



  @SuppressWarnings("unchecked") // Can't type-check this without <T> type<T>, which breaks javac
  public static <T> Constructor<T> createConstructor(RVMMethod m) {
    Class<?> declaringClass = m.getDeclaringClass().getClassForType();
    Class[] parameterTypes = convertMethodParametersTypesToClasses(m);
    Class[] checkedExceptions = determineCheckedExceptionTypes(m.getExceptionTypes());
    int modifiers = m.getModifiers();
    // slot is implementation defined
    int slot = SLOT_CONTENTS;
    String signature = convertAtomToInternedStringOrNull(m.getSignature());
    MethodAnnotations annotationsData = (MethodAnnotations) m.getAnnotationsData();
    byte[] annotations = annotationsData.getRawAnnotations();
    byte[] parameterAnnotations = annotationsData.getRawParameterAnnotations();
    Constructor<T> newConstructor = (Constructor<T>) ra.newConstructor(declaringClass, parameterTypes, checkedExceptions, modifiers, slot, signature, annotations, parameterAnnotations);
    Magic.setObjectAtOffset(newConstructor, ClassLibraryHelpers.javaLangReflectConstructor_rvmMethodField.getOffset(), m);
    return newConstructor;
  }

  public static Object createVMConstructor(RVMMethod m) {
    VM.sysWriteln("CreateVMConstructor is called");
    throw new Error("OpenJDK doesn't provide the VMConstructor API and shouldn't need it");
  }

  public static RVMField getFieldOf(Field f) {
    return (RVMField) Magic.getObjectAtOffset(f, ClassLibraryHelpers.javaLangReflectField_rvmFieldField.getOffset());
  }

  public static RVMMethod getMethodOf(Method m) {
    return (RVMMethod) Magic.getObjectAtOffset(m, ClassLibraryHelpers.javaLangReflectMethod_rvmMethodField.getOffset());
  }

  public static RVMMethod getMethodOf(Constructor cons) {
    return (RVMMethod) Magic.getObjectAtOffset(cons, ClassLibraryHelpers.javaLangReflectConstructor_rvmMethodField.getOffset());
  }

  private static Class[] determineCheckedExceptionTypes(
      TypeReference[] exceptionTypeReferences) {
    // TODO OPENJDK/ICEDTEA partly sharable with GNU Classpath
    if (exceptionTypeReferences == null) {
      return new Class[0];
    } else {
      // FIXME OPENJDK/ICEDTEA move this into a shared helper or core VM and add test cases
      Class<?>[] classes = JavaLangReflectSupport.typesToClasses(exceptionTypeReferences);
      return determineCheckedExceptionTypes(classes);
    }
  }

  private static Class[] determineCheckedExceptionTypes(Class<?>[] classes) {
    List<Class<?>> checkedExceptionClasses = new LinkedList<Class<?>>();
    Class<?> ancestor = null;
    for (Class<?> c : classes) {
      ancestor = c;
      while (ancestor != null) {
        String name = ancestor.getName();
        if ("java.lang.RuntimeException".equals(name) || "java.lang.Error".equals(name)) {
          break;
        } else if ("java.lang.Exception".equals(name)) {
          checkedExceptionClasses.add(c);
          break;
        } else {
          ancestor = ancestor.getSuperclass();
        }
      }
    }
    return checkedExceptionClasses.toArray(new Class[0]);
  }


}
