/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.opt.ir.*;
import org.vmmagic.unboxed.Offset;

/**
 * @author Doug Lorch (retired)
 * @author Dave Grove
 * @author Julian Dolby
 **/
public final class OPT_ClassLoaderProxy implements VM_Constants, OPT_Constants {

  /**
   * Returns a common superclass of the two types.
   * NOTE: If both types are references, but are not both loaded, then this
   * may be a conservative approximation (java.lang.Object).
   * If there is no common superclass, than null is returned.
   */
  public static VM_TypeReference findCommonSuperclass (VM_TypeReference t1, VM_TypeReference t2) {
    if (t1 == t2)
      return t1;
    if (t1.isPrimitiveType() || t2.isPrimitiveType()) {
      if (t1.isIntLikeType() && t2.isIntLikeType()) {
        if (t1.isIntType() || t2.isIntType())
          return VM_TypeReference.Int;
        if (t1.isCharType() || t2.isCharType())
          return VM_TypeReference.Char;
        if (t1.isShortType() || t2.isShortType())
          return VM_TypeReference.Short;
        if (t1.isByteType() || t2.isByteType())
          return VM_TypeReference.Byte;
      } else if (t1.isWordType() && t2.isWordType()) {
        return VM_TypeReference.Word;
      }
      return null;
    }

    // can these next two cases happen?
    if (t1 == VM_TypeReference.NULL_TYPE)
      return t2;
    if (t2 == VM_TypeReference.NULL_TYPE)
      return t1;
    if (OPT_IRGenOptions.DBG_TYPE)
      VM.sysWrite("finding common supertype of " + t1 + " and " + t2);
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
        // Kludge around the fact that we have two ways to name magic arrays
        if (t1.isWordType() && t2.isWordType()) {
          arrayDimensions++;
          type = t1;
        } else if (t1.isCodeType() && t2.isCodeType()) {
          arrayDimensions++;
          type = t1;
        } else {
            if (VM.VerifyAssertions) VM._assert(false);
        }
      }
      --arrayDimensions;
      while (arrayDimensions-- > 0)
        type = type.getArrayTypeForElementType();
      if (OPT_IRGenOptions.DBG_TYPE)
        VM.sysWrite("one is a primitive array, so supertype is " + type);
      return type;
    }
    // neither is a primitive, and they are not both array types.
    if (!t1.isClassType() || !t2.isClassType()) {
      // one is a class type, while the other isn't.
      VM_TypeReference type = VM_TypeReference.JavaLangObject;
      while (arrayDimensions-- > 0)
        type = type.getArrayTypeForElementType();
      if (OPT_IRGenOptions.DBG_TYPE)
        VM.sysWrite("differing dimensionalities for arrays, so supertype is "
                    + type);
      return type;
    }
    // they both must be class types.
    // technique: push heritage of each type on a separate stack,
    // then find the highest point in the stack where they differ.
    VM_Class c1 = (VM_Class)t1.peekResolvedType();
    VM_Class c2 = (VM_Class)t2.peekResolvedType();
    if (c1 != null && c2 != null) {
      // The ancestor hierarchy is available, so do this exactly
      OPT_Stack s1 = new OPT_Stack();
      do {
        s1.push(c1);
        c1 = c1.getSuperClass();
      } while (c1 != null);
      OPT_Stack s2 = new OPT_Stack();
      do {
        s2.push(c2);
        c2 = c2.getSuperClass();
      } while (c2 != null);
      if (OPT_IRGenOptions.DBG_TYPE)
        VM.sysWrite("stack 1: " + s1);
      if (OPT_IRGenOptions.DBG_TYPE)
        VM.sysWrite("stack 2: " + s2);
      VM_TypeReference best = VM_TypeReference.JavaLangObject;
      while (!s1.empty() && !s2.empty()) {
        VM_Class temp = (VM_Class)s1.pop();
        if (temp == s2.pop())
          best = temp.getTypeRef(); 
        else 
          break;
      }
      if (OPT_IRGenOptions.DBG_TYPE)
        VM.sysWrite("common supertype of the two classes is " + best);
      while (arrayDimensions-- > 0)
        best = best.getArrayTypeForElementType();
      return best;
    } else {
      if (OPT_IRGenOptions.DBG_TYPE && c1 == null)
        VM.sysWrite(c1 + " is not loaded, using Object as common supertype");
      if (OPT_IRGenOptions.DBG_TYPE && c2 == null)
        VM.sysWrite(c2 + " is not loaded, using Object as common supertype");
      VM_TypeReference common = VM_TypeReference.JavaLangObject;
      while (arrayDimensions-- > 0)
        common = common.getArrayTypeForElementType();
      return common;
    }
  }

  /**
   * Return OPT_Constants.YES if the parent type is defintely a supertype
   *    of the child type.
   * <p> Return OPT_Constants.NO if the parent type is definitely not 
   * a supertype of the child type.
   * <p> Return OPT_Constants.MAYBE if the question cannot be currently answered
   *    (for example if one/both of the classes is not resolved)
   *
   * <p> Takes into account the special 'null-type', which corresponds to a null
   * constant.
   *
   * @param parentType parent type
   * @param childType child type
   * @return OPT_Constants.YES, OPT_Constants.NO, or OPT_Constants.MAYBE
   */
  public static byte includesType (VM_TypeReference parentType, VM_TypeReference childType) {
    // First handle some cases that we can answer without needing to 
    // look at the type hierarchy
    // NOTE: The ordering of these tests is critical!
    if (childType == VM_TypeReference.NULL_TYPE) {
      return parentType.isReferenceType() ? YES : NO;
    }
    if (parentType == VM_TypeReference.NULL_TYPE)
      return NO;
    if (parentType == childType)
      return YES;
    if (parentType == VM_TypeReference.Word && childType.isWordType())
      return YES;
    if (parentType.isPrimitiveType() || childType.isPrimitiveType())
      return NO;
    if (parentType == VM_TypeReference.JavaLangObject)
      return YES;
    // Oh well, we're going to have to try to actually look 
    // at the type hierarchy.
    // IMPORTANT: We aren't allowed to cause dynamic class loading, 
    // so we have to roll some of this ourselves 
    // instead of simply calling VM_Runtime.instanceOf 
    // (which is allowed/required to load classes to answer the question).
    try {
      if (parentType.isArrayType()) {
        if (childType == VM_TypeReference.JavaLangObject)
          return MAYBE;        // arrays are subtypes of Object.
        if (!childType.isArrayType())
          return NO;
        VM_TypeReference parentET = parentType.getInnermostElementType();
        if (parentET == VM_TypeReference.JavaLangObject) {
          int LHSDimension = parentType.getDimensionality();
          int RHSDimension = childType.getDimensionality();
          if ((RHSDimension > LHSDimension) ||
              (RHSDimension == LHSDimension && 
               childType.getInnermostElementType().isClassType()))
            return YES; 
          else 
            return NO;
        } else {
          // parentType is [^k of something other than Object
          // If dimensionalities are equal, then we can reduce 
          // to isAssignableWith(parentET, childET).
          // If the dimensionalities are not equal then the answer is NO
          if (parentType.getDimensionality() == childType.getDimensionality())
            return includesType(parentET, childType.getInnermostElementType()); 
          else 
            return NO;
        }
      } else {                    // parentType.isClassType()
        if (!childType.isClassType()) {
          // parentType is known to not be java.lang.Object.
          return NO;
        }
        VM_Class childClass = (VM_Class)childType.peekResolvedType();
        VM_Class parentClass = (VM_Class)parentType.peekResolvedType();
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
              if (childClass.isFinal())
                return NO; 
              else {
                if (VM_Runtime.isAssignableWith(childClass, parentClass))
                  return MAYBE; 
                else 
                  return NO;
              }
            }
          }
        }
        return MAYBE;
      }
    } catch (Throwable e) {
      OPT_OptimizingCompilerException.UNREACHABLE();
      return MAYBE;            // placate jikes.
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
  public static OPT_IntConstantOperand getIntFromConstantPool (VM_Class klass, 
                                                               int index) {
    Offset offset = klass.getLiteralOffset(index) ;
    int val = VM_Statics.getSlotContentsAsInt(offset);
    return new OPT_IntConstantOperand(val);
  }

  /**
   * Get the double stored at a particular index of a class's constant
   * pool.
   */
  public static OPT_DoubleConstantOperand getDoubleFromConstantPool (VM_Class klass, 
                                                                     int index) {
    Offset offset = klass.getLiteralOffset(index) ;
    long val_raw = VM_Statics.getSlotContentsAsLong(offset);
    double val = Double.longBitsToDouble(val_raw);
    return new OPT_DoubleConstantOperand(val, offset);
  }

  /**
   * Get the float stored at a particular index of a class's constant
   * pool.
   */
  public static OPT_FloatConstantOperand getFloatFromConstantPool (VM_Class klass, 
                                                                   int index) {
    Offset offset = klass.getLiteralOffset(index) ;
    int val_raw = VM_Statics.getSlotContentsAsInt(offset);
    float val = Float.intBitsToFloat(val_raw);
    return new OPT_FloatConstantOperand(val, offset);
  }

  /**
   * Get the long stored at a particular index of a class's constant
   * pool.
   */
  public static OPT_LongConstantOperand getLongFromConstantPool (VM_Class klass, 
                                                                 int index) {
    Offset offset = klass.getLiteralOffset(index) ;
    long val = VM_Statics.getSlotContentsAsLong(offset);
    return new OPT_LongConstantOperand(val, offset);
  }

  /**
   * Get the String stored at a particular index of a class's constant
   * pool.
   */
  public static OPT_StringConstantOperand getStringFromConstantPool (VM_Class klass, int index) {
    Offset offset = klass.getLiteralOffset(index);
    String val;
    if (VM.runningVM) {
      val = (String)VM_Statics.getSlotContentsAsObject(offset);
    } else {
      // Sigh. What we really want to do is acquire the 
      // String object from the class constant pool.
      // But, we aren't set up to do that.  The following
      // isn't strictly correct, but is closer than the completely bogus
      // thing we were doing before. 
      // TODO: Fix this to do the right thing. 
      //       This will be wrong if someone is comparing string constants
      //       using ==, != since we're very unlikely to get the aliasing right.
      //       Then again, if you are using ==, != with strings and one of them
      //       isn't <null>, perhaps you deserve what you get.
      // This is defect 2838.
      val = ("BootImageStringConstant "+VM_Statics.offsetAsSlot(offset)).intern();
    }
    return new OPT_StringConstantOperand(val, offset);
  }
}
