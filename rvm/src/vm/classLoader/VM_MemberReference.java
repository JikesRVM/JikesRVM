/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.VM;
import java.util.HashMap;

/**
 * A class to represent the reference in a class file to some 
 * member (field or method). 
 * A member reference is uniquely defined by
 * <ul>
 * <li> an initiating class loader
 * <li> a class name
 * <li> a method name
 * <li> a descriptor
 * </ul>
 * Resolving a VM_MemberReference to a VM_Member can
 * be an expensive operation.  Therefore we cannonicalize
 * VM_MemberReference instances and cache the result of resolution.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public abstract class VM_MemberReference {

  /**
   * Used to cannonicalize memberReferences
   */
  private static HashMap dictionary = new HashMap();

  /**
   * Dictionary of all VM_MemberReference instances.
   */
  private static VM_MemberReference[] members = new VM_MemberReference[16000];

  /**
   * Used to assign ids.  Id 0 is not used to support usage of member reference id's in JNI.
   */
  private static int nextId = 1; 
  
  /**
   * The initiating class loader
   */
  protected final ClassLoader classloader;

  /**
   * The class name
   */
  protected final VM_Atom className;

  /**
   * The member name
   */
  protected final VM_Atom memberName;

  /**
   * The descriptor
   */
  protected final VM_Atom descriptor;

  /**
   * Unique id for the member reference
   */
  protected int id;

  /**
   * Find or create the cannonical VM_MemberReference instance for
   * the given tuple.
   * @param cl the classloader (defining/initiating depending on usage)
   * @param cn the name of the class
   * @param mn the name of the member
   * @param md the descriptor of the member
   */
  public static synchronized VM_MemberReference findOrCreate(ClassLoader cl, VM_Atom cn, VM_Atom mn, VM_Atom md) {
    VM_MemberReference key;
    if (md.isMethodDescriptor()) {
      if (cn.isArrayDescriptor()) {
	cn = VM_Type.JavaLangObjectType.getDescriptor();
      }
      key = new VM_MethodReference(cl, cn, mn, md);
    } else {
      key = new VM_FieldReference(cl, cn, mn, md);
    }
    VM_MemberReference val = (VM_MemberReference)dictionary.get(key);
    if (val != null)  return val;
    key.id = nextId++;
    VM_TableBasedDynamicLinker.ensureCapacity(key.id);
    if (key.id == members.length) {
      VM_MemberReference[] tmp = new VM_MemberReference[members.length*2];
      System.arraycopy(members, 0, tmp, 0, members.length);
      members = tmp;
    }
    members[key.id] = key;
    dictionary.put(key, key);
    return key;
  }

  public static synchronized VM_MemberReference getMemberRef(int id) {
    return members[id];
  }


  /**
   * @param cl the classloader
   * @param cn the class name
   * @param mn the field or method name
   * @param d the field or method descriptor
   */
  protected VM_MemberReference(ClassLoader cl, VM_Atom cn, VM_Atom mn, VM_Atom d) {
    classloader = cl;
    className = cn;
    memberName = mn;
    descriptor = d;
  }

  /**
   * @return the classloader component of this member reference
   */
  public final ClassLoader getClassLoader() {
    return classloader;
  }
      
  /**
   * @return the class name component of this member reference
   */
  public final VM_Atom getClassName() {
    return className;
  }

  /** 
   * Temporary migration code until we switch to type refs.
   */
  public final VM_Class getDeclaringClass() {
    return (VM_Class)VM_ClassLoader.findOrCreateType(className, classloader);
  }

  /**
   * @return the member name component of this member reference
   */
  public final VM_Atom getMemberName() {
    return memberName;
  }

  /**
   * @return the descriptor component of this member reference
   */
  public final VM_Atom getDescriptor() {
    return descriptor;
  }

  /**
   * @return the dynamic linking id to use for this member.
   */
  public final int getId() {
    return id;
  }

  /**
   * Is this member reference to a field?
   */
  public final boolean isFieldReference() {
    return this instanceof VM_FieldReference;
  }

  /**
   * Is this member reference to a method?
   */
  public final boolean isMethodReference() {
    return this instanceof VM_MethodReference;
  }

  /**
   * @return this cast to a VM_FieldReference
   */
  public final VM_FieldReference asFieldReference() {
    return (VM_FieldReference)this;
  }

  /**
   * @return this cast to a VM_MethodReference
   */
  public final VM_MethodReference asMethodReference() {
    return (VM_MethodReference)this;
  }

  /**
   * Is dynamic linking code required to access "this" member when 
   * referenced from "that" method?
   */ 
  public final boolean needsDynamicLink(VM_Method that) {
    VM_Class thisClass = (VM_Class)VM_ClassLoader.findOrCreateType(className, classloader);
    
    if (thisClass.isInitialized()) {
      // No dynamic linking code is required to access this member
      // because its size and offset are known and its class's static 
      // initializer has already run.
      return false;
    }
        
    if (isFieldReference() && thisClass.isResolved() && 
	thisClass.getClassInitializerMethod() == null) {
      // No dynamic linking code is required to access this field
      // because its size and offset is known and its class has no static
      // initializer, therefore its value need not be specially initialized
      // (its default value of zero or null is sufficient).
      return false;
    }
        
    if (VM.writingBootImage && thisClass.isInBootImage()) {
      // Loads, stores, and calls within boot image are compiled without dynamic
      // linking code because all boot image classes are explicitly 
      // loaded/resolved/compiled and have had their static initializers 
      // run by the boot image writer.
      if (!thisClass.isResolved()) VM.sysWrite("unresolved: \"" + this + "\" referenced from \"" + that + "\"\n");
      if (VM.VerifyAssertions) VM._assert(thisClass.isResolved());
      return false;
    }

    if (thisClass == that.getDeclaringClass()) {
      // Intra-class references don't need to be compiled with dynamic linking
      // because they execute *after* class has been loaded/resolved/compiled.
      return false;
    }
  
    // This member needs size and offset to be computed, or its class's static
    // initializer needs to be run when the member is first "touched", so
    // dynamic linking code is required to access the member.
    return true;
  }

  public final int hashCode() {
    return dictionaryHash(this);
  }

  public final boolean equals(Object that) {
    if (that instanceof VM_MemberReference) {
      return dictionaryCompare(this, (VM_MemberReference)that) == 1;
    } else {
      return false;
    }
  }

  /**
   * Hash VM_Dictionary keys.
   */ 
  public static int dictionaryHash(VM_MemberReference key) {
    return VM_Atom.dictionaryHash(key.className) +
      VM_Atom.dictionaryHash(key.memberName) +
      VM_Atom.dictionaryHash(key.descriptor) ;
  }

  /**
   * Compare VM_Dictionary keys.
   * @return 0 iff "leftKey" is null
   *         1 iff "leftKey" is to be considered a duplicate of "rightKey"
   *         -1 otherwise
   */
  public static int dictionaryCompare(VM_MemberReference leftKey, VM_MemberReference rightKey) {
    if (leftKey == null)
      return 0;
         
    if (leftKey.classloader.equals(rightKey.classloader) && 
	leftKey.className == rightKey.className &&
	leftKey.memberName == rightKey.memberName &&
	leftKey.descriptor == rightKey.descriptor )
      return 1;
      
    return -1;
  }
   
  public final String toString() {
    return "<" + classloader + ", "+ className + ", " + memberName + ", " + descriptor + ">";
  }
}
