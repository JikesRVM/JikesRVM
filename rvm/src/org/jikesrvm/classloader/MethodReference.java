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
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.vmmagic.pragma.Pure;
import org.vmmagic.pragma.Uninterruptible;

/**
 * A class to represent the reference in a class file to a method of
 * that class or interface.
 */
public final class MethodReference extends MemberReference {

  /**
   * type of return value
   */
  private final TypeReference returnType;

  /**
   * types of parameters (not including "this", if virtual)
   */
  private final TypeReference[] parameterTypes;

  /**
   * The RVMMethod that this method reference resolved to (null if not yet resolved).
   */
  private RVMMethod resolvedMember;

  /**
   * Find or create a method reference
   * @see MemberReference#findOrCreate(TypeReference, Atom, Atom)
   */
  @Pure
  public static MethodReference findOrCreate(TypeReference tRef, Atom mn, Atom md) {
    return MemberReference.findOrCreate(tRef, mn, md).asMethodReference();
  }

  /**
   * @param tr a type reference to the defining class in which this method
   * appears. (e.g., "Ljava/lang/String;")
   * @param mn the name of this method (e.g., "equals")
   * @param d the method descriptor (e.g., "(Ljava/lang/Object;)Z")
   * @param id the new ID of the member were a new member required
   */
  MethodReference(TypeReference tr, Atom mn, Atom d, int id) {
    super(tr, mn, d, id);
    ClassLoader cl = tr.getClassLoader();
    returnType = d.parseForReturnType(cl);
    parameterTypes = d.parseForParameterTypes(cl);
  }

  /**
   * @return return type of the method
   */
  @Uninterruptible
  public TypeReference getReturnType() {
    return returnType;
  }

  /**
   * @return parameter types of the method
   */
  @Uninterruptible
  public TypeReference[] getParameterTypes() {
    return parameterTypes;
  }

  /**
   * Space required by method for its parameters, in words.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  @Uninterruptible
  public int getParameterWords() {
    int pw = 0;
    for (TypeReference parameterType : parameterTypes) pw += parameterType.getStackWords();
    return pw;
  }

  /**
   * Do this and that definitely refer to the different methods?
   */
  public boolean definitelyDifferent(MethodReference that) {
    if (this == that) return false;
    if (name != that.name || descriptor != that.descriptor) return true;
    RVMMethod mine = peekResolvedMethod();
    RVMMethod theirs = that.peekResolvedMethod();
    if (mine == null || theirs == null) return false;
    return mine != theirs;
  }

  /**
   * Do this and that definitely refer to the same method?
   */
  public boolean definitelySame(MethodReference that) {
    if (this == that) return true;
    if (name != that.name || descriptor != that.descriptor) return false;
    RVMMethod mine = peekResolvedMethod();
    RVMMethod theirs = that.peekResolvedMethod();
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
  public RVMMethod getResolvedMember() {
    return resolvedMember;
  }

  /**
   * For use by RVMMethod constructor
   */
  void setResolvedMember(RVMMethod it) {
    if (VM.VerifyAssertions) VM._assert(resolvedMember == null || resolvedMember == it);
    resolvedMember = it;
  }

  /**
   * Resolve the method reference for an invoke special into a target
   * method, if that is possible without causing classloading.
   *
   * @return target method or {@code null} if the method cannot be resolved without classloading.
   */
  public synchronized RVMMethod resolveInvokeSpecial() {
    RVMClass thisClass = (RVMClass) type.peekType();
    if (thisClass == null && name != RVMClassLoader.StandardObjectInitializerMethodName) {
      thisClass = (RVMClass) type.resolve();
      /* Can't fail to resolve thisClass; we're at compile time doing
         resolution of an invocation to a private method or super call.  We
         must have loaded the class already */
    }
    if (thisClass == null) {
      return null; // can't be found now.
    }
    RVMMethod sought = resolveInternal(thisClass);

    if (sought.isObjectInitializer()) {
      return sought;   // <init>
    }

    RVMClass cls = sought.getDeclaringClass();
    if (!cls.isSpecial()) {
      return sought;   // old-style invokespecial semantics
    }

    for (; cls != null; cls = cls.getSuperClass()) {
      RVMMethod found = cls.findDeclaredMethod(sought.getName(), sought.getDescriptor());
      if (found != null) {
        return found; // new-style invokespecial semantics
      }
    }
    return null; // cannot be found
  }

  /**
   * Find the RVMMethod that this method reference refers to using
   * the search order specified in JVM spec 5.4.3.3.
   * @return the RVMMethod that this method ref resolved to or {@code null} if it cannot be resolved.
   */
  public RVMMethod peekResolvedMethod() {
    if (resolvedMember != null) return resolvedMember;

    // Hasn't been resolved yet. Try to do it now without triggering class loading.
    RVMType declaringClass = type.peekType();
    if (declaringClass == null) return null;
    return resolveInternal((RVMClass)declaringClass);
  }

  /**
   * Find the RVMMethod that this field reference refers to using
   * the search order specified in JVM specification 5.4.3.3.
   * @return the RVMMethod that this method reference resolved to.
   */
  public synchronized RVMMethod resolve() {
    if (resolvedMember != null) return resolvedMember;

    // Hasn't been resolved yet. Do it now triggering class loading if necessary.
    return resolveInternal((RVMClass) type.resolve());
  }

  /**
   * Return {@code true} iff this member reference refers to a method which
   * is declared as part of an abstract class but actually is an
   * interface method, known formally as a "miranda method".
   * <p>
   * This method is necessary to handle the special case where an
   * invokevirtual is defined on an abstract class, where the
   * method invocation points to a method inherited from an
   * interface.
   *
   * @return boolean    {@code true} iff this member method reference is a miranda method
   */
  public boolean isMiranda() {

    // Hasn't been resolved yet. Try to do it now without triggering class loading.
    RVMClass declaringClass = (RVMClass) type.peekType();
    if (declaringClass == null) { return false; }

    if (!declaringClass.isResolved()) {
      declaringClass.resolve();
    }

    // See if method is explicitly declared in any superclass
    for (RVMClass c = declaringClass; c != null; c = c.getSuperClass()) {

      if (c.findDeclaredMethod(name, descriptor) != null) {
        // Method declared in superclass => not interface method
        return false;
      }
    }

    // Not declared in any superclass; now check to see if it is coming from an interface somewhere
    for (RVMClass c = declaringClass; c != null; c = c.getSuperClass()) {
      // See if method is in any interfaces of c
      for (RVMClass intf : c.getDeclaredInterfaces()) {
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
    return getType().isMagicType() || ((resolvedMember != null) && (resolvedMember.isSysCall() || resolvedMember.isSpecializedInvoke()));
  }

  /**
   * Is the method reference to a specialized invoke? NB. we don't know until they are resolved.
   */
  public boolean isSpecializedInvoke() {
    return (resolvedMember != null) && (resolvedMember.isSpecializedInvoke());
  }

  /**
   * Is the method reference to a magic method? NB. In the case of
   * SysCall annotated methods we don't know until they are resolved.
   */
  public boolean isSysCall() {
    return (getType() == TypeReference.SysCall) || ((resolvedMember != null) && (resolvedMember.isSysCall()));
  }

  /**
   * Find the RVMMethod that this member reference refers to using
   * the search order specified in JVM spec 5.4.3.3.
   * @return the RVMMethod that this method ref resolved to.
   */
  private RVMMethod resolveInternal(RVMClass declaringClass) {
    final boolean DBG=false;
    if (!declaringClass.isResolved()) {
      declaringClass.resolve();
    }
    for (RVMClass c = declaringClass; c != null; c = c.getSuperClass()) {
      if (DBG) {
        VM.sysWrite("Checking for <" + name + "," + descriptor + "> in class " + c + "...");
      }

      RVMMethod it = c.findDeclaredMethod(name, descriptor);
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
      VM.sysWrite("MethodReference.resolveInternal():");
      VM.sysWrite(" Unable to find a method named ");
      name.sysWrite();
      VM.sysWrite(" with descriptor ");
      descriptor.sysWrite();
      VM.sysWrite(" in the class ");
      declaringClass.getDescriptor().sysWrite();
      if (VM.runningVM) {
        VM.sysWriteln(", while booting the VM");
        VM.sysFail("MethodReference.resolveInternal(): Unable to resolve a method during VM booting");

      } else {
        VM.sysWriteln(", while writing the boot image");
        Thread.dumpStack();
        throw new Error("MethodReference.resolveInternal(): Unable to resolve a method during boot image writing");
      }
    }
    throw new NoSuchMethodError(this.toString());
  }

  /**
   * Find the RVMMethod that this member reference refers to using
   * the search order specified in JVM spec 5.4.3.4.
   * @return the RVMMethod that this method ref resolved to or {@code null} if it cannot be resolved without trigering class loading
   */
  public RVMMethod peekInterfaceMethod() {
    if (resolvedMember != null) return resolvedMember;

    // Hasn't been resolved yet. Try to do it now.
    RVMClass declaringClass = (RVMClass) type.peekType();
    if (declaringClass == null) return null;
    if (!declaringClass.isResolved()) {
      declaringClass.resolve();
    }
    if (!declaringClass.isInterface()) return null;
    return resolveInterfaceMethodInternal(declaringClass);
  }

  /**
   * Find the RVMMethod that this member reference refers to using
   * the search order specified in JVM spec 5.4.3.4.
   * @return the RVMMethod that this method ref resolved to
   */
  public RVMMethod resolveInterfaceMethod() throws IncompatibleClassChangeError, NoSuchMethodError {
    if (resolvedMember != null) return resolvedMember;

    // Hasn't been resolved yet. Do it now.
    RVMClass declaringClass = (RVMClass) type.resolve();
    if (!declaringClass.isResolved()) {
      declaringClass.resolve();
    }

    /* Interface method may be either in interface, or a miranda.
    */
    if (!declaringClass.isInterface() && !isMiranda()) {
      throw new IncompatibleClassChangeError();
    }
    RVMMethod ans = resolveInterfaceMethodInternal(declaringClass);
    if (ans == null) {
      throw new NoSuchMethodError(this.toString());
    }
    return ans;
  }

  /**
   * Find the RVMMethod that this member reference refers to using
   * the search order specified in JVM spec 5.4.3.4.
   * @return the RVMMethod that this method ref resolved to or {@code null} for error
   */
  private RVMMethod resolveInterfaceMethodInternal(RVMClass declaringClass) {
    RVMMethod it = declaringClass.findDeclaredMethod(name, descriptor);
    if (it != null) {
      resolvedMember = it;
      return resolvedMember;
    }
    for (RVMClass intf : declaringClass.getDeclaredInterfaces()) {
      it = searchInterfaceMethods(intf);
      if (it != null) {
        resolvedMember = it;
        return resolvedMember;
      }
    }
    return null;
  }

  private RVMMethod searchInterfaceMethods(RVMClass c) {
    if (!c.isResolved()) c.resolve();
    RVMMethod it = c.findDeclaredMethod(name, descriptor);
    if (it != null) return it;
    for (RVMClass intf : c.getDeclaredInterfaces()) {
      it = searchInterfaceMethods(intf);
      if (it != null) return it;
    }
    return null;
  }
}
