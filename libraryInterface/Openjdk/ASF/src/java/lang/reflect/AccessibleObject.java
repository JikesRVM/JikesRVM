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

import org.apache.harmony.lang.reflect.ReflectPermissionCollection;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMMember;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.TypeReference;

/**
 * This class must be implemented by the VM vendor. This class is the superclass
 * of all member reflect classes (Field, Constructor, Method). AccessibleObject
 * provides the ability to toggle access checks for these objects. By default
 * accessing a member (for example, setting a field or invoking a method) checks
 * the validity of the access (for example, invoking a private method from
 * outside the defining class is prohibited) and throws IllegalAccessException
 * if the operation is not permitted. If the accessible flag is set to true,
 * these checks are omitted. This allows privileged applications such as Java
 * Object Serialization, inspectors, and debuggers to have complete access to
 * objects.
 * 
 * @see Field
 * @see Constructor
 * @see Method
 * @see ReflectPermission
 * @since 1.2
 */
public class AccessibleObject implements AnnotatedElement {
  /**
	* Has the object been marked as accessible?
	*/
  private boolean isAccessible = false;

  /**
	* Attempts to set the value of the accessible flag for all the objects in
	* the array provided. Only one security check is performed. Setting this
	* flag to false will enable access checks, setting to true will disable
	* them. If there is a security manager, checkPermission is called with a
	* ReflectPermission("suppressAccessChecks").
	* 
	* @param objects the accessible objects
	* @param flag the new value for the accessible flag
	* @see #setAccessible(boolean)
	* @see ReflectPermission
	* @throws SecurityException if the request is denied
	*/
  public static void setAccessible(AccessibleObject[] objects, boolean flag)
	 throws SecurityException {
	 SecurityManager sc = System.getSecurityManager();
	 if (sc != null) {
		sc.checkPermission(ReflectPermissionCollection.SUPPRESS_ACCESS_CHECKS_PERMISSION);
	 }
	 for (int i = 0; i < objects.length; i++) {
		objects[i].setAccessible0(flag);
	 }
  }

  /**
	* AccessibleObject constructor. AccessibleObjects can only be created by
	* the Virtual Machine.
	*/
  AccessibleObject() {
	 super();
  }

  /**
	* Returns the value of the accessible flag. This is false if access checks
	* are performed, true if they are skipped.
	* 
	* @return the value of the accessible flag
	*/
  public boolean isAccessible() {
	 return isAccessible;
  }

  /**
	* Attempts to set the value of the accessible flag. Setting this flag to
	* false will enable access checks, setting to true will disable them. If
	* there is a security manager, checkPermission is called with a
	* ReflectPermission("suppressAccessChecks").
	* 
	* @param flag the new value for the accessible flag
	* @see ReflectPermission
	* @throws SecurityException if the request is denied
	*/
  public void setAccessible(boolean flag) throws SecurityException {
	 SecurityManager sc = System.getSecurityManager();
	 if (sc != null) {
		sc.checkPermission(ReflectPermissionCollection.SUPPRESS_ACCESS_CHECKS_PERMISSION);
	 }
	 setAccessible0(flag);
  }

  public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
    return getVMMember().isAnnotationPresent(annotationType);
  }

  public Annotation[] getDeclaredAnnotations() {
    return getVMMember().getDeclaredAnnotations();
  }

  public Annotation[] getAnnotations() {
    return getVMMember().getDeclaredAnnotations();
  }

  public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
    return getVMMember().getAnnotation(annotationType);
  }

  /* ---- Non-API Methods ---- */

  /**
   * Set the accessibilty to the value of flag. Overridden in Constructor.
   */
  void setAccessible0(boolean flag) {
	 isAccessible = flag;
  }

  /**
   * Get the VM member implementation. Package protected to stop outside use.
   */
  RVMMember getVMMember() {
    throw new Error("This method should always be overridden");
  }

  /**
   * Get the VM method implementation, invalid for fields. Package protected to
   * stop outside use.
   */
  RVMMethod getVMMethod() {
    throw new Error("This method should always be overridden");
  }

  /**
   * <p>
   * Gets an array of arrays that represent the annotations of the formal
   * parameters of this RVMMethod. If there are no parameters on this
   * constructor, then an empty array is returned. If there are no annotations
   * set, then and array of empty arrays is returned.
   * </p>
   * 
   * @return An array of arrays of {@link Annotation} instances.
   * @since 1.5
   */
  Annotation[][] getParameterAnnotations() {
    return getVMMethod().getDeclaredParameterAnnotations();
  }

  /**
   * <p>
   * Indicates whether or not this RVMMethod takes a variable number
   * argument.
   * </p>
   * 
   * @return A value of <code>true</code> if a vararg is declare, otherwise
   *         <code>false</code>.
   */
  boolean isVarArgs() {
    return (getVMMethod().getModifiers() & Modifier.VARARGS) != 0;
  }

  boolean isSynthetic() {
    return (getVMMethod().getModifiers() & Modifier.SYNTHETIC) != 0;
  }

  /**
   * Return the modifiers for the modeled method. The Modifier class
   * should be used to decode the result.
   * 
   * @return the modifiers
   * @see java.lang.reflect.Modifier
   */
  int getModifiers() {
    return getVMMember().getModifiers();
  }

  /**
	* Return an array of the {@link Class} objects associated with the
	* exceptions declared to be thrown by this method. If the method
	* was not declared to throw any exceptions, the array returned will be
	* empty.
	* 
	* @return the declared exception classes
	*/
  Class<?>[] getExceptionTypes() {
	 TypeReference[] exceptionTypes = getVMMethod().getExceptionTypes();
	 if (exceptionTypes == null) {
		return new Class[0];
	 } else {
		return VMCommonLibrarySupport.typesToClasses(exceptionTypes);
	 }
  }
  /**
	* Appends the specified class name to the buffer. The class may represent
	* a simple type, a reference type or an array type.
	* 
	* @param sb buffer
	* @param obj the class which name should be appended to the buffer
	* @throws NullPointerException if any of the arguments is null 
	*/
  static void appendArrayType(StringBuilder sb, Class<?> obj) {
	 if (!obj.isArray()) {
		sb.append(obj.getName());
		return;
	 }
	 int dimensions = 1;
	 Class simplified = obj.getComponentType();
	 obj = simplified;
	 while (simplified.isArray()) {
		obj = simplified;
		dimensions++;
	 }
	 sb.append(obj.getName());
	 switch (dimensions) {
	 case 1:
		sb.append("[]");
		break;
	 case 2:
		sb.append("[][]");
		break;
	 case 3:
		sb.append("[][][]");
		break;
	 default:
		for (int i=0; i < dimensions; i++) {
		  sb.append("[]");
		}
	 }
  }

  /**
	* Appends names of the specified array classes to the buffer. The array
	* elements may represent a simple type, a reference type or an array type.
	* Output format: java.lang.Object[], java.io.File, void
	* 
	* @param sb buffer
	* @param objs array of classes to print the names
	* @throws NullPointerException if any of the arguments is null 
	*/
  void appendArrayType(StringBuilder sb, Class[] objs) {
	 if (objs.length > 0) {
		appendArrayType(sb, objs[0]);
		for (int i = 1; i < objs.length; i++) {
		  sb.append(',');
		  appendArrayType(sb, objs[i]);
		}
	 }
  }

  /**
	* Appends names of the specified array classes to the buffer. The array
	* elements may represent a simple type, a reference type or an array type.
	* In case if the specified array element represents an array type its
	* internal will be appended to the buffer.   
	* Output format: [Ljava.lang.Object;, java.io.File, void
	* 
	* @param sb buffer
	* @param objs array of classes to print the names
	* @throws NullPointerException if any of the arguments is null 
	*/
  static void appendSimpleType(StringBuilder sb, Class<?>[] objs) {
	 if (objs.length > 0) {
		sb.append(objs[0].getName());
		for (int i = 1; i < objs.length; i++) {
		  sb.append(',');
		  sb.append(objs[i].getName());
		}
	 }
  }

 /**
  * Return a descriptor for the member
  */
  String getSignature() {
    return getVMMember().getDescriptor().toString();
  }
}
