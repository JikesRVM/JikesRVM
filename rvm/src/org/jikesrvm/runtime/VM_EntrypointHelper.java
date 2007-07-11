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
package org.jikesrvm.runtime;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_BootstrapClassLoader;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_Member;
import org.jikesrvm.classloader.VM_NormalMethod;
import org.jikesrvm.classloader.VM_TypeReference;

/**
 * Helper class for retrieving entrypoints. Entrypoints are fields and
 * methods of the virtual machine that are needed by compiler-generated
 * machine code or C runtime code.
 */
public class VM_EntrypointHelper {
  /**
   * Get description of virtual machine component (field or method).
   * Note: This is method is intended for use only by VM classes that need
   * to address their own fields and methods in the runtime virtual machine
   * image.  It should not be used for general purpose class loading.
   * @param classDescriptor  class  descriptor - something like "Lorg/jikesrvm/VM_Runtime;"
   * @param memberName       member name       - something like "invokestatic"
   * @param memberDescriptor member descriptor - something like "()V"
   * @return corresponding VM_Member object
   */
  private static VM_Member getMember(String classDescriptor, String memberName, String memberDescriptor) {
    VM_Atom clsDescriptor = VM_Atom.findOrCreateAsciiAtom(classDescriptor);
    VM_Atom memName = VM_Atom.findOrCreateAsciiAtom(memberName);
    VM_Atom memDescriptor = VM_Atom.findOrCreateAsciiAtom(memberDescriptor);
    try {
      VM_TypeReference tRef =
          VM_TypeReference.findOrCreate(VM_BootstrapClassLoader.getBootstrapClassLoader(), clsDescriptor);
      VM_Class cls = (VM_Class) tRef.resolve();
      cls.resolve();

      VM_Member member;
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
    VM.sysWrite("VM_Entrypoints.getMember: can't resolve class=" +
                classDescriptor +
                " member=" +
                memberName +
                " desc=" +
                memberDescriptor +
                "\n");
    VM._assert(VM_Constants.NOT_REACHED);
    return null; // placate jikes
  }

  public static VM_NormalMethod getMethod(String klass, String member, String descriptor, final boolean runtimeServiceMethod) {
    VM_NormalMethod m = (VM_NormalMethod) getMember(klass, member, descriptor);
    m.setRuntimeServiceMethod(runtimeServiceMethod);
    return m;
  }

  public static VM_NormalMethod getMethod(String klass, String member, String descriptor) {
    return getMethod(klass, member, descriptor, true);
  }

  public static VM_Field getField(String klass, String member, String descriptor) {
    return (VM_Field) getMember(klass, member, descriptor);
  }
}
