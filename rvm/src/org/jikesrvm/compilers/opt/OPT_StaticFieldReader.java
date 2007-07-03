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

import java.lang.reflect.Field;
import org.jikesrvm.VM;
import org.jikesrvm.VM_SizeConstants;
import org.jikesrvm.classloader.VM_Field;
import org.jikesrvm.classloader.VM_Type;
import org.jikesrvm.classloader.VM_TypeReference;
import org.jikesrvm.compilers.opt.ir.OPT_AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ClassConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_NullConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_ObjectConstantOperand;
import org.jikesrvm.compilers.opt.ir.OPT_StringConstantOperand;
import org.jikesrvm.runtime.VM_Magic;
import org.jikesrvm.runtime.VM_Statics;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Offset;
import org.vmmagic.unboxed.Word;

/**
 * Code for accessing the value of a static field at
 * compile time.  This is used to optimize
 * getstatic's of initialized static fields
 * by replacing the getstatic with a constant operand.
 */
public abstract class OPT_StaticFieldReader implements VM_SizeConstants {

  /**
   * Read the field from obj and return as the appropriate constant
   */
  public static OPT_ConstantOperand getFieldValueAsConstant(VM_Field field, Object obj) throws NoSuchFieldException {
    if (VM.VerifyAssertions) VM._assert(field.isFinal(), "Error reading field " + field);
    if (VM.VerifyAssertions) {
      VM._assert(field.getDeclaringClass().isInitialized() || field.getDeclaringClass().isInBootImage(),
                 "Error reading field " + field);
    }

    VM_TypeReference type = field.getType();
    if (VM.runningVM) {
      if (type.isReferenceType() && !type.isMagicType()) {
        Object value = field.getObjectValueUnchecked(obj);
        if (value != null) {
          return new OPT_ObjectConstantOperand(value, Offset.zero());
        } else {
          return new OPT_NullConstantOperand();
        }
      } else if (type.isIntType()) {
        return new OPT_IntConstantOperand(field.getIntValueUnchecked(obj));
      } else if (type.isBooleanType()) {
        return new OPT_IntConstantOperand(field.getBooleanValueUnchecked(obj) ? 1 : 0);
      } else if (type.isByteType()) {
        return new OPT_IntConstantOperand(field.getByteValueUnchecked(obj));
      } else if (type.isCharType()) {
        return new OPT_IntConstantOperand(field.getCharValueUnchecked(obj));
      } else if (type.isDoubleType()) {
        return new OPT_DoubleConstantOperand(field.getDoubleValueUnchecked(obj));
      } else if (type.isFloatType()) {
        return new OPT_FloatConstantOperand(field.getFloatValueUnchecked(obj));
      } else if (type.isLongType()) {
        return new OPT_LongConstantOperand(field.getLongValueUnchecked(obj));
      } else if (type.isShortType()) {
        return new OPT_IntConstantOperand(field.getShortValueUnchecked(obj));
      } else {
        OPT_OptimizingCompilerException.UNREACHABLE("Unknown type " + type);
        return null;
      }
    } else {
      try {
        String cn = field.getDeclaringClass().toString();
        Field f = Class.forName(cn).getDeclaredField(field.getName().toString());
        f.setAccessible(true);
        if (type.isReferenceType() && !type.isMagicType()) {
          Object value = f.get(obj);
          if (value != null) {
            return new OPT_ObjectConstantOperand(value, Offset.zero());
          } else {
            return new OPT_NullConstantOperand();
          }
        } else if (type.isIntType()) {
          return new OPT_IntConstantOperand(f.getInt(obj));
        } else if (type.isBooleanType()) {
          return new OPT_IntConstantOperand(f.getBoolean(obj) ? 1 : 0);
        } else if (type.isByteType()) {
          return new OPT_IntConstantOperand(f.getByte(obj));
        } else if (type.isCharType()) {
          return new OPT_IntConstantOperand(f.getChar(obj));
        } else if (type.isDoubleType()) {
          return new OPT_DoubleConstantOperand(f.getDouble(obj));
        } else if (type.isFloatType()) {
          return new OPT_FloatConstantOperand(f.getFloat(obj));
        } else if (type.isLongType()) {
          return new OPT_LongConstantOperand(f.getLong(obj));
        } else if (type.isShortType()) {
          return new OPT_IntConstantOperand(f.getShort(obj));
        } else {
          OPT_OptimizingCompilerException.UNREACHABLE("Unknown type " + type);
          return null;
        }
      } catch (IllegalArgumentException e) {
        throw new NoSuchFieldException(field.toString());
      } catch (IllegalAccessException e) {
        throw new NoSuchFieldException(field.toString());
      } catch (NoSuchFieldError e) {
        throw new NoSuchFieldException(field.toString());
      } catch (ClassNotFoundException e) {
        throw new NoSuchFieldException(field.toString());
      } catch (NoClassDefFoundError e) {
        throw new NoSuchFieldException(field.toString());
      } catch (IllegalAccessError e) {
        throw new NoSuchFieldException(field.toString());
      }
    }
  }

  /**
   * Returns a constant operand with the current value of a static field.
   *
   * @param field the static field whose current value we want to read
   * @return a constant operand representing the current value of the field.
   */
  public static OPT_ConstantOperand getStaticFieldValue(VM_Field field) throws NoSuchFieldException {
    if (VM.VerifyAssertions) VM._assert(field.isFinal(), "Error reading field " + field);
    if (VM.VerifyAssertions) VM._assert(field.isStatic(), "Error reading field " + field);
    if (VM.VerifyAssertions) {
      VM._assert(field.getDeclaringClass().isInitialized() || field.getDeclaringClass().isInBootImage(),
                 "Error reading field " + field);
    }

    VM_TypeReference fieldType = field.getType();
    Offset off = field.getOffset();
    if ((fieldType == VM_TypeReference.Address) ||
        (fieldType == VM_TypeReference.Word) ||
        (fieldType == VM_TypeReference.Offset) ||
        (fieldType == VM_TypeReference.Extent)) {
      Address val = getAddressStaticFieldValue(field);
      return new OPT_AddressConstantOperand(val);
    } else if (fieldType.isIntLikeType()) {
      int val = getIntStaticFieldValue(field);
      return new OPT_IntConstantOperand(val);
    } else if (fieldType.isLongType()) {
      long val = getLongStaticFieldValue(field);
      return new OPT_LongConstantOperand(val, off);
    } else if (fieldType.isFloatType()) {
      float val = getFloatStaticFieldValue(field);
      return new OPT_FloatConstantOperand(val, off);
    } else if (fieldType.isDoubleType()) {
      double val = getDoubleStaticFieldValue(field);
      return new OPT_DoubleConstantOperand(val, off);
    } else { // Reference type
      if (VM.VerifyAssertions) VM._assert(fieldType.isReferenceType());
      Object val = getObjectStaticFieldValue(field);
      if (val == null) {
        return new OPT_NullConstantOperand();
      } else if (fieldType == VM_TypeReference.JavaLangString) {
        return new OPT_StringConstantOperand((String) val, off);
      } else if (fieldType == VM_TypeReference.JavaLangClass) {
        Class<?> klass = (Class<?>) getObjectStaticFieldValue(field);
        VM_Type type;
        if (VM.runningVM) {
          type = java.lang.JikesRVMSupport.getTypeForClass(klass);
        } else {
          type = VM_TypeReference.findOrCreate(klass).resolve();
        }
        return new OPT_ClassConstantOperand(type.getClassForType(), off);
      } else {
        return new OPT_ObjectConstantOperand(val, off);
      }
    }
  }

  /**
   * Returns the current contents of an int-like static field.
   *
   * @param field a static field
   * @return the current value of the field
   */
  public static int getIntStaticFieldValue(VM_Field field) throws NoSuchFieldException {
    if (VM.runningVM) {
      return VM_Statics.getSlotContentsAsInt(field.getOffset());
    } else {
      try {
        Field f = getJDKField(field);
        VM_TypeReference fieldType = field.getType();
        if (fieldType.isBooleanType()) {
          boolean val = f.getBoolean(null);
          return val ? 1 : 0;
        } else if (fieldType.isByteType()) {
          return f.getByte(null);
        } else if (fieldType.isShortType()) {
          return f.getShort(null);
        } else if (fieldType.isIntType()) {
          return f.getInt(null);
        } else if (fieldType.isCharType()) {
          return f.getChar(null);
        } else {
          throw new OPT_OptimizingCompilerException("Unsupported type " + field + "\n");
        }
      } catch (IllegalAccessException e) {
        throw new OPT_OptimizingCompilerException("Accessing " + field + " caused " + e);
      } catch (IllegalArgumentException e) {
        throw new OPT_OptimizingCompilerException("Accessing " + field + " caused " + e);
      }
    }
  }

  /**
   * Returns the current contents of a float static field.
   *
   * @param field a static field
   * @return the current value of the field
   */
  public static float getFloatStaticFieldValue(VM_Field field) throws NoSuchFieldException {
    if (VM.runningVM) {
      int bits = VM_Statics.getSlotContentsAsInt(field.getOffset());
      return VM_Magic.intBitsAsFloat(bits);
    } else {
      try {
        return getJDKField(field).getFloat(null);
      } catch (IllegalAccessException e) {
        throw new OPT_OptimizingCompilerException("Accessing " + field + " caused " + e);
      } catch (IllegalArgumentException e) {
        throw new OPT_OptimizingCompilerException("Accessing " + field + " caused " + e);
      }
    }
  }

  /**
   * Returns the current contents of a long static field.
   *
   * @param field a static field
   * @return the current value of the field
   */
  public static long getLongStaticFieldValue(VM_Field field) throws NoSuchFieldException {
    if (VM.runningVM) {
      return VM_Statics.getSlotContentsAsLong(field.getOffset());
    } else {
      try {
        return getJDKField(field).getLong(null);
      } catch (IllegalAccessException e) {
        throw new OPT_OptimizingCompilerException("Accessing " + field + " caused " + e);
      } catch (IllegalArgumentException e) {
        throw new OPT_OptimizingCompilerException("Accessing " + field + " caused " + e);
      }
    }
  }

  /**
   * Returns the current contents of a double static field.
   *
   * @param field a static field
   * @return the current value of the field
   */
  public static double getDoubleStaticFieldValue(VM_Field field) throws NoSuchFieldException {
    if (VM.runningVM) {
      long bits = VM_Statics.getSlotContentsAsLong(field.getOffset());
      return VM_Magic.longBitsAsDouble(bits);
    } else {
      try {
        return getJDKField(field).getDouble(null);
      } catch (IllegalAccessException e) {
        throw new OPT_OptimizingCompilerException("Accessing " + field + " caused " + e);
      } catch (IllegalArgumentException e) {
        throw new OPT_OptimizingCompilerException("Accessing " + field + " caused " + e);
      }
    }
  }

  /**
   * Returns the current contents of a reference static field.
   *
   * @param field a static field
   * @return the current value of the field
   */
  public static Object getObjectStaticFieldValue(VM_Field field) throws NoSuchFieldException {
    if (VM.runningVM) {
      return VM_Statics.getSlotContentsAsObject(field.getOffset());
    } else {
      try {
        return getJDKField(field).get(null);
      } catch (IllegalAccessException e) {
        throw new OPT_OptimizingCompilerException("Accessing " + field + " caused " + e);
      } catch (IllegalArgumentException e) {
        throw new OPT_OptimizingCompilerException("Accessing " + field + " caused " + e);
      }
    }
  }

  /**
   * Returns the current contents of a Address static field.
   *
   * @param field a static field
   * @return the current value of the field
   */
  public static Address getAddressStaticFieldValue(VM_Field field) throws NoSuchFieldException {
    if (VM.runningVM) {
      return VM_Statics.getSlotContentsAsAddress(field.getOffset());
    } else {
      try {
        Object unboxed = getJDKField(field).get(null);
        if (unboxed instanceof Address) {
          return (Address) unboxed;
        } else if (unboxed instanceof Word) {
          return ((Word) unboxed).toAddress();
        } else if (unboxed instanceof Extent) {
          return ((Extent) unboxed).toWord().toAddress();
        } else if (unboxed instanceof Offset) {
          return ((Offset) unboxed).toWord().toAddress();
        } else {
          if (VM.VerifyAssertions) VM._assert(false);
          return Address.zero();
        }
      } catch (IllegalAccessException e) {
        throw new OPT_OptimizingCompilerException("Accessing " + field + " caused " + e);
      } catch (IllegalArgumentException e) {
        throw new OPT_OptimizingCompilerException("Accessing " + field + " caused " + e);
      }
    }
  }

  /**
   * Does a static field null contain null?
   *
   * @param field a static field
   * @return true if the field contains null, false otherwise
   */
  public static boolean isStaticFieldNull(VM_Field field) throws NoSuchFieldException {
    return getObjectStaticFieldValue(field) == null;
  }

  /**
   * Get the type of an object contained in a static field.
   *
   * @param field a static field
   * @return type of value contained in the field
   */
  public static VM_TypeReference getTypeFromStaticField(VM_Field field) throws NoSuchFieldException {
    Object o = getObjectStaticFieldValue(field);
    if (o == null) return VM_TypeReference.NULL_TYPE;
    if (VM.runningVM) {
      return VM_Magic.getObjectType(o).getTypeRef();
    } else {
      return VM_TypeReference.findOrCreate(o.getClass());
    }
  }

  /**
   * Utilitiy to convert a VM_Field to a java.lang.reflect.Field
   */
  private static Field getJDKField(VM_Field field) throws NoSuchFieldException {
    try {
      String cn = field.getDeclaringClass().toString();
      Field f = Class.forName(cn).getDeclaredField(field.getName().toString());
      f.setAccessible(true);
      return f;
    } catch (NoSuchFieldError e) {
      throw new NoSuchFieldException(field.toString());
    } catch (ClassNotFoundException e) {
      throw new NoSuchFieldException(field.toString());
    } catch (NoClassDefFoundError e) {
      throw new NoSuchFieldException(field.toString());
    } catch (IllegalAccessError e) {
      throw new NoSuchFieldException(field.toString());
    }
  }
}
