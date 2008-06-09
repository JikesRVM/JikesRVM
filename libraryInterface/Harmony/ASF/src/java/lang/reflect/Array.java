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

/**
 * This class must be implemented by the VM vendor. This class provides methods
 * to dynamically create and access arrays.
 */
public final class Array {
    
    /**
     * Prevent this class from being instantiated
     */
    private Array(){
        //do nothing
    }
    
    /**
     * <p>TODO Document this method. Is it actually used?</p>
     * @param componentType
     * @param dimensions
     * @param dimensionsArray
     * @return
     */
    @SuppressWarnings("unused")
    private static native Object multiNewArrayImpl(Class<?> componentType,
            int dimensions, int[] dimensionsArray);

    /**
     * <p>TODO Document this method. Is it actually used?</p>
     * @param componentType
     * @param dimension
     * @return
     */
    @SuppressWarnings("unused")
    private static native Object newArrayImpl(Class<?> componentType, int dimension);
    
	/**
	 * Return the element of the array at the specified index. This reproduces
	 * the effect of <code>array[index]</code> If the array component is a
	 * base type, the result is automatically wrapped.
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @return the requested element, possibly wrapped
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native Object get(Object array, int index)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Return the element of the array at the specified index, converted to a
	 * boolean if possible. This reproduces the effect of
	 * <code>array[index]</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @return the requested element
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the element cannot be
	 *                converted to the requested type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native boolean getBoolean(Object array, int index)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Return the element of the array at the specified index, converted to a
	 * byte if possible. This reproduces the effect of <code>array[index]</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @return the requested element
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the element cannot be
	 *                converted to the requested type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native byte getByte(Object array, int index)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Return the element of the array at the specified index, converted to a
	 * char if possible. This reproduces the effect of <code>array[index]</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @return the requested element
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the element cannot be
	 *                converted to the requested type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native char getChar(Object array, int index)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Return the element of the array at the specified index, converted to a
	 * double if possible. This reproduces the effect of
	 * <code>array[index]</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @return the requested element
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the element cannot be
	 *                converted to the requested type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native double getDouble(Object array, int index)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Return the element of the array at the specified index, converted to a
	 * float if possible. This reproduces the effect of
	 * <code>array[index]</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @return the requested element
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the element cannot be
	 *                converted to the requested type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native float getFloat(Object array, int index)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Return the element of the array at the specified index, converted to an
	 * int if possible. This reproduces the effect of <code>array[index]</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @return the requested element
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the element cannot be
	 *                converted to the requested type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native int getInt(Object array, int index)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Return the length of the array. This reproduces the effect of
	 * <code>array.length</code>
	 * 
	 * @param array
	 *            the array
	 * @return the length
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array
	 */
	public static native int getLength(Object array)
			throws IllegalArgumentException;

	/**
	 * Return the element of the array at the specified index, converted to a
	 * long if possible. This reproduces the effect of <code>array[index]</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @return the requested element
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the element cannot be
	 *                converted to the requested type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native long getLong(Object array, int index)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Return the element of the array at the specified index, converted to a
	 * short if possible. This reproduces the effect of
	 * <code>array[index]</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @return the requested element
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the element cannot be
	 *                converted to the requested type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native short getShort(Object array, int index)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Return a new multidimensional array of the specified component type and
	 * dimensions. This reproduces the effect of
	 * <code>new componentType[d0][d1]...[dn]</code> for a dimensions array of {
	 * d0, d1, ... , dn }
	 * 
	 * @param componentType
	 *            the component type of the new array
	 * @param dimensions
	 *            the dimensions of the new array
	 * @return the new array
	 * @throws java.lang.NullPointerException
	 *                if the component type is null
	 * @throws java.lang.NegativeArraySizeException
	 *                if any of the dimensions are negative
	 * @throws java.lang.IllegalArgumentException
	 *                if the array of dimensions is of size zero, or exceeds the
	 *                limit of the number of dimension for an array (currently
	 *                255)
	 */
	public static Object newInstance(Class<?> componentType, int[] dimensions)
			throws NegativeArraySizeException, IllegalArgumentException {
		return null;
	}

	/**
	 * Return a new array of the specified component type and length. This
	 * reproduces the effect of <code>new componentType[size]</code>
	 * 
	 * @param componentType
	 *            the component type of the new array
	 * @param size
	 *            the length of the new array
	 * @return the new array
	 * @throws java.lang.NullPointerException
	 *                if the component type is null
	 * @throws java.lang.NegativeArraySizeException
	 *                if the size if negative
	 */
	public static Object newInstance(Class<?> componentType, int size)
			throws NegativeArraySizeException {
		return null;
	}

	/**
	 * Set the element of the array at the specified index to the value. This
	 * reproduces the effect of <code>array[index] = value</code> If the array
	 * component is a base type, the value is automatically unwrapped
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @param value
	 *            the new value
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the value cannot be
	 *                converted to the array type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native void set(Object array, int index, Object value)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Set the element of the array at the specified index to the boolean value.
	 * This reproduces the effect of <code>array[index] = value</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @param value
	 *            the new value
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the value cannot be
	 *                converted to the array type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native void setBoolean(Object array, int index, boolean value)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Set the element of the array at the specified index to the byte value.
	 * This reproduces the effect of <code>array[index] = value</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @param value
	 *            the new value
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the value cannot be
	 *                converted to the array type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native void setByte(Object array, int index, byte value)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Set the element of the array at the specified index to the char value.
	 * This reproduces the effect of <code>array[index] = value</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @param value
	 *            the new value
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the value cannot be
	 *                converted to the array type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native void setChar(Object array, int index, char value)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Set the element of the array at the specified index to the double value.
	 * This reproduces the effect of <code>array[index] = value</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @param value
	 *            the new value
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the value cannot be
	 *                converted to the array type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native void setDouble(Object array, int index, double value)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Set the element of the array at the specified index to the float value.
	 * This reproduces the effect of <code>array[index] = value</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @param value
	 *            the new value
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the value cannot be
	 *                converted to the array type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native void setFloat(Object array, int index, float value)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Set the element of the array at the specified index to the int value.
	 * This reproduces the effect of <code>array[index] = value</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @param value
	 *            the new value
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the value cannot be
	 *                converted to the array type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native void setInt(Object array, int index, int value)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Set the element of the array at the specified index to the long value.
	 * This reproduces the effect of <code>array[index] = value</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @param value
	 *            the new value
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the value cannot be
	 *                converted to the array type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native void setLong(Object array, int index, long value)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

	/**
	 * Set the element of the array at the specified index to the short value.
	 * This reproduces the effect of <code>array[index] = value</code>
	 * 
	 * @param array
	 *            the array
	 * @param index
	 *            the index
	 * @param value
	 *            the new value
	 * @throws java.lang.NullPointerException
	 *                if the array is null
	 * @throws java.lang.IllegalArgumentException
	 *                if the array is not an array or the value cannot be
	 *                converted to the array type by a widening conversion
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *                if the index is out of bounds -- negative or greater than
	 *                or equal to the array length
	 */
	public static native void setShort(Object array, int index, short value)
			throws IllegalArgumentException, ArrayIndexOutOfBoundsException;

}
