/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 *  Extension to the VM_TypeDictionary and VM_AtomDictionary to look up 
 * an entry without having to create an Atom.  This is mostly taken from 
 * VM_AtomDictionary.findOrCreateId(), reorganized with inlining to avoid any
 * object creation.  This is intended to be used by the debugger running
 * under the interpreter it solves two problems with creating a VM_xxx object
 *  (1) the constructors are private:  VM_Atom, VM_Type, ... so the interpreter can't access
 *      them, and we don't want to make them public either
 *  (2) After creation, they will live in the interpreter space but belong to the debugger
 *      This complicates the situation since then some VM_xxx object in the debugger are
 *      not mapped and we have a mixture of mapped and unmapped VM_xxx objects.
 * @author Ton Ngo
 */

class dictionaryExtension 
{
  /**
   * look up the VM_AtomDictionary 
   * @param  keyString  the name to look up
   * @return the VM_Atom if it exists, null otherwise
   */
  static VM_Atom findAsciiAtom(String keyString) {
    // get pointers to the dictionary table
    VM_Atom[]   keys   = VM_AtomDictionary.getKeysPointer();     // dictionary keys
    int[][]     chains = VM_AtomDictionary.getChainsPointer();   // hash table 
    int id = findIdByString(keyString, keys, chains);
    // the following replaces VM_AtomDictionary.getValue(id) until we implement X_getstatic
    VM_Atom[] val = VM_AtomDictionary.getValuesPointer();
    return val[id];
  }

  /**
   * look up the VM_TypeDictionary 
   * @param  keyString  the name to look up
   * @return the id for the VM_Type if it exists, 0 otherwise
   */
  static int findTypeID(String keyString) {
    // get pointers to the dictionary table
    VM_Atom[]   keys   = VM_TypeDictionary.getKeysPointer();     // dictionary keys
    int[][]     chains = VM_TypeDictionary.getChainsPointer();   // hash table 
    int id = findIdByString(keyString, keys, chains);
    return id;
  }

  /**
   * look up the VM_TypeDictionary 
   * @param  keyString  the name to look up
   * @return the VM_Type if it exists, null otherwise
   */
  static VM_Type findType(String keyString) {
    int id = findTypeID(keyString);
    // the following replaces VM_TypeDictionary.getValue(id) until we implement X_getstatic
    VM_Type[] val = VM_TypeDictionary.getValuesPointer();    
    return val[id];
  }

  /**
   * Find a dictionary key using only the ascii string,
   * don't create a VM_Atom object just to hold the string
   * These two methods are inlined, so any changes to these
   * methods will have to be replicated here (not very good 
   * software engineering!):
   * 	  VM_Atom.init(byte[])
   * 	  VM_Atom.escape(byte[])
   * 	  VM_Atom.dictionaryCompare
   * @param  keyString key sought in String format
   * @param  keys  dictionary keys
   * @param  chains hash table
   * @return id for use by getKey(), setValue(), and getValue()
   * 	     0 if key is not present in dictionary
   */
  static int
  findIdByString(String keyString, VM_Atom[] keys, int[][] chains)
  {
    // Compute the hash number and find the chain    
    // (normally this is precomputed and saved in the VM_Atom)
    int    len   = keyString.length();
    byte[] keyByte = new byte[len];
    keyString.getBytes(0, len, keyByte, 0);
    int hash = hashName(keyByte);

    int chainIndex = (hash & 0x7fffffff) % chains.length;
    int[] chain = chains[chainIndex];
    
    // no chain - key not present in dictionary
    if (chain == null) 
      return 0;


    // scan the selected chain to see if there is any match
    for (int i = 0, n = chain.length; i < n; ++i)  {
      int candidateId  = chain[i];
      VM_Atom candidateKey = keys[candidateId];

      if (candidateKey == null)
	// candidateKey is end of chain sentinal (null/zero) - key not present in dictionary
	return 0;

      byte[] candidateVal  = candidateKey.getBytes();
      boolean match = true;         
      if (candidateVal.length != keyByte.length) {
	match = false;	
      } else {
	for (int j = candidateVal.length; --j >= 0; )
	  if (candidateVal[j] != keyByte[j])
            match = false;
      }

      // key present in dictionary
      if (match)
	return candidateId;
    }

    // end of chain - key not present in dictionary
    return 0;
  }
 
  public static int hashName(byte[] keyByte) {
    int hash = 99989;
    for (int i = keyByte.length; --i >= 0; )  {
      byte b = keyByte[i];
      if ((b & 0x80) != 0)  {
	hash = escapeHashCode(keyByte);
	break;
      }
      hash = 99991 * hash + b;
    }
    return hash;
  }

   // Compute hash code from data known to contain a utf8 escape sequence.
   // The only escape we recognize is that for the ascii null character.
   // In java, the ascii null character is represented as a two byte
   // utf8 escape sequence. We store it internally as one byte of zeros.
   // All other escape sequences are left unchanged.
   // (this is replicated from VM_Atom.escape(byte[]) with some code deleted)
   public static int
   escapeHashCode(byte utf8[]) {
     int hash = 0;
     for (int src = 0, dst = 0; src < utf8.length; src += 1)  {
       // ascii char
       if ((utf8[src] & 0x80) == 0)   { 
	 hash = 31 * hash + utf8[src];
	 src += 1;
	 continue; 
       }
       // escape sequence for ascii null
       if (utf8[src] == -64 && utf8[src + 1] == -128)  { 
	 hash = 31 * hash + 0;
	 src += 2;
	 continue; 
       }
       // escape sequence for non-ascii character
       while (src < utf8.length && (utf8[src] & 0x80) != 0)  { 
	 hash = 31 * hash + utf8[src];
	 src += 1;
       }
     }

     return hash;
   }

}
