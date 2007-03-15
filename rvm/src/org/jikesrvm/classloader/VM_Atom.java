/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001, 2003, 2005
 */
package org.jikesrvm.classloader;

import org.jikesrvm.*;
import org.jikesrvm.util.*;

import org.vmmagic.pragma.*;

/** 
 * An  utf8-encoded byte string.
 *
 * VM_Atom's are interned (canonicalized) 
 * so they may be compared for equality using the "==" operator.
 *
 * VM_Atoms are used to represent names, descriptors, and string literals
 * appearing in a class's constant pool.
 *
 * There is almost always a zero-length VM_Atom, since any class which
 * contains statements like:
 *          return "";
 * will have one in its constant pool.
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 * @modified  Steven Augart
 */
public final class VM_Atom implements VM_ClassLoaderConstants {

  /**
   * Used to canonicalize VM_Atoms: Key => VM_Atom
   */
  private static final VM_HashMap<Key,VM_Atom> dictionary = 
    new VM_HashMap<Key,VM_Atom>();

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
  private final byte[] val;

  /**
   * Cached hash code for this atom.
   */
  private final int hash;  
   
  /**
   * The id of this atom
   */
  private final int id;

  /**
   *@return the id of this atom.
   */
  int getId() { return id; }

  /**
   * Find or create an atom.
   * @param str atom value, as string literal whose characters are unicode
   * @return atom
   */
  public static VM_Atom findOrCreateUnicodeAtom(String str) {
    byte[] utf8 = VM_UTF8Convert.toUTF8(str);
    return findOrCreate(utf8, true);
  }

  /**
   * Find an atom.
   * @param str atom value, as string literal whose characters are unicode
   * @return atom or null if it doesn't already exist
   */
  public static VM_Atom findUnicodeAtom(String str) {
    byte[] utf8 = VM_UTF8Convert.toUTF8(str);
    return findOrCreate(utf8, false);
  }

  /**
   * Find or create an atom.
   * @param str atom value, as string literal whose characters are from 
   *            ascii subset of unicode (not including null)
   * @return atom
   */ 
  public static VM_Atom findOrCreateAsciiAtom(String str) {
    return findOrCreate(VM_StringUtilities.stringToBytes(str), true);
  }
   
  /**
   * Find an atom.
   * @param str atom value, as string literal whose characters are from 
   *            ascii subset of unicode (not including null)
   * @return atom or null if it doesn't already exist
   */ 
  public static VM_Atom findAsciiAtom(String str) {
    return findOrCreate(VM_StringUtilities.stringToBytes(str), false);
  }
   
  /**
   * Find or create an atom.
   * @param utf8 atom value, as utf8 encoded bytes
   * @return atom
   */
  public static VM_Atom findOrCreateUtf8Atom(byte[] utf8) {
    return findOrCreate(utf8, true);
  }

  /**
   * Find an atom.
   * @param utf8 atom value, as utf8 encoded bytes
   * @return atom or null it it doesn't already exisit
   */
  public static VM_Atom findUtf8Atom(byte[] utf8) {
    return findOrCreate(utf8, false);
  }

  /**
   * @param id the id of an Atom
   * @return the VM_Atom whose id was given
   */
  @Uninterruptible
  public static VM_Atom getAtom(int id) { 
    return atoms[id];
  }

  private static VM_Atom findOrCreate(byte[] utf8, int off, int len) {
    byte[] val = new byte[len];
    for (int i = 0; i < len; ++i)
      val[i] = utf8[off++];
    return findOrCreate(val, true);
  }

  /** This is the findOrCreate() method through which all VM_Atoms are
   * ultimately created.   The constructor for VM_Atom is a private method, so
   * someone has to call one of the public findOrCreate() methods to get a new
   * one.  And they all feed through here.  */
  private static synchronized VM_Atom findOrCreate(byte[] bytes, boolean create) {
    Key key = new Key(bytes);
    VM_Atom val = dictionary.get(key);
    if (val != null || !create)  return val;
    val = new VM_Atom(key, nextId++);
    if (val.id == atoms.length) {
      VM_Atom[] tmp = new VM_Atom[atoms.length+1000];
      System.arraycopy(atoms, 0, tmp, 0, atoms.length);
      atoms = tmp;
    }
    atoms[val.id] = val;
    dictionary.put(key, val);
    return val;
  }

  //-------------//
  // conversions //
  //-------------//
   
  /**
   * Return printable representation of "this" atom.
   * Does not correctly handle UTF8 translation.
   */ 
  public String toString() {
    return VM_StringUtilities.asciiBytesToString(val);
  }

  /** Get at a string-like representation without doing any heap allocation.
   * Hideous but necessary.  We will use it in the PrintContainer class. */
  @Uninterruptible
  public byte[] toByteArray() { 
    return val;
  }

  /**
   * Return printable representation of "this" atom.
   */ 
  public String toUnicodeString() 
    throws java.io.UTFDataFormatException 
  { 
    return VM_UTF8Convert.fromUTF8(val);
  }

  /**
   * Return array descriptor corresponding to "this" array-element descriptor.
   * this: array-element descriptor - something like "I" or "Ljava/lang/Object;"
   * @return array descriptor - something like "[I" or "[Ljava/lang/Object;"
   */
  VM_Atom arrayDescriptorFromElementDescriptor() {
    if (VM.VerifyAssertions)
      VM._assert(val.length > 0);
    byte[] sig = new byte[1 + val.length];
    sig[0] = (byte)'[';
    for (int i = 0, n = val.length; i < n; ++i)
      sig[i + 1] = val[i];
    return findOrCreate(sig, true);
  }

  /**
   * Return class descriptor corresponding to "this" class name.
   * this: class name       - something like "java/lang/Object"
   * @return class descriptor - something like "Ljava/lang/Object;"
   */ 
  public VM_Atom descriptorFromClassName() {
    if (VM.VerifyAssertions)
      VM._assert(val.length > 0);
    if (val[0] == '[') return this;
    byte[] sig = new byte[1 + val.length + 1];
    sig[0] = (byte)'L';
    for (int i = 0, n = val.length; i < n; ++i)
      sig[i + 1] = val[i];
    sig[sig.length - 1] = (byte)';';
    return findOrCreate(sig, true);
  }

  /**
   * Return class name corresponding to "this" class descriptor.
   * this: class descriptor - something like "Ljava/lang/String;"
   * @return class name       - something like "java.lang.String"
   */ 
  public String classNameFromDescriptor() {
    if (VM.VerifyAssertions){
      VM._assert(val.length > 0);
      VM._assert(val[0] == 'L' && val[val.length-1] == ';'); 
    }
    return VM_StringUtilities.asciiBytesToString(val, 1, val.length - 2).replace('/','.'); 
  }

  /**
   * Return name of class file corresponding to "this" class descriptor.
   * this: class descriptor - something like "Ljava/lang/String;"
   * @return class file name  - something like "java/lang/String.class"
   */ 
  public String classFileNameFromDescriptor() {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 0);
      VM._assert(val[0] == 'L' && val[val.length-1] == ';'); 
    }
    return VM_StringUtilities.asciiBytesToString(val,1, val.length - 2) + ".class";
  }

  //----------------//
  // classification //
  //----------------//
   
  /**
   * Is "this" atom a reserved member name?
   * Note: Sun has reserved all member names starting with '<' for future use.
   *       At present, only <init> and <clinit> are used.
   */ 
  @Uninterruptible
  public boolean isReservedMemberName() { 
    if (VM.VerifyAssertions) VM._assert(val.length > 0);
    return val[0] == '<';
  }

  /**
   * Is "this" atom a class descriptor?
   */ 
  @Uninterruptible
  public boolean isClassDescriptor() { 
    if (VM.VerifyAssertions) VM._assert(val.length > 0);
    return val[0] == 'L';
  }
      
  /**
   * Is "this" atom an array descriptor?
   */ 
  @Uninterruptible
  public boolean isArrayDescriptor() { 
    if (VM.VerifyAssertions) VM._assert(val.length > 0);
    return val[0] == '[';
  }
      
  /**
   * Is "this" atom a method descriptor?
   */ 
  @Uninterruptible
  public boolean isMethodDescriptor() { 
    if (VM.VerifyAssertions) VM._assert(val.length > 0);
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
  public VM_TypeReference parseForReturnType(ClassLoader cl) {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 0);
      VM._assert(val[0] == '(', "Method descriptors start with `(`");
    }
    int i = 0;
    while (val[i++] != ')') {
      if (VM.VerifyAssertions)
        VM._assert(i < val.length, "Method descriptor missing closing ')'");
    }
    if (VM.VerifyAssertions)
      VM._assert(i < val.length, "Method descriptor missing type after closing ')'");
    switch (val[i]) {
    case VoidTypeCode:
      return VM_TypeReference.Void;
    case BooleanTypeCode:
      return VM_TypeReference.Boolean;
    case ByteTypeCode:
      return VM_TypeReference.Byte;
    case ShortTypeCode:
      return VM_TypeReference.Short;
    case IntTypeCode:
      return VM_TypeReference.Int;
    case LongTypeCode:
      return VM_TypeReference.Long;
    case FloatTypeCode:
      return VM_TypeReference.Float;
    case DoubleTypeCode:
      return VM_TypeReference.Double;
    case CharTypeCode:
      return VM_TypeReference.Char;
    case ClassTypeCode:   // fall through
    case ArrayTypeCode:
      return VM_TypeReference.findOrCreate(cl, findOrCreate(val, i, val.length - i));
    default:
        if (VM.VerifyAssertions) {
          VM._assert(false,
                     "Need a valid method descriptor; got \"" + this
                     + "\"; can't parse the character '"  
                     + byteToString(val[i]) + "'");
        }
        return null;            // NOTREACHED
    }
  }
      
  private String byteToString(byte b) {
    return Character.toString((char) b);
  }

  /**
   * Parse "this" method descriptor to obtain descriptions of method's 
   * parameters.
   * this: method descriptor     - something like "(III)V"
   * @return parameter descriptions
   */ 
  public VM_TypeReference[] parseForParameterTypes(ClassLoader cl) {
    if (VM.VerifyAssertions)  {
      VM._assert(val.length > 0);
      VM._assert(val[0] == '(', "Method descriptors start with `(`");
    }
    VM_TypeReferenceVector sigs = new VM_TypeReferenceVector();
    int i = 1;
    while (true) {
      if (VM.VerifyAssertions)
        VM._assert(i < val.length, "Method descriptor missing closing `)`");
      
      switch (val[i++]) {
      case VoidTypeCode:    sigs.addElement(VM_TypeReference.Void);     
        continue;
      case BooleanTypeCode: sigs.addElement(VM_TypeReference.Boolean);  
        continue;
      case ByteTypeCode:    sigs.addElement(VM_TypeReference.Byte);     
        continue;
      case ShortTypeCode:   sigs.addElement(VM_TypeReference.Short);
        continue;
      case IntTypeCode:     sigs.addElement(VM_TypeReference.Int);
        continue;
      case LongTypeCode:    sigs.addElement(VM_TypeReference.Long);     
        continue;
      case FloatTypeCode:   sigs.addElement(VM_TypeReference.Float);
        continue;
      case DoubleTypeCode:  sigs.addElement(VM_TypeReference.Double);
        continue;
      case CharTypeCode:    sigs.addElement(VM_TypeReference.Char);
        continue;
      case ClassTypeCode: {
        int off = i - 1;
        while (val[i++] != ';') {
          if (VM.VerifyAssertions)
            VM._assert(i < val.length, "class descriptor missing a final ';'");
        }
        sigs.addElement(
            VM_TypeReference
               .findOrCreate(cl, 
                             findOrCreate(val, off, i - off)));
        continue;
      }
      case ArrayTypeCode: {
        int off = i - 1;
        while (val[i] == ArrayTypeCode) {
          if (VM.VerifyAssertions)
            VM._assert(i < val.length, "malformed array descriptor");
          ++i;
        }
        if (val[i++] == ClassTypeCode) while (val[i++] != ';');
        sigs.addElement(VM_TypeReference.findOrCreate(cl, findOrCreate(val, off, i - off)));
        continue;
      }
      case (byte)')': // end of parameter list
        return sigs.finish();
            
      default:  
        if (VM.VerifyAssertions)
          VM._assert(false,
                "The class descriptor \"" + this + "\" contains the illegal"
                + " character '" + byteToString(val[i]) + "'");
      }
    }
  }

  /**
   * Return the underlying set of bytes for the VM_Atom.  This can be used
   * to perform comparisons without requiring the allocation of a string.
   */ 
  @Uninterruptible
  public byte[] getBytes() { 
    return val;
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
  public byte parseForTypeCode() 
    throws IllegalArgumentException
  {
    if (VM.VerifyAssertions)
      VM._assert(val.length > 0);
    return val[0];
  }
  
  /**
   * Parse "this" array descriptor to obtain number of dimensions in 
   * corresponding array type.
   * this: descriptor     - something like "[Ljava/lang/String;" or "[[I"
   * @return dimensionality - something like "1" or "2"
   */ 
  public int parseForArrayDimensionality() 
  {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 1, "An array descriptor has at least two characters");
      VM._assert(val[0] == '[', "An array descriptor must start with '['");
    }
    for (int i = 0; ; ++i) {
      if (VM.VerifyAssertions)
        VM._assert(i < val.length, "Malformed array descriptor: it can't just have [ characters");
      if (val[i] != '[')
        return i;
    }
  }

  /**
   * Parse "this" array descriptor to obtain type code for its element type.
   * this: descriptor - something like "[Ljava/lang/String;" or "[I"
   * @return type code  - something like VM.ObjectTypeCode or VM.IntTypeCode
   * The type code will be one of the constants appearing in the table above.
   *
   * Implementation note: This is supposed to be uninterruptible, since another
   * allegedly uninterruptible method (VM_Array.getLogElementSize()) calls it.
   */ 
  @Uninterruptible
  public byte parseForArrayElementTypeCode() { 
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 1, "An array descriptor has at least two characters");
      VM._assert(val[0] == '[', "An array descriptor must start with '['");
    }
    return val[1];
  }

  /**
   * Return the innermost element type reference for an array
   */
  public VM_Atom parseForInnermostArrayElementDescriptor() {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 1, "An array descriptor has at least two characters");
      VM._assert(val[0] == '[', "An array descriptor must start with '['");
    }
    int i=0; 
    while (val[i] == '[') {
      if (VM.VerifyAssertions)
        VM._assert(i < val.length, "Malformed array descriptor: it can't just have [ characters");
      i++;
    }
    return findOrCreate(val, i, val.length -i);
  }

  /**
   * Parse "this" array descriptor to obtain descriptor for array's element 
   * type.
   * this: array descriptor         - something like "[I"
   * @return array element descriptor - something like "I"
   */
  public VM_Atom parseForArrayElementDescriptor() {
    if (VM.VerifyAssertions) {
      VM._assert(val.length > 1, "An array descriptor has at least two characters");
      VM._assert(val[0] == '[', "An array descriptor must start with '['");
    }
    return findOrCreate(val, 1, val.length - 1);
  }


  private static final byte[][] bootstrapClassPrefixes 
    = { "Ljava/".getBytes(), 
        "Lorg/jikesrvm/".getBytes(),
        "Lgnu/java/".getBytes(),
        "Lgnu/classpath/".getBytes(),
        "Lorg/vmmagic/".getBytes(),
        "Lorg/mmtk/".getBytes()};

  private static final byte[][] rvmClassPrefixes 
    = { "Lorg/jikesrvm/".getBytes(),
        "Lorg/vmmagic/".getBytes(),
        "Lorg/mmtk/".getBytes()};

  /**
   * @return true if this is a class descriptor of a bootstrap class
   * (ie a class that must be loaded by the bootstrap class loader)
   */
  public boolean isBootstrapClassDescriptor() {
  outer:
  for (byte[] test : bootstrapClassPrefixes) {
    if (test.length > val.length) continue;
    for (int j = 0; j < test.length; j++) {
      if (val[j] != test[j])
        continue outer;
    }
    return true;
  }
    return false;
  }
    

  /**
   * @return true if this is a class descriptor of a RVM core class.  This is
   * defined as one that it would be unwise to invalidate, since invalidating
   * it might make it impossible to recompile.
   */
  public boolean isRVMDescriptor() {
  outer:
  for (byte[] test : rvmClassPrefixes) {
    if (test.length > val.length) continue;
    for (int j = 0; j < test.length; j++) {
      if (val[j] != test[j])
        continue outer;
    }
    return true;
  }
    return false;
  }
    

  //-------------//
  // annotations //
  //-------------//
  
  /**
   * Create an annotation name from a class name. For example
   * Lfoo.bar; becomes Lfoo.bar$$; NB in Sun VMs the annotation name
   * of the first annotation is $Proxy1. Classpath may later rely on
   * this to implement serialization correctly.
   */
  public VM_Atom annotationInterfaceToAnnotationClass() {
    byte[] annotationClassName_tmp = new byte[val.length+2];
    System.arraycopy(val, 0, annotationClassName_tmp, 0, val.length-1);
    annotationClassName_tmp[val.length-1] = '$';
    annotationClassName_tmp[val.length]   = '$';
    annotationClassName_tmp[val.length+1] = ';';
    return VM_Atom.findOrCreateUtf8Atom(annotationClassName_tmp);
  }
  /**
   * Create a class name from a type name. For example Lfoo.bar$$;
   * becomes the string foo.bar
   */
  public String annotationClassToAnnotationInterface() {
    if (VM.VerifyAssertions){
      VM._assert(val.length > 0);
      VM._assert(val[0] == 'L' && val[val.length-1] == ';', toString()); 
    }
    return VM_StringUtilities.asciiBytesToString(val,1,val.length-4).replace('/','.'); 
  }
  /**
   * Is this an annotation class name of the form Lfoo.bar$$;
   */
  public boolean isAnnotationClass() {
    return (val.length > 4) && (val[val.length-3] == '$') && (val[val.length-2] == '$');
  }

  //-----------//
  // debugging //
  //-----------//
   
  @Uninterruptible
  public void sysWrite() { 
    for (int i = 0, n = val.length; i < n; ++i) {
      VM.sysWrite((char)val[i]);
    }
  }

  @Uninterruptible
  public int length() { 
    return val.length;
  }

  /**
   * Create atom from the key that maps to it.
   */ 
  private VM_Atom(Key key, int id) {
    this.val = key.val;
    this.hash = key.hashCode();
    this.id = id;
  }

  public int hashCode() {
    return hash;
  }

  /*
   * We canonicalize VM_Atoms, therefore we can use == for equals
   */
  @Uninterruptible
  public boolean equals(Object other) {
    return this == other;
  }

  /**
   * A Key into the atom dictionary.
   * We do this to enable VM_Atom.equals to be efficient (==).
   */ 
  private static class Key {
    final byte[] val;

    Key(byte[] utf8) {
      val = utf8;
    }

    public final int hashCode() {
      int tmp = 99989;
      for (int i = val.length; --i >= 0; ) {
        tmp = 99991 * tmp + val[i];
      }
      return tmp;
    }

    public final boolean equals(Object other) {
      if (this == other) return true;
      if (other instanceof Key) {
        Key that = (Key)other;
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
}
