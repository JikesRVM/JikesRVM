/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;
/** 
 * A utf8-encoded byte string.
 *
 * <p> VM_Atom's of a given value are stored only once in the vm,
 * so they may be compared for equality using the "==" operator.
 *
 * <p> Atoms are used to represent names, descriptors, and string literals
 * appearing in a class's constant pool.
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public final class VM_Atom implements VM_Constants, VM_ClassLoaderConstants {

   /**
    * Find or create an atom.
    * @param str atom value, as string literal whose characters are unicode
    * @return atom
    */
  public static VM_Atom findOrCreateUnicodeAtom(String str) {
    byte[] utf8 = VM_UTF8Convert.toUTF8(str);
    return VM_AtomDictionary.getValue(findOrCreateAtomId(utf8));
  }

  /**
   * Find or create an atom.
   * @param str atom value, as string literal whose characters are from 
   * ascii subset of unicode (not including null)
   * @return atom
   */ 
  public static VM_Atom findOrCreateAsciiAtom(String str) {
    int    len   = str.length();
    byte[] ascii = new byte[len];
    str.getBytes(0, len, ascii, 0);
    return VM_AtomDictionary.getValue(findOrCreateAtomId(ascii));
  }
   
  /**
   * Find or create an atom.
   * @param utf8 atom value, as utf8 encoded bytes
   * @return id, for use by VM_AtomDictionary.getValue()
   */
  public static int findOrCreateAtomId(byte[] utf8) {
    VM_Atom atom = new VM_Atom(utf8);
    return VM_AtomDictionary.findOrCreateId(atom, atom);
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
    return findOrCreateAtom(sig);
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
    return findOrCreateAtom(sig);
  }

  /**
   * Return class name corresponding to "this" class descriptor.
   * this: class descriptor - something like "Ljava/lang/String;"
   * @return class name       - something like "java.lang.String"
   */ 
  public final String classNameFromDescriptor() {
    if (VM.VerifyAssertions) VM._assert(val[0] == 'L'); // !!TODO: should we also handle "array" type descriptors?
    // return new String(val,    1, val.length - 2).replace('/','.');  // preferred (unicode)
    return new String(val, 0, 1, val.length - 2).replace('/','.');  // deprecated (ascii)
  }
   
  /**
   * Return name of class file corresponding to "this" class descriptor.
   * this: class descriptor - something like "Ljava/lang/String;"
   * @return class file name  - something like "java/lang/String.class"
   */ 
  public final String classFileNameFromDescriptor() {
    if (VM.VerifyAssertions && val[0] != 'L') VM._assert(false, toString());
    // return new String(val,    1, val.length - 2) + ".class"; // preferred (unicode)
    return new String(val, 0, 1, val.length - 2) + ".class"; // deprecated (ascii)
  }

  //----------------//
  // classification //
  //----------------//
   
  /**
   * Is "this" atom a reserved member name?
   * Note: Sun has reserved all member names starting with '<' for future use.
   *       At present, only <init> and <clinit> are used.
   */ 
  public final boolean isReservedMemberName() {
    return val[0] == '<';
  }

  /**
   * Is "this" atom a class descriptor?
   */ 
  public final boolean isClassDescriptor() {
    return val[0] == 'L';
  }
      
  /**
   * Is "this" atom an array descriptor?
   */ 
  public final boolean isArrayDescriptor() {
    return val[0] == '[';
  }
      
  /**
   * Is "this" atom a method descriptor?
   */ 
  public final boolean isMethodDescriptor() {
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
  public final VM_Type parseForReturnType(ClassLoader classloader) {
    if (VM.VerifyAssertions) VM._assert(val[0] == '(');

    int i = 0;
    while (val[i++] != ')');
    switch (val[i])
      {
      case VoidTypeCode:    return VM_Type.VoidType;
      case BooleanTypeCode: return VM_Type.BooleanType;
      case ByteTypeCode:    return VM_Type.ByteType;
      case ShortTypeCode:   return VM_Type.ShortType;
      case IntTypeCode:     return VM_Type.IntType;
      case LongTypeCode:    return VM_Type.LongType;
      case FloatTypeCode:   return VM_Type.FloatType;
      case DoubleTypeCode:  return VM_Type.DoubleType;
      case CharTypeCode:    return VM_Type.CharType;
      case ClassTypeCode:   // fall through
      case ArrayTypeCode:   return VM_ClassLoader.findOrCreateType(findOrCreateAtom(val, i, val.length - i), classloader);
      default:              if (VM.VerifyAssertions) VM._assert(NOT_REACHED); return null;
      }
  }
      
  /**
   * Parse "this" method descriptor to obtain descriptions of method's 
   * parameters.
   * this: method descriptor     - something like "(III)V"
   * @return parameter descriptions
   */ 
  public final VM_Type[] parseForParameterTypes(ClassLoader classloader) {
    if (VM.VerifyAssertions) VM._assert(val[0] == '(');

    VM_TypeVector sigs = new VM_TypeVector();
    for (int i = 1;;)
      switch (val[i++])
	{
	case VoidTypeCode:    sigs.addElement(VM_Type.VoidType);     continue;
	case BooleanTypeCode: sigs.addElement(VM_Type.BooleanType);  continue;
	case ByteTypeCode:    sigs.addElement(VM_Type.ByteType);     continue;
	case ShortTypeCode:   sigs.addElement(VM_Type.ShortType);    continue;
	case IntTypeCode:     sigs.addElement(VM_Type.IntType);      continue;
	case LongTypeCode:    sigs.addElement(VM_Type.LongType);     continue;
	case FloatTypeCode:   sigs.addElement(VM_Type.FloatType);    continue;
	case DoubleTypeCode:  sigs.addElement(VM_Type.DoubleType);   continue;
	case CharTypeCode:    sigs.addElement(VM_Type.CharType);     continue;
	case ClassTypeCode: {
	  int off = i - 1;
	  while (val[i++] != ';');
	  sigs.addElement(VM_ClassLoader.findOrCreateType(findOrCreateAtom(val, off, i - off), classloader));
	  continue;
	}
	case ArrayTypeCode: {
	  int off = i - 1;
	  while (val[i] == ArrayTypeCode) ++i;
	  if (val[i++] == ClassTypeCode) while (val[i++] != ';');
	  sigs.addElement(VM_ClassLoader.findOrCreateType(findOrCreateAtom(val, off, i - off), classloader));
	  continue;
	}
	case (byte)')': // end of parameter list
	  return sigs.finish();
            
	default: if (VM.VerifyAssertions) VM._assert(NOT_REACHED);
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
   * Parse "this" array descriptor to obtain descriptor for array's element 
   * type.
   * this: array descriptor         - something like "[I"
   * @return array element descriptor - something like "I"
   */
  public final VM_Atom parseForArrayElementDescriptor() {
    if (VM.VerifyAssertions) VM._assert(val[0] == '[');
    return findOrCreateAtom(val, 1, val.length - 1);
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
   * Access internal representation.
   * (Note: this is intended for the debugger only)
   */ 
  public final byte[] getBytes() {
    return val;
  }

  //----------------//
  // implementation //
  //----------------//

  private byte val[];  
  private int  hash;  
   
  /**
   * To guarantee uniqueness, only the VM_Atom class may construct 
   * VM_Atom instances.
   * All VM_Atom creation should be performed by calling 
   * "VM_Atom.findOrCreate" methods.
   */ 
  private VM_Atom() {}
   
  /**
   * Create atom from given utf8 sequence.
   */ 
  private VM_Atom(byte utf8[]) {
    int hash = 99989;
    for (int i = utf8.length; --i >= 0; )
      hash = 99991 * hash + utf8[i];
          
    this.val  = utf8;
    this.hash = hash;
  }

  private static VM_Atom findOrCreateAtom(byte utf8[]) {
    return VM_AtomDictionary.getValue(findOrCreateAtomId(utf8));
  }

  private static VM_Atom findOrCreateAtom(byte utf8[], int off, int len) {
    byte val[] = new byte[len];
    for (int i = 0; i < len; ++i)
      val[i] = utf8[off++];
    return VM_AtomDictionary.getValue(findOrCreateAtomId(val));
  }

  public final int hashCode() {
    return hash;
  }
   
  /**
   * Hash VM_Dictionary keys.
   */ 
  static int dictionaryHash(VM_Atom atom) {
    return atom.hash;
  }

  /**
   * Compare VM_Dictionary keys.
   * @return 0 iff "leftKey" is null
   *           1 iff "leftKey" is to be considered a duplicate of "rightKey"
   *          -1 otherwise
   */
  static int dictionaryCompare(VM_Atom left, VM_Atom right) {
    if (left == null)
      return 0;
         
    if (left.val.length != right.val.length)
      return -1;

    byte[] leftVal  = left.val;
    byte[] rightVal = right.val;
    for (int i = leftVal.length; --i >= 0; )
      if (leftVal[i] != rightVal[i])
	return -1;

    return 1;
  }  
}
