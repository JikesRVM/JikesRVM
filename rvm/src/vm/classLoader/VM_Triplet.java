/*
 * (C) Copyright IBM Corp. 2001
 */

/**
 * A triplet of atoms, ie. classDescriptor + memberName + memberDescriptor
 */
class VM_Triplet {
  //-----------//
  // interface //
  //-----------//
   
  VM_Triplet(VM_Atom a, VM_Atom b, VM_Atom c) {
    this.a = a;
    this.b = b;
    this.c = c;
  }
      
  //----------------//
  // implementation //
  //----------------//
   
  private VM_Atom a;
  private VM_Atom b;
  private VM_Atom c;
   
  // Hash VM_Dictionary keys.
  //
  static int dictionaryHash(VM_Triplet key) {
    return VM_Atom.dictionaryHash(key.a) +
      VM_Atom.dictionaryHash(key.b) +
      VM_Atom.dictionaryHash(key.c) ;
  }

  // Compare VM_Dictionary keys.
  // Returned: 0 iff "leftKey" is null
  //           1 iff "leftKey" is to be considered a duplicate of "rightKey"
  //          -1 otherwise
  //
  static int dictionaryCompare(VM_Triplet leftKey, VM_Triplet rightKey) {
    if (leftKey == null)
      return 0;
         
    if (leftKey.a == rightKey.a &&
	leftKey.b == rightKey.b &&
	leftKey.c == rightKey.c )
      return 1;
      
    return -1;
  }
   
  public final String toString() {
    return "{" + a + " " + b + " " + c + "}";
  }
}
