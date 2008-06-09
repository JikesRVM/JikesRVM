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

import java.lang.annotation.Annotation;

import org.jikesrvm.classloader.VM_Method;

/**
 * This class must be implemented by the VM vendor. This class models a method.
 * Information about the method can be accessed, and the method can be invoked
 * dynamically.
 * 
 */
public final class Method extends AccessibleObject implements GenericDeclaration, Member {
    private final VM_Method vmMethod;

    /**
     * Constructor
     */
    Method(VM_Method vmMethod){
      this.vmMethod = vmMethod;
    }
    
    /**
     * Prevent this class from being instantiated
     */
    private Method(){
      vmMethod = null;
    }
    
    public TypeVariable<Method>[] getTypeParameters() {
        return null;
    }

    /**
     * <p>
     * Returns the String representation of the method's declaration, including
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
     * Gets the parameter types as an array of {@link Type} instances, in
     * declaration order. If the method has no parameters, then an empty array
     * is returned.
     * </p>
     * 
     * @return An array of {@link Type} instances.
     * @throws GenericSignatureFormatError if the generic method signature is
     *         invalid.
     * @throws TypeNotPresentException if the component type points to a missing
     *         type.
     * @throws MalformedParameterizedTypeException if the component type points
     *         to a type that can't be instantiated for some reason.
     * @since 1.5
     */
    public Type[] getGenericParameterTypes() {
        return null;
    }

    /**
     * <p>
     * Gets the exception types as an array of {@link Type} instances. If the
     * method has no declared exceptions, then an empty array is returned.
     * </p>
     * 
     * @return An array of {@link Type} instances.
     * @throws GenericSignatureFormatError if the generic method signature is
     *         invalid.
     * @throws TypeNotPresentException if the component type points to a missing
     *         type.
     * @throws MalformedParameterizedTypeException if the component type points
     *         to a type that can't be instantiated for some reason.
     * @since 1.5
     */
    public Type[] getGenericExceptionTypes() {
        return null;
    }

    /**
     * <p>
     * Gets the return type as a {@link Type} instance.
     * </p>
     * 
     * @return A {@link Type} instance.
     * @throws GenericSignatureFormatError if the generic method signature is
     *         invalid.
     * @throws TypeNotPresentException if the component type points to a missing
     *         type.
     * @throws MalformedParameterizedTypeException if the component type points
     *         to a type that can't be instantiated for some reason.
     * @since 1.5
     */
    public Type getGenericReturnType() {
        return null;
    }

    /**
     * <p>
     * Gets an array of arrays that represent the annotations of the formal
     * parameters of this method. If there are no parameters on this method,
     * then an empty array is returned. If there are no annotations set, then
     * and array of empty arrays is returned.
     * </p>
     * 
     * @return An array of arrays of {@link Annotation} instances.
     * @since 1.5
     */
    public Annotation[][] getParameterAnnotations() {
        return null;
    }

    /**
     * <p>
     * Indicates whether or not this method takes a variable number argument.
     * </p>
     * 
     * @return A value of <code>true</code> if a vararg is declare, otherwise
     *         <code>false</code>.
     * @since 1.5
     */
    public boolean isVarArgs() {
        return false;
    }

    /**
     * <p>
     * Indicates whether or not this method is a bridge.
     * </p>
     * 
     * @return A value of <code>true</code> if this method's a bridge,
     *         otherwise <code>false</code>.
     * @since 1.5
     */
    public boolean isBridge() {
        return false;
    }

    public boolean isSynthetic() {
        return false;
    }
    
    /**
     * <p>Gets the default value for the annotation member represented by
     * this method.</p>
     * @return The default value or <code>null</code> if none.
     * @throws TypeNotPresentException if the annotation is of type {@link Class}
     * and no definition can be found.
     * @since 1.5
     */
    public Object getDefaultValue() {
        return null;
    }
    
	/**
	 * Compares the specified object to this Method and answer if they are
	 * equal. The object must be an instance of Method with the same defining
	 * class and parameter types.
	 * 
	 * @param object
	 *            the object to compare
	 * @return true if the specified object is equal to this Method, false
	 *         otherwise
	 * @see #hashCode
	 */
	@Override
    public boolean equals(Object object) {
		return false;
	}

	/**
	 * Return the {@link Class} associated with the class that defined this
	 * method.
	 * 
	 * @return the declaring class
	 */
	public Class<?> getDeclaringClass() {
		return null;
	}

	/**
	 * Return an array of the {@link Class} objects associated with the
	 * exceptions declared to be thrown by this method. If the method was not
	 * declared to throw any exceptions, the array returned will be empty.
	 * 
	 * @return the declared exception classes
	 */
	public Class<?>[] getExceptionTypes() {
		return null;
	}

	/**
	 * Return the modifiers for the modeled method. The Modifier class
	 * should be used to decode the result.
	 * 
	 * @return the modifiers
	 * @see java.lang.reflect.Modifier
	 */
    public int getModifiers() {
		return 0;
	}

	/**
	 * Return the name of the modeled method.
	 * 
	 * @return the name
	 */
	public String getName() {
		return null;
	}

	/**
	 * Return an array of the {@link Class} objects associated with the
	 * parameter types of this method. If the method was declared with no
	 * parameters, the array returned will be empty.
	 * 
	 * @return the parameter types
	 */
	public Class<?>[] getParameterTypes() {
		return null;
	}

	/**
	 * Return the {@link Class} associated with the return type of this
	 * method.
	 * 
	 * @return the return type
	 */
	public Class<?> getReturnType() {
		return null;
	}

	/**
	 * Answers an integer hash code for the receiver. Objects which are equal
	 * answer the same value for this method. The hash code for a Method is the
	 * hash code of the method's name.
	 * 
	 * @return the receiver's hash
	 * @see #equals
	 */
	@Override
    public int hashCode() {
		return 0;
	}

	/**
	 * Return the result of dynamically invoking the modeled method. This
	 * reproduces the effect of
	 * <code>receiver.methodName(arg1, arg2, ... , argN)</code> This method
	 * performs the following:
	 * <ul>
	 * <li>If the modeled method is static, the receiver argument is ignored.
	 * </li>
	 * <li>Otherwise, if the receiver is null, a NullPointerException is
	 * thrown.</li>
	 * If the receiver is not an instance of the declaring class of the method,
	 * an IllegalArgumentException is thrown.
	 * <li>If this Method object is enforcing access control (see
	 * AccessibleObject) and the modeled method is not accessible from the
	 * current context, an IllegalAccessException is thrown.</li>
	 * <li>If the number of arguments passed and the number of parameters do
	 * not match, an IllegalArgumentException is thrown.</li>
	 * <li>For each argument passed:
	 * <ul>
	 * <li>If the corresponding parameter type is a base type, the argument is
	 * unwrapped. If the unwrapping fails, an IllegalArgumentException is
	 * thrown.</li>
	 * <li>If the resulting argument cannot be converted to the parameter type
	 * via a widening conversion, an IllegalArgumentException is thrown.</li>
	 * </ul>
	 * <li>If the modeled method is static, it is invoked directly. If it is
	 * non-static, the modeled method and the receiver are then used to perform
	 * a standard dynamic method lookup. The resulting method is then invoked.
	 * </li>
	 * <li>If an exception is thrown during the invocation it is caught and
	 * wrapped in an InvocationTargetException. This exception is then thrown.
	 * </li>
	 * <li>If the invocation completes normally, the return value is itself
	 * returned. If the method is declared to return a base type, the return
	 * value is first wrapped. If the return type is void, null is returned.
	 * </li>
	 * </ul>
	 * 
	 * @param receiver
	 * 	          The object on which to call the modeled method
	 * @param args
	 *            the arguments to the method
	 * @return the new, initialized, object
	 * @throws java.lang.NullPointerException
	 *                if the receiver is null for a non-static method
	 * @throws java.lang.IllegalAccessException
	 *                if the modeled method is not accessible
	 * @throws java.lang.IllegalArgumentException
	 *                if an incorrect number of arguments are passed, the
	 *                receiver is incompatible with the declaring class, or an
	 *                argument could not be converted by a widening conversion
	 * @throws java.lang.reflect.InvocationTargetException
	 *                if an exception was thrown by the invoked method
	 * @see java.lang.reflect.AccessibleObject
	 */
	public Object invoke(Object receiver, Object... args)
			throws IllegalAccessException, IllegalArgumentException,
			InvocationTargetException {
		return null;
	}

	/**
	 * Answers a string containing a concise, human-readable description of the
	 * receiver. The format of the string is modifiers (if any) return type
	 * declaring class name '.' method name '(' parameter types, separated by
	 * ',' ')' If the method throws exceptions, ' throws ' exception types,
	 * separated by ',' For example:
	 * <code>public native Object java.lang.Method.invoke(Object,Object) throws IllegalAccessException,IllegalArgumentException,InvocationTargetException</code>
	 * 
	 * @return a printable representation for the receiver
	 */
	@Override
    public String toString() {
		return null;
	}
}
