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

import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMMember;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.runtime.ReflectionBase;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.Reflection;

/**
 * This class must be implemented by the VM vendor. This class models a
 * constructor. Information about the constructor can be accessed, and the
 * constructor can be invoked dynamically.
 * 
 */
public final class Constructor<T> extends AccessibleObject implements GenericDeclaration, Member {
  /**
	* Data being wrapped by the Constructor API
	*/
  private final RVMMethod vmConstructor;

  /**
   * Possible reflective method invoker
   */
  private final ReflectionBase invoker;

  /**
	* Constructor
	*/
  Constructor(RVMMethod vmConstructor){
	 this.vmConstructor = vmConstructor;
     if (Reflection.cacheInvokerInJavaLangReflect) {
       invoker = vmConstructor.getInvoker();
     } else {
       invoker = null;
     }
  }

  /**
	* Prevent this class from being instantiated
	*/
  private Constructor(){
	 //do nothing
	 this.vmConstructor = null;
	 invoker = null;
  }

  public TypeVariable<Constructor<T>>[] getTypeParameters() {
	 throw new Error("TODO");
  }

  /**
	* <p>
	* Returns the String representation of the constructor's declaration,
	* including the type parameters.
	* </p>
	* 
	* @return An instance of String.
	* @since 1.5
	*/
  public String toGenericString() {
	 throw new Error("TODO");
  }

  /**
	* <p>
	* Gets the parameter types as an array of {@link Type} instances, in
	* declaration order. If the constructor has no parameters, then an empty
	* array is returned.
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
	 throw new Error("TODO");
  }

  /**
	* <p>
	* Gets the exception types as an array of {@link Type} instances. If the
	* constructor has no declared exceptions, then an empty array is returned.
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
	 throw new Error("TODO");
  }

  /**
	* <p>
	* Gets an array of arrays that represent the annotations of the formal
	* parameters of this constructor. If there are no parameters on this
	* constructor, then an empty array is returned. If there are no annotations
	* set, then and array of empty arrays is returned.
	* </p>
	* 
	* @return An array of arrays of {@link Annotation} instances.
	* @since 1.5
	*/
  public Annotation[][] getParameterAnnotations() {
	 return super.getParameterAnnotations();
  }

  /**
	* <p>
	* Indicates whether or not this constructor takes a variable number
	* argument.
	* </p>
	* 
	* @return A value of <code>true</code> if a vararg is declare, otherwise
	*         <code>false</code>.
	* @since 1.5
	*/
  public boolean isVarArgs() {
	 return super.isVarArgs();
  }

  public boolean isSynthetic() {
	 return super.isSynthetic();
  }

  /**
	* Compares the specified object to this Constructor and answer if they are
	* equal. The object must be an instance of Constructor with the same
	* defining class and parameter types.
	* 
	* @param object the object to compare
	* @return true if the specified object is equal to this Constructor, false
	*         otherwise
	* @see #hashCode
	*/
  @Override
    public boolean equals(Object object) {
	 if (object instanceof Constructor) {
		Constructor that = (Constructor)object;
		return this.vmConstructor == that.vmConstructor;
	 } else {
		return false;
	 }
  }

  /**
	* Return the {@link Class} associated with the class that defined this
	* constructor.
	* 
	* @return the declaring class
	*/
  public Class<T> getDeclaringClass() {
	 return (Class<T>)vmConstructor.getDeclaringClass().getClassForType();
  }

  /**
	* Return an array of the {@link Class} objects associated with the
	* exceptions declared to be thrown by this constructor. If the constructor
	* was not declared to throw any exceptions, the array returned will be
	* empty.
	* 
	* @return the declared exception classes
	*/
  public Class<?>[] getExceptionTypes() {
	 return super.getExceptionTypes();
  }

  /**
	* Return the modifiers for the modeled constructor. The Modifier class
	* should be used to decode the result.
	* 
	* @return the modifiers
	* @see java.lang.reflect.Modifier
	*/
  public int getModifiers() {
	 return super.getModifiers();
  }

  /**
	* Return the name of the modeled constructor. This is the name of the
	* declaring class.
	* 
	* @return the name
	*/
  public String getName() {
	 return getDeclaringClass().getName();
  }

  /**
	* Return an array of the {@link Class} objects associated with the
	* parameter types of this constructor. If the constructor was declared with
	* no parameters, the array returned will be empty.
	* 
	* @return the parameter types
	*/
  public Class<?>[] getParameterTypes() {
	 return VMCommonLibrarySupport.typesToClasses(vmConstructor.getParameterTypes());
  }

  /**
	* Answers an integer hash code for the receiver. Objects which are equal
	* answer the same value for this method. The hash code for a Constructor is
	* the hash code of the declaring class' name.
	* 
	* @return the receiver's hash
	* @see #equals
	*/
  @Override
    public int hashCode() {
	 return getName().hashCode();
  }

  /**
	* Return a new instance of the declaring class, initialized by dynamically
	* invoking the modeled constructor. This reproduces the effect of
	* <code>new declaringClass(arg1, arg2, ... , argN)</code> This method
	* performs the following:
	* <ul>
	* <li>A new instance of the declaring class is created. If the declaring
	* class cannot be instantiated (i.e. abstract class, an interface, an array
	* type, or a base type) then an InstantiationException is thrown.</li>
	* <li>If this Constructor object is enforcing access control (see
	* AccessibleObject) and the modeled constructor is not accessible from the
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
	* <li>The modeled constructor is then invoked. If an exception is thrown
	* during the invocation, it is caught and wrapped in an
	* InvocationTargetException. This exception is then thrown. If the
	* invocation completes normally, the newly initialized object is returned.
	* </ul>
	* 
	* @param args the arguments to the constructor
	* @return the new, initialized, object
	* @exception java.lang.InstantiationException if the class cannot be
	*            instantiated
	* @exception java.lang.IllegalAccessException if the modeled constructor
	*            is not accessible
	* @exception java.lang.IllegalArgumentException if an incorrect number of
	*            arguments are passed, or an argument could not be converted by
	*            a widening conversion
	* @exception java.lang.reflect.InvocationTargetException if an exception
	*            was thrown by the invoked constructor
	* @see java.lang.reflect.AccessibleObject
	*/
  public T newInstance(Object... args) throws InstantiationException, IllegalAccessException,
															 IllegalArgumentException, InvocationTargetException {
	 return (T)VMCommonLibrarySupport.construct(vmConstructor, this, args, RVMClass.getClassFromStackFrame(1), invoker);
  }

  /**
	* Answers a string containing a concise, human-readable description of the
	* receiver. The format of the string is modifiers (if any) declaring class
	* name '(' parameter types, separated by ',' ')' If the constructor throws
	* exceptions, ' throws ' exception types, separated by ',' For example:
	* <code>public String(byte[],String) throws UnsupportedEncodingException</code>
	* 
	* @return a printable representation for the receiver
	*/
  @Override
  public String toString() {
	 StringBuilder sb = new StringBuilder(80);
	 // append modifiers if any
	 int modifier = getModifiers();
	 if (modifier != 0) {
		// VARARGS incorrectly recognized
		final int MASK = ~Modifier.VARARGS;  
		sb.append(Modifier.toString(modifier & MASK)).append(' ');            
	 }
	 // append constructor name
	 appendArrayType(sb, getDeclaringClass());
	 // append parameters
	 sb.append('(');
	 appendArrayType(sb, getParameterTypes());
	 sb.append(')');
	 // append exeptions if any
	 Class[] exn = getExceptionTypes();
	 if (exn.length > 0) {
		sb.append(" throws ");
		appendSimpleType(sb, exn);
	 }
	 return sb.toString();
  }

  /* ---- Non-API methods ---- */

  /**
   * Set the accessibilty to the value of flag performing a check on the constructor.
   */
  @Override
  void setAccessible0(boolean flag) {
	 if (flag && getDeclaringClass() == Class.class) {
		throw new SecurityException("Can not make the java.lang.Class class constructor accessible");
	 }
	 super.setAccessible0(flag);
  }

  /**
   * Get the VM member implementation. Package protected to stop outside use.
   */
  @Override
  RVMMember getVMMember() {
	 return vmConstructor;
  }

  /**
   * Get the VM method implementation, invalid for fields. Package protected to
   * stop outside use.
   */
  @Override
  RVMMethod getVMMethod() {
	 return vmConstructor;
  }
}
