/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
import java.util.HashMap;

/** 
 * An  utf8-encoded byte string.
 *
 * VM_Atom's are interned (cannonicalized) 
 * so they may be compared for equality using the "==" operator.
 *
 * VM_Atoms are used to represent names, descriptors, and string literals
 * appearing in a class's constant pool.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public final class VM_Atom implements VM_ClassLoaderConstants {

  /**
   * Used to cannonicalize VM_Atoms
   */
  private static HashMap dictionary = new HashMap();

  /**
   * Dictionary of all VM_Atom instances.
   */
  private static VM_Atom[] atoms = new VM_Atom[16000];

  /**
   * Used to assign ids. Don't use id 0 to allow clients to use id 0 as a 'null'.
   */
  private static int nextId = 1; 
  
  /**
   * The utf8 value this atom represents
   */
  private final byte val[];  

  /**
   * Cached hash code for this atom.
   */
  private final int  hash;  
   
  /**
   * The id of this atom
   */
  private int id;

  /**
   *@return the id of this atom.
   */
  final int getId() { return id; }

  /**
   * Find or create an atom.
   * @param str atom value, as string literal whose characters are unicode
   * @return atom
   */
  public static VM_Atom findOrCreateUnicodeAtom(String str) {
    byte[] utf8 = VM_UTF8Convert.toUTF8(str);
    return findOrCreate(utf8);
  }

  /**
   * Find or create an atom.
   * @param str atom value, as string literal whose characters are from 
   *            ascii subset of unicode (not including null)
   * @return atom
   */ 
  public static VM_Atom findOrCreateAsciiAtom(String str) {
    int    len   = str.length();
    byte[] ascii = new byte[len];
    str.getBytes(0, len, ascii, 0);
    return findOrCreate(ascii);
  }
   
  /**
   * Find or create an atom.
   * @param utf8 atom value, as utf8 encoded bytes
   * @return atom
   */
  public static VM_Atom findOrCreateUtf8Atom(byte[] utf8) {
    return findOrCreate(utf8);
  }

  /**
   * @param id the id of an Atom
   * @return the VM_Atom whose id was given
   */
  public static VM_Atom getAtom(int id) throws VM_PragmaUninterruptible {
    return atoms[id];
  }

  private static VM_Atom findOrCreate(byte utf8[], int off, int len) {
    byte val[] = new byte[len];
    for (int i = 0; i < len; ++i)
      val[i] = utf8[off++];
    return findOrCreate(val);
  }

  private static synchronized VM_Atom findOrCreate(byte[] bytes) {
    VM_Atom key = new VM_Atom(bytes);
    VM_Atom val = (VM_Atom)dictionary.get(key);
    if (val != null)  return val;
    key.id = nextId++;
    if (key.id == atoms.length) {
      VM_Atom[] tmp = new VM_Atom[atoms.length+1000];
      System.arraycopy(atoms, 0, tmp, 0, atoms.length);
      atoms = tmp;
    }
    atoms[key.id] = key;
    dictionary.put(key, key);
    return key;
  }

  //-------------//
  // conversions //
  //-------------//
   
  /**
   * Return printable representation of "this" atom.
   * Does not correctly handle UTF8 translation.
   */ 
  public final String toString() {
    return new String(val, 0);
  }

  /**
   * Return printable representation of "this" atom.
   */ 
  public final String toUnicodeString() throws java.io.UTFDataFormatException { 
    return VM_UTF8Convert.fromUTF8(val);
  }

  /**
   * Return array descriptor corresponding to "this" array-element descriptor.
   * this: array-element descriptor - something like "I" or "Ljava/lang/Object;"
   * @return array descriptor - something like "[I" or "[Ljava/lang/Object;"
   */  
  final VM_Atom arrayDescriptorFromElementDescriptor() {
    byte sig[] = new byte[1 + val.length];
    sig[0] = (byte)'[';
    for (int i = 0, n = val.length; i < n; ++i)
      sig[i + 1] = val[i];
    return findOrCreate(sig);
  }

  /**
   * Return class descriptor corresponding to "this" class name.
   * this: class name       - something like "java/lang/Object"
   * @return class descriptor - something like "Ljava/lang/Object;"
   */ 
  public final VM_Atom descriptorFromClassName() {
    if (val[0] == '[') return this;
    byte sig[] = new byte[1 + val.length + 1];
    sig[0] = (byte)'L';
    for (int i = 0, n = val.length; i < n; ++i)
      sig[i + 1] = val[i];
    sig[sig.length - 1] = (byte)';';
    return findOrCreate(sig);
  }

  /**
   * Return class name corresponding to "this" class descriptor.
   * this: class descriptor - something like "Ljava/lang/String;"
   * @return class name       - something like "java.lang.String"
   */ 
  public final String classNameFromDescriptor() {
    if (VM.VerifyAssertions) VM._assert(val[0] == 'L' && val[val.length-1] == ';'); 
    return new String(val, 0, 1, val.length - 2).replace('/','.'); 
  }
   
  /**
   * Return name of class file corresponding to "this" class descriptor.
   * this: class descriptor - something like "Ljava/lang/String;"
   * @return class file name  - something like "java/lang/String.class"
   */ 
  public final String classFileNameFromDescriptor() {
    if (VM.VerifyAssertions) VM._assert(val[0] == 'L' && val[val.length-1] == ';'); 
    return new String(val, 0, 1, val.length - 2) + ".class";
  }

  //----------------//
  // classification //
  //----------------//
   
  /**
   * Is "this" atom a reserved member name?
   * Note: Sun has reserved all member names starting with '<' for future use.
   *       At present, only <init> and <clinit> are used.
   */ 
  public final boolean isReservedMemberName() throws VM_PragmaUninterruptible {
    return val[0] == '<';
  }

  /**
   * Is "this" atom a class descriptor?
   */ 
  public final boolean isClassDescriptor() throws VM_PragmaUninterruptible {
    return val[0] == 'L';
  }
      
  /**
   * Is "this" atom an array descriptor?
   */ 
  public final boolean isArrayDescriptor() throws VM_PragmaUninterruptible {
    return val[0] == '[';
  }
      
  /**
   * Is "this" atom a method descriptor?
   */ 
  public final boolean isMethodDescriptor() throws VM_PragmaUninterruptible {
    return val[0] == '(';
  }
      
  //--------------------//
  // descriptor parsing //
  //--------------------//
   
  /**
   * Parse "this" method descriptor to obtain description of method's 
   * return type.
   * this: method descriptor - something like "(III)V"
   * @return type description
   */
  public final VM_TypeReference parseForReturnType(ClassLoader cl) {
    if (VM.VerifyAssertions) VM._assert(val[0] == '(');

    int i = 0;
    while (val[i++] != ')');
    switch (val[i])
      {
      case VoidTypeCode:    return VM_TypeReference.Void;
      case BooleanTypeCode: return VM_TypeReference.Boolean;
      case ByteTypeCode:    return VM_TypeReference.Byte;
      case ShortTypeCode:   return VM_TypeReference.Short;
      case IntTypeCode:     return VM_TypeReference.Int;
      case LongTypeCode:    return VM_TypeReference.Long;
      case FloatTypeCode:   return VM_TypeReference.Float;
      case DoubleTypeCode:  return VM_TypeReference.Double;
      case CharTypeCode:    return VM_TypeReference.Char;
      case ClassTypeCode:   // fall through
      case ArrayTypeCode:   return VM_TypeReference.findOrCreate(cl, findOrCreate(val, i, val.length - i));
      default:              if (VM.VerifyAssertions) VM._assert(false); return null;
      }
  }
      
  /**
   * Parse "this" method descriptor to obtain descriptions of method's 
   * parameters.
   * this: method descriptor     - something like "(III)V"
   * @return parameter descriptions
   */ 
  public final VM_TypeReference[] parseForParameterTypes(ClassLoader cl) {
    if (VM.VerifyAssertions) VM._assert(val[0] == '(');

    VM_TypeReferenceVector sigs = new VM_TypeReferenceVector();
    int i = 1;
    while (true) {
      switch (val[i++])	{
      case VoidTypeCode:    sigs.addElement(VM_TypeReference.Void);     continue;
      case BooleanTypeCode: sigs.addElement(VM_TypeReference.Boolean);  continue;
      case ByteTypeCode:    sigs.addElement(VM_TypeReference.Byte);     continue;
      case ShortTypeCode:   sigs.addElement(VM_TypeReference.Short);    continue;
      case IntTypeCode:     sigs.addElement(VM_TypeReference.Int);      continue;
      case LongTypeCode:    sigs.addElement(VM_TypeReference.Long);     continue;
      case FloatTypeCode:   sigs.addElement(VM_TypeReference.Float);    continue;
      case DoubleTypeCode:  sigs.addElement(VM_TypeReference.Double);   continue;
      case CharTypeCode:    sigs.addElement(VM_TypeReference.Char);     continue;
      case ClassTypeCode: {
	int off = i - 1;
	while (val[i++] != ';');
	sigs.addElement(VM_TypeReference.findOrCreate(cl, findOrCreate(val, off, i - off)));
	continue;
      }
      case ArrayTypeCode: {
	int off = i - 1;
	while (val[i] == ArrayTypeCode) ++i;
	if (val[i++] == ClassTypeCode) while (val[i++] != ';');
	sigs.addElement(VM_TypeReference.findOrCreate(cl, findOrCreate(val, off, i - off)));
	continue;
      }
      case (byte)')': // end of parameter list
	return sigs.finish();
            
      default: if (VM.VerifyAssertions) VM._assert(false);
      }
    }
  }

  /**
   * Parse "this" field, parameter, or return descriptor to obtain its 
   * type code.
   * this: descriptor - something like "Ljava/lang/String;" or "[I" or "I"
   * @return type code  - something like ObjectTypeCode, ArrayTypeCode, or 
   * IntTypeCode
   * 
   * The type code will be one of the following constants:
   * 
   * <pre>
   *               constant         value
   *           ----------------     -----
   *            ClassTypeCode        'L'
   *            ArrayTypeCode        '['
   *            VoidTypeCode         'V'
   *            BooleanTypeCode      'Z'
   *            ByteTypeCode         'B'
   *            ShortTypeCode        'S'
   *            IntTypeCode          'I'
   *            LongTypeCode         'J'
   *            FloatTypeCode        'F'
   *            DoubleTypeCode       'D'
   *            CharTypeCode         'C'
   * </pre>
   */
  public final byte parseForTypeCode() {
    return val[0];
  }
  
  /**
   * Parse "this" array descriptor to obtain number of dimensions in 
   * corresponding array type.
   * this: descriptor     - something like "[Ljava/lang/String;" or "[[I"
   * @return dimensionality - something like "1" or "2"
   */ 
  public final int parseForArrayDimensionality() {
    if (VM.VerifyAssertions) VM._assert(val[0] == '[');
    for (int i = 0; ; ++i)
      if (val[i] != '[')
	return i;
  }

  /**
   * Parse "this" array descriptor to obtain type code for its element type.
   * this: descriptor - something like "[Ljava/lang/String;" or "[I"
   * @return type code  - something like VM.ObjectTypeCode or VM.IntTypeCode
   * The type code will be one of the constants appearing in the table above.
   */ 
  public final byte parseForArrayElementTypeCode() throws VM_PragmaUninterruptible {
    if (VM.VerifyAssertions) VM._assert(val[0] == '[');
    return val[1];
  }

  /**
   * Return the innermost element type reference for an array
   */
  public final VM_Atom parseForInnermostArrayElementDescriptor() {
    if (VM.VerifyAssertions) VM._assert(val[0] == '[');
    int i=0; 
    while (val[i] == '[') i++;
    return findOrCreate(val, i, val.length -i);
  }

  /**
   * Parse "this" array descriptor to obtain descriptor for array's element 
   * type.
   * this: array descriptor         - something like "[I"
   * @return array element descriptor - something like "I"
   */
  public final VM_Atom parseForArrayElementDescriptor() {
    if (VM.VerifyAssertions) VM._assert(val[0] == '[');
    return findOrCreate(val, 1, val.length - 1);
  }

  private static final byte[][] systemClasses = { "Ljava/".getBytes(), "Lcom/ibm/JikesRVM/".getBytes()};

  /**
   * @return true if this is a class descriptor of a system class
   * (ie a class that must be loaded by the system class loader
   */
  public final boolean isSystemClassDescriptor() {
  outer:
    for (int i=0; i<systemClasses.length; i++) {
      byte[] test = systemClasses[i];
      for (int j=0; j<test.length; j++) {
	if (val[j] != test[j]) continue outer;
      }
      return true;
    }
    return false;
  }
    

  //-----------//
  // debugging //
  //-----------//
   
  public final void sysWrite() throws VM_PragmaUninterruptible {
    for (int i = 0, n = val.length; i < n; ++i)
      VM.sysWrite((char)val[i]);
  }

  public final int length() throws VM_PragmaUninterruptible {
    return val.length;
  }

  /**
   * Create atom from given utf8 sequence.
   */ 
  private VM_Atom(byte utf8[]) {
    int tmp = 99989;
    for (int i = utf8.length; --i >= 0; )
      tmp = 99991 * tmp + utf8[i];
    this.val  = utf8;
    this.hash = tmp;
  }

  public final int hashCode() {
    return hash;
  }

  public final boolean equals(Object other) {
    if (this == other) return true;
    if (other instanceof VM_Atom) {
      VM_Atom that = (VM_Atom)other;
      if (hash != that.hash) return false;
      if (val.length != that.val.length) return false;
      for (int i=0; i<val.length; i++) {
	if (val[i] != that.val[i]) return false;
      }
      return true;
    } else {
      return false;
    }
  }
}
