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
     * TODO Is this necessary?
     */
    static final Object[] emptyArgs = new Object[0];

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
        return;
    }

    /**
     * <p>
     * TODO Document this method.
     * </p>
     * 
     * @param parameterTypes
     * @param args
     * @return
     * @throws IllegalArgumentException
     */
    static Object[] marshallArguments(Class[] parameterTypes, Object[] args)
            throws IllegalArgumentException {
        return null;
    }

    /**
     * <p>
     * TODO Document this method.
     * </p>
     * 
     * @param clazz
     */
    static native void initializeClass(Class<?> clazz);

    /**
     * Answer the class at depth. Notes: 1) This method operates on the defining
     * classes of methods on stack. NOT the classes of receivers. 2) The item at
     * index zero describes the caller of this method.
     */
    static final native Class<?> getStackClass(int depth);

    /**
     * AccessibleObject constructor. AccessibleObjects can only be created by
     * the Virtual Machine.
     */
    protected AccessibleObject() {
        super();
    }

    /**
     * <p>
     * TODO Document this method.
     * </p>
     * 
     * @return
     */
    native Class[] getParameterTypesImpl();

    /**
     * <p>
     * TODO Document this method.
     * </p>
     * 
     * @return
     */
    native int getModifiers();

    /**
     * <p>
     * TODO Document this method.
     * </p>
     * 
     * @return
     */
    native Class[] getExceptionTypesImpl();

    /**
     * <p>
     * TODO Document this method.
     * </p>
     * 
     * @return
     */
    native String getSignature();

    /**
     * <p>
     * TODO Document this method.
     * </p>
     * 
     * @param senderClass
     * @param receiver
     * @return
     */
    native boolean checkAccessibility(Class<?> senderClass, Object receiver);

    /**
     * Returns the value of the accessible flag. This is false if access checks
     * are performed, true if they are skipped.
     * 
     * @return the value of the accessible flag
     */
    public boolean isAccessible() {
        return false;
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
        return;
    }

    public boolean isAnnotationPresent(Class<? extends Annotation> annotationType) {
        return false;
    }

    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[0];
    }

    public Annotation[] getAnnotations() {
        return new Annotation[0];
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return null;
    }

    /**
     * <p>
     * TODO Document this method.
     * </p>
     * 
     * @param receiver
     * @param args
     * @throws InvocationTargetException
     */
    void invokeV(Object receiver, Object args[]) throws InvocationTargetException {
        return;
    }

    /**
     * <p>
     * TODO Document this method.
     * </p>
     * 
     * @param receiver
     * @param args
     * @return
     * @throws InvocationTargetException
     */
    Object invokeL(Object receiver, Object args[]) throws InvocationTargetException {
        return null;
    }

    /**
     * <p>
     * TODO Document this method.
     * </p>
     * 
     * @param receiver
     * @param args
     * @return
     * @throws InvocationTargetException
     */
    int invokeI(Object receiver, Object args[]) throws InvocationTargetException {
        return 0;
    }

    /**
     * <p>
     * TODO Document this method.
     * </p>
     * 
     * @param receiver
     * @param args
     * @return
     * @throws InvocationTargetException
     */
    long invokeJ(Object receiver, Object args[]) throws InvocationTargetException {
        return 0L;
    }

    /**
     * <p>
     * TODO Document this method.
     * </p>
     * 
     * @param receiver
     * @param args
     * @return
     * @throws InvocationTargetException
     */
    float invokeF(Object receiver, Object args[]) throws InvocationTargetException {
        return 0.0F;
    }

    /**
     * <p>
     * TODO Document this method.
     * </p>
     * 
     * @param receiver
     * @param args
     * @return
     * @throws InvocationTargetException
     */
    double invokeD(Object receiver, Object args[]) throws InvocationTargetException {
        return 0.0D;
    }
}
