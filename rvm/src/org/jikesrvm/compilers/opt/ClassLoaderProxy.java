/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.bc2ir.IRGenOptions;
import org.jikesrvm.compilers.opt.driver.OptConstants;
import org.jikesrvm.compilers.opt.ir.operand.ClassConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.StringConstantOperand;
import org.jikesrvm.compilers.opt.util.Stack;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.Statics;
import org.vmmagic.unboxed.Offset;

/**
 **/
public final class ClassLoaderProxy implements Constants, OptConstants {

  /**
   * Returns a common superclass of the two types.
   * NOTE: If both types are references, but are not both loaded, then this
   * may be a conservative approximation (java.lang.Object).
   *
   * @param t1 first type
   * @param t2 second type
   * @return a common superclass or {@code null} if there's none
   */
  public static TypeReference findCommonSuperclass(TypeReference t1, TypeReference t2) {
    if (t1 == t2) {
      return t1;
    }

    if (t1.isPrimitiveType() || t2.isPrimitiveType()) {
      if (t1.isIntLikeType() && t2.isIntLikeType()) {
        // 2 non-identical int like types, return the largest
        if (t1.isIntType() || t2.isIntType()) {
          return TypeReference.Int;
        } else if (t1.isCharType() || t2.isCharType()) {
          return TypeReference.Char;
        } else if (t1.isShortType() || t2.isShortType()) {
          return TypeReference.Short;
        } else if (t1.isByteType() || t2.isByteType()) {
          return TypeReference.Byte;
        } else {
          // Unreachable
          if (VM.VerifyAssertions) VM._assert(false);
          return null;
        }
      } else if (t1.isWordLikeType() && t2.isWordLikeType()) {
        return TypeReference.Word;
      } else {
        // other primitive and unboxed types have no commonality so return null
        return null;
      }
    }

    //Neither t1 nor t2 are primitive or unboxed types at this point

    // Is either t1 or t2 null? Null is assignable to all types so the type of
    // the other operand is the most precise
    if (t1 == TypeReference.NULL_TYPE) {
      return t2;
    } else if (t2 == TypeReference.NULL_TYPE) {
      return t1;
    }

    if (IRGenOptions.DBG_TYPE) {
      VM.sysWrite("finding common supertype of " + t1 + " and " + t2);
    }

    // Strip off all array junk.
    int arrayDimensions = 0;
    while (t1.isArrayType() && t2.isArrayType()) {
      ++arrayDimensions;
      t1 = t1.getArrayElementType();
      t2 = t2.getArrayElementType();
    }
    // at this point, they are not both array types.
    // if one is a primitive, then we want an object array of one less
    // dimensionality
    if (t1.isPrimitiveType() || t2.isPrimitiveType()) {
      TypeReference type = TypeReference.JavaLangObject;
      if (t1 == t2) {
        //Unboxed types are wrapped in their own array objects
        if (t1.isUnboxedType()) {
          arrayDimensions++;
          type = t1;
        } else {
          if (VM.VerifyAssertions) VM._assert(false);
        }
      }
      --arrayDimensions;
      while (arrayDimensions-- > 0) {
        type = type.getArrayTypeForElementType();
      }
      if (IRGenOptions.DBG_TYPE) {
        VM.sysWrite("one is a primitive array, so supertype is " + type);
      }
      return type;
    }

    // At this point neither t1 or t2 is a primitive or word type and either
    // one or the other maybe an array type

    // is this a case of arrays with different dimensionalities?
    if (t1.isArrayType() || t2.isArrayType()) {
      // one is a class type, while the other is an array
      TypeReference type = TypeReference.JavaLangObject;
      while (arrayDimensions-- > 0) {
        type = type.getArrayTypeForElementType();
      }
      if (IRGenOptions.DBG_TYPE) {
        VM.sysWrite("differing dimensionalities for arrays, so supertype is " + type);
      }
      return type;
    }
    // At this point they both must be class types.

    // technique: push heritage of each type on a separate stack,
    // then find the highest point in the stack where they differ.
    RVMClass c1 = (RVMClass) t1.peekType();
    RVMClass c2 = (RVMClass) t2.peekType();
    if (c1 != null && c2 != null) {
      // The ancestor hierarchy is available, so do this exactly
      Stack<RVMClass> s1 = new Stack<RVMClass>();
      do {
        s1.push(c1);
        c1 = c1.getSuperClass();
      } while (c1 != null);
      Stack<RVMClass> s2 = new Stack<RVMClass>();
      do {
        s2.push(c2);
        c2 = c2.getSuperClass();
      } while (c2 != null);
      if (IRGenOptions.DBG_TYPE) {
        VM.sysWrite("stack 1: " + s1);
      }
      if (IRGenOptions.DBG_TYPE) {
        VM.sysWrite("stack 2: " + s2);
      }
      TypeReference best = TypeReference.JavaLangObject;
      while (!s1.empty() && !s2.empty()) {
        RVMClass temp = s1.pop();
        if (temp == s2.pop()) {
          best = temp.getTypeRef();
        } else {
          break;
        }
      }
      if (IRGenOptions.DBG_TYPE) {
        VM.sysWrite("common supertype of the two classes is " + best);
      }
      while (arrayDimensions-- > 0) {
        best = best.getArrayTypeForElementType();
      }
      return best;
    } else {
      if (IRGenOptions.DBG_TYPE && c1 == null) {
        VM.sysWrite(c1 + " is not loaded, using Object as common supertype");
      }
      if (IRGenOptions.DBG_TYPE && c2 == null) {
        VM.sysWrite(c2 + " is not loaded, using Object as common supertype");
      }
      TypeReference common = TypeReference.JavaLangObject;
      while (arrayDimensions-- > 0) {
        common = common.getArrayTypeForElementType();
      }
      return common;
    }
  }

  /**
   * Return Constants.YES if the parent type is defintely a supertype
   *    of the child type.
   * <p> Return Constants.NO if the parent type is definitely not
   * a supertype of the child type.
   * <p> Return Constants.MAYBE if the question cannot be currently answered
   *    (for example if one/both of the classes is not resolved)
   *
   * <p> Takes into account the special 'null-type', which corresponds to a {@code null}
   * constant.
   *
   * @param parentType parent type
   * @param childType child type
   * @return Constants.YES, Constants.NO, or Constants.MAYBE
   */
  public static byte includesType(TypeReference parentType, TypeReference childType) {
    // First handle some cases that we can answer without needing to
    // look at the type hierarchy
    // NOTE: The ordering of these tests is critical!
    if (childType == TypeReference.NULL_TYPE) {
      // Sanity assertion that a null isn't being assigned to an unboxed type
      if (VM.VerifyAssertions && parentType.isReferenceType()) VM._assert(!parentType.isWordLikeType());
      return parentType.isReferenceType() ? YES : NO;
    } else if (parentType == TypeReference.NULL_TYPE) {
      return NO;
    } else if (parentType == childType) {
      return YES;
    } else if (parentType == TypeReference.Word && childType.isWordLikeType()) {
      return YES;
    } else if (parentType.isPrimitiveType() || childType.isPrimitiveType()) {
      return NO;
    } else if (parentType == TypeReference.JavaLangObject) {
      return YES;
    } else {
      // Unboxed types are handled in the word and primitive type case
      if (VM.VerifyAssertions) {
        VM._assert(!parentType.isWordLikeType() && !childType.isWordLikeType());
      }
      // Oh well, we're going to have to try to actually look
      // at the type hierarchy.
      // IMPORTANT: We aren't allowed to cause dynamic class loading,
      // so we have to roll some of this ourselves
      // instead of simply calling RuntimeEntrypoints.instanceOf
      // (which is allowed/required to load classes to answer the question).
      try {
        if (parentType.isArrayType()) {
          if (childType == TypeReference.JavaLangObject) {
            return MAYBE;        // arrays are subtypes of Object.
          } else if (!childType.isArrayType()) {
            return NO;
          } else {
            TypeReference parentET = parentType.getInnermostElementType();
            if (parentET == TypeReference.JavaLangObject) {
              int LHSDimension = parentType.getDimensionality();
              int RHSDimension = childType.getDimensionality();
              if ((RHSDimension > LHSDimension) ||
                  (RHSDimension == LHSDimension && childType.getInnermostElementType().isClassType())) {
                return YES;
              } else {
                return NO;
              }
            } else {
              // parentType is [^k of something other than Object
              // If dimensionalities are equal, then we can reduce
              // to isAssignableWith(parentET, childET).
              // If the dimensionalities are not equal then the answer is NO
              if (parentType.getDimensionality() == childType.getDimensionality()) {
                return includesType(parentET, childType.getInnermostElementType());
              } else {
                return NO;
              }
            }
          }
        } else {                    // parentType.isClassType()
          if (!childType.isClassType()) {
            // parentType is known to not be java.lang.Object.
            return NO;
          } else {
            RVMClass childClass = (RVMClass) childType.peekType();
            RVMClass parentClass = (RVMClass) parentType.peekType();
            if (childClass != null && parentClass != null) {
              if (parentClass.isResolved() && childClass.isResolved() ||
                  (VM.writingBootImage && parentClass.isInBootImage() && childClass.isInBootImage())) {
                if (parentClass.isInterface()) {
                  if (RuntimeEntrypoints.isAssignableWith(parentClass, childClass)) {
                    return YES;
                  } else {
                    // If child is not a final class, it is
                    // possible that a subclass will implement parent.
                    return childClass.isFinal() ? NO : MAYBE;
                  }
                } else if (childClass.isInterface()) {
                  // parent is a proper class, child is an interface
                  return MAYBE;
                } else {
                  // parent & child are both proper classes.
                  if (RuntimeEntrypoints.isAssignableWith(parentClass, childClass)) {
                    return YES;
                  }
                  // If child is a final class, then
                  // !instanceOfClass(parent, child) lets us return NO.
                  // However, if child is not final, then it might have
                  // subclasses so we can't return NO out of hand.
                  // But, if the reverse instanceOf is also false, then we know
                  // that parent and child are completely
                  // unrelated and we can return NO.
                  if (childClass.isFinal()) {
                    return NO;
                  } else {
                    if (RuntimeEntrypoints.isAssignableWith(childClass, parentClass)) {
                      return MAYBE;
                    } else {
                      return NO;
                    }
                  }
                }
              }
            }
            return MAYBE;
          }
        }
      } catch (Throwable e) {
        e.printStackTrace();
        OptimizingCompilerException.UNREACHABLE();
        return MAYBE;            // placate jikes.
      }
    }
  }

  // --------------------------------------------------------------------------
  // Querry classloader data structures
  // --------------------------------------------------------------------------

  /**
   * Find the method of the given class that matches the given descriptor.
   */
  public static RVMMethod lookupMethod(RVMClass cls, MethodReference ref) {
    RVMMethod newmeth = null;
    if (cls.isResolved() && !cls.isInterface()) {
      Atom mn = ref.getName();
      Atom md = ref.getDescriptor();
      for (; (newmeth == null) && (cls != null); cls = cls.getSuperClass()) {
        newmeth = cls.findDeclaredMethod(mn, md);
      }
    }
    return newmeth;
  }

  // --------------------------------------------------------------------------
  // Constant pool access
  // --------------------------------------------------------------------------

  /**
   * Get the integer stored at a particular index of a class's constant
   * pool.
   */
  public static IntConstantOperand getIntFromConstantPool(RVMClass klass, int index) {
    Offset offset = klass.getLiteralOffset(index);
    int val = Statics.getSlotContentsAsInt(offset);
    return new IntConstantOperand(val);
  }

  /**
   * Get the double stored at a particular index of a class's constant
   * pool.
   */
  public static DoubleConstantOperand getDoubleFromConstantPool(RVMClass klass, int index) {
    Offset offset = klass.getLiteralOffset(index);
    long val_raw = Statics.getSlotContentsAsLong(offset);
    double val = Double.longBitsToDouble(val_raw);
    return new DoubleConstantOperand(val, offset);
  }

  /**
   * Get the float stored at a particular index of a class's constant
   * pool.
   */
  public static FloatConstantOperand getFloatFromConstantPool(RVMClass klass, int index) {
    Offset offset = klass.getLiteralOffset(index);
    int val_raw = Statics.getSlotContentsAsInt(offset);
    float val = Float.intBitsToFloat(val_raw);
    return new FloatConstantOperand(val, offset);
  }

  /**
   * Get the long stored at a particular index of a class's constant
   * pool.
   */
  public static LongConstantOperand getLongFromConstantPool(RVMClass klass, int index) {
    Offset offset = klass.getLiteralOffset(index);
    long val = Statics.getSlotContentsAsLong(offset);
    return new LongConstantOperand(val, offset);
  }

  /**
   * Get the String stored at a particular index of a class's constant
   * pool.
   */
  public static StringConstantOperand getStringFromConstantPool(RVMClass klass, int index) {
    Offset offset = klass.getLiteralOffset(index);
    try {
      String val;
      val = (String) Statics.getSlotContentsAsObject(offset);
      return new StringConstantOperand(val, offset);
    } catch (ClassCastException e) {
      throw new Error("Corrupt JTOC at offset " + offset.toInt(), e);
    }
  }

  /**
   * Get the Class stored at a particular index of a class's constant
   * pool.
   */
  public static ClassConstantOperand getClassFromConstantPool(RVMClass klass, int index) {
    Offset offset = klass.getLiteralOffset(index);
    try {
      Class<?> val = (Class<?>) Statics.getSlotContentsAsObject(offset);
      return new ClassConstantOperand(val, offset);
    } catch (ClassCastException e) {
      throw new Error("Corrupt JTOC at offset " + offset.toInt(), e);
    }
  }
}
