/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;
import java.lang.reflect.Field;

/**
 * Code for accessing the value of a static field at
 * compile time.  This is used to optimize
 * getstatic's of initialized static fields
 * by replacing the getstatic with a constant operand.
 *
 * @author Steve Fink
 * @author Dave Grove
 */
abstract class OPT_StaticFieldReader {

  /**
   * Returns a constant operand with the current value of a static field.
   *
   * @param field the static field whose current value we want to read
   * @return a constant operand representing the current value of the field.
   */
  public static OPT_ConstantOperand getStaticFieldValue(VM_Field field) 
    throws NoSuchFieldException {
    if (VM.VerifyAssertions) VM.assert(field.isStatic());

    VM_Type fieldType = field.getType();
    if (fieldType.isIntLikeType()) {
      int val = getIntStaticFieldValue(field);
      return new OPT_IntConstantOperand(val);
    } else if (fieldType.isLongType()) {
      long val = getLongStaticFieldValue(field);
      return new OPT_LongConstantOperand(val);
    } else if (fieldType.isFloatType()) {
      float val = getFloatStaticFieldValue(field);
      return new OPT_FloatConstantOperand(val);
    } else if (fieldType.isDoubleType()) {
      double val = getDoubleStaticFieldValue(field);
      return new OPT_DoubleConstantOperand(val);
    } else if (fieldType == VM_Type.JavaLangStringType) {
      int slot = field.getOffset() >>> 2;
      OPT_ClassLoaderProxy.StringWrapper sw = new OPT_RVMClassLoaderProxy.RVMStringWrapper(slot);
      return new OPT_StringConstantOperand(sw);
    } else {
      // TODO: Add array and scalar reference constant operands
      throw new OPT_OptimizingCompilerException("Unsupported type");
    }
  }

  /**
   * Returns the current contents of an int-like static field.
   * 
   * @param field a static field
   * @return the current value of the field
   */
  static int getIntStaticFieldValue(VM_Field field) 
    throws NoSuchFieldException {
    if (VM.runningVM) {
      int slot = field.getOffset() >>> 2;
      return VM_Statics.getSlotContentsAsInt(slot);
    } else {
      try {
	Field f = getJDKField(field);
	VM_Type fieldType = field.getType();
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
  static float getFloatStaticFieldValue(VM_Field field) 
    throws NoSuchFieldException {
    if (VM.runningVM) {
      int slot = field.getOffset() >>> 2;
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
   * Returns a constant operand with the current value of a long
   * static field.
   *
   * @param field a static field
   * @return a constant operand representing the current value of the field
   */
  static final long getLongStaticFieldValue(VM_Field field) 
    throws NoSuchFieldException {
    if (VM.runningVM) {
      int slot = field.getOffset() >>> 2;
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
   * Returns a constant operand with the current value of a double
   * static field.
   *
   * @param field a static field
   * @return a constant operand representing the current value of the field
   */
  static final double getDoubleStaticFieldValue(VM_Field field) 
    throws NoSuchFieldException {
    if (VM.runningVM) {
      int slot = field.getOffset() >>> 2;
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
   * Does a static field null contain null?
   *
   * @param field a static field
   * @return true if the field contains null, false otherwise
   */
  static final boolean isStaticFieldNull(VM_Field field) 
    throws NoSuchFieldException {
    if (VM.runningVM) {
      int slot = field.getOffset() >>> 2;
      Object it = VM_Statics.getSlotContentsAsObject(slot);
      return it == null;
    } else {
      try {
	return getJDKField(field).get(null) == null;
      } catch (IllegalAccessException e) {
	throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      } catch (IllegalArgumentException e) {
	throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      }
    }      
  }

  /**
   * Get the type of an object contained in a static field.
   *
   * @param field a static field
   * @return type of value contained in the field
   */
  static final VM_Type getTypeFromStaticField (VM_Field field) 
    throws NoSuchFieldException {
    if (VM.runningVM) {
      int slot = field.getOffset() >>> 2;
      Object it = VM_Statics.getSlotContentsAsObject(slot);
      if (VM.VerifyAssertions) VM.assert(it != null);
      return VM_Magic.getObjectType(it);
    } else {
      try {
	Object o = getJDKField(field).get(null);
	if (o == null) {
	  return OPT_ClassLoaderProxy.NULL_TYPE;
	} else {
	  Class rc = o.getClass();
	  String className = rc.getName();
	  if (className.startsWith("[")) {
	    // an array
	    return VM_ClassLoader.findOrCreateType(VM_Atom.findOrCreateAsciiAtom(className));
	  } else {
	    // a class
	    VM_Atom classDescriptor = 
	      VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
	    return VM_ClassLoader.findOrCreateType(classDescriptor);
	  }
	}
      } catch (IllegalAccessException e) {
	throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      } catch (IllegalArgumentException e) {
	throw new OPT_OptimizingCompilerException("Accessing "+field+" caused "+e);
      }
    }
  }

  private static Field getJDKField(VM_Field field) 
    throws NoSuchFieldException {
    try {
      String cn = field.getDeclaringClass().getName();
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
