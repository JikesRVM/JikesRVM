/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Runtime;

/**
 * A class to represent the reference in a class file to a field.
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
  private final VM_Type returnType;          

  /**
   * types of parameters (not including "this", if virtual)
   */
  private final VM_Type[] parameterTypes;      

  /**
   * words needed for parameters (not including "this", if virtual)
   */
  private final int parameterWords;      

  /**
   * @param cl the classloader
   * @param cn the class name
   * @param mn the field or method name
   * @param d the field or method descriptor
   */
  VM_MethodReference(ClassLoader cl, VM_Atom cn, VM_Atom mn, VM_Atom d) {
    super(cl, cn, mn, d);
    returnType = d.parseForReturnType(cl);
    parameterTypes = d.parseForParameterTypes(cl);
    int pw = 0;
    for (int i = 0, n = parameterTypes.length; i < n; ++i)
      pw += parameterTypes[i].getStackWords();
    parameterWords = pw;
  }

  /**
   * @return return type of the method
   */
  public final VM_Type getReturnType() {
    return returnType;
  }

  /**
   * @return parameter types of the method
   */
  public final VM_Type[] getParameterTypes() {
    return parameterTypes;
  }

  /**
   * Space required by method for its parameters, in words.
   * Note: does *not* include implicit "this" parameter, if any.
   */
  public final int getParameterWords() {
    return parameterWords;
  }

  // TEMPORARY KLUDGE UNTIL WE INTRRODUCE TYPE REFERENCES
  public final boolean isWordType() {
    return className == VM_Type.WordType.getDescriptor() || 
      className == VM_Type.AddressType.getDescriptor() ||
      className == VM_Type.OffsetType.getDescriptor();
  }
  public final boolean isMagicType() {
    return className == VM_Type.MagicType.getDescriptor();
  }
    

  /**
   * Do this and that definitely refer to the different methods?
   */
  public final boolean definitelyDifferent(VM_MethodReference that) {
    if (this == that) return false;
    if (getMemberName() != that.getMemberName() ||
	getDescriptor() != that.getDescriptor()) return true;
    VM_Method mine = resolve(false);
    VM_Method theirs = that.resolve(false);
    if (mine == null || theirs == null) return false;
    return mine != theirs;
  }

    
  /**
   * Do this and that definitely refer to the same method?
   */
  public final boolean definitelySame(VM_MethodReference that) {
    if (this == that) return true;
    if (getMemberName() != that.getMemberName() ||
	getDescriptor() != that.getDescriptor()) return false;
    VM_Method mine = resolve(false);
    VM_Method theirs = that.resolve(false);
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
    VM_Class thisClass = (VM_Class)VM_ClassLoader.findOrCreateType(className, classloader);
    if (!thisClass.isResolved()) return null; // can't be found now.
    VM_Method sought = resolve();

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
   * Find the VM_Method that this member reference refers to using
   * the search order specified in JVM spec 5.4.3.3.
   * @return the VM_Method that this method ref resolved to.
   */
  public final VM_Method resolve() {
    if (resolvedMember != null) return resolvedMember;
    // Hasn't been resolved yet. Do it now.
    VM_Class declaringClass = (VM_Class)VM_ClassLoader.findOrCreateType(className, classloader);
    if (VM.VerifyAssertions) VM._assert(declaringClass.isResolved());
    for (VM_Class c = declaringClass; c != null; c = c.getSuperClass()) {
      VM_Method it = c.findDeclaredMethod(memberName, descriptor);
      if (it != null) {
	resolvedMember = it;
	return resolvedMember;
      }
    }
    throw new NoSuchMethodError(this.toString());
  }

  /**
   * Find the VM_Method that this member reference refers to using
   * the search order specified in JVM spec 5.4.3.3.
   * @param canLoad are we allowed to load classes to resolve the method reference?
   * @return the VM_Method that this method ref resolved to or <code>null</code> if it cannot be resolved.
   */
  public final VM_Method resolve(boolean canLoad) {
    if (resolvedMember != null) return resolvedMember;
    VM_Class declaringClass = (VM_Class)VM_ClassLoader.findOrCreateType(className, classloader);
    if (!declaringClass.isResolved()) {
      if (canLoad) {
	try {
	  declaringClass.load();
	  declaringClass.resolve();
	} catch (VM_ResolutionException e) {
	  return null;
	}
      } else {
	return null;
      }
    }
    return resolve();
  }

  /**
   * Find the VM_Method that this member reference refers to using
   * the search order specified in JVM spec 5.4.3.4.
   * @param canLoad are we allowed to load classes to resolve the method reference?
   * @return the VM_Method that this method ref resolved to.
   */
  public final VM_Method resolveInterfaceMethod(boolean canLoad) throws VM_ResolutionException {
    if (resolvedMember != null) return resolvedMember;
    
    // Hasn't been resolved yet. Do it now.
    // If compileTime is true, then we don't raise errors and return
    // null if the method can't be resolved at this time.
    VM_Class declaringClass = (VM_Class)VM_ClassLoader.findOrCreateType(className, classloader);
    if (!declaringClass.isResolved()) {
      if (!canLoad) return null;
      VM_Runtime.initializeClassForDynamicLink(declaringClass);
    }
    if (!declaringClass.isInterface()) {
      if (!canLoad) return null;
      throw new IncompatibleClassChangeError();
    }
    VM_Method it = declaringClass.findDeclaredMethod(memberName, descriptor);
    if (it != null) {
      resolvedMember = it; 
      return resolvedMember;
    }
    VM_Class[] interfaces = declaringClass.getDeclaredInterfaces();
    for (int i=0; i<interfaces.length; i++) {
      it = searchInterfaceMethods(interfaces[i], canLoad);
      if (it != null) {
	resolvedMember = it;
	return resolvedMember;
      }
    }
    if (!canLoad) return null;
    throw new NoSuchMethodError(this.toString());
  }
    
  private final VM_Method searchInterfaceMethods(VM_Class c, boolean canLoad) {
    if (!canLoad && !c.isResolved()) return null;
    VM_Method it = c.findDeclaredMethod(memberName, descriptor);
    if (it != null) return it;
    VM_Class[] interfaces = c.getDeclaredInterfaces();
    for (int i=0; i<interfaces.length; i++) {
      it = searchInterfaceMethods(interfaces[i], canLoad);
      if (it != null) return it;
    }
    return null;
  }
}
