/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.*;

/**
 * Data structures and code for fast dynamic type checking.
 * <p>
 * As a convention, we convert all dynamic type checking 
 * operations into the following question: LHS :?= RHS
 * (i.e. can an instance of the RHS class be stored in a
 * variable of the LHS class or interface.)  This question
 * arises for four bytecodes: instanceof, checkcast, aastore
 * and invokeinterface and entry into catch blocks. 
 * This gives us a uniform terminology, but in some cases 
 * (instanceof) can be somewhat counter-intuitive since despite 
 * the fact that the Java source code is written as 
 * <code>x instanceof C</code>, for the purposes of dynamic type checking
 * <code>x</code> is the RHS and <code>C</code> is the LHS!
 * <p>
 * The idea of the optimizations presented below is to treat
 * each context in which these queries arises as a special
 * case to be optimised in isolation.  Consider the following
 * taxonomy of dynamic type checking conexts:
 * <p>
 * (1) Is the LHS unknown at compile time?  True only for aastore?
 *    If so, the following test will be fast in most instances:
 *    is the runtime type of the LHS array the same as compile-time 
 *    type of the variable that contains it?  If so, the Java-to-bytecode
 *    compiler (and the verifier) guarantees that the test passes.  
 *    Unfortunately, this test can only be used in two of three cases:
 *    when the LHS variable is a field or a parameter.  When the LHS is 
 *    in a local variable the Java-to-bytecode compiler has thrown away
 *    the necessary type information.
 * <p>
 * (2) Otherwise, is the LHS an array?
 *    If so, there are three sub-cases
 *    (2a) LHS is [^k primitive:
 *        If so, the dimensionality of the RHS must be k
 *        and the baseclass of the RHS must be the same primitive
 *    (2b) LHS is [^k class:
 *        If so, the dimensionality of the RHS must be k
 *        and the baseclass of the RHS must be assignable with class (see #3)
 *    (2c) LHS is [^k Ljava.lang.Object:
 *        If so, either the dimensionality of the RHS is greater than k
 *        or, this dimensionality is k and the baseclass is NOT primitive
 * <p>
 * (3) Otherwise, is the LHS unresolved?
 *    If so, fall back to calling VM_Runtime.instanceOf at runtime which will
 *    load/resolve the types and then call VM_DynamicTypeCheck.instanceOf.
 * <p>
 * (4) Otherwise, is the LHS an interface?  
 *    If so, query the doesImplement array of the RHS's TIB at the entry 
 *    for the interface ID. If a class does not directly implement any
 *    interfaces then it inherits the doesImplement array from its superclass.
 * <p>
 * (5) Otherwise, is the depth of the LHS greater than 
 * MIN_SUPERCLASS_IDS_SIZE? If so, if LHS depth is greater that 
 * RHS's superclassIds.length, the test fails.  Else, see #6.
 * <p>
 * (6) Otherwise.  If the LHS depth component of the RHS's superclassIds
 *    array is the LHS class ID, the test succeeds.  Else, it fails.
 *
 * @see com.ibm.JikesRVM.opt.OPT_DynamicTypeCheckExpansion
 * @see VM_Type
 * @see VM_Class
 * @see VM_Array
 * 
 * @author Bowen Alpern
 * @author Dave Grove
 */
public class VM_DynamicTypeCheck implements VM_TIBLayoutConstants {

  /**
   * Minimum length of the superclassIds array in TIB.
   * Note: this array is padded to save a index out of
   * bounds test for classes with shallow class depth.
   */
  public static final int MIN_SUPERCLASS_IDS_SIZE = 6; // a short[], so multiple of 2.

  /**
   * Minimum length of the doesImplements array in TIB.
   * Note: this array is padded to save a index out of
   * bounds test for the first 32 * MIN_DOES_IMPLEMENT_SIZE interfaces loaded.
   */
  public static final int MIN_DOES_IMPLEMENT_SIZE = 5; // an int[]

  /**
   * Create the superclass Id vector for a VM_Type.
   *
   * @param t a VM_Type to create a superclass Id vector for
   * @return the superclass Id vector
   */
  static short[] buildSuperclassIds(VM_Type t) {
    int depth   = t.getTypeDepth();
    short[] tsi;
    if (t.isJavaLangObjectType()) {
      if (VM.VerifyAssertions) VM._assert(depth == 0);
      tsi = new short[1];
    } else {
      int size    = MIN_SUPERCLASS_IDS_SIZE <= depth ? depth+1 : MIN_SUPERCLASS_IDS_SIZE;
      tsi = new short[size];
      VM_Type p;                          
      if (t.isArrayType() || t.asClass().isInterface()) {
        p = VM_Type.JavaLangObjectType;
      } else {
        p = t.asClass().getSuperClass();
      }
      short[] psi = p.getSuperclassIds();
      for (int i=0; i<depth; i++) {
        tsi[i] = psi[i];
      }
    }
    int id = t.getId();
    if (VM.VerifyAssertions) VM._assert(id <= 0xFFFF); // when this fails, make superclassIds int[] 
    tsi[depth] = (short) id;
    return tsi;
  }

  private static int[] arrayDoesImplement;
  /**
   * Create the doesImplement vector for a VM_Array.
   * All arrays implement exactly java.io.Serializable and java.lang.Cloneable.
   * 
   * @param t a VM_Array to create a doesImplement vector for
   * @return the doesImplement vector
   */
  static int[] buildDoesImplement(VM_Array t) {
    if (arrayDoesImplement == null) {
      int cloneIdx = VM_Type.JavaLangCloneableType.getDoesImplementIndex();
      int serialIdx = VM_Type.JavaIoSerializableType.getDoesImplementIndex();
      int size = Math.max(cloneIdx, serialIdx);
      size = Math.max(MIN_DOES_IMPLEMENT_SIZE, size+1);
      int [] tmp = new int[size];
      tmp[cloneIdx] = VM_Type.JavaLangCloneableType.getDoesImplementBitMask();
      tmp[serialIdx] |= VM_Type.JavaIoSerializableType.getDoesImplementBitMask();
      arrayDoesImplement = tmp;
    }
    return arrayDoesImplement;
  }
  
  /**
   * Create the doesImplement vector for a VM_Class.
   * 
   * @param t a VM_Class to create a doesImplement vector for
   * @return the doesImplement vector
   */
  static int[] buildDoesImplement(VM_Class t) {
    if (t.isJavaLangObjectType()) {
      // object implements no interfaces.
      return new int[MIN_DOES_IMPLEMENT_SIZE];
    }

    VM_Class [] superInterfaces = t.getDeclaredInterfaces();

    if (!t.isInterface() && superInterfaces.length == 0) {
      // I add nothing new; share with parent.
      return t.getSuperClass().getDoesImplement();
    }

    // I need one of my own; first figure out how big it needs to be.
    int size;
    if (t.isInterface()) {
      size = Math.max(MIN_DOES_IMPLEMENT_SIZE, t.getDoesImplementIndex()+1);
    } else {
      size = t.getSuperClass().getDoesImplement().length;
    }
    for (int i=0; i<superInterfaces.length; i++) {
      VM_Class superInterface = superInterfaces[i];
      size = Math.max(size, superInterface.getDoesImplement().length);
    }

    // then create and populate it
    int[] mine = new int[size];
    if (t.isInterface()) {
      mine[t.getDoesImplementIndex()] = t.getDoesImplementBitMask();
    } else {
      int[] parent = t.getSuperClass().getDoesImplement();
      for (int j=0; j<parent.length; j++) {
        mine[j] |= parent[j];
      }
    }
    for (int i=0; i<superInterfaces.length; i++) {
      int[] parent = superInterfaces[i].getDoesImplement();
      for (int j=0; j<parent.length; j++) {
        mine[j] |= parent[j];
      }
    }
    
    return mine;
  }


  /**
   * LHSclass is a fully loaded class or interface.  
   *   Is rhsTIB the TIB of an instanceof LHSclass?
   * 
   * @param LHSclass a fully loaded class or interface class
   * @param rhsTIB the TIB of an object that might be an instance of LHSclass
   * @return <code>true</code> if the object is an instance of LHSClass
   *         or <code>false</code> if it is not
   */
  public static boolean instanceOfNonArray(VM_Class LHSclass, Object[] rhsTIB) {
    if (LHSclass.isInterface()) {
      return instanceOfInterface(LHSclass, rhsTIB);
    } else {
      return instanceOfClass(LHSclass, rhsTIB);
    }
  }    


  /**
   * LHSclass is a fully loaded class.
   *  Is rhsTIB the TIB of a subclass of LHSclass?
   * 
   * @param LHSclass a (fully loaded) class
   * @param rhsTIB the TIB of an object that might be an instance of LHSclass
   * @return <code>true</code> if the object is an instance of LHSClass
   *         or <code>false</code> if it is not
   */
  public static boolean instanceOfClass(VM_Class LHSclass, Object[] rhsTIB) throws VM_PragmaUninterruptible {
    short[] superclassIds = VM_Magic.objectAsShortArray(rhsTIB[TIB_SUPERCLASS_IDS_INDEX]);
    int LHSDepth = LHSclass.getTypeDepth();
    if (LHSDepth >= superclassIds.length) return false;
    int LHSId = LHSclass.getId();
    return superclassIds[LHSDepth] == LHSId;
  }    


  /** 
   * LHSclass is a fully loaded interface.
   *   Is rhsTIB the TIB of a class that implements LHSclass?
   * 
   * @param LHSclass a class (that is a fully loaded interface)
   * @param rhsTIB the TIB of an object that might be an instance of LHSclass
   * @return <code>true</code> if the object is an instance of LHSClass
   *         or <code>false</code> if it is not
   */
  public static boolean instanceOfInterface(VM_Class LHSclass, Object[] rhsTIB) {
    int[] doesImplement = VM_Magic.objectAsIntArray(rhsTIB[TIB_DOES_IMPLEMENT_INDEX]);
    int idx = LHSclass.getDoesImplementIndex();
    int mask = LHSclass.getDoesImplementBitMask();
    return idx < doesImplement.length && ((doesImplement[idx] & mask) != 0);
  }

  /**
   * Can we store an object of type RHSType in a variable of type LHSType?
   * Assumption. LHSType and RHSType are already resolved.
   * 
   * @param LHSType the left-hand-side type
   * @param RHSType the right-hand-size type
   * @return <code>true</code> if we can store an object of 
   *         RHSType into a variable of type LSType
   *         or <code>false</code> if we cannot.
   */
  public static boolean instanceOfResolved(VM_Type LHSType, VM_Type RHSType) {
    int LHSDimension = LHSType.getDimensionality();
    int RHSDimension = RHSType.getDimensionality();
    if (LHSDimension < 0 || RHSDimension < 0) return false;
    if (LHSDimension == 0) return instanceOfNonArray(LHSType.asClass(), 
                                                     RHSType.getTypeInformationBlock());
    VM_Type LHSInnermostElementType = LHSType.asArray().getInnermostElementType();
    if (LHSInnermostElementType == VM_Type.JavaLangObjectType){
      if (RHSDimension < LHSDimension) return false;
      if (RHSDimension > LHSDimension) return true;
      return RHSType.asArray().getInnermostElementType().isClassType(); // !primitive 
    } else if (!LHSInnermostElementType.isPrimitiveType()) {
      if (RHSDimension != LHSDimension) return false;
      VM_Type RHSInnermostElementType = RHSType.asArray().getInnermostElementType();
      if (RHSInnermostElementType.isPrimitiveType()) return false;
      return instanceOfNonArray(LHSInnermostElementType.asClass(), 
                                RHSInnermostElementType.getTypeInformationBlock());
    } else {
      return false;
    }
  }
}
