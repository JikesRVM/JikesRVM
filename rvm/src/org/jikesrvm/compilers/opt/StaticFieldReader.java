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

import java.lang.reflect.Field;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.compilers.opt.ir.operand.AddressConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.ClassConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.ConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.DoubleConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.FloatConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.IntConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.LongConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.NullConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.ObjectConstantOperand;
import org.jikesrvm.compilers.opt.ir.operand.StringConstantOperand;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Statics;
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
public abstract class StaticFieldReader {

  public static ConstantOperand getFieldValueAsConstant(RVMField field, Object obj) throws NoSuchFieldException {
    if (VM.VerifyAssertions) {
      boolean isFinalField = field.isFinal();
      boolean isInitializedField = field.getDeclaringClass().isInitialized() || field.getDeclaringClass().isInBootImage();
      if (!(isFinalField && isInitializedField)) {
        String msg = "Error reading field " + field;
        VM._assert(VM.NOT_REACHED, msg);
      }
    }

    TypeReference type = field.getType();
    if (VM.runningVM) {
      if (type.isReferenceType() && (!type.isMagicType() || type.isUnboxedArrayType())) {
        Object value = field.getObjectValueUnchecked(obj);
        if (value != null) {
          return new ObjectConstantOperand(value, Offset.zero());
        } else {
          return new NullConstantOperand();
        }
      } else if (type.isWordLikeType()) {
        return new AddressConstantOperand(field.getWordValueUnchecked(obj).toAddress());
      } else if (type.isIntType()) {
        return new IntConstantOperand(field.getIntValueUnchecked(obj));
      } else if (type.isBooleanType()) {
        return new IntConstantOperand(field.getBooleanValueUnchecked(obj) ? 1 : 0);
      } else if (type.isByteType()) {
        return new IntConstantOperand(field.getByteValueUnchecked(obj));
      } else if (type.isCharType()) {
        return new IntConstantOperand(field.getCharValueUnchecked(obj));
      } else if (type.isDoubleType()) {
        return new DoubleConstantOperand(field.getDoubleValueUnchecked(obj));
      } else if (type.isFloatType()) {
        return new FloatConstantOperand(field.getFloatValueUnchecked(obj));
      } else if (type.isLongType()) {
        return new LongConstantOperand(field.getLongValueUnchecked(obj));
      } else if (type.isShortType()) {
        return new IntConstantOperand(field.getShortValueUnchecked(obj));
      } else {
        OptimizingCompilerException.UNREACHABLE("Unknown type " + type);
        return null;
      }
    } else {
      try {
        String cn = field.getDeclaringClass().toString();
        Field f = Class.forName(cn).getDeclaredField(field.getName().toString());
        f.setAccessible(true);
        if (type.isReferenceType() && (!type.isMagicType() || type.isUnboxedArrayType())) {
          Object value = f.get(obj);
          if (value != null) {
            return new ObjectConstantOperand(value, Offset.zero());
          } else {
            return new NullConstantOperand();
          }
        } else if (type.isWordLikeType()) {
          Object value = f.get(obj);
          if (type.equals(TypeReference.Word))
            return new AddressConstantOperand((Word)value);
          else if (type.equals(TypeReference.Address))
            return new AddressConstantOperand((Address)value);
          else if (type.equals(TypeReference.Offset))
            return new AddressConstantOperand((Offset)value);
          else if (type.equals(TypeReference.Extent))
            return new AddressConstantOperand((Extent)value);
          else {
            OptimizingCompilerException.UNREACHABLE("Unknown word type " + type);
            return null;
          }
        } else if (type.isIntType()) {
          return new IntConstantOperand(f.getInt(obj));
        } else if (type.isBooleanType()) {
          return new IntConstantOperand(f.getBoolean(obj) ? 1 : 0);
        } else if (type.isByteType()) {
          return new IntConstantOperand(f.getByte(obj));
        } else if (type.isCharType()) {
          return new IntConstantOperand(f.getChar(obj));
        } else if (type.isDoubleType()) {
          return new DoubleConstantOperand(f.getDouble(obj));
        } else if (type.isFloatType()) {
          return new FloatConstantOperand(f.getFloat(obj));
        } else if (type.isLongType()) {
          return new LongConstantOperand(f.getLong(obj));
        } else if (type.isShortType()) {
          return new IntConstantOperand(f.getShort(obj));
        } else {
          OptimizingCompilerException.UNREACHABLE(cn + "." + f.getName() + " has unknown type " + type);
          return null;
        }
      } catch (IllegalArgumentException e) {
        throwNoSuchFieldExceptionWithCause(field, e);
      } catch (IllegalAccessException e) {
        throwNoSuchFieldExceptionWithCause(field, e);
      } catch (NoSuchFieldError e) {
        throwNoSuchFieldExceptionWithCause(field, e);
      } catch (ClassNotFoundException e) {
        throwNoSuchFieldExceptionWithCause(field, e);
      } catch (NoClassDefFoundError e) {
        throwNoSuchFieldExceptionWithCause(field, e);
      } catch (IllegalAccessError e) {
        throwNoSuchFieldExceptionWithCause(field, e);
      }
      assertNotReached();
      return null;
    }
  }

  /**
   * Returns a constant operand with the current value of a static field.
   *
   * @param field the static field whose current value we want to read
   * @return a constant operand representing the current value of the field.
   * @throws NoSuchFieldException when the field could not be found
   */
  public static ConstantOperand getStaticFieldValue(RVMField field) throws NoSuchFieldException {
    if (VM.VerifyAssertions) {
      boolean fieldIsReady = field.getDeclaringClass().isInitialized() ||
          field.getDeclaringClass().isInBootImage();
      boolean isFinalField = field.isFinal();
      boolean isStaticField = field.isStatic();
      if (!(isFinalField && isStaticField && fieldIsReady)) {
        String msg = "Error reading field " + field;
        VM._assert(VM.NOT_REACHED, msg);
      }
    }

    TypeReference fieldType = field.getType();
    Offset off = field.getOffset();
    if ((fieldType == TypeReference.Address) ||
        (fieldType == TypeReference.Word) ||
        (fieldType == TypeReference.Offset) ||
        (fieldType == TypeReference.Extent)) {
      Address val = getAddressStaticFieldValue(field);
      return new AddressConstantOperand(val);
    } else if (fieldType.isIntLikeType()) {
      int val = getIntStaticFieldValue(field);
      return new IntConstantOperand(val);
    } else if (fieldType.isLongType()) {
      long val = getLongStaticFieldValue(field);
      return new LongConstantOperand(val);
    } else if (fieldType.isFloatType()) {
      float val = getFloatStaticFieldValue(field);
      return new FloatConstantOperand(val, off);
    } else if (fieldType.isDoubleType()) {
      double val = getDoubleStaticFieldValue(field);
      return new DoubleConstantOperand(val, off);
    } else { // Reference type
      if (VM.VerifyAssertions) VM._assert(fieldType.isReferenceType());
      Object val = getObjectStaticFieldValue(field);
      if (val == null) {
        return new NullConstantOperand();
      } else if (fieldType == TypeReference.JavaLangString) {
        return new StringConstantOperand((String) val, off);
      } else if (fieldType == TypeReference.JavaLangClass) {
        Class<?> klass = (Class<?>) getObjectStaticFieldValue(field);
        RVMType type;
        if (VM.runningVM) {
          type = java.lang.JikesRVMSupport.getTypeForClass(klass);
        } else {
          type = TypeReference.findOrCreate(klass).resolve();
        }
        return new ClassConstantOperand(type.getClassForType(), off);
      } else {
        return new ObjectConstantOperand(val, off);
      }
    }
  }

  /**
   * Returns the current contents of an int-like static field.
   *
   * @param field a static field
   * @return the current value of the field
   * @throws NoSuchFieldException when the field could not be found
   */
  public static int getIntStaticFieldValue(RVMField field) throws NoSuchFieldException {
    if (VM.runningVM) {
      return Statics.getSlotContentsAsInt(field.getOffset());
    } else {
      try {
        Field f = getJDKField(field);
        TypeReference fieldType = field.getType();
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
          throw new OptimizingCompilerException("Unsupported type " + field + "\n");
        }
      } catch (IllegalAccessException e) {
        throwOptimizingCompilerExceptionBecauseOfIllegalAccess(field, e);
      } catch (IllegalArgumentException e) {
        throwOptimizingCompilerExceptionBecauseOfIllegalAccess(field, e);
      }
      assertNotReached();
      return 0;
    }
  }

  /**
   * Returns the current contents of a float static field.
   *
   * @param field a static field
   * @return the current value of the field
   * @throws NoSuchFieldException when the field could not be found
   */
  public static float getFloatStaticFieldValue(RVMField field) throws NoSuchFieldException {
    if (VM.runningVM) {
      int bits = Statics.getSlotContentsAsInt(field.getOffset());
      return Magic.intBitsAsFloat(bits);
    } else {
      try {
        return getJDKField(field).getFloat(null);
      } catch (IllegalAccessException e) {
        throwOptimizingCompilerExceptionBecauseOfIllegalAccess(field, e);
      } catch (IllegalArgumentException e) {
        throwOptimizingCompilerExceptionBecauseOfIllegalAccess(field, e);
      }
      assertNotReached();
      return 0f;
    }
  }

  /**
   * Returns the current contents of a long static field.
   *
   * @param field a static field
   * @return the current value of the field
   * @throws NoSuchFieldException when the field could not be found
   */
  public static long getLongStaticFieldValue(RVMField field) throws NoSuchFieldException {
    if (VM.runningVM) {
      return Statics.getSlotContentsAsLong(field.getOffset());
    } else {
      try {
        return getJDKField(field).getLong(null);
      } catch (IllegalAccessException e) {
        throwOptimizingCompilerExceptionBecauseOfIllegalAccess(field, e);
      } catch (IllegalArgumentException e) {
        throwOptimizingCompilerExceptionBecauseOfIllegalAccess(field, e);
      }
      assertNotReached();
      return 0L;
    }
  }

  /**
   * Returns the current contents of a double static field.
   *
   * @param field a static field
   * @return the current value of the field
   * @throws NoSuchFieldException when the field could not be found
   */
  public static double getDoubleStaticFieldValue(RVMField field) throws NoSuchFieldException {
    if (VM.runningVM) {
      long bits = Statics.getSlotContentsAsLong(field.getOffset());
      return Magic.longBitsAsDouble(bits);
    } else {
      try {
        return getJDKField(field).getDouble(null);
      } catch (IllegalAccessException e) {
        throwOptimizingCompilerExceptionBecauseOfIllegalAccess(field, e);
      } catch (IllegalArgumentException e) {
        throwOptimizingCompilerExceptionBecauseOfIllegalAccess(field, e);
      }
      assertNotReached();
      return 0d;
    }
  }

  /**
   * Returns the current contents of a reference static field.
   *
   * @param field a static field
   * @return the current value of the field
   * @throws NoSuchFieldException when the field could not be found
   */
  public static Object getObjectStaticFieldValue(RVMField field) throws NoSuchFieldException {
    if (VM.runningVM) {
      return Statics.getSlotContentsAsObject(field.getOffset());
    } else {
      try {
        return getJDKField(field).get(null);
      } catch (IllegalAccessException e) {
        throwOptimizingCompilerExceptionBecauseOfIllegalAccess(field, e);
      } catch (IllegalArgumentException e) {
        throwOptimizingCompilerExceptionBecauseOfIllegalAccess(field, e);
      }
      assertNotReached();
      return null;
    }
  }

  /**
   * Returns the current contents of a Address static field.
   *
   * @param field a static field
   * @return the current value of the field
   * @throws NoSuchFieldException when the field could not be found
   */
  public static Address getAddressStaticFieldValue(RVMField field) throws NoSuchFieldException {
    if (VM.runningVM) {
      return Statics.getSlotContentsAsAddress(field.getOffset());
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
          if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
          return Address.zero();
        }
      } catch (IllegalAccessException e) {
        throwOptimizingCompilerExceptionBecauseOfIllegalAccess(field, e);
      } catch (IllegalArgumentException e) {
        throwOptimizingCompilerExceptionBecauseOfIllegalAccess(field, e);
      }
      assertNotReached();
      return Address.zero();
    }
  }

  /**
   * Does a static field null contain {@code null}?
   *
   * @param field a static field
   * @return {@code true} if the field contains {@code null}, {@code false} otherwise
   * @throws NoSuchFieldException when the field could not be found
   */
  public static boolean isStaticFieldNull(RVMField field) throws NoSuchFieldException {
    return getObjectStaticFieldValue(field) == null;
  }

  /**
   * Get the type of an object contained in a static field.
   *
   * @param field a static field
   * @return type of value contained in the field
   * @throws NoSuchFieldException when the field could not be found
   */
  public static TypeReference getTypeFromStaticField(RVMField field) throws NoSuchFieldException {
    Object o = getObjectStaticFieldValue(field);
    if (o == null) return TypeReference.NULL_TYPE;
    if (VM.runningVM) {
      return Magic.getObjectType(o).getTypeRef();
    } else {
      return TypeReference.findOrCreate(o.getClass());
    }
  }

  /**
   * Converts a RVMField to a java.lang.reflect.Field.
   *
   * @param field the internal field representation
   * @return the java.lang field representation
   * @throws NoSuchFieldException when the field could not be found
   */
  private static Field getJDKField(RVMField field) throws NoSuchFieldException {
    try {
      String cn = field.getDeclaringClass().toString();
      if (VM.BuildForGnuClasspath &&
          field.getDeclaringClass().getClassForType().equals(java.lang.reflect.Proxy.class) &&
          field.getName().toString().equals("proxyClasses")) {
        // Avoid confusing bootstrap JVM and classpath fields
        throw new NoSuchFieldException(field.toString());
      }
      Field f = Class.forName(cn).getDeclaredField(field.getName().toString());
      f.setAccessible(true);
      return f;
    } catch (NoSuchFieldError e) {
      throwNoSuchFieldExceptionWithCause(field, e);
    } catch (ClassNotFoundException e) {
      throwNoSuchFieldExceptionWithCause(field, e);
    } catch (NoClassDefFoundError e) {
      throwNoSuchFieldExceptionWithCause(field, e);
    } catch (IllegalAccessError e) {
      throwNoSuchFieldExceptionWithCause(field, e);
    } catch (UnsatisfiedLinkError e) {
      throwNoSuchFieldExceptionWithCause(field, e);
    }
    assertNotReached();
    return null;
  }

  private static void throwOptimizingCompilerExceptionBecauseOfIllegalAccess(
      RVMField field, Throwable e) {
    throw new OptimizingCompilerException("Accessing " + field + " caused " + e);
  }

  private static void throwNoSuchFieldExceptionWithCause(RVMField field, Throwable cause)
      throws NoSuchFieldException {
    NoSuchFieldException e = new NoSuchFieldException(field.toString());
    e.initCause(cause);
    throw e;
  }

  private static void assertNotReached() {
    if (VM.VerifyAssertions) {
      VM._assert(VM.NOT_REACHED, "Exception should have been thrown beforehand");
    } else {
      VM.sysFail("An exception should have been thrown before this point was reached!");
    }
  }

}
