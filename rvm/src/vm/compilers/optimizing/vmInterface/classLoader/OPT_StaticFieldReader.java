/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import com.ibm.JikesRVM.opt.ir.*;
import java.lang.reflect.Field;

/**
 * Code for accessing the value of a static field at
 * compile time.  This is used to optimize
 * getstatic's of initialized static fields
 * by replacing the getstatic with a constant operand.
 *
 * @author Steve Fink
 * @author Dave Grove
 * @modified Perry Cheng
 */
public abstract class OPT_StaticFieldReader implements VM_SizeConstants{

  /**
   * Returns a constant operand with the current value of a static field.
   *
   * @param field the static field whose current value we want to read
   * @return a constant operand representing the current value of the field.
   */
  public static OPT_ConstantOperand getStaticFieldValue(VM_Field field) 
    throws NoSuchFieldException {
    if (VM.VerifyAssertions) VM._assert(field.isStatic());

    VM_TypeReference fieldType = field.getType();
    int slot = field.getOffset() >>> LOG_BYTES_IN_INT;
    if (fieldType == VM_TypeReference.Address) {
      Object obj = getObjectStaticFieldValue(field);
      VM_Address val = (VM.runningVM) ? VM_Magic.objectAsAddress(obj) : (VM_Address) obj;
      return new OPT_AddressConstantOperand(val);
    } else if (fieldType == VM_TypeReference.Word) {
      Object obj = getObjectStaticFieldValue(field);
      VM_Word val = (VM.runningVM) ? VM_Magic.objectAsAddress(obj).toWord() : (VM_Word) obj;
      return new OPT_AddressConstantOperand(val.toAddress());
    } else if (fieldType == VM_TypeReference.Offset) {
      Object obj = getObjectStaticFieldValue(field);
      int v = (VM.runningVM) ? VM_Magic.objectAsAddress(obj).toInt() : ((VM_Offset) obj).toInt();
      return new OPT_AddressConstantOperand(VM_Address.fromInt(v));
    } else if (fieldType == VM_TypeReference.Extent) {
      Object obj = getObjectStaticFieldValue(field);
      int v = (VM.runningVM) ? VM_Magic.objectAsAddress(obj).toInt() : ((VM_Extent) obj).toInt();
      return new OPT_AddressConstantOperand(VM_Address.fromInt(v));
    } else if (fieldType.isIntLikeType()) {
      int val = getIntStaticFieldValue(field);
      return new OPT_IntConstantOperand(val);
    } else if (fieldType.isLongType()) {
      long val = getLongStaticFieldValue(field);
      return new OPT_LongConstantOperand(val, slot);
    } else if (fieldType.isFloatType()) {
      float val = getFloatStaticFieldValue(field);
      return new OPT_FloatConstantOperand(val, slot);
    } else if (fieldType.isDoubleType()) {
      double val = getDoubleStaticFieldValue(field);
      return new OPT_DoubleConstantOperand(val, slot);
    } else if (fieldType == VM_TypeReference.JavaLangString) {
      String val = (String)getObjectStaticFieldValue(field);
      return new OPT_StringConstantOperand(val, slot);
    } else {
      // TODO: Add array and scalar reference constant operands
      throw new OPT_OptimizingCompilerException("Unsupported type " + fieldType);
    }
  }

  /**
   * Returns the current contents of an int-like static field.
   * 
   * @param field a static field
   * @return the current value of the field
   */
  public static int getIntStaticFieldValue(VM_Field field) 
    throws NoSuchFieldException {
    if (VM.runningVM) {
      int slot = field.getOffset() >>> LOG_BYTES_IN_INT;
      return VM_Statics.getSlotContentsAsInt(slot);
    } else {
      try {
        Field f = getJDKField(field);
        VM_TypeReference fieldType = field.getType();
        if (fieldType.isBooleanType()) {
          boolean val = f.getBoolean(null);
          return val?1:0;
        } else if (fieldType.isByteType()) {
          return f.getByte(null);
        } else if (fieldType.isShortType()) {
          return f.getShort(null);
        } else if (fieldType.isIntType()) {
          return f.getInt(null);
        } else if (fieldType.isCharType()) {
          return f.getChar(null);
        } else {
          throw new OPT_OptimizingCompilerException("Unsupported type "+field+"\n");
        }
      } catch (IllegalAccessException e) {
        throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      } catch (IllegalArgumentException e) {
        throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      }
    }
  }

  /**
   * Returns the current contents of a float static field.
   * 
   * @param field a static field
   * @return the current value of the field
   */
  public static float getFloatStaticFieldValue(VM_Field field) 
    throws NoSuchFieldException {
    if (VM.runningVM) {
      int slot = field.getOffset() >>> LOG_BYTES_IN_INT;
      int bits = VM_Statics.getSlotContentsAsInt(slot);
      return VM_Magic.intBitsAsFloat(bits);
    } else {
      try {
        return getJDKField(field).getFloat(null);
      } catch (IllegalAccessException e) {
        throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      } catch (IllegalArgumentException e) {
        throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      }
    }
  }

  /**
   * Returns the current contents of a long static field.
   *
   * @param field a static field
   * @return the current value of the field
   */
  public static final long getLongStaticFieldValue(VM_Field field) 
    throws NoSuchFieldException {
    if (VM.runningVM) {
      int slot = field.getOffset() >>> LOG_BYTES_IN_INT;
      return VM_Statics.getSlotContentsAsLong(slot);
    } else {
      try {
        return getJDKField(field).getLong(null);
      } catch (IllegalAccessException e) {
        throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      } catch (IllegalArgumentException e) {
        throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      }
    }
  }

  /**
   * Returns the current contents of a double static field.
   *
   * @param field a static field
   * @return the current value of the field
   */
  public static final double getDoubleStaticFieldValue(VM_Field field) 
    throws NoSuchFieldException {
    if (VM.runningVM) {
      int slot = field.getOffset() >>> LOG_BYTES_IN_INT;
      long bits = VM_Statics.getSlotContentsAsLong(slot);
      return VM_Magic.longBitsAsDouble(bits);
    } else {
      try {
        return getJDKField(field).getDouble(null);
      } catch (IllegalAccessException e) {
        throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      } catch (IllegalArgumentException e) {
        throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      }
    }
  }

  /**
   * Returns the current contents of a reference static field.
   *
   * @param field a static field
   * @return the current value of the field
   */
  public static final Object getObjectStaticFieldValue(VM_Field field) 
    throws NoSuchFieldException {
    if (VM.runningVM) {
      int slot = field.getOffset() >>> LOG_BYTES_IN_INT;
      return VM_Statics.getSlotContentsAsObject(slot);
    } else {
      try {
        return getJDKField(field).get(null);
      } catch (IllegalAccessException e) {
        throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      } catch (IllegalArgumentException e) {
        throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      }
    }
  }


  /**
   * Does a static field null contain null?
   *
   * @param field a static field
   * @return true if the field contains null, false otherwise
   */
  public static final boolean isStaticFieldNull(VM_Field field) 
    throws NoSuchFieldException {
    return getObjectStaticFieldValue(field) == null;
  }

  /**
   * Get the type of an object contained in a static field.
   *
   * @param field a static field
   * @return type of value contained in the field
   */
  public static final VM_TypeReference getTypeFromStaticField (VM_Field field) 
    throws NoSuchFieldException {
    Object o = getObjectStaticFieldValue(field);
    if (o == null) return VM_TypeReference.NULL_TYPE;
    if (VM.runningVM) {
      return VM_Magic.getObjectType(o).getTypeRef();
    } else {
      Class rc = o.getClass();
      String className = rc.getName();
      VM_Atom classAtom = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/'));
      if (className.startsWith("[")) {
        // an array
        return VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), classAtom);
      } else {
        // a class
        VM_Atom classDescriptor = classAtom.descriptorFromClassName();
        return VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(), classDescriptor);
      }
    }
  }

  private static Field getJDKField(VM_Field field) 
    throws NoSuchFieldException {
    try {
      String cn = field.getDeclaringClass().toString();
      if (VM.writingBootImage) {
        if (cn.startsWith("java")) {
          throw new NoSuchFieldException("Avoiding host JDK/RVM incompatability problems");
        }
      }
      Field f = Class.forName(cn).getDeclaredField(field.getName().toString());
      f.setAccessible(true);
      return f;
    } catch (ClassNotFoundException e) {
      throw new NoSuchFieldException(field.toString());
    }
  }
}
