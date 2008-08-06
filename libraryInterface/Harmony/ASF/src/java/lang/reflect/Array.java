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

import java.lang.reflect.VMCommonLibrarySupport;

import org.jikesrvm.runtime.RuntimeEntrypoints;

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
  public static Object get(Object array, int index)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 try {
		return ((Object[])array)[index];
	 } catch (ClassCastException e) {
		if (array instanceof int[]) {
		  return ((int[])array)[index];
		}
		if (array instanceof boolean[]) {
		  return ((boolean[])array)[index];
		}
		if (array instanceof float[]) {
		  return ((float[])array)[index];
		}
		if (array instanceof char[]) {
		  return ((char[])array)[index];
		}
		if (array instanceof double[]) {
		  return ((double[])array)[index];
		}
		if (array instanceof long[]) {
		  return ((long[])array)[index];
		}
		if (array instanceof short[]) {
		  return ((short[])array)[index];
		}
		if (array instanceof byte[]) {
		  return ((byte[])array)[index];
		}
	 }
	 throw new IllegalArgumentException("Specified argument is not an array");
  }

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
  public static boolean getBoolean(Object array, int index)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 try {
		return ((boolean[])array)[index];
	 } catch (ClassCastException e) {
		throw new IllegalArgumentException(e.getMessage());
	 }
  }

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
  public static byte getByte(Object array, int index)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 try {
		return ((byte[])array)[index];
	 } catch (ClassCastException e) {
		throw new IllegalArgumentException(e.getMessage());
	 }
  }

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
  public static char getChar(Object array, int index)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 try {
		return ((char[])array)[index];
	 } catch (ClassCastException e) {
		throw new IllegalArgumentException(e.getMessage());
	 }
  }

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
  public static double getDouble(Object array, int index)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 if (array instanceof double[]) {
		return ((double[])array)[index];
	 }
	 return getFloat(array, index);
  }

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
  public static float getFloat(Object array, int index)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 if (array instanceof float[]) {
		return ((float[])array)[index];
	 }
	 return getLong(array, index);
  }

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
  public static int getInt(Object array, int index)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 if (array instanceof int[]) {
		return ((int[])array)[index];
	 }
	 if (array instanceof char[]) {
		return ((char[])array)[index];
	 }
	 return getShort(array, index);
  }

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
  public static int getLength(Object array)
	 throws IllegalArgumentException {
	 try {
		return ((Object[])array).length;
	 } catch (ClassCastException e) {
		if (array instanceof int[]) {
		  return ((int[])array).length;
		}
		if (array instanceof boolean[]) {
		  return ((boolean[])array).length;
		}
		if (array instanceof float[]) {
		  return ((float[])array).length;
		}
		if (array instanceof char[]) {
		  return ((char[])array).length;
		}
		if (array instanceof double[]) {
		  return ((double[])array).length;
		}
		if (array instanceof long[]) {
		  return ((long[])array).length;
		}
		if (array instanceof short[]) {
		  return ((short[])array).length;
		}
		if (array instanceof byte[]) {
		  return ((byte[])array).length;
		}
	 }
	 throw new IllegalArgumentException("Specified argument is not an array");
  }

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
  public static long getLong(Object array, int index)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 if (array instanceof long[]) {
		return ((long[])array)[index];
	 }
	 return getInt(array, index);
  }

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
  public static short getShort(Object array, int index)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 if (array instanceof short[]) {
		return ((short[])array)[index];
	 }
	 return getByte(array, index);
  }

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
    return VMCommonLibrarySupport.createArray(componentType, dimensions);
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
    return VMCommonLibrarySupport.createArray(componentType, size);
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
  public static void set(Object array, int index, Object value)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 if (array == null) {
		throw new NullPointerException();
	 }
       
	 try {
		((Object[])array)[index] = value;
		return;
	 } catch (ClassCastException e) {
		if (value instanceof Number) {
		  if (value instanceof Integer) {
			 setInt(array, index, ((Integer)value).intValue());
			 return;
		  } else if (value instanceof Float) {
			 setFloat(array, index, ((Float)value).floatValue());
			 return;
		  } else if (value instanceof Double) {
			 setDouble(array, index, ((Double)value).doubleValue());
			 return;
		  } else if (value instanceof Long) {
			 setLong(array, index, ((Long)value).longValue());
			 return;
		  } else if (value instanceof Short) {
			 setShort(array, index, ((Short)value).shortValue());
			 return;
		  } else if (value instanceof Byte) {
			 setByte(array, index, ((Byte)value).byteValue());
			 return;
		  }
		} else if (value instanceof Boolean) {
		  setBoolean(array, index, ((Boolean)value).booleanValue());
		  return;
		} else if (value instanceof Character) {
		  setChar(array, index, ((Character)value).charValue());
		  return;
		}
	 } catch (ArrayStoreException e) {
		throw new IllegalArgumentException(e.getMessage());
	 }
	 throw new IllegalArgumentException(
													"Can not assign the specified value to the specified array component");
  }

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
  public static void setBoolean(Object array, int index, boolean value)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 try {
		((boolean[])array)[index] = value;
	 } catch (ClassCastException e) {
		throw new IllegalArgumentException(e.getMessage());
	 }
  }

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
  public static void setByte(Object array, int index, byte value)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 if (array instanceof byte[]) {
		((byte[])array)[index] = value;
		return;
	 }
	 setShort(array, index, value);
  }

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
  public static void setChar(Object array, int index, char value)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 if (array instanceof char[]) {
		((char[])array)[index] = value;
		return;
	 }
	 setInt(array, index, value);
  }

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
  public static void setDouble(Object array, int index, double value)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 try {
		((double[])array)[index] = value;
	 } catch (ClassCastException e) {
		throw new IllegalArgumentException(e.getMessage());
	 }
  }

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
  public static void setFloat(Object array, int index, float value)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 if (array instanceof float[]) {
		((float[])array)[index] = value;
		return;
	 }
	 setDouble(array, index, value);
  }

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
  public static void setInt(Object array, int index, int value)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 if (array instanceof int[]) {
		((int[])array)[index] = value;
		return;
	 }
	 setLong(array, index, value);
  }

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
  public static void setLong(Object array, int index, long value)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 if (array instanceof long[]) {
		((long[])array)[index] = value;
		return;
	 }
	 setFloat(array, index, value);
  }

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
  public static void setShort(Object array, int index, short value)
	 throws IllegalArgumentException, ArrayIndexOutOfBoundsException {
	 if (array instanceof short[]) {
		((short[])array)[index] = value;
		return;
	 }
	 setInt(array, index, value);
  }
}
