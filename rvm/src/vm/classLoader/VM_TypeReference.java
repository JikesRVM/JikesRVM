/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import java.util.HashMap;

/**
 * A class to represent the reference in a class file to some 
 * type (class, primitive or array).
 * A type reference is uniquely defined by
 * <ul>
 * <li> an initiating class loader
 * <li> a type name
 * </ul>
 * Resolving a VM_TypeReference to a VM_Type can
 * be an expensive operation.  Therefore we cannonicalize
 * VM_TypeReference instances and cache the result of resolution.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_TypeReference {

  /**
   * Used to cannonicalize TypeReferences
   */
  private static HashMap dictionary = new HashMap();

  /**
   * Dictionary of all VM_TypeReference instances.
   */
  private static VM_TypeReference[] types = new VM_TypeReference[2000];

  /**
   * Used to assign ids.  Id 0 is not used.
   */
  private static int nextId = 1; 
  
  /**
   * The initiating class loader
   */
  protected final ClassLoader classloader;

  /**
   * The type name
   */
  protected final VM_Atom name;

  /**
   * Unique id for the member reference
   */
  protected int id;

  /**
   * The VM_Type instance that this type reference resolves to.
   * Null of the reference has not yet been resolved.
   */
  protected VM_Type resolvedType;

  /**
   * Find or create the cannonical VM_TypeReference instance for
   * the given pair.
   * @param cl the classloader (defining/initiating depending on usage)
   * @param tn the name of the type
   */
  public static synchronized VM_TypeReference findOrCreate(ClassLoader cl, VM_Atom tn) {
    VM_TypeReference key = new VM_TypeReference(cl, tn);
    VM_TypeReference val = (VM_TypeReference)dictionary.get(key);
    if (val != null)  return val;
    key.id = nextId++;
    if (key.id == types.length) {
      VM_TypeReference[] tmp = new VM_TypeReference[types.length + 500];
      System.arraycopy(types, 0, tmp, 0, types.length);
      types = tmp;
    }
    types[key.id] = key;
    dictionary.put(key, key);
    return key;
  }

  public static VM_TypeReference getTypeRef(int id) throws VM_PragmaUninterruptible {
    return types[id];
  }

  /**
   * @param cl the classloader
   * @param tn the type name
   */
  protected VM_TypeReference(ClassLoader cl, VM_Atom tn) {
    classloader = cl;
    name = tn;
  }

  /**
   * @return the classloader component of this type reference
   */
  public final ClassLoader getClassLoader() throws VM_PragmaUninterruptible {
    return classloader;
  }
      
  /**
   * @return the type name component of this type reference
   */
  public final VM_Atom getName() throws VM_PragmaUninterruptible {
    return name;
  }

  /**
   * Does 'this' refer to a class?
   */ 
  public final boolean isClass() {
    return name.isClassDescriptor();
  }
      
  /**
   * Does 'this' refer to an array?
   */ 
  public final boolean isArray() {
    return name.isArrayDescriptor();
  }

  /**
   * Does 'this' refer to VM_Word, VM_Address, or VM_Offset
   */
  public final boolean isWordType() {
    return name == VM_Type.WordType.getDescriptor() || 
      name == VM_Type.AddressType.getDescriptor() ||
      name == VM_Type.OffsetType.getDescriptor();
  }

  /**
   * Does 'this' refer to VM_Magic?
   */
  public final boolean isMagicType() {
    return name == VM_Type.MagicType.getDescriptor();
  }
    
  /**
   * Do this and that definitely refer to the different types?
   */
  public final boolean definitelyDifferent(VM_TypeReference that) {
    if (this == that) return false;
    if (name != that.name) return true;
    VM_Type mine = resolve(false);
    VM_Type theirs = that.resolve(false);
    if (mine == null || theirs == null) return false;
    return mine != theirs;
  }

    
  /**
   * Do this and that definitely refer to the same type?
   */
  public final boolean definitelySame(VM_TypeReference that) {
    if (this == that) return true;
    if (name != that.name) return false;
    VM_Type mine = resolve(false);
    VM_Type theirs = that.resolve(false);
    if (mine == null || theirs == null) return false;
    return mine == theirs;
  }

  /**
   * Has the field reference already been resolved into a target method?
   */
  public final boolean isResolved() {
    return resolvedType != null;
  }

  /**
   * For use by VM_Type constructor
   */
  final void setResolvedType(VM_Type it) {
    if (VM.VerifyAssertions) VM._assert(resolvedType == null);
    resolvedType = it;
  }

  /** 
   * @return the VM_Type instance that this references resolves to.
   */
  public final VM_Type resolve(boolean canLoad) {
    if (resolvedType != null) return resolvedType;
    resolvedType = VM_ClassLoader.findOrCreateType(name, classloader);
    return resolvedType;
  }

  /**
   * @return the dynamic linking id to use for this member.
   */
  public final int getId() throws VM_PragmaUninterruptible {
    return id;
  }

  public final int hashCode() {
    return name.hashCode();
  }

  public final boolean equals(Object other) {
    if (other instanceof VM_TypeReference) {
      VM_TypeReference that = (VM_TypeReference)other;
      return name == that.name && classloader.equals(that.classloader);
    } else {
      return false;
    }
  }

  public final String toString() {
    return "< " + classloader + ", "+ name + " >";
  }
}
