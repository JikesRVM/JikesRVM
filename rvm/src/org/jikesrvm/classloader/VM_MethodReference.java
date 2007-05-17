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
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A class to represent the reference in a class file to a method of
 * that class or interface.
 */
public final class VM_MethodReference extends VM_MemberReference {

  /**
   * The VM_Method that this method reference resolved to (null if not yet resolved).
   */
  private VM_Method resolvedMember;

  /**
   * type of return value
   */
  private final VM_TypeReference returnType;

  /**
   * types of parameters (not including "this", if virtual)
   */
  private final VM_TypeReference[] parameterTypes;

  /**
   * @param tr a type reference to the defining class in which this method
   * appears. (e.g., "Ljava/lang/String;")
   * @param mn the name of this method (e.g., "equals")
   * @param d the method descriptor (e.g., "(Ljava/lang/Object;)Z")
   */
  VM_MethodReference(VM_TypeReference tr, VM_Atom mn, VM_Atom d) {
    super(tr, mn, d);
    ClassLoader cl = tr.getClassLoader();
    returnType = d.parseForReturnType(cl);
    parameterTypes = d.parseForParameterTypes(cl);
  }

  /**
   * @return return type of the method
   */
  @Uninterruptible
  public VM_TypeReference getReturnType() {
    return returnType;
  }

  /**
   * @return parameter types of the method
   */
  @Uninterruptible
  public VM_TypeReference[] getParameterTypes() {
    return parameterTypes;
  }

  /**
   * Space required by method for its parameters, in words.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  @Uninterruptible
  public int getParameterWords() {
    int pw = 0;
    for (VM_TypeReference parameterType : parameterTypes) pw += parameterType.getStackWords();
    return pw;
  }

  /**
   * Do this and that definitely refer to the different methods?
   */
  public boolean definitelyDifferent(VM_MethodReference that) {
    if (this == that) return false;
    if (name != that.name || descriptor != that.descriptor) return true;
    VM_Method mine = peekResolvedMethod();
    VM_Method theirs = that.peekResolvedMethod();
    if (mine == null || theirs == null) return false;
    return mine != theirs;
  }

  /**
   * Do this and that definitely refer to the same method?
   */
  public boolean definitelySame(VM_MethodReference that) {
    if (this == that) return true;
    if (name != that.name || descriptor != that.descriptor) return false;
    VM_Method mine = peekResolvedMethod();
    VM_Method theirs = that.peekResolvedMethod();
    if (mine == null || theirs == null) return false;
    return mine == theirs;
  }

  /**
   * Has the method reference already been resolved into a target method?
   */
  public boolean isResolved() {
    return resolvedMember != null;
  }

  /**
   * Get the member this reference has been resolved to, if
   * it has already been resolved. Does NOT force resolution.
   */
  @Uninterruptible
  public VM_Method getResolvedMember() {
    return resolvedMember;
  }

  /**
   * For use by VM_Method constructor
   */
  void setResolvedMember(VM_Method it) {
    if (VM.VerifyAssertions) VM._assert(resolvedMember == null);
    resolvedMember = it;
  }

  /**
   * Resolve the method reference for an invoke special into a target
   * method, return null if the method cannot be resolved without classloading.
   */
  public synchronized VM_Method resolveInvokeSpecial() {
    VM_Class thisClass = (VM_Class) type.peekResolvedType();
    if (thisClass == null && name != VM_ClassLoader.StandardObjectInitializerMethodName) {
      thisClass = (VM_Class) type.resolve();
      /* Can't fail to resolve thisClass; we're at compile time doing
         resolution of an invocation to a private method or super call.  We
         must have loaded the class already */
    }
    if (thisClass == null) {
      return null; // can't be found now.
    }
    VM_Method sought = resolveInternal(thisClass);

    if (sought.isObjectInitializer()) {
      return sought;   // <init>
    }

    VM_Class cls = sought.getDeclaringClass();
    if (!cls.isSpecial()) {
      return sought;   // old-style invokespecial semantics
    }

    for (; cls != null; cls = cls.getSuperClass()) {
      VM_Method found = cls.findDeclaredMethod(sought.getName(), sought.getDescriptor());
      if (found != null) {
        return found; // new-style invokespecial semantics
      }
    }
    return null; // cannot be found
  }

  /**
   * Find the VM_Method that this method reference refers to using
   * the search order specified in JVM spec 5.4.3.3.
   * @return the VM_Method that this method ref resolved to or null if it cannot be resolved.
   */
  public VM_Method peekResolvedMethod() {
    if (resolvedMember != null) return resolvedMember;

    // Hasn't been resolved yet. Try to do it now without triggering class loading.
    VM_Class declaringClass = (VM_Class) type.peekResolvedType();
    if (declaringClass == null) return null;
    return resolveInternal(declaringClass);
  }

  /**
   * Find the VM_Method that this field reference refers to using
   * the search order specified in JVM spec 5.4.3.3.
   * @return the VM_Method that this method ref resolved to.
   */
  public synchronized VM_Method resolve() {
    if (resolvedMember != null) return resolvedMember;

    // Hasn't been resolved yet. Do it now triggering class loading if necessary.
    return resolveInternal((VM_Class) type.resolve());
  }

  static final boolean DBG = false;

  /**
   * Return true iff this member reference refers to a method which
   * is declared as part of an abstract class but actually is an
   * interface method, known formally as a "miranda method".
   *
   * This method is necessary to handle the special case where an
   * invokevirtual is defined on an abstract class, where the
   * method invocation points to a method inherited from an
   * interface.
   *
   * @return boolean    true iff this member method reference is a miranda method
   */
  public boolean isMiranda() {

    // Hasn't been resolved yet. Try to do it now without triggering class loading.
    VM_Class declaringClass = (VM_Class) type.peekResolvedType();
    if (declaringClass == null) { return false; }

    if (!declaringClass.isResolved()) {
      declaringClass.resolve();
    }

    // See if method is in any superclasses
    for (VM_Class c = declaringClass; c != null; c = c.getSuperClass()) {

      if (c.findDeclaredMethod(name, descriptor) != null) {
        // Method in superclass => not interface method
        return false;
      }

      // See if method is in any interfaces of c
      for (VM_Class intf : c.getDeclaredInterfaces()) {
        if (searchInterfaceMethods(intf) != null) {
          // Found method in interface or superinterface
          return true;
        }
      }
    }

    // neither in superclass or interface => not interface method
    return false;
  }

  /**
   * Is the method reference to a magic method? NB. In the case of
   * SysCall annotated methods we don't know until they are resolved.
   */
  public boolean isMagic() {
    return getType().isMagicType() || ((resolvedMember != null) && (resolvedMember.isSysCall()));
  }

  /**
   * Is the method reference to a magic method? NB. In the case of
   * SysCall annotated methods we don't know until they are resolved.
   */
  public boolean isSysCall() {
    return (getType() == VM_TypeReference.SysCall) || ((resolvedMember != null) && (resolvedMember.isSysCall()));
  }

  /**
   * Find the VM_Method that this member reference refers to using
   * the search order specified in JVM spec 5.4.3.3.
   * @return the VM_Method that this method ref resolved to.
   */
  private VM_Method resolveInternal(VM_Class declaringClass) {
    if (!declaringClass.isResolved()) {
      declaringClass.resolve();
    }
    for (VM_Class c = declaringClass; c != null; c = c.getSuperClass()) {
      if (DBG) {
        VM.sysWrite("Checking for <" + name + "," + descriptor + "> in class " + c + "...");
      }

      VM_Method it = c.findDeclaredMethod(name, descriptor);
      if (it != null) {
        if (DBG) {
          VM.sysWriteln("...found <" + name + "," + descriptor + "> in class " + c);
        }
        resolvedMember = it;
        return resolvedMember;
      }
      if (DBG) {
        VM.sysWriteln("...NOT found <" + name + "," + descriptor + "> in class " + c);
      }
    }
    if (!VM.fullyBooted) {
      VM.sysWrite("VM_MethodReference.resolveInternal():");
      VM.sysWrite(" Unable to find a method named ");
      name.sysWrite();
      VM.sysWrite(" with descriptor ");
      descriptor.sysWrite();
      VM.sysWrite(" in the class ");
      declaringClass.getDescriptor().sysWrite();
      if (VM.runningVM) {
        VM.sysWriteln(", while booting the VM");
      } else {
        VM.sysWriteln(", while writing the boot image");
      }
      VM.sysFail(
          "VM_MethodReference.resolveInternal(): Unable to resolve a method during VM booting or boot image writing");
    }
    throw new NoSuchMethodError(this.toString());
  }

  /**
   * Find the VM_Method that this member reference refers to using
   * the search order specified in JVM spec 5.4.3.4.
   * @return the VM_Method that this method ref resolved to or null if it cannot be resolved without trigering class loading
   */
  public VM_Method peekInterfaceMethod() {
    if (resolvedMember != null) return resolvedMember;

    // Hasn't been resolved yet. Try to do it now.
    VM_Class declaringClass = (VM_Class) type.peekResolvedType();
    if (declaringClass == null) return null;
    if (!declaringClass.isResolved()) {
      declaringClass.resolve();
    }
    if (!declaringClass.isInterface()) return null;
    return resolveInterfaceMethodInternal(declaringClass);
  }

  /**
   * Find the VM_Method that this member reference refers to using
   * the search order specified in JVM spec 5.4.3.4.
   * @return the VM_Method that this method ref resolved to
   */
  public VM_Method resolveInterfaceMethod() throws IncompatibleClassChangeError, NoSuchMethodError {
    if (resolvedMember != null) return resolvedMember;

    // Hasn't been resolved yet. Do it now.
    VM_Class declaringClass = (VM_Class) type.resolve();
    if (!declaringClass.isResolved()) {
      declaringClass.resolve();
    }

    /* Interface method may be either in interface, or a miranda.
    */
    if (!declaringClass.isInterface() && !isMiranda()) {
      throw new IncompatibleClassChangeError();
    }
    VM_Method ans = resolveInterfaceMethodInternal(declaringClass);
    if (ans == null) {
      throw new NoSuchMethodError(this.toString());
    }
    return ans;
  }

  /**
   * Find the VM_Method that this member reference refers to using
   * the search order specified in JVM spec 5.4.3.4.
   * @return the VM_Method that this method ref resolved to or null for error
   */
  private VM_Method resolveInterfaceMethodInternal(VM_Class declaringClass) {
    VM_Method it = declaringClass.findDeclaredMethod(name, descriptor);
    if (it != null) {
      resolvedMember = it;
      return resolvedMember;
    }
    for (VM_Class intf : declaringClass.getDeclaredInterfaces()) {
      it = searchInterfaceMethods(intf);
      if (it != null) {
        resolvedMember = it;
        return resolvedMember;
      }
    }
    return null;
  }

  private VM_Method searchInterfaceMethods(VM_Class c) {
    if (!c.isResolved()) c.resolve();
    VM_Method it = c.findDeclaredMethod(name, descriptor);
    if (it != null) return it;
    for (VM_Class intf : c.getDeclaredInterfaces()) {
      it = searchInterfaceMethods(intf);
      if (it != null) return it;
    }
    return null;
  }
}
