/* 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.lang.reflect;

import org.jikesrvm.classloader.VM_Field;

/**
 * This class must be implemented by the VM vendor. This class models a field.
 * Information about the field can be accessed, and the field's value can be
 * accessed dynamically.
 * 
 */
public final class Field extends AccessibleObject implements Member {
  private final VM_Field vmField;

    /**
     * Constructor
     */
    Field(VM_Field vmField){
      this.vmField = vmField;
    }

    /**
     * Prevent this class from being instantiated
     */
    private Field(){
      vmField = null;
    }

    /**
     * <p>TODO Document this method. Is it used?</p>
     * @return
     * @see java.lang.reflect.AccessibleObject#getSignature()
     */
    native String getSignature();
    
    public boolean isSynthetic() {
        return false;
    }

    /**
     * <p>
     * Returns the String representation of the field's declaration, including
     * the type parameters.
     * </p>
     * 
     * @return An instance of String.
     * @since 1.5
     */
    public String toGenericString() {
        return null;
    }

    /**
     * <p>
     * Indicates whether or not this field is an enumeration constant.
     * </p>
     * 
     * @return A value of <code>true</code> if this field is an enumeration
     *         constant, otherwise <code>false</code>.
     * @since 1.5
     */
    public boolean isEnumConstant() {
        return false;
    }

    /**
     * <p>
     * Gets the declared type of this field.
     * </p>
     * 
     * @return An instance of {@link Type}.
     * @throws GenericSignatureFormatError if the generic method signature is
     *         invalid.
     * @throws TypeNotPresentException if the component type points to a missing
     *         type.
     * @throws MalformedParameterizedTypeException if the component type points
     *         to a type that can't be instantiated for some reason.
     * @since 1.5
     */
    public Type getGenericType() {
        return null;
    }
    
	/**
     * Compares the specified object to this Field and answer if they are equal.
     * The object must be an instance of Field with the same defining class and
     * name.
     * 
     * @param object the object to compare
     * @return true if the specified object is equal to this Field, false
     *         otherwise
     * @see #hashCode
     */
	@Override
    public boolean equals(Object object) {
		return false;
	}

	/**
	 * Return the value of the field in the specified object. This reproduces
	 * the effect of <code>object.fieldName</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * <p>
	 * The value of the field is returned. If the type of this field is a base
	 * type, the field value is automatically wrapped.
	 * 
	 * @param object
	 *            the object to access
	 * @return the field value, possibly wrapped
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native Object get(Object object) throws IllegalAccessException,
			IllegalArgumentException;

	/**
	 * Return the value of the field in the specified object as a boolean. This
	 * reproduces the effect of <code>object.fieldName</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @return the field value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native boolean getBoolean(Object object)
			throws IllegalAccessException, IllegalArgumentException;

	/**
	 * Return the value of the field in the specified object as a byte. This
	 * reproduces the effect of <code>object.fieldName</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @return the field value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native byte getByte(Object object) throws IllegalAccessException,
			IllegalArgumentException;

	/**
	 * Return the value of the field in the specified object as a char. This
	 * reproduces the effect of <code>object.fieldName</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @return the field value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native char getChar(Object object) throws IllegalAccessException,
			IllegalArgumentException;

	/**
	 * Return the {@link Class} associated with the class that defined this
	 * field.
	 * 
	 * @return the declaring class
	 */
	public Class<?> getDeclaringClass() {
		return null;
	}

	/**
	 * Return the value of the field in the specified object as a double. This
	 * reproduces the effect of <code>object.fieldName</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @return the field value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native double getDouble(Object object)
			throws IllegalAccessException, IllegalArgumentException;

	/**
	 * Return the value of the field in the specified object as a float. This
	 * reproduces the effect of <code>object.fieldName</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @return the field value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native float getFloat(Object object) throws IllegalAccessException,
			IllegalArgumentException;

	/**
	 * Return the value of the field in the specified object as an int. This
	 * reproduces the effect of <code>object.fieldName</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @return the field value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native int getInt(Object object) throws IllegalAccessException,
			IllegalArgumentException;

	/**
	 * Return the value of the field in the specified object as a long. This
	 * reproduces the effect of <code>object.fieldName</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @return the field value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native long getLong(Object object) throws IllegalAccessException,
			IllegalArgumentException;

	/**
	 * Return the modifiers for the modeled field. The Modifier class should be
	 * used to decode the result.
	 * 
	 * @return the modifiers
	 * @see java.lang.reflect.Modifier
	 */
	public native int getModifiers();

	/**
	 * Return the name of the modeled field.
	 * 
	 * @return the name
	 */
	public String getName() {
		return null;
	}

	/**
	 * Return the value of the field in the specified object as a short. This
	 * reproduces the effect of <code>object.fieldName</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * <p>
	 * 
	 * @param object
	 *            the object to access
	 * @return the field value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native short getShort(Object object) throws IllegalAccessException,
			IllegalArgumentException;

	/**
	 * Return the {@link Class} associated with the type of this field.
	 * 
	 * @return the type
	 */
	public Class<?> getType() {
		return null;
	}

	/**
	 * Answers an integer hash code for the receiver. Objects which are equal
	 * answer the same value for this method.
	 * <p>
	 * The hash code for a Field is the hash code of the field's name.
	 * 
	 * @return the receiver's hash
	 * @see #equals
	 */
	@Override
    public int hashCode() {
		return 0;
	}

	/**
	 * Set the value of the field in the specified object to the boolean value.
	 * This reproduces the effect of <code>object.fieldName = value</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * <p>
	 * If the field type is a base type, the value is automatically unwrapped.
	 * If the unwrap fails, an IllegalArgumentException is thrown. If the value
	 * cannot be converted to the field type via a widening conversion, an
	 * IllegalArgumentException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @param value
	 *            the new value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native void set(Object object, Object value)
			throws IllegalAccessException, IllegalArgumentException;

	/**
	 * Set the value of the field in the specified object to the boolean value.
	 * This reproduces the effect of <code>object.fieldName = value</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * <p>
	 * If the value cannot be converted to the field type via a widening
	 * conversion, an IllegalArgumentException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @param value
	 *            the new value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native void setBoolean(Object object, boolean value)
			throws IllegalAccessException, IllegalArgumentException;

	/**
	 * Set the value of the field in the specified object to the byte value.
	 * This reproduces the effect of <code>object.fieldName = value</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * <p>
	 * If the value cannot be converted to the field type via a widening
	 * conversion, an IllegalArgumentException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @param value
	 *            the new value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native void setByte(Object object, byte value)
			throws IllegalAccessException, IllegalArgumentException;

	/**
	 * Set the value of the field in the specified object to the char value.
	 * This reproduces the effect of <code>object.fieldName = value</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * <p>
	 * If the value cannot be converted to the field type via a widening
	 * conversion, an IllegalArgumentException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @param value
	 *            the new value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native void setChar(Object object, char value)
			throws IllegalAccessException, IllegalArgumentException;

	/**
	 * Set the value of the field in the specified object to the double value.
	 * This reproduces the effect of <code>object.fieldName = value</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * <p>
	 * If the value cannot be converted to the field type via a widening
	 * conversion, an IllegalArgumentException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @param value
	 *            the new value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native void setDouble(Object object, double value)
			throws IllegalAccessException, IllegalArgumentException;

	/**
	 * Set the value of the field in the specified object to the float value.
	 * This reproduces the effect of <code>object.fieldName = value</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * <p>
	 * If the value cannot be converted to the field type via a widening
	 * conversion, an IllegalArgumentException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @param value
	 *            the new value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native void setFloat(Object object, float value)
			throws IllegalAccessException, IllegalArgumentException;

	/**
	 * Set the value of the field in the specified object to the int value. This
	 * reproduces the effect of <code>object.fieldName = value</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * <p>
	 * If the value cannot be converted to the field type via a widening
	 * conversion, an IllegalArgumentException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @param value
	 *            the new value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native void setInt(Object object, int value)
			throws IllegalAccessException, IllegalArgumentException;

	/**
	 * Set the value of the field in the specified object to the long value.
	 * This reproduces the effect of <code>object.fieldName = value</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * <p>
	 * If the value cannot be converted to the field type via a widening
	 * conversion, an IllegalArgumentException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @param value
	 *            the new value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native void setLong(Object object, long value)
			throws IllegalAccessException, IllegalArgumentException;

	/**
	 * Set the value of the field in the specified object to the short value.
	 * This reproduces the effect of <code>object.fieldName = value</code>
	 * <p>
	 * If the modeled field is static, the object argument is ignored.
	 * Otherwise, if the object is null, a NullPointerException is thrown. If
	 * the object is not an instance of the declaring class of the method, an
	 * IllegalArgumentException is thrown.
	 * <p>
	 * If this Field object is enforcing access control (see AccessibleObject)
	 * and the modeled field is not accessible from the current context, an
	 * IllegalAccessException is thrown.
	 * <p>
	 * If the value cannot be converted to the field type via a widening
	 * conversion, an IllegalArgumentException is thrown.
	 * 
	 * @param object
	 *            the object to access
	 * @param value
	 *            the new value
	 * @throws NullPointerException
	 *             if the object is null and the field is non-static
	 * @throws IllegalArgumentException
	 *             if the object is not compatible with the declaring class
	 * @throws IllegalAccessException
	 *             if modeled field is not accessible
	 */
	public native void setShort(Object object, short value)
			throws IllegalAccessException, IllegalArgumentException;

	/**
	 * Answers a string containing a concise, human-readable description of the
	 * receiver.
	 * <p>
	 * The format of the string is:
	 * <ul>
	 * <li>modifiers (if any)
	 * <li>return type
	 * <li>declaring class name
	 * <li>'.'
	 * <li>field name
	 * </ul>
	 * <p>
	 * For example:
	 * <code>public static java.io.InputStream java.lang.System.in</code>
	 * 
	 * @return a printable representation for the receiver
	 */
	public String toString() {
		return null;
	}
}
