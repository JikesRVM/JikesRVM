/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang.reflect;

import com.ibm.JikesRVM.classloader.VM_Field;
import com.ibm.JikesRVM.classloader.VM_TypeReference;
import com.ibm.JikesRVM.VM_Reflection;

/**
 * Implementation of java.lang.reflect.Field for JikesRVM.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API. 
 *
 * @author John Barton 
 * @author Julian Dolby
 * @author Stephen Fink
 * @author Eugene Gluzberg
 * @author Dave Grove
 */
public final class Field extends AccessibleObject implements Member {

  VM_Field field;

  /**
   * Prevent this class from being instantiated.
   */
  private Field() {}
    
  Field(VM_Field f) {
    field = f;
  }
    
  public boolean equals(Object object) {
    if (object instanceof Field) {
      return field == ((Field)object).field;
    } else {
      return false;
    }
  }

  public Object get(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return (field.getObject(object));
  }

  public boolean getBoolean(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return field.getBooleanValue(object);
  }
    
  public byte getByte(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return field.getByteValue(object);
  }

  public char getChar(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return field.getCharValue(object);
  }

  public Class getDeclaringClass() {
    return field.getDeclaringClass().getClassForType();
  }

  public double getDouble(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return field.getDoubleValue(object);
  }

  public float getFloat(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return field.getFloatValue(object);
  }

  public int getInt(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return field.getIntValue(object);
  }

  public long getLong(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return field.getLongValue(object);
  }

  public int getModifiers() {
    return field.getModifiers();
  }

  public String getName() {
    return field.getName().toString();
  }
    
  public short getShort(Object object) throws IllegalAccessException, IllegalArgumentException {
    checkReadAccess(object);
    return field.getShortValue(object);
  }

  public Class getType() {
    try {
      return field.getType().resolve().getClassForType();
    } catch (ClassNotFoundException e) {
      throw new InternalError("How can this happen??");
    }
  }

  public int hashCode() {
    int code1 = getName().hashCode();
    int code2 = field.getDeclaringClass().toString().hashCode();
    return code1 ^ code2;
  }

  public void set(Object object, Object value) 
    throws IllegalAccessException, IllegalArgumentException     {
    checkWriteAccess(object);

    VM_TypeReference type = field.getType();
    if (type.isReferenceType()) {
      field.setObjectValue(object, value);
    } else if (type.isCharType()) {
      field.setCharValue(object, VM_Reflection.unwrapChar(value));
    } else if (type.isDoubleType()) {
      field.setDoubleValue(object, VM_Reflection.unwrapDouble(value));
    } else if (type.isFloatType()) {
      field.setFloatValue(object, VM_Reflection.unwrapFloat(value));
    } else if (type.isLongType()) {
      field.setLongValue(object, VM_Reflection.unwrapLong(value));
    } else if (type.isIntType()) {
      field.setIntValue(object, VM_Reflection.unwrapInt(value));
    } else if (type.isShortType()) {
      field.setShortValue(object, VM_Reflection.unwrapShort(value));
    } else if (type.isByteType()) {
      field.setByteValue(object, VM_Reflection.unwrapByte(value));
    } else if (type.isBooleanType()) {
      field.setBooleanValue(object, VM_Reflection.unwrapBoolean(value));
    }
  }
    
  public void setBoolean(Object object, boolean value) 
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    field.setBooleanValue(object, value);
  }

  public void setByte(Object object, byte value) 
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    field.setByteValue(object, value);
  }

  public void setChar(Object object, char value)
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    field.setCharValue(object, value);
  }

  public void setDouble(Object object, double value)
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    field.setDoubleValue(object, value);
  }

  public void setFloat(Object object, float value)
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    field.setFloatValue(object, value);
  }

  public void setInt(Object object, int value)
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    field.setIntValue(object, value);
  }

  public void setLong(Object object, long value)
    throws IllegalAccessException, IllegalArgumentException    {
    checkWriteAccess(object);
    field.setLongValue(object, value);
  }

  public void setShort(Object object, short value)
    throws IllegalAccessException, IllegalArgumentException   {
    checkWriteAccess(object);
    field.setShortValue(object, value);
  }

  public String toString() {
    int arity = 0;

    StringBuffer buf = new StringBuffer();
    buf.append(Modifier.toString(getModifiers()));
    buf.append(" ");

    Class current = getType();
    while(current.isArray()) {
      current = current.getComponentType();
      arity++;
    }
    buf.append(current.getName());
    for(;arity > 0; arity--) buf.append("[]");

    buf.append(" ");
    buf.append(getDeclaringClass().getName());
    buf.append(".");
    buf.append(getName());
    return buf.toString();
  }

  private void checkReadAccess(Object obj) 
    throws IllegalAccessException, IllegalArgumentException  {
    if (!field.isStatic()) {
      if (obj == null)
	throw new NullPointerException();

      if (!field.getDeclaringClass().getClassForType().isInstance(obj))
	throw new IllegalArgumentException();
    }

    // TODO: enforce accessibility

    // TODO: if field is static, must initialize declaring class
  }

  private void checkWriteAccess(Object obj) 
    throws IllegalAccessException, IllegalArgumentException  {
    if (!field.isStatic()) {
      if (obj == null)
	throw new NullPointerException();

      if (!field.getDeclaringClass().getClassForType().isInstance(obj))
	throw new IllegalArgumentException();
    }

    // TODO: enforce accessibility

    // TODO: if field is static, must initialize declaring class

    if (field.isFinal())
      throw new IllegalAccessException();
  }

}
