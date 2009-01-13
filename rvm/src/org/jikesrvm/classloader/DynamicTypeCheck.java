/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.jikesrvm.mm.mminterface.MemoryManager;
import org.jikesrvm.objectmodel.TIB;
import org.jikesrvm.objectmodel.TIBLayoutConstants;
import org.jikesrvm.runtime.Magic;
import org.vmmagic.pragma.Uninterruptible;

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
 *        _OR_ the dimensionality of the RHS must be >k
 *        and the baseclass of the LHS is java.lang.Cloneable or java.io.Serializable
 *    (2c) LHS is [^k Ljava.lang.Object:
 *        If so, either the dimensionality of the RHS is greater than k
 *        or, this dimensionality is k and the baseclass is NOT primitive
 * <p>
 * (3) Otherwise, is the LHS unresolved?
 *    If so, fall back to calling RuntimeEntrypoints.instanceOf at runtime which will
 *    load/resolve the types and then call DynamicTypeCheck.instanceOf.
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
 * @see org.jikesrvm.compilers.opt.hir2lir.DynamicTypeCheckExpansion
 * @see RVMType
 * @see RVMClass
 * @see RVMArray
 */
public class DynamicTypeCheck implements TIBLayoutConstants {

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
   * Create the superclass Id vector for a RVMType.
   *
   * @param t a RVMType to create a superclass Id vector for
   * @return the superclass Id vector
   */
  static short[] buildSuperclassIds(RVMType t) {
    int depth = t.getTypeDepth();
    short[] tsi;
    if (t.isJavaLangObjectType()) {
      if (VM.VerifyAssertions) VM._assert(depth == 0);
      tsi = MemoryManager.newNonMovingShortArray(1);
    } else {
      int size = MIN_SUPERCLASS_IDS_SIZE <= depth ? depth + 1 : MIN_SUPERCLASS_IDS_SIZE;
      tsi = MemoryManager.newNonMovingShortArray(size);
      RVMType p;
      if (t.isArrayType() || t.asClass().isInterface()) {
        p = RVMType.JavaLangObjectType;
      } else {
        p = t.asClass().getSuperClass();
      }
      short[] psi = p.getSuperclassIds();
      for (int i = 0; i < depth; i++) {
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
   * Create the doesImplement vector for a RVMArray.
   * All arrays implement exactly java.io.Serializable and java.lang.Cloneable.
   *
   * @param t a RVMArray to create a doesImplement vector for
   * @return the doesImplement vector
   */
  static int[] buildDoesImplement(RVMArray t) {
    if (arrayDoesImplement == null) {
      int cloneIdx = RVMType.JavaLangCloneableType.getDoesImplementIndex();
      int serialIdx = RVMType.JavaIoSerializableType.getDoesImplementIndex();
      int size = Math.max(cloneIdx, serialIdx);
      size = Math.max(MIN_DOES_IMPLEMENT_SIZE, size + 1);
      int[] tmp = MemoryManager.newNonMovingIntArray(size);
      tmp[cloneIdx] = RVMType.JavaLangCloneableType.getDoesImplementBitMask();
      tmp[serialIdx] |= RVMType.JavaIoSerializableType.getDoesImplementBitMask();
      arrayDoesImplement = tmp;
    }
    return arrayDoesImplement;
  }

  /**
   * Create the doesImplement vector for a RVMClass.
   *
   * @param t a RVMClass to create a doesImplement vector for
   * @return the doesImplement vector
   */
  static int[] buildDoesImplement(RVMClass t) {
    if (t.isJavaLangObjectType()) {
      // object implements no interfaces.
      return MemoryManager.newNonMovingIntArray(MIN_DOES_IMPLEMENT_SIZE);
    }

    RVMClass[] superInterfaces = t.getDeclaredInterfaces();

    if (!t.isInterface() && superInterfaces.length == 0) {
      // I add nothing new; share with parent.
      return t.getSuperClass().getDoesImplement();
    }

    // I need one of my own; first figure out how big it needs to be.
    int size;
    if (t.isInterface()) {
      size = Math.max(MIN_DOES_IMPLEMENT_SIZE, t.getDoesImplementIndex() + 1);
    } else {
      size = t.getSuperClass().getDoesImplement().length;
    }
    for (RVMClass superInterface : superInterfaces) {
      size = Math.max(size, superInterface.getDoesImplement().length);
    }

    // then create and populate it
    int[] mine = MemoryManager.newNonMovingIntArray(size);
    if (t.isInterface()) {
      mine[t.getDoesImplementIndex()] = t.getDoesImplementBitMask();
    } else {
      int[] parent = t.getSuperClass().getDoesImplement();
      for (int j = 0; j < parent.length; j++) {
        mine[j] |= parent[j];
      }
    }
    for (RVMClass superInterface : superInterfaces) {
      int[] parent = superInterface.getDoesImplement();
      for (int j = 0; j < parent.length; j++) {
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
  public static boolean instanceOfNonArray(RVMClass LHSclass, TIB rhsTIB) {
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
  @Uninterruptible
  public static boolean instanceOfClass(RVMClass LHSclass, TIB rhsTIB) {
    if (VM.VerifyAssertions) {
      VM._assert(rhsTIB != null);
      VM._assert(rhsTIB.getSuperclassIds() != null);
    }
    short[] superclassIds = Magic.objectAsShortArray(rhsTIB.getSuperclassIds());
    int LHSDepth = LHSclass.getTypeDepth();
    if (LHSDepth >= superclassIds.length) return false;
    int LHSId = LHSclass.getId();
    return (superclassIds[LHSDepth] & 0xFFFF) == LHSId;
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
  public static boolean instanceOfInterface(RVMClass LHSclass, TIB rhsTIB) {
    int[] doesImplement = rhsTIB.getDoesImplement();
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
  public static boolean instanceOfResolved(RVMType LHSType, RVMType RHSType) {
    int LHSDimension = LHSType.getDimensionality();
    int RHSDimension = RHSType.getDimensionality();
    if (LHSDimension < 0 || RHSDimension < 0) return false;
    if (LHSDimension == 0) {
      return instanceOfNonArray(LHSType.asClass(), RHSType.getTypeInformationBlock());
    }
    RVMType LHSInnermostElementType = LHSType.asArray().getInnermostElementType();
    if (LHSInnermostElementType == RVMType.JavaLangObjectType) {
      if (RHSDimension < LHSDimension) return false;
      if (RHSDimension > LHSDimension) return true;
      return RHSType.asArray().getInnermostElementType().isClassType(); // !primitive
    } else if (!LHSInnermostElementType.isPrimitiveType()) {
      if (RHSDimension == LHSDimension) {
        RVMType RHSInnermostElementType = RHSType.asArray().getInnermostElementType();
        if (RHSInnermostElementType.isPrimitiveType()) return false;
        return instanceOfNonArray(LHSInnermostElementType.asClass(), RHSInnermostElementType.getTypeInformationBlock());
      } else {
        // All array types implicitly implement java.lang.Cloneable and java.io.Serializable
        // so if LHS is if lesser dimensionality then this check must succeed if its innermost
        // element type is one of these special interfaces.
        return (LHSDimension < RHSDimension &&
                (LHSInnermostElementType == RVMType.JavaLangCloneableType ||
                 LHSInnermostElementType == RVMType.JavaIoSerializableType));
      }
    } else {
      return false;
    }
  }
}
