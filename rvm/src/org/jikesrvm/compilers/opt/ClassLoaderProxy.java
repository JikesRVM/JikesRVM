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
package org.jikesrvm.compilers.opt;

import org.jikesrvm.VM;
import org.jikesrvm.VM_Constants;
import org.jikesrvm.classloader.VM_Atom;
import org.jikesrvm.classloader.VM_Class;
import org.jikesrvm.classloader.VM_Method;
import org.jikesrvm.classloader.VM_MethodReference;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.bc2ir.IRGenOptions;
import org.jikesrvm.compilers.opt.ir.ClassConstantOperand;
import org.jikesrvm.compilers.opt.ir.DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.StringConstantOperand;
import org.jikesrvm.compilers.opt.util.Stack;
import org.jikesrvm.runtime.VM_Runtime;
import org.jikesrvm.runtime.VM_Statics;
import org.vmmagic.unboxed.Offset;

/**
 **/
public final class ClassLoaderProxy implements VM_Constants, Constants {

  /**
   * Returns a common superclass of the two types.
   * NOTE: If both types are references, but are not both loaded, then this
   * may be a conservative approximation (java.lang.Object).
   * If there is no common superclass, than null is returned.
   */
  public static VM_TypeReference findCommonSuperclass(VM_TypeReference t1, VM_TypeReference t2) {
    if (t1 == t2) {
      return t1;
    }

    if (t1.isPrimitiveType() || t2.isPrimitiveType()) {
      if (t1.isIntLikeType() && t2.isIntLikeType()) {
        // 2 non-identical int like types, return the largest
        if (t1.isIntType() || t2.isIntType()) {
          return VM_TypeReference.Int;
        } else if (t1.isCharType() || t2.isCharType()) {
          return VM_TypeReference.Char;
        } else if (t1.isShortType() || t2.isShortType()) {
          return VM_TypeReference.Short;
        } else if (t1.isByteType() || t2.isByteType()) {
          return VM_TypeReference.Byte;
        } else {
          // Unreachable
          if (VM.VerifyAssertions) VM._assert(false);
          return null;
        }
      } else if (t1.isWordType() && t2.isWordType()) {
        return VM_TypeReference.Word;
      } else {
        // other primitive and unboxed types have no commonality so return null
        return null;
      }
    }

    //Neither t1 nor t2 are primitive or unboxed types at this point

    // Is either t1 or t2 null? Null is assignable to all types so the type of
    // the other operand is the most precise
    if (t1 == VM_TypeReference.NULL_TYPE) {
      return t2;
    } else if (t2 == VM_TypeReference.NULL_TYPE) {
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
      VM_TypeReference type = VM_TypeReference.JavaLangObject;
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
      VM_TypeReference type = VM_TypeReference.JavaLangObject;
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
    VM_Class c1 = (VM_Class) t1.peekType();
    VM_Class c2 = (VM_Class) t2.peekType();
    if (c1 != null && c2 != null) {
      // The ancestor hierarchy is available, so do this exactly
      Stack<VM_Class> s1 = new Stack<VM_Class>();
      do {
        s1.push(c1);
        c1 = c1.getSuperClass();
      } while (c1 != null);
      Stack<VM_Class> s2 = new Stack<VM_Class>();
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
      VM_TypeReference best = VM_TypeReference.JavaLangObject;
      while (!s1.empty() && !s2.empty()) {
        VM_Class temp = s1.pop();
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
      VM_TypeReference common = VM_TypeReference.JavaLangObject;
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
   * <p> Takes into account the special 'null-type', which corresponds to a null
   * constant.
   *
   * @param parentType parent type
   * @param childType child type
   * @return Constants.YES, Constants.NO, or Constants.MAYBE
   */
  public static byte includesType(VM_TypeReference parentType, VM_TypeReference childType) {
    // First handle some cases that we can answer without needing to
    // look at the type hierarchy
    // NOTE: The ordering of these tests is critical!
    if (childType == VM_TypeReference.NULL_TYPE) {
      // Sanity assertion that a null isn't being assigned to an unboxed type
      if (VM.VerifyAssertions && parentType.isReferenceType()) VM._assert(!parentType.isWordType());
      return parentType.isReferenceType() ? YES : NO;
    } else if (parentType == VM_TypeReference.NULL_TYPE) {
      return NO;
    } else if (parentType == childType) {
      return YES;
    } else if (parentType == VM_TypeReference.Word && childType.isWordType()) {
      return YES;
    } else if (parentType.isPrimitiveType() || childType.isPrimitiveType()) {
      return NO;
    } else if (parentType == VM_TypeReference.JavaLangObject) {
      return YES;
    } else {
      // Unboxed types are handled in the word and primitive type case
      if (VM.VerifyAssertions) {
        VM._assert(!parentType.isWordType() && !childType.isWordType());
      }
      // Oh well, we're going to have to try to actually look
      // at the type hierarchy.
      // IMPORTANT: We aren't allowed to cause dynamic class loading,
      // so we have to roll some of this ourselves
      // instead of simply calling VM_Runtime.instanceOf
      // (which is allowed/required to load classes to answer the question).
      try {
        if (parentType.isArrayType()) {
          if (childType == VM_TypeReference.JavaLangObject) {
            return MAYBE;        // arrays are subtypes of Object.
          } else if (!childType.isArrayType()) {
            return NO;
          } else {
            VM_TypeReference parentET = parentType.getInnermostElementType();
            if (parentET == VM_TypeReference.JavaLangObject) {
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
            VM_Class childClass = (VM_Class) childType.peekType();
            VM_Class parentClass = (VM_Class) parentType.peekType();
            if (childClass != null && parentClass != null) {
              if (parentClass.isResolved() && childClass.isResolved() ||
                  (VM.writingBootImage && parentClass.isInBootImage() && childClass.isInBootImage())) {
                if (parentClass.isInterface()) {
                  if (VM_Runtime.isAssignableWith(parentClass, childClass)) {
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
                  if (VM_Runtime.isAssignableWith(parentClass, childClass)) {
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
                    if (VM_Runtime.isAssignableWith(childClass, parentClass)) {
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
  public static VM_Method lookupMethod(VM_Class cls, VM_MethodReference ref) {
    VM_Method newmeth = null;
    if (cls.isResolved() && !cls.isInterface()) {
      VM_Atom mn = ref.getName();
      VM_Atom md = ref.getDescriptor();
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
  public static IntConstantOperand getIntFromConstantPool(VM_Class klass, int index) {
    Offset offset = klass.getLiteralOffset(index);
    int val = VM_Statics.getSlotContentsAsInt(offset);
    return new IntConstantOperand(val);
  }

  /**
   * Get the double stored at a particular index of a class's constant
   * pool.
   */
  public static DoubleConstantOperand getDoubleFromConstantPool(VM_Class klass, int index) {
    Offset offset = klass.getLiteralOffset(index);
    long val_raw = VM_Statics.getSlotContentsAsLong(offset);
    double val = Double.longBitsToDouble(val_raw);
    return new DoubleConstantOperand(val, offset);
  }

  /**
   * Get the float stored at a particular index of a class's constant
   * pool.
   */
  public static FloatConstantOperand getFloatFromConstantPool(VM_Class klass, int index) {
    Offset offset = klass.getLiteralOffset(index);
    int val_raw = VM_Statics.getSlotContentsAsInt(offset);
    float val = Float.intBitsToFloat(val_raw);
    return new FloatConstantOperand(val, offset);
  }

  /**
   * Get the long stored at a particular index of a class's constant
   * pool.
   */
  public static LongConstantOperand getLongFromConstantPool(VM_Class klass, int index) {
    Offset offset = klass.getLiteralOffset(index);
    long val = VM_Statics.getSlotContentsAsLong(offset);
    return new LongConstantOperand(val, offset);
  }

  /**
   * Get the String stored at a particular index of a class's constant
   * pool.
   */
  public static StringConstantOperand getStringFromConstantPool(VM_Class klass, int index) {
    Offset offset = klass.getLiteralOffset(index);
    try {
      String val;
      val = (String) VM_Statics.getSlotContentsAsObject(offset);
      return new StringConstantOperand(val, offset);
    } catch (ClassCastException e) {
      throw new Error("Corrupt JTOC at offset " + offset.toInt(), e);
    }
  }

  /**
   * Get the Class stored at a particular index of a class's constant
   * pool.
   */
  public static ClassConstantOperand getClassFromConstantPool(VM_Class klass, int index) {
    Offset offset = klass.getLiteralOffset(index);
    try {
      Class<?> val = klass.getClassForType();
      return new ClassConstantOperand(val, offset);
    } catch (ClassCastException e) {
      throw new Error("Corrupt JTOC at offset " + offset.toInt(), e);
    }
  }
}
