/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$


/**
 * Code that deals with interface methods.
 *  An interface method signature is a pair of atoms: 
 *  interfaceMethodName + interfaceMethodDescriptor.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
class VM_InterfaceMethodSignature implements VM_ObjectLayoutConstants {
  //-----------//
  // interface //
  //-----------//

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

  /**
   * If using embedded IMTs, Get offset of interface method slot in TIB.
   * If using indirect IMTs, Get offset of interface method slot in IMT.
   * 
   * Note that all methods with same name & descriptor map to the same slot.
   * Taken:    interface method signature id, obtained from 
   * "VM_ClassLoader.findOrCreateInterfaceMethodSignatureId"
   * Returned: offset
   * 
   * TODO!! replace the simplistic hash algorithm
   */ 
  static int getOffset(int id) {
    int offset = VM_InterfaceMethodSignatureDictionary.getValue(id);
    if (VM.BuildForEmbeddedIMT) {
      if (offset == UNRESOLVED_INTERFACE_METHOD_OFFSET) {
        offset = (TIB_FIRST_INTERFACE_METHOD_INDEX + id % 
                  IMT_METHOD_SLOTS) << 2;
        VM_InterfaceMethodSignatureDictionary.setValue(id, offset);
      }
    }else {
      if ( offset == UNRESOLVED_INTERFACE_METHOD_OFFSET) {
        offset = (id % IMT_METHOD_SLOTS) << 2;
        VM_InterfaceMethodSignatureDictionary.setValue(id, offset);
      }
    }
    return offset;
  }

  // Get index of interface method in IMT
  //
  static int getIndex(int id) {
    if (VM.BuildForEmbeddedIMT) {
      return (getOffset(id) >> 2 ) - TIB_FIRST_INTERFACE_METHOD_INDEX;
    } else {
      return (getOffset(id) >> 2 );
    }
  }

   //----------------//
   // implementation //
   //----------------//
   
   private VM_Atom name;
   private VM_Atom descriptor;
   
   // Hash VM_Dictionary keys.
   //
   static int dictionaryHash(VM_InterfaceMethodSignature key) {
     return VM_Atom.dictionaryHash(key.name) +
       VM_Atom.dictionaryHash(key.descriptor) ;
   }

   // Compare VM_Dictionary keys.
   // Returned: 0 iff "leftKey" is null
   //           1 iff "leftKey" is to be considered a duplicate of "rightKey"
   //          -1 otherwise
   //
   static int dictionaryCompare(VM_InterfaceMethodSignature leftKey, 
				VM_InterfaceMethodSignature rightKey) {
     if (leftKey == null)
       return 0;
         
     if (leftKey.name == rightKey.name &&
	 leftKey.descriptor == rightKey.descriptor )
       return 1;
      
     return -1;
   }
   
   public final String toString() {
     return "{" + name + " " + descriptor + "}";
   }

  static class Link {
    int       signatureId;
    VM_Method method;
    Link      next;
       
    Link (int sId, VM_Method m, Link n) {
      signatureId = sId;
      method      = m;
      next        = n;
    }

    static boolean isOn (Link l, int sId) {
      if (l == null) return false;
      if (l.signatureId == sId) return true;
      else return isOn(l.next, sId);
    }
  }
}
