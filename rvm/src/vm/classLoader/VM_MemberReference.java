/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.classloader;

/**
 * A class to represent the reference in a class file to some 
 * member (field or method). A reference consistes of the three 
 * components <className, memberName, memberDescriptor>.
 * In the very near future, a member reference will also contain 
 * a class loader component.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public class VM_MemberReference {

  /**
   * The class name
   */
  private final VM_Atom className;

  /**
   * The member name
   */
  private final VM_Atom memberName;

  /**
   * The descriptor
   */
  private final VM_Atom descriptor;

  /**
   * @param cn the class name
   * @param mn the field or method name
   * @param d the field or method descriptor
   */
  public VM_MemberReference(VM_Atom cn, VM_Atom mn, VM_Atom d) {
    className = cn;
    memberName = mn;
    descriptor = d;
  }
      
  public final VM_Atom getClassName() {
    return className;
  }

  public final VM_Atom getMemberName() {
    return memberName;
  }

  public final VM_Atom getDescriptor() {
    return descriptor;
  }

  public final int hashCode() {
    return dictionaryHash(this);
  }

  public final boolean equals(Object that) {
    return (that instanceof VM_MemberReference) &&
      dictionaryCompare(this, (VM_MemberReference)that) == 1;
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
         
    if (leftKey.className == rightKey.className &&
	leftKey.memberName == rightKey.memberName &&
	leftKey.descriptor == rightKey.descriptor )
      return 1;
      
    return -1;
  }
   
  public final String toString() {
    return className + "." + memberName + " " + descriptor;
  }
}
