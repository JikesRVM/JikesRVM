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
package org.jikesrvm.runtime;

import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.BootstrapClassLoader;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMember;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.NormalMethod;
import org.jikesrvm.classloader.TypeReference;

/**
 * Helper class for retrieving entrypoints. Entrypoints are fields and
 * methods of the virtual machine that are needed by compiler-generated
 * machine code or C runtime code.
 */
public class EntrypointHelper {
  /**
   * Get description of virtual machine component (field or method).
   * Note: This is method is intended for use only by VM classes that need
   * to address their own fields and methods in the runtime virtual machine
   * image.  It should not be used for general purpose class loading.
   * @param classDescriptor  class  descriptor - something like "Lorg/jikesrvm/RuntimeEntrypoints;"
   * @param memberName       member name       - something like "invokestatic"
   * @param memberDescriptor member descriptor - something like "()V"
   * @return corresponding RVMMember object
   */
  private static RVMMember getMember(String classDescriptor, String memberName, String memberDescriptor) {
    Atom clsDescriptor = Atom.findOrCreateAsciiAtom(classDescriptor);
    Atom memName = Atom.findOrCreateAsciiAtom(memberName);
    Atom memDescriptor = Atom.findOrCreateAsciiAtom(memberDescriptor);
    try {
      TypeReference tRef =
          TypeReference.findOrCreate(BootstrapClassLoader.getBootstrapClassLoader(), clsDescriptor);
      RVMClass cls = (RVMClass) tRef.resolve();
      cls.resolve();

      RVMMember member;
      if ((member = cls.findDeclaredField(memName, memDescriptor)) != null) {
        return member;
      }
      if ((member = cls.findDeclaredMethod(memName, memDescriptor)) != null) {
        return member;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    // The usual causes for getMember() to fail are:
    //  1. you mispelled the class name, member name, or member signature
    //  2. the class containing the specified member didn't get compiled
    //
    VM.sysWrite("Entrypoints.getMember: can't resolve class=" +
                classDescriptor +
                " member=" +
                memberName +
                " desc=" +
                memberDescriptor +
                "\n");
    VM._assert(Constants.NOT_REACHED);
    return null; // placate jikes
  }

  public static NormalMethod getMethod(String klass, String member, String descriptor, final boolean runtimeServiceMethod) {
    NormalMethod m = (NormalMethod) getMember(klass, member, descriptor);
    m.setRuntimeServiceMethod(runtimeServiceMethod);
    return m;
  }

  public static NormalMethod getMethod(String klass, String member, String descriptor) {
    return getMethod(klass, member, descriptor, true);
  }

  private static String makeDescriptor(Class<?>... argTypes) {
    Class<?> lastClass = null;
    StringBuilder result = new StringBuilder("(");
    for (Class<?> c: argTypes) {
      if (lastClass != null) {
        result.append(TypeReference.findOrCreate(lastClass).getName().toString());
      }
      lastClass = c;
    }
    result.append(")").append(TypeReference.findOrCreate(lastClass).getName().toString());
    return result.toString();
  }

  public static RVMMethod getMethod(Class<?> klass, Atom member, Class<?>... argTypes) {
    if (!VM.runningVM) { // avoid compiling this code into the boot image
      try {
        TypeReference tRef = TypeReference.findOrCreate(klass);
        RVMClass cls = tRef.resolve().asClass();
        cls.resolve();

        Atom descriptor = Atom.findOrCreateAsciiAtom(makeDescriptor(argTypes));

        RVMMethod method = cls.findDeclaredMethod(member, descriptor);
        if (method != null) {
          return method;
        }
      } catch(Throwable t) {
        throw new Error("Entrypoints.getMethod: can't resolve class=" +
            klass + " member=" + member + " desc=" + makeDescriptor(argTypes), t);
      }
    }
    throw new Error("Entrypoints.getMethod: can't resolve class=" +
        klass + " member=" + member + " desc=" + makeDescriptor(argTypes));
  }

  public static MethodReference getMethodReference(Class<?> klass, Atom member, Class<?>... argTypes) {
    if (!VM.runningVM) { // avoid compiling this code into the boot image
      TypeReference tRef = TypeReference.findOrCreate(klass);
      if (tRef.resolve().isClassType()) {
        return getMethod(klass, member, argTypes).getMemberRef().asMethodReference();
      } else { // handle method references to unboxed types
        Atom descriptor = Atom.findOrCreateAsciiAtom(makeDescriptor(argTypes));
        return MethodReference.findOrCreate(tRef, member, descriptor);
      }
    }
    throw new Error("Entrypoints.getMethod: can't resolve class=" +
        klass + " member=" + member + " desc=" + makeDescriptor(argTypes));
  }

  public static RVMField getField(String klass, String member, String descriptor) {
    return (RVMField) getMember(klass, member, descriptor);
  }

  /**
   * Get description of virtual machine field.
   * @param klass class containing field
   * @param member member name - something like "invokestatic"
   * @param type of field
   * @return corresponding RVMField
   */
  public static RVMField getField(Class<?> klass, String member, Class<?> type) {
    if (!VM.runningVM) { // avoid compiling this code into the boot image
      try {
        TypeReference klassTRef = TypeReference.findOrCreate(klass);
        RVMClass cls = klassTRef.resolve().asClass();
        cls.resolve();

        Atom memName = Atom.findOrCreateAsciiAtom(member);
        Atom typeName = TypeReference.findOrCreate(type).getName();

        RVMField field = cls.findDeclaredField(memName, typeName);
        if (field != null) {
          return field;
        }
      } catch(Throwable t) {
        throw new Error("Entrypoints.getField: can't resolve class=" +
            klass + " member=" + member + " desc=" + type, t);
      }
    }
    throw new Error("Entrypoints.getField: can't resolve class=" +
        klass + " member=" + member + " desc=" + type);
  }

  /**
   * Get description of virtual machine field.
   * @param klass class containing field
   * @param member member name - something like "invokestatic"
   * @param type of field
   * @return corresponding RVMField
   */
  static RVMField getField(String klass, String member, Class<?> type) {
    if (!VM.runningVM) { // avoid compiling this code into the boot image
      try {
        TypeReference tRef = TypeReference.findOrCreate(klass);
        RVMClass cls = tRef.resolve().asClass();
        cls.resolve();

        Atom memName = Atom.findOrCreateAsciiAtom(member);
        Atom typeName = TypeReference.findOrCreate(type).getName();

        RVMField field = cls.findDeclaredField(memName, typeName);
        if (field != null) {
          return field;
        }
      } catch(Throwable t) {
        throw new Error("Entrypoints.getField: can't resolve class=" +
            klass + " member=" + member + " desc=" + type, t);
      }
    }
    throw new Error("Entrypoints.getField: can't resolve class=" +
        klass + " member=" + member + " desc=" + type);
  }

  /**
   * Get description of virtual machine method.
   * @param klass class  containing method
   * @param member member name - something like "invokestatic"
   * @param descriptor member descriptor - something like "()V"
   * @return corresponding RVMMethod
   */
  public static NormalMethod getMethod(Class<?> klass, String member, String descriptor) {
    if (!VM.runningVM) { // avoid compiling this code into the boot image
      try {
        TypeReference klassTRef = TypeReference.findOrCreate(klass);
        RVMClass cls = klassTRef.resolve().asClass();
        cls.resolve();

        Atom memName = Atom.findOrCreateAsciiAtom(member);
        Atom memDescriptor = Atom.findOrCreateAsciiAtom(descriptor);

        NormalMethod m = (NormalMethod)cls.findDeclaredMethod(memName, memDescriptor);
        if (m != null) {
          m.setRuntimeServiceMethod(true);
          return m;
        }
      } catch(Throwable t) {
        throw new Error("Entrypoints.getField: can't resolve class=" +
            klass + " member=" + member + " desc=" + descriptor, t);
      }
    }
    throw new Error("Entrypoints.getMethod: can't resolve class=" +
        klass + " method=" + member + " desc=" + descriptor);
  }
}
