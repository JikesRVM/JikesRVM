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
package java.lang;

import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Member;
import org.vmmagic.pragma.NoInline;

/**
 * Common utilities for Jikes RVM implementations of the java.lang API
 */
class VMCommonLibrarySupport {
  /* ---- Non-inlined Exception Throwing Methods --- */
  /**
   * Method just to throw an illegal access exception without being inlined
   */
  @NoInline
  private static void throwNewIllegalAccessException(VM_Member member, VM_Class accessingClass) throws IllegalAccessException{
    throw new IllegalAccessException("Access to " + member + " is denied to " + accessingClass);
  }
  /* ---- General Reflection Support ---- */
  /**
   * Check to see if a method declared by the accessingClass
   * should be allowed to access the argument VM_Member.
   * Assumption: member is not public.  This trivial case should
   * be approved by the caller without needing to call this method.
   */
  static void checkAccess(VM_Member member, VM_Class accessingClass) throws IllegalAccessException {
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
    throwNewIllegalAccessException(member, accessingClass);
  }
}
