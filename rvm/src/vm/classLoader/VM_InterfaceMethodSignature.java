/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 *  An interface method signature is a pair of atoms: 
 *  interfaceMethodName + interfaceMethodDescriptor.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
class VM_InterfaceMethodSignature {

  private VM_Atom name;
  private VM_Atom descriptor;

  VM_InterfaceMethodSignature(VM_Atom name, VM_Atom descriptor) {
    this.name = name;
    this.descriptor = descriptor;
  }

  VM_Atom getName() {
    return name;
  }

  VM_Atom getDescriptor() {
    return descriptor;
  }

  public final String toString() {
    return "{" + name + " " + descriptor + "}";
  }

  /*
   * Stuff used by dictionary template (generated file VM_InterfaceMethodSignatureDictionary)
   */

  // Hash VM_Dictionary keys.
  //
  static int dictionaryHash(VM_InterfaceMethodSignature key) {
    return VM_Atom.dictionaryHash(key.name) + VM_Atom.dictionaryHash(key.descriptor) ;
  }

  // Compare VM_Dictionary keys.
  // Returned: 0 iff "leftKey" is null
  //           1 iff "leftKey" is to be considered a duplicate of "rightKey"
  //          -1 otherwise
  //
  static int dictionaryCompare(VM_InterfaceMethodSignature leftKey, 
			       VM_InterfaceMethodSignature rightKey) {
    if (leftKey == null) {
      return 0;
    } else if (leftKey.name == rightKey.name && leftKey.descriptor == rightKey.descriptor) {
      return 1;
    } else {
    return -1;
    }
  }
}
