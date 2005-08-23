/*
 * (C) Copyright IBM Corp. 2003, 2005
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Runtime;
import org.vmmagic.pragma.*;

/**
 * A class to represent the reference in a class file to a method of
 * that class or interface. 
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
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
  public final VM_TypeReference getReturnType() throws UninterruptiblePragma {
    return returnType;
  }

  /**
   * @return parameter types of the method
   */
  public final VM_TypeReference[] getParameterTypes() throws UninterruptiblePragma {
    return parameterTypes;
  }

  /**
   * Space required by method for its parameters, in words.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  public final int getParameterWords() throws UninterruptiblePragma {
    int pw = 0;
    for (int i = 0; i<parameterTypes.length; i++)
           pw += parameterTypes[i].getStackWords();
    return pw;
  }

  /**
   * Do this and that definitely refer to the different methods?
   */
  public final boolean definitelyDifferent(VM_MethodReference that) {
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
  public final boolean definitelySame(VM_MethodReference that) {
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
  public final boolean isResolved() {
    return resolvedMember != null;
  }

  /**
   * Get the member this reference has been resolved to, if 
   * it has already been resolved. Does NOT force resolution.
   */
  public final VM_Method getResolvedMember() throws UninterruptiblePragma {
    return resolvedMember;
  }

  /**
   * For use by VM_Method constructor
   */
  final void setResolvedMember(VM_Method it) {
    if (VM.VerifyAssertions) VM._assert(resolvedMember == null);
    resolvedMember = it;
  }

  /**
   * Resolve the method reference for an invoke special into a target
   * method, return null if the method cannot be resolved without classloading.
   */
  public final VM_Method resolveInvokeSpecial() {
    VM_Class thisClass = (VM_Class)type.peekResolvedType();
    if (thisClass == null 
        && name != VM_ClassLoader.StandardObjectInitializerMethodName) 
    {
      thisClass = (VM_Class)type.resolve();
      /* Can't fail to resolve thisClass; we're at compile time doing
         resolution of an invocation to a private method or super call.  We
         must have loaded the class already */ 
    }
    if (thisClass == null) 
      return null; // can't be found now.
    VM_Method sought = resolveInternal(thisClass);

    if (sought.isObjectInitializer())
      return sought;   // <init>

    VM_Class cls = sought.getDeclaringClass();
    if (!cls.isSpecial())
      return sought;   // old-style invokespecial semantics

    for (; cls != null; cls = cls.getSuperClass()) {
      VM_Method found = cls.findDeclaredMethod(sought.getName(), 
                                               sought.getDescriptor());
      if (found != null)
        return found; // new-style invokespecial semantics
    }
    return null; // cannot be found
  }

  /**
   * Find the VM_Method that this method reference refers to using
   * the search order specified in JVM spec 5.4.3.3.
   * @return the VM_Method that this method ref resolved to or null if it cannot be resolved.
   */
  public final VM_Method peekResolvedMethod() {
    if (resolvedMember != null) return resolvedMember;
    
    // Hasn't been resolved yet. Try to do it now without triggering class loading.
    VM_Class declaringClass = (VM_Class)type.peekResolvedType();
    if (declaringClass == null) return null;
    return resolveInternal(declaringClass);
  }

  /**
   * Find the VM_Method that this field reference refers to using
   * the search order specified in JVM spec 5.4.3.3.
   * @return the VM_Method that this method ref resolved to.
   */
  public final VM_Method resolve() {
    if (resolvedMember != null) return resolvedMember;
    
    // Hasn't been resolved yet. Do it now triggering class loading if necessary.
    return resolveInternal((VM_Class)type.resolve());
  }


  final static boolean DBG = false;

  /**
   * Find the VM_Method that this member reference refers to using
   * the search order specified in JVM spec 5.4.3.3.
   * @return the VM_Method that this method ref resolved to.
   */
  private final VM_Method resolveInternal(VM_Class declaringClass) {
    if (!declaringClass.isResolved()) {
      declaringClass.resolve();
    }
    for (VM_Class c = declaringClass; c != null; c = c.getSuperClass()) {
      if (DBG)
        VM.sysWrite("Checking for <" + name + "," + descriptor + "> in class " + c + "...");
      
      VM_Method it = c.findDeclaredMethod(name, descriptor);
      if (it != null) {
        if (DBG)
          VM.sysWriteln("...found <" + name + "," + descriptor
                      + "> in class " + c);
        resolvedMember = it;
        return resolvedMember;
      }
      if (DBG) {
        VM.sysWriteln("...NOT found <" + name + "," + descriptor
                      + "> in class " + c);
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
        if (VM.runningVM)
          VM.sysWriteln(", while booting the VM");
        else
          VM.sysWriteln(", while writing the boot image");
        VM.sysFail("VM_MethodReference.resolveInternal(): Unable to resolve a method during VM booting or boot image writing");
    }
    throw new NoSuchMethodError(this.toString());
  }

  /**
   * Find the VM_Method that this member reference refers to using
   * the search order specified in JVM spec 5.4.3.4.
   * @return the VM_Method that this method ref resolved to or null if it cannot be resolved without trigering class loading
   */
  public final VM_Method peekInterfaceMethod() {
    if (resolvedMember != null) return resolvedMember;
    
    // Hasn't been resolved yet. Try to do it now.
    VM_Class declaringClass = (VM_Class)type.peekResolvedType();
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
  public final VM_Method resolveInterfaceMethod()
    throws IncompatibleClassChangeError, 
           NoSuchMethodError {
    if (resolvedMember != null) return resolvedMember;
    
    // Hasn't been resolved yet. Do it now.
    VM_Class declaringClass = (VM_Class)type.resolve();
    if (!declaringClass.isResolved()) {
      declaringClass.resolve();
    }
    if (!declaringClass.isInterface()) {
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
  private final VM_Method resolveInterfaceMethodInternal(VM_Class declaringClass) {
    VM_Method it = declaringClass.findDeclaredMethod(name, descriptor);
    if (it != null) {
      resolvedMember = it; 
      return resolvedMember;
    }
    VM_Class[] interfaces = declaringClass.getDeclaredInterfaces();
    for (int i=0; i<interfaces.length; i++) {
      it = searchInterfaceMethods(interfaces[i]);
      if (it != null) {
        resolvedMember = it;
        return resolvedMember;
      }
    }
    return null;
  }
    
  private final VM_Method searchInterfaceMethods(VM_Class c) {
    if (!c.isResolved()) c.resolve();
    VM_Method it = c.findDeclaredMethod(name, descriptor);
    if (it != null) return it;
    VM_Class[] interfaces = c.getDeclaredInterfaces();
    for (int i=0; i<interfaces.length; i++) {
      it = searchInterfaceMethods(interfaces[i]);
      if (it != null) return it;
    }
    return null;
  }
}
