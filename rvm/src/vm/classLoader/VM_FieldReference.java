/*
 * (C) Copyright IBM Corp. 2003
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;

/**
 * A class to represent the reference in a class file to a field.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_FieldReference extends VM_MemberReference {

  /** 
   * The VM_Field that this field reference resolved to (null if not yet resolved).
   */
  private VM_Field resolvedMember;

  /**
   * The field's type
   */
  private final VM_Type fieldContentsType;

  /**
   * @param tr a type reference
   * @param mn the field or method name
   * @param d the field or method descriptor
   */
  VM_FieldReference(VM_TypeReference tr, VM_Atom mn, VM_Atom d) {
    super(tr, mn, d);
    fieldContentsType = VM_ClassLoader.findOrCreateType(d, tr.getClassLoader());
  }

  /**
   * @return the type of the field's value
   */
  public final VM_Type getFieldContentsType() throws VM_PragmaUninterruptible {
    return fieldContentsType;
  }

  /**
   * Get size of the field's value, in bytes.
   */ 
  public final int getSize() throws VM_PragmaUninterruptible {
    return fieldContentsType.getStackWords() << 2;
  }

  /**
   * Do this and that definitely refer to the different fields?
   */
  public final boolean definitelyDifferent(VM_FieldReference that) {
    if (this == that) return false;
    if (getName() != that.getName() ||
	getDescriptor() != that.getDescriptor()) return true;
    VM_Field mine = resolve(false);
    VM_Field theirs = that.resolve(false);
    if (mine == null || theirs == null) return false;
    return mine != theirs;
  }

    
  /**
   * Do this and that definitely refer to the same field?
   */
  public final boolean definitelySame(VM_FieldReference that) {
    if (this == that) return true;
    if (getName() != that.getName() ||
	getDescriptor() != that.getDescriptor()) return false;
    VM_Field mine = resolve(false);
    VM_Field theirs = that.resolve(false);
    if (mine == null || theirs == null) return false;
    return mine == theirs;
  }

  /**
   * Has the field reference already been resolved into a target method?
   */
  public final boolean isResolved() {
    return resolvedMember != null;
  }

  /**
   * For use by VM_Field constructor
   */
  final void setResolvedMember(VM_Field it) {
    if (VM.VerifyAssertions) VM._assert(resolvedMember == null);
    resolvedMember = it;
  }

  /**
   * Find the VM_Field that this field reference refers to using
   * the search order specified in JVM spec 5.4.3.2.
   * @return the VM_Field that this method ref resolved to or null if it cannot be resolved.
   */
  public final VM_Field resolve(boolean canLoad) {
    if (resolvedMember != null) return resolvedMember;
    
    // Hasn't been resolved yet. Try to do it now.
    VM_Class declaringClass = (VM_Class)type.resolve(canLoad);
    if (declaringClass == null) return null;
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
   * Find the VM_Field that this field reference refers to using
   * the search order specified in JVM spec 5.4.3.2.
   * @return the VM_Field that this method ref resolved to.
   */
  public final VM_Field resolve() {
    if (resolvedMember != null) return resolvedMember;
    
    // Hasn't been resolved yet. Do it now.
    VM_Class declaringClass = (VM_Class)type.resolve(true);
    if (VM.VerifyAssertions) VM._assert(declaringClass.isResolved());
    for (VM_Class c = declaringClass; c != null; c = c.getSuperClass()) {
      // Look in this class
      VM_Field it = c.findDeclaredField(name, descriptor);
      if (it != null) {
	resolvedMember = it; 
	return resolvedMember;
      }
      // Look at all interfaces directly and indirectly implemented by this class.
      VM_Class[] interfaces = c.getDeclaredInterfaces();
      for (int i=0; i<interfaces.length; i++) {
	it = searchInterfaceFields(interfaces[i]);
	if (it != null) {
	  resolvedMember = it;
	  return resolvedMember;
	}
      }
    }
    throw new NoSuchFieldError(this.toString());
  }

  private final VM_Field searchInterfaceFields(VM_Class c) {
    VM_Field it = c.findDeclaredField(name, descriptor);
    if (it != null) return it;
    VM_Class[] interfaces = c.getDeclaredInterfaces();
    for (int i=0; i<interfaces.length; i++) {
      it = searchInterfaceFields(interfaces[i]);
      if (it != null) return it;
    }
    return null;
  }
}
