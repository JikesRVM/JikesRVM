/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * This class provides a set of static method entrypoints used in the
 * implementation of standard library type operations.
 *
 * @author John Barton 
 * @author Stephen Fink
 * @author Eugene Gluzberg
 */

package com.ibm.JikesRVM.librarySupport;
import java.util.HashMap;
import VM;
import VM_Atom;
import VM_Array;
import VM_Class;
import VM_ClassLoader;
import VM_Field;
import VM_Magic;
import VM_Method;
import VM_Reflection;
import VM_ResolutionException;
import VM_Runtime;
import VM_SystemClassLoader;
import VM_Type;
import VM_UnimplementedError;
import java.lang.reflect.*;

public class ReflectionSupport {

  /**
   * cache a mapping from Class to VM_Array that is [Class to speedup newInstance
   */
  private static HashMap arrayClassCache = new HashMap();

  /**
   * cache a mapping from String to Class objects to speedup forName
   */
  private static HashMap classCache = new HashMap();


  /**
   * @return the java.lang.Class object corresponding to java.lang.Byte
   */
  public static Class getJavaLangByteClass() {
    return VM_Type.ByteType.getClassForType();
  }

  /**
   * @return the java.lang.Class object corresponding to java.lang.Boolean
   */
  public static Class getJavaLangBooleanClass() {
    return VM_Type.BooleanType.getClassForType();
  }

  /**
   * @return the java.lang.Class object corresponding to java.lang.Character
   */
  public static Class getJavaLangCharacterClass() {
    return VM_Type.CharType.getClassForType();
  }

  /**
   * @return the java.lang.Class object corresponding to java.lang.Double
   */
  public static Class getJavaLangDoubleClass() {
    return VM_Type.DoubleType.getClassForType();
  }

  /**
   * @return the java.lang.Class object corresponding to java.lang.Float
   */
  public static Class getJavaLangFloatClass() {
    return VM_Type.FloatType.getClassForType();
  }

  /**
   * @return the java.lang.Class object corresponding to java.lang.Integer
   */
  public static Class getJavaLangIntegerClass() {
    return VM_Type.IntType.getClassForType();
  }

  /**
   * @return the java.lang.Class object corresponding to java.lang.Long
   */
  public static Class getJavaLangLongClass() {
    return VM_Type.LongType.getClassForType();
  }

  /**
   * @return the java.lang.Class object corresponding to java.lang.Short
   */
  public static Class getJavaLangShortClass() {
    return VM_Type.ShortType.getClassForType();
  }

  /**
   * @return the java.lang.Class object corresponding to java.lang.Void
   */
  public static Class getJavaLangVoidClass() {
    return VM_Type.VoidType.getClassForType();
  }

  /**
   * Answers true if the type A
   * can be converted via an identity conversion or a widening
   * reference conversion to type B (i.e. if either the receiver or the
   * argument represent primitive types, only the identity
   * conversion applies).
   *
   * @return <code>true</code>	the argument can be assigned into the receiver
   *         <code>false</code>   the argument cannot be assigned into the receiver
   * @param A a Class to test		
   * @param B a Class to test		
   * @exception	NullPointerException if either parameter is null
   *
   */
  public static boolean isAssignableFrom(Class A, Class B) {
    try {
      return A.getVMType() == B.getVMType() ||
        VM_Runtime.isAssignableWith(A.type, B.type);
    } catch (VM_ResolutionException e) {
      throw new NoClassDefFoundError(e.getException().toString());
    }
  }

  /**
   * Load the VM_Type for Class C.
   */ 
  private static void loadType(Class C) {
    if (C.type.isLoaded()) return;
    synchronized (VM_ClassLoader.lock) {
      try { C.type.load(); }
      catch (VM_ResolutionException e) {
        throw new NoClassDefFoundError(e.getException().toString());
      }
    }
  }

  /**
   * Load and resolve the VM_Type for Class C.
   */ 
  private static void loadAndResolveType(Class C) {
    if (C.type.isResolved()) return;
    synchronized (VM_ClassLoader.lock) {
      try {
        C.type.load();
        C.type.resolve();
      }
      catch (VM_ResolutionException e) {
        throw new NoClassDefFoundError(e.getException().toString());
      }
    }
  }
  /**
   * Answers an array of Class objects which match the interfaces
   * specified in the C's <code>implements</code>
   * declaration
   *
   * @param C the class in question
   * @return the interfaces the receiver claims to implement.
   */
  public static Class[] getInterfaces(Class C) {
    if (!C.type.isClassType())
      return new Class[0];
    loadType(C);
    VM_Class[] interfaces  = C.type.asClass().getDeclaredInterfaces();
    Class[]    jinterfaces = new Class[interfaces.length];
    for (int i = 0; i != interfaces.length; i++)
      jinterfaces[i] = interfaces[i].getClassForType();
    return jinterfaces;
  }
  /**
   * Answers true if the parameter represents an interface.
   *
   * @return		<code>true</code>
   *					if the receiver represents an interface
   *              <code>false</code>
   *                  if it does not represent an interface
   */
  public static boolean isInterface(Class C) {
    if (C.type.isClassType())
    {
      loadType(C);
      return C.type.asClass().isInterface();
    }
    return false;
  }
  /**
   * Answers true if the parameter represents a base type.
   *
   * @return		<code>true</code>
   *					if the receiver represents a base type
   *              <code>false</code>
   *                  if it does not represent a base type
   */
  public static boolean isPrimitive(Class C)
  {
    return C.type.isPrimitiveType();
  }

  /**
   * Answers true if the parameter represents an array type.
   *
   * @return		<code>true</code>
   *					if the receiver represents an
   *					array type
   *              <code>false</code>
   *                  if it does not represent an array type
   */
  public static boolean isArray(Class C) {
    return C.type.isArrayType();
  }

  /**
   * Answers a string containing a concise, human-readable
   * description of the receiver.
   *
   * @return		String 	a printable representation for the receiver.
   */
  public static String classToString(Class C) {
    // Note change from 1.1.7 to 1.2: For primitive types,
    // return just the type name.
    if (C.isPrimitive()) return getName(C);
    else if (C.type.isArrayType())
      return "class " + getName(C);
    else
    {
      loadType(C);
      if (C.isInterface()) return "interface " + getName(C);
      else  return "class "     + getName(C);
    }
  }
  /**
   * Answers the Class which represents the receiver's
   * superclass. For Classes which represent base types,
   * interfaces, and for java.lang.Object the method
   * answers null.
   *
   * @return		Class
   *					the receiver's superclass.
   */
  public static Class getSuperclass(Class C)
  {
    loadAndResolveType(C);
    if (C.type.isArrayType()) {
      return VM_Type.JavaLangObjectType.getClassForType();
    }
    if (!C.type.isClassType()) return null;

    VM_Type supe = C.type.asClass().getSuperClass();
    if (supe == null)
      return null;
    return supe.getClassForType();
  }


  /**
   * Return a new instance of the declaring class, initialized by dynamically invoking
   * the modelled constructor. This reproduces the effect of
   *		<code>new declaringClass(arg1, arg2, ... , argN)</code>
   *
   * This method performs the following:
   * <ul>
   * <li>A new instance of the declaring class is created. If the declaring class cannot be instantiated
   *	(i.e. abstract class, an interface, an array type, or a base type) then an InstantiationException
   *	is thrown.</li>
   * <li>If this Constructor object is enforcing access control (see AccessibleObject) and the modelled
   *	constructor is not accessible from the current context, an IllegalAccessException is thrown.</li>
   * <li>If the number of arguments passed and the number of parameters do not match, an
   *	IllegalArgumentException is thrown.</li>
   * <li>For each argument passed:
   * <ul>
   * <li>If the corresponding parameter type is a base type, the argument is unwrapped. If the unwrapping
   *	fails, an IllegalArgumentException is thrown.</li>
   * <li>If the resulting argument cannot be converted to the parameter type via a widening conversion,
   *	an IllegalArgumentException is thrown.</li>
   * </ul>
   * <li>The modelled constructor is then invoked. If an exception is thrown during the invocation,
   *	it is caught and wrapped in an InvocationTargetException. This exception is then thrown. If
   *	the invocation completes normally, the newly initialized object is returned.
   * </ul>
   *
   * @author		OTI
   * @version		initial
   *
   * @param		args	the arguments to the constructor
   * @return		the new, initialized, object
   * @exception	java.lang.InstantiationException	if the class cannot be instantiated
  * @exception	java.lang.IllegalAccessException	if the modelled constructor is not accessible
    * @exception	java.lang.IllegalArgumentException	if an incorrect number of arguments are passed, or an argument could not be converted by a widening conversion
    * @exception	java.lang.reflect.InvocationTargetException	if an exception was thrown by the invoked constructor
    * @see			java.lang.reflect.AccessibleObject
    */
    public static Object newInstance(Constructor c, Object args[])
    throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
      // TODO check access on ctor

      // Get a new instance, uninitialized.
      //
      VM_Method constructor = c.constructor;
      VM_Class cls = constructor.getDeclaringClass();
      if (!cls.isInitialized()) {
        try {
          VM_Runtime.initializeClassForDynamicLink(cls);
        } catch (VM_ResolutionException e) {
          throw new InstantiationException();
        }
      }

      int      size = cls.getInstanceSize();
      Object[] tib  = cls.getTypeInformationBlock();
      boolean  hasFinalizer = cls.hasFinalizer();
      Object   obj  = VM_Runtime.quickNewScalar(size, tib, hasFinalizer);

      // Run <init> on the instance.
      //
      Method m = new Method(constructor);

      // Note that <init> is not overloaded but it is
      // not static either: it is called with a "this"
      // pointer but not looked up in the TypeInformationBlock.
      // See VM_Reflection.invoke().
      //
      m.invoke(obj, args);
      return obj;
    }


  /**
   * Return a new multidimensional array of the specified component type and dimensions.
   * This reproduces the effect of <code>new componentType[d0][d1]...[dn]</code>
   * for a dimensions array of { d0, d1, ... , dn }
   *
   * @param	componentType	the component type of the new array
   * @param	dimensions		the dimensions of the new array
   * @return	the new array
   * @exception	java.lang.NullPointerException
   *					if the component type is null
   * @exception	java.lang.NegativeArraySizeException
   *					if any of the dimensions are negative
   * @exception	java.lang.IllegalArgumentException
   *					if the array of dimensions is of size zero, or exceeds the limit of
   *					the number of dimension for an array (currently 255)
   */
  public static Object newInstance(Class componentType, int [] dimensions) 
    throws NegativeArraySizeException, IllegalArgumentException {
      int      i, length;
      VM_Type  componentVMType;
      VM_Array arrayType;

      if(componentType == null)
        throw new NullPointerException();
      if(((length = dimensions.length) < 1) || (length > 255))
        throw new IllegalArgumentException();

      // get VM_Array type for multi-dimensional array 
      componentVMType = componentType.getVMType();
      arrayType = componentVMType.getArrayTypeForElementType();
      for ( i = 0; i < length-1; i++ )
        arrayType = arrayType.getArrayTypeForElementType();

      Object obj = VM_Runtime.buildMultiDimensionalArray( dimensions, 0, arrayType );
      return obj;
    }
  /**
   * Return a new array of the specified component type and length.
   * This reproduces the effect of <code>new componentType[size]</code>
   *
   * @param	componentType	the component type of the new array
   * @param	size			the length of the new array
   * @return	the new array
   * @exception	java.lang.NullPointerException
   *					if the component type is null
   * @exception	java.lang.NegativeArraySizeException
   *					if the size if negative
   */
  public static Object newInstance(Class componentType, int size) throws NegativeArraySizeException {
    if(componentType == null)
      throw new NullPointerException();
    if(size < 0)
      throw new NegativeArraySizeException();

    VM_Array arrayType = (VM_Array)arrayClassCache.get(componentType);
    if (arrayType == null) {
      synchronized (VM_ClassLoader.lock) {
        arrayType = componentType.getVMType().getArrayTypeForElementType();
        arrayType.load();
        arrayType.resolve();
        arrayType.instantiate();
        arrayClassCache.put(componentType, arrayType);
      }
    }

    Object[] tib = arrayType.getTypeInformationBlock();
    Object   obj = VM_Runtime.quickNewArray(size, arrayType.getInstanceSize(size), tib);
    return obj;
  }
  /**
   * Answers a Class object which represents the class
   * named by the argument. The name should be the name
   * of a class as described in the class definition of
   * java.lang.Class, however Classes representing base
   * types can not be found using this method.
   *
   * @return		the named Class
   * @param		className name of the non-base type class to find
   * @exception	ClassNotFoundException If the class could not be found
   *
   * @see			java.lang.Class
   */
  public static Class forName(String typeName) throws ClassNotFoundException {
    boolean DEBUG = false;
    if (DEBUG) VM.sysWrite("Class.forName starting", typeName);
    SecurityManager security = System.getSecurityManager();
    if (security != null)
      throw new VM_UnimplementedError("Classloading with security manager");
    ClassLoader defaultClassLoader = VM_Class.getClassLoaderFromStackFrame(1);
    // If requestor was loaded by a user's classloader, invoke it for load
    // 
    if (defaultClassLoader != null && defaultClassLoader != 
        VM_SystemClassLoader.getVMClassLoader()) {
      if (DEBUG) VM.sysWrite("Class.forName loading with special class loader ", typeName);
      return defaultClassLoader.loadClass(typeName);
    }
    else {
      if (DEBUG) VM.sysWrite("Class.forName loading with default class loader ", typeName);
      Class guess = (Class) classCache.get( typeName );
      if (guess != null)  return guess;

      synchronized (VM_ClassLoader.lock) {
        try {
          if (typeName.startsWith("[")) {
            if (!validArrayDescriptor(typeName)) throw new IllegalArgumentException();
            else classCache.put(typeName, VM_Array.forName(typeName).getClassForType());
          }
          else
            classCache.put(typeName, VM_Class.forName(typeName).getClassForType());
          if (DEBUG) VM.sysWrite("Class.forName returning for ", typeName);
          return (Class) classCache.get( typeName );
        }
        catch (VM_ResolutionException e) {
          throw new ClassNotFoundException(typeName);
        }
      }
    }
  }
  /**
   * Answers a Class object which represents the class
   * named by the argument. The name should be the name
   * of a class as described in the class definition of
   * java.lang.Class, however Classes representing base
   * types can not be found using this method.
   * Security rules will be obeyed.
   *
   * @return		Class The named class.
   * @param		className The name of the non-base type class to find
   * @param		initializeBoolean A boolean indicating whether the class should be
   *					initialized
   * @param		classLoader The classloader to use to load the class
   * @exception	ClassNotFoundException If the class could not be found
   *
   * @see			java.lang.Class
   */
  public static Class forName(String className, boolean initialize, ClassLoader classLoader) throws ClassNotFoundException {
    SecurityManager security = System.getSecurityManager();
    boolean DEBUG = false;
    if (security != null)
      throw new VM_UnimplementedError("Classloading with security manager");

    if ( (initialize == true) 
         && ( (classLoader == null) 
              || (classLoader instanceof VM_SystemClassLoader) ) ) {

      Class guess = (Class) classCache.get( className );;
      if (guess != null) return guess;

      synchronized (VM_ClassLoader.lock) {
        try {
          if (className.startsWith("[")) {
            if (!validArrayDescriptor(className)) throw new IllegalArgumentException();
            classCache.put(className, VM_Array.forName(className).getClassForType());
            return (Class) classCache.get( className );
          }
          else {
            classCache.put(className, VM_Class.forName(className).getClassForType());
            return (Class) classCache.get( className );
          }
        }
        catch (VM_ResolutionException e) {
          throw new ClassNotFoundException(className);
        }
      }

    }
    else {

      if (DEBUG) VM.sysWrite("Class.forName 3 args, loading", className);
      if  ( classLoader == null  ||  classLoader instanceof VM_SystemClassLoader) 
        throw new VM_UnimplementedError("NoLoad option not supported for system classloader yet");
      else
        return classLoader.loadClass(className, initialize);
    }
  }
  /**
   * Answers a Method object from Class C which represents the method
   * described by the arguments. Note that the associated
   * method may not be visible from the current execution
   * context.
   *
   * @return		the method described by the arguments.
   * @param		name the name of the method
   * @param		parameterTypes the types of the arguments.
   * @exception	NoSuchMethodException	if the method could not be found.
   * @exception	SecurityException if member access is not allowed
   *
   * @see			getMethods
   */
  public static Method getDeclaredMethod(Class C, String name, Class parameterTypes[]) 
    throws NoSuchMethodException, SecurityException {
      checkMemberAccess(C,Member.DECLARED);

      // Handle the no parameter case upfront
      if(parameterTypes == null || parameterTypes.length == 0)
        /// ** changed for RVM **
        ///return getDeclaredMethodImpl(name, new Class[0], "()");
        /// ** end RVM change **
        return getMethod0(C, name, new Class[0], Method.DECLARED);

      return getMethod0(C, name, parameterTypes, Method.DECLARED);
    }
  /**
   * Answers an array containing Method objects describing
   * all methods which are defined by the receiver. Note that
   * some of the fields which are returned may not be visible
   * in the current execution context.
   *
   * @return		the receiver's methods.
   * @exception	SecurityException if member access is not allowed
   * @see			getMethods
   */
  public static Method[] getDeclaredMethods(Class C) throws SecurityException {
    checkMemberAccess(C,Member.DECLARED);
    if (!C.type.isClassType()) return new Method[0];
    return getMethods0(C, Member.DECLARED);
  }

  /**
   * Answers a Method object which represents the method
   * described by the arguments.
   *
   * @param		name String
   *					the name of the method
   * @param		parameterTypes Class[]
   *					the types of the arguments.
   * @return		Method
   *					the method described by the arguments.
   * @exception	NoSuchMethodException
   *					if the method could not be found.
   * @exception	SecurityException
   *					if member access is not allowed
   *
   * @see			getMethods
   */
  public static Method getMethod(Class C, String name, Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
    checkMemberAccess(C,Member.PUBLIC);

    // Handle the no parameter case upfront
    if(parameterTypes == null || parameterTypes.length == 0)
      return getMethod0(C, name, new Class[0], Member.PUBLIC);

    return getMethod0(C, name, parameterTypes, Member.PUBLIC);
  }
  /**
   * Answers an array containing Method objects describing
   * all methods which are visible from the current execution
   * context.
   *
   * @return		Method[]
   *					all visible methods starting from the receiver.
   * @exception	SecurityException
   *					if member access is not allowed
   *
   * @see			getDeclaredMethods
   */
  public static Method[] getMethods(Class C) throws SecurityException {
    checkMemberAccess(C,Member.PUBLIC);
    return getMethods0(C, Member.PUBLIC);
  }

  /**
   * Answers a Field object describing the field in C
   * named by the argument. Note that the Constructor may not be
   * visible from the current execution context.
   *
   * @param		name The name of the field to look for.
   * @return		the field in the receiver named by the argument.
   * @exception	NoSuchFieldException if the requested field could not be found
   * @exception	SecurityException if member access is not allowed
   * @see			getDeclaredFields
   */
  public static Field getDeclaredField(Class C, String name) throws NoSuchFieldException, SecurityException {
    checkMemberAccess(C,Member.DECLARED);
    return getField0(C, name, Member.DECLARED);
  }

  /**
   * Answers a Field object describing the field in the receiver
   * named by the argument which must be visible from the current
   * execution context.
   *
   * @return		the field in the receiver named by the argument.
   * @param		name The name of the field to look for.
   *
   * @see			getDeclaredFields
   */
  public static Field getField(Class C, String name) throws NoSuchFieldException, SecurityException {
    checkMemberAccess(C,Member.PUBLIC);
    return getField0(C, name, Member.PUBLIC );
  }
  /**
   * Answers an array containing Field objects describing
   * all fields which are visible from the current execution
   * context.
   *
   * @return		Field[]
   *					all visible fields starting from the receiver.
   * @exception	SecurityException
   *					if member access is not allowed
   *
   * @see			getDeclaredFields
   */
  public static Field[] getFields(Class C) throws SecurityException {
    checkMemberAccess(C, Member.PUBLIC);
    return getFields0(C,Member.PUBLIC);
  }

  /**
   * Answers an array containing Field objects describing
   * all fields which are defined by the receiver. Note that
   * some of the fields which are returned may not be visible
   * in the current execution context.
   *
   * @return		the receiver's fields.
   * @exception	SecurityException if member access is not allowed
   * @see			getFields
   */
  public static Field[] getDeclaredFields(Class C) throws SecurityException {
    checkMemberAccess(C, Member.DECLARED);
    if (!C.type.isClassType()) return new Field[0];

    return getFields0(C, Member.DECLARED);
  }



  private static Method getMethod0(Class C, String name, Class[] parameterTypes, int which)
    throws NoSuchMethodException
    {
      loadAndResolveType(C);

      if (!C.type.isClassType()) throw new NoSuchMethodException();

      VM_Method vm_virtual_methods[] = null;
      VM_Method vm_other_methods[] = null;

      if ( which == Member.PUBLIC ) {
        vm_virtual_methods = C.type.getVirtualMethods();
        vm_other_methods = C.type.getStaticMethods();
      } else {
        vm_virtual_methods = new VM_Method[0];
        vm_other_methods = C.type.asClass().getDeclaredMethods(); 
      }

      // TODO: Problem here with User flooding the atom dictionary if
      // getMethod used too many times with different not existing names
      // Solution: create a VM_Atom.findUnicodeAtom method.. to be done later.
      // - Eugene
      VM_Atom aName = VM_Atom.findOrCreateUnicodeAtom( name );

      // TODO: I should use VM_Class.findVirtualMethod, findStaticMethod,
      // findDeclaredMethod, but I need to create a
      // "member descriptor" Atom from the array of parameter types... 
      // maybe later ...
      // ( note if we create the string that represents that array,
      // we need to worry about the flooding problem too just as above when
      // getting the atom )
      // - Eugene

      for (int j = 0; j < vm_virtual_methods.length; j++)
      {
        VM_Method meth = vm_virtual_methods[j];
        if (!meth.isObjectInitializer() &&
            ( meth.getName() == aName ) &&
            parametersMatch(meth.getParameterTypes(), parameterTypes))
          return new java.lang.reflect.Method(meth);
      }

      // TODO: Object initializers should not be returned by getStaticMethods
      // - Eugene
      for (int j = 0; j < vm_other_methods.length; j++) {
        VM_Method meth = vm_other_methods[j];
        if (!meth.isClassInitializer() && !meth.isObjectInitializer() &&
            ( meth.getName() == aName ) &&
            parametersMatch(meth.getParameterTypes(), parameterTypes))
          return new java.lang.reflect.Method(meth);
      }

      throw new NoSuchMethodException( name + parameterTypes );
    }

  /**
   * Set the value of the field in the specified object to the boolean value.
   * This reproduces the effect of <code>object.fieldName = value</code>
   *
   * If the modelled field is static, the object argument is ignored.
   * Otherwise, if the object is null, a NullPointerException is thrown.
   * If the object is not an instance of the declaring class of the method, an
   * IllegalArgumentException is thrown.
   *
   * If this Field object is enforcing access control (see AccessibleObject) and the modelled
   * field is not accessible from the current context, an IllegalAccessException is thrown.
   *
   * If the field type is a base type, the value is automatically unwrapped. If the unwrap fails,
   * an IllegalArgumentException is thrown. If the value cannot be converted to the field type via
   * a widening conversion, an IllegalArgumentException is thrown.
   *
   * @param	object	the object to access
   * @param	value	the new value
   * @exception	java.lang.NullPointerException
   *					if the object is null and the field is non-static
   * @exception	java.lang.IllegalArgumentException
   *					if the object is not compatible with the declaring class, or the value could not be converted to the field type via a widening conversion
   * @exception	java.lang.IllegalAccessException
   *					if modelled field is not accessible
   */
  public static void setField(Field f, Object object, Object value) throws IllegalAccessException, IllegalArgumentException
  {
    VM_Type type = f.field.getType();

    if (type.isReferenceType())      f.field.setObjectValue(object, value);
    else if (type.isCharType())      f.field.setCharValue(object, VM_Reflection.unwrapChar(value));
    else if (type.isDoubleType())    f.field.setDoubleValue(object, VM_Reflection.unwrapDouble(value));
    else if (type.isFloatType())     f.field.setFloatValue(object, VM_Reflection.unwrapFloat(value));
    else if (type.isLongType())      f.field.setLongValue(object, VM_Reflection.unwrapLong(value));
    else if (type.isIntType())       f.field.setIntValue(object, VM_Reflection.unwrapInt(value));
    else if (type.isShortType())     f.field.setShortValue(object, VM_Reflection.unwrapShort(value));
    else if (type.isByteType())      f.field.setByteValue(object, VM_Reflection.unwrapByte(value));
    else if (type.isBooleanType())   f.field.setBooleanValue(object, VM_Reflection.unwrapBoolean(value));

  }
  /**
   * Return the result of dynamically invoking the modelled method.
   * This reproduces the effect of
   *		<code>receiver.methodName(arg1, arg2, ... , argN)</code>
   *
   * This method performs the following:
   * <ul>
   * <li>If the modelled method is static, the receiver argument is ignored.</li>
   * <li>Otherwise, if the receiver is null, a NullPointerException is thrown.</li>
   * If the receiver is not an instance of the declaring class of the method, an
   *	IllegalArgumentException is thrown.
   * <li>If this Method object is enforcing access control (see AccessibleObject) and the modelled
   *	constructor is not accessible from the current context, an IllegalAccessException is thrown.</li>
   * <li>If the number of arguments passed and the number of parameters do not match, an
   *	IllegalArgumentException is thrown.</li>
   * <li>For each argument passed:
   * <ul>
   * <li>If the corresponding parameter type is a base type, the argument is unwrapped. If the unwrapping
   *	fails, an IllegalArgumentException is thrown.</li>
   * <li>If the resulting argument cannot be converted to the parameter type via a widening conversion,
   *	an IllegalArgumentException is thrown.</li>
   * </ul>
   * <li> If the modelled method is static, it is invoked directly. If it is non-static, the modelled
   *	method and the reciever are then used to perform a standard dynamic method lookup. The resulting
   *	method is then invoked.</li>
   * <li>If an exception is thrown during the invocation it is caught and wrapped in an
   *	InvocationTargetException. This exception is then thrown.</li>
   * <li>If the invocation completes normally, the return value is itself returned. If the method
   *	is declared to return a base type, the return value is first wrapped. If the return type is
   *	void, null is returned.</li>
   * </ul>
   *
  *
    * @param		args	the arguments to the constructor
    * @return		the new, initialized, object
    * @exception	java.lang.NullPointerException		if the receiver is null for a non-static method
    * @exception	java.lang.IllegalAccessException	if the modelled method is not accessible
    * @exception	java.lang.IllegalArgumentException	if an incorrect number of arguments are passed, the receiver is incompatible with the declaring class, or an argument could not be converted by a widening conversion
    * @exception	java.lang.reflect.InvocationTargetException	if an exception was thrown by the invoked constructor
    * @see			java.lang.reflect.AccessibleObject
    */
    public static Object invoke(Method m, Object receiver, Object args[])
    throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
      VM_Method method = m.method;
      VM_Type[] parameterTypes = method.getParameterTypes();
      VM_Type   returnType     = method.getReturnType();

      // validate "this" argument
      //
      if (!method.isStatic())
      {
        if (receiver == null)
          throw new NullPointerException();

        if (!argumentIsCompatible(method.getDeclaringClass(), receiver))
          throw new IllegalArgumentException("type mismatch on \"this\" argument");
      }

      // validate number and types of remaining arguments
      // !!TODO: deal with widening/narrowing conversions on primitives
      //
      if (args == null) {
        if (parameterTypes.length != 0)
          throw new IllegalArgumentException("argument count mismatch");
      } else if (args.length != parameterTypes.length)
        throw new IllegalArgumentException("argument count mismatch");

      for (int i = 0, n = parameterTypes.length; i < n; ++i)
        if (!argumentIsCompatible(parameterTypes[i], args[i]))
          throw new IllegalArgumentException("type mismatch on argument " + i);

      // invoke method
      // Note that we catch all possible exceptions, not just Error's and RuntimeException's,
      // for compatibility with jdk behavior (which even catches a "throw new Throwable()").
      //
      try
      {
        return VM_Reflection.invoke(method, receiver, args);
      }
      catch (Throwable e)
      {
        throw new InvocationTargetException(e);
      }
    }


  /**
   * Return an array of the java.lang.Class objects associated with the
   * exceptions declared to be thrown by this method. If the method was
   * not declared to throw any exceptions, the array returned will be
   * empty.
   *
   * @return		the declared exception classes
   */
  public static Class[] getExceptionTypes(Method m)
  {
    VM_Type[] exceptionTypes = m.method.getExceptionTypes();
    return typesToClasses(exceptionTypes == null ? new VM_Type[0] : exceptionTypes);
  }

  /**
   * Return an array of the java.lang.Class objects associated with the
   * exceptions declared to be thrown by this constructor. If the method was
   * not declared to throw any exceptions, the array returned will be
   * empty.
   *
   * @return		the declared exception classes
   */
  public static Class[] getExceptionTypes(Constructor c)
  {
    VM_Type[] exceptionTypes = c.constructor.getExceptionTypes();
    return typesToClasses(exceptionTypes == null ? new VM_Type[0] : exceptionTypes);
  }

  /**
   * Return the java.lang.Class associated with the return type of
   * this method.
   *
   * @return		the return type
   */
  public static Class getReturnType(Method m)
  {
    return m.method.getReturnType().getClassForType();
  }


  /**
   * Return the java.lang.Class associated with the class that defined
   * this constructor.
   *
   * @return		the declaring class
   */
  public static Class getDeclaringClass(Constructor c)
  {
    return c.constructor.getDeclaringClass().getClassForType();
  }
  /**
   * Return the java.lang.Class associated with the class that defined
   * this method.
   *
   * @return		the declaring class
   */
  public static Class getDeclaringClass(Method m)
  {
    return m.method.getDeclaringClass().getClassForType();
  }

  /**
   * Return an array of the java.lang.Class objects associated with the
   * parameter types of this constructor. If the method was declared with no
   * parameters, the array returned will be empty.
   *
   * @return		the parameter types
   */
  public static Class[] getParameterTypes(Constructor c)
  {
    return typesToClasses(c.constructor.getParameterTypes());
  }

  /**
   * Return an array of the java.lang.Class objects associated with the
   * parameter types of this method. If the method was declared with no
   * parameters, the array returned will be empty.
   *
   * @return		the parameter types
   */
  public static Class[] getParameterTypes(Method m)
  {
    return typesToClasses(m.method.getParameterTypes());
  }

  /**
   * Wrap underlying VM_Methods in Method objects.
   * @return array of Method objects for this Class
   *
   * @author jjb 5/98
   * @author Eugene Gluzberg 4/2000
   */

  private static Method[] getMethods0(Class C, int which) {
    loadAndResolveType(C);
    Collector coll = new Collector();

    VM_Method vm_virtual_methods[] = null;
    VM_Method vm_other_methods[] = null;
    if ( which == Member.PUBLIC ) {
      vm_virtual_methods = C.type.getVirtualMethods();
      vm_other_methods = C.type.getStaticMethods();
    } else {
      vm_virtual_methods = new VM_Method[0];
      vm_other_methods = C.type.asClass().getDeclaredMethods(); 
    }

    for (int j = 0; j < vm_other_methods.length; j++)
    {
      // Java Language Spec 8.2: class and initializers are not members thus not methods.
      // TODO: Object initializers should not be returned by getStaticMethods
      // - Eugene
      if (!vm_other_methods[j].isClassInitializer() &&
          !vm_other_methods[j].isObjectInitializer() &&
          ! ( which == Member.PUBLIC && !vm_other_methods[j].isPublic() )  )
        coll.collect(new java.lang.reflect.Method(vm_other_methods[j]));
    }

    for (int j = 0; j < vm_virtual_methods.length; j++)
    {
      if (!vm_virtual_methods[j].isObjectInitializer() &&
          ! ( which == Member.PUBLIC && !vm_virtual_methods[j].isPublic() )
         )
        coll.collect(new java.lang.reflect.Method(vm_virtual_methods[j]));
    }

    return coll.methodArray();
  }

  /**
   * Answers an array containing Constructor objects describing
   * all constructors which are visible from the current execution
   * context.
   *
   * @return		all visible constructors starting from the receiver.
   * @exception	SecurityException if member access is not allowed
   * @see			getMethods
   */
  public static Constructor[] getConstructors(Class C) throws SecurityException {
    checkMemberAccess(C, Member.PUBLIC);
    return getConstructors0(C, Member.PUBLIC );
  }



  private static Constructor[] getConstructors0(Class C, int which)
  {
    loadAndResolveType(C);
    // TODO: constructors should not be returned by getStaticMethods 
    // - Eugene
    VM_Method vm_static_methods[] = C.type.getStaticMethods();
    Collector coll = new Collector();
    for (int i = 0; i < vm_static_methods.length; i++) 
    {
      if (vm_static_methods[i].isObjectInitializer() &&
          ! ( which == Member.PUBLIC && !vm_static_methods[i].isPublic() ) )
        coll.collect(new Constructor(vm_static_methods[i]));
    }
    return coll.constructorArray();
  }

  private static Constructor getConstructor0(Class C, Class parameterTypes[], int which )
    throws NoSuchMethodException
    {
      loadAndResolveType(C);

      if (!C.type.isClassType()) throw new NoSuchMethodException();

      // TODO: Did I already mention that Object initializers should not be
      // returned by getStaticMethods? :)
      // - Eugene

      // TODO: I should use findInitilizerMethod, but I need to create a
      // "member descriptor" Atom from the array of parameter types... 
      // maybe later ...
      // ( note if we create the string that represents that array,
      // we need to worry about the flooding problem too
      // just as above (in getMethod0)  when getting the atom )
      // - Eugene

      VM_Method methods[];

      if ( which == Member.PUBLIC ) {
        methods = C.type.getStaticMethods();
      } else {
        methods = C.type.asClass().getDeclaredMethods();
      }

      for (int i = 0, n = methods.length; i < n; ++i)
      {
        VM_Method method = methods[i];
        if (method.isObjectInitializer() &&
            ! ( which == Member.PUBLIC && !method.isPublic() )
            && parametersMatch(method.getParameterTypes(), parameterTypes))
          return new java.lang.reflect.Constructor(method);
      }

      throw new NoSuchMethodException("<init> " + parameterTypes );
    }

  private static Field[] getFields0(Class C, int which)
  {
    loadAndResolveType(C);
    Collector coll = new Collector();

    VM_Field vm_instance_fields[] = null;
    VM_Field vm_other_fields[] = null;
    if ( which == Member.PUBLIC ) {
      vm_instance_fields = C.type.getInstanceFields();
      vm_other_fields = C.type.getStaticFields();
    } else {
      vm_instance_fields = new VM_Field[0];
      vm_other_fields = C.type.asClass().getDeclaredFields(); 
    }

    for (int j = 0; j < vm_other_fields.length; j++)
    {
      if ( ! ( which == Member.PUBLIC && !vm_other_fields[j].isPublic() )  )
        coll.collect(new java.lang.reflect.Field(vm_other_fields[j]));
    }

    for (int j = 0; j < vm_instance_fields.length; j++)
    {
      if ( ! ( which == Member.PUBLIC && !vm_instance_fields[j].isPublic() )
         )
        coll.collect(new java.lang.reflect.Field(vm_instance_fields[j]));
    }

    return coll.fieldArray();
  }

  private static Field getField0(Class C, String name, int which)
    throws NoSuchFieldException
    {
      loadAndResolveType(C);

      if (!C.type.isClassType()) throw new NoSuchFieldException();

      VM_Field vm_instance_fields[] = null;
      VM_Field vm_other_fields[] = null;
      if ( which == Member.PUBLIC ) {
        vm_instance_fields = C.type.getInstanceFields();
        vm_other_fields = C.type.getStaticFields();
      } else {
        vm_instance_fields = new VM_Field[0];
        vm_other_fields = C.type.asClass().getDeclaredFields(); 
      }


      // TODO: Problem here with User flooding the atom dictionary if
      // getMethod used too many times with different not existing names
      // Solution: create a VM_Atom.findUnicodeAtom method.. to be done later.
      // - Eugene
      VM_Atom aName = VM_Atom.findOrCreateUnicodeAtom( name );

      for (int j = 0; j < vm_other_fields.length; j++)
      {
        if ( ! ( which == Member.PUBLIC && !vm_other_fields[j].isPublic() ) &&
             ( vm_other_fields[j].getName() == aName ) )
          return new java.lang.reflect.Field( vm_other_fields[j] );
      }

      for (int j = 0; j < vm_instance_fields.length; j++)
      {
        if ( ! ( which == Member.PUBLIC && !vm_instance_fields[j].isPublic() ) &&
             ( vm_instance_fields[j].getName() == aName ) )
          return new java.lang.reflect.Field(vm_instance_fields[j]);
      }

      throw new NoSuchFieldException( name );

    }
  /**
   * Answers a Class object which represents C's
   * component type if C represents an array type.
   * Otherwise answers nil. The component type of an array
   * type is the type of the elements of the array.
   *
   * @return		Class the component type of the receiver.
   * @see			java.lang.Class
   */
  public static Class getComponentType(Class C) {
    if (C.type.isArrayType()) return C.type.asArray().getElementType().getClassForType();
    else                    return null;
  }

  /**
   * Answers an array containing Constructor objects describing
   * all constructors which are defined by C. Note that
   * some of the fields which are returned may not be visible
   * in the current execution context.
   *
   * @return		the receiver's constructors.
   * @exception	SecurityException if member access is not allowed
   * @see			getMethods
   */
  public static Constructor[] getDeclaredConstructors(Class C) throws SecurityException {
    checkMemberAccess(C, Member.DECLARED);
    if (!C.type.isClassType()) return new Constructor[0];

    return getConstructors0(C, Member.DECLARED);
  }

  /**
   * Answers a Constructor object which represents the
   * constructor described by the arguments.
   *
   * @return		the constructor described by the arguments.
   * @param		parameterTypes the types of the arguments.
   * @exception	NoSuchMethodException if the constructor could not be found.
   * @exception	SecurityException if member access is not allowed
   * @see			getConstructors
   */
  public static Constructor getDeclaredConstructor(Class C, Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
    checkMemberAccess(C, Member.DECLARED);

    // Handle the default constructor case upfront
    if(parameterTypes == null || parameterTypes.length == 0)
      return getConstructor0(C, new Class[0], Member.DECLARED);

    return getConstructor0(C, parameterTypes, Member.DECLARED);
  }


  /**
   * Answers a public Constructor object which represents the
   * constructor described by the arguments.
   *
   * @return		the constructor described by the arguments.
   * @param		parameterTypes the types of the arguments.
   * @exception	NoSuchMethodException if the constructor could not be found.
   * @exception	SecurityException if member access is not allowed
   * @see			getConstructors
   */
  public static Constructor getConstructor(Class C, Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
    checkMemberAccess(C, Member.PUBLIC);

    // Handle the default constructor case upfront
    if(parameterTypes == null || parameterTypes.length == 0)
      return getConstructor0(C, new Class[0], Member.PUBLIC);

    return getConstructor0(C, parameterTypes, Member.PUBLIC);
  }                                                                                            

  /**
   * Answers an integer which which is the receiver's modifiers.
   * Note that the constants which describe the bits which are
   * returned are implemented in class java.lang.reflect.Modifiers
   * which may not be available on the target.
   *
   * @return		the receiver's modifiers
   */
  public static int getModifiers(Class C) {
    if (C.type.isClassType()) {
      return C.type.asClass().getModifiers();
    } else if (C.type.isArrayType()) {  
      int result = C.type.asArray().getElementType().getClassForType().getModifiers();
      result &= Modifier.FINAL;
      result &= ~Modifier.INTERFACE;
      return result;
    } else {  // primitive or reference type;
      int result = Modifier.PUBLIC; 
      result &= Modifier.FINAL;
      return result;
    }
  }

  /**
   * Return the modifiers for a method
   * The Modifier class should be used to decode the result.
   *
   * @return		the modifiers
   * @see			java.lang.reflect.Modifier
   */
  public static int getModifiers(Method m)
  {
    return m.method.getModifiers();
  }
  /**
   * Return the modifiers for the modelled constructor.
   * The Modifier class should be used to decode the result.
   *
   * @return		the modifiers
   * @see			java.lang.reflect.Modifier
   */
  public static int getModifiers(Constructor c)
  {
    return c.constructor.getModifiers();
  }
  /**
   */ 
  public static String getSignature(Constructor c) {
    return c.constructor.getDescriptor().toString();
  } 
  /**
   */ 
  public static String getSignature(Method m) {
    return m.method.getDescriptor().toString();
  } 

  /**
   * Compares the specified object to this Method and answer if they
   * are equal. The object must be an instance of Method with the same
   * defining class and parameter types.
   *
   * @param		object	the object to compare
   * @return		true if the specified object is equal to this Method, false otherwise
   *
   * @see			#hashCode
   */
  public static boolean methodEquals(Method m,Object object)
  {
    VM_Method method = m.method;
    if (object != null && object instanceof Method)
    {
      Method other = (Method) object;
      if (method != null)
        return method.equals(other.method);
      else {
        VM.sysFail("Should not get here");
      }

    }
    return false;
  }


  /**
   * Answers the name of the class which the receiver represents.
   * For a description of the format which is used, see the class
   * definition of java.lang.Class.
   *
   * @return		the receiver's name.
   *
   * @see			java.lang.Class
   */
  public static String getName(Class C) {
    return C.type.getName();
  }

  /**
   * Return the name of the modelled method.
   *
   * @return		the name
   */
  public static String getName(Method m)
  {
    return m.method.getName().toString();
  }

  /**
   * Utility for security checks
   */
  private static void checkMemberAccess(Class C, int type) {
    SecurityManager security = System.getSecurityManager();
    if (security != null) {
      security.checkMemberAccess(C, type);
      String packageName = C.getPackageName();
      if(packageName != "")
        security.checkPackageAccess(packageName);
    }
  }
  /**
   * Answers a new instance of the class represented by the
   * receiver, created by invoking the default (i.e. zero-argument)
   * constructor. If there is no such constructor, or if the
   * creation fails (either because of a lack of available memory or
   * because an exception is thrown by the constructor), an
   * InstantiationException is thrown. If the default constructor
   * exists, but is not accessible from the context where this
   * message is sent, an IllegalAccessException is thrown.
   *
   * @return		a new instance of the class represented by the receiver.
   * @exception	IllegalAccessException if the constructor is not visible to the sender.
   * @exception	InstantiationException if the instance could not be created.
   */
  public static Object newInstance(Class C) throws IllegalAccessException, InstantiationException {
    checkMemberAccess(C, Member.PUBLIC);
    Constructor cons;
    try
    {
      cons = getDeclaredConstructor(C, new Class[0]);
      return cons.newInstance(new Object[0]);
    }
    catch (java.lang.reflect.InvocationTargetException e)
    {
      throw new InstantiationException(e.getMessage());
    }
    catch (NoSuchMethodException e)
    {
      throw new IllegalAccessException(e.getMessage());
    }
  }

  /**
   * Create and return a new instance of class <code>instantiationClass</code> but running the
   * constructor defined in class <code>constructorClass</code> (same as <code>instantiationClass</code>
   * or a superclass).
   *
   *	Has to be native to avoid visibility rules and to be able to have <code>instantiationClass</code>
   * not the same as <code>constructorClass</code> (no such API in java.lang.reflect).
   *
   * @param		instantiationClass		The new object will be an instance of this class
   * @param		constructorClass		The empty constructor to run will be in this class
   */
  public static Object newInstance (Class instantiationClass, 
                                    Class constructorClass) 
    throws NoSuchMethodException, 
  IllegalAccessException,
  java.lang.reflect.InvocationTargetException{  
    VM_Class iKlass = instantiationClass.getVMType().asClass();
    if (!iKlass.isInstantiated()) {
      iKlass.instantiate();
      iKlass.initialize();
    }

    // allocate the object
    int size     = iKlass.getInstanceSize();
    Object[] tib = iKlass.getTypeInformationBlock();
    boolean  hasFinalizer = iKlass.hasFinalizer();
    Object obj   = VM_Runtime.quickNewScalar(size,tib,hasFinalizer);

    // run the constructor 
    Constructor cons = constructorClass.getDeclaredConstructor(new Class[0]);
    VM_Method cm = cons.getVMConstructor();
    Method m = new Method(cm);
    m.invoke(obj,null);
    return obj;
  }

  /**
   * Compare parameter lists for agreement.
   */ 
  private static boolean parametersMatch(VM_Type[] lhs, Class[] rhs) {
    if (lhs.length != rhs.length)
      return false;

    for (int i = 0, n = lhs.length; i < n; ++i)
      if (lhs[i] != rhs[i].type)
        return false;

    return true;
  }
  // Check if (possibly wrapped) method argument is compatible with expected type.
  //
  private static boolean argumentIsCompatible(VM_Type expectedType, Object arg) {
    if (!expectedType.isPrimitiveType())
    { // object argument - not wrapped
      if (arg == null)
        return true;

      VM_Type actualType = VM_Magic.getObjectType(arg);
      try { 
        if (expectedType == actualType) return true;
        if (expectedType == VM_Type.JavaLangObjectType) return true;
        return VM_Runtime.isAssignableWith(expectedType, actualType); 
      } catch (VM_ResolutionException e) {}

      return false;
    }

    // primitive argument - wrapped
    //
    if (arg == null)
      return false;

    if (expectedType == VM_Type.VoidType)    return arg instanceof java.lang.Void;
    if (expectedType == VM_Type.BooleanType) return arg instanceof java.lang.Boolean;
    if (expectedType == VM_Type.ByteType)    return arg instanceof java.lang.Byte;
    if (expectedType == VM_Type.ShortType)   return arg instanceof java.lang.Short;
    if (expectedType == VM_Type.IntType)     return arg instanceof java.lang.Integer;
    if (expectedType == VM_Type.LongType)    return arg instanceof java.lang.Long;
    if (expectedType == VM_Type.FloatType)   return arg instanceof java.lang.Float;
    if (expectedType == VM_Type.DoubleType)  return arg instanceof java.lang.Double;
    if (expectedType == VM_Type.CharType)    return arg instanceof java.lang.Character;

    return false;
  }

  /**
   * Convert from "vm" type system to "jdk" type system.
   */ 
  private static Class[] typesToClasses(VM_Type[] types)
  {
    Class[] classes = new Class[types.length];
    for (int i = 0; i < types.length; i++)
      classes[i] = types[i].getClassForType();
    return classes;
  }
  private static boolean validArrayDescriptor (String name)
  {
    int i;
    int length = name.length();

    for (i = 0; i < length; i++)
      if (name.charAt(i) != '[') break;
    if (i == length) return false;	// string of only ['s

    if (i == length - 1)
      switch (name.charAt(i))
      {
        case 'B':	return true;	// byte
        case 'C':	return true; 	// char
        case 'D':	return true; 	// double
        case 'F':	return true; 	// float
        case 'I':	return true; 	// integer
        case 'J':	return true; 	// long
        case 'S':	return true; 	// short
        case 'Z':	return true; 	// boolean
        default:	return false;
      }

    else if (name.charAt(i) != 'L') return false;    // not a class descriptor
    else if (name.charAt(length - 1) != ';') return false;	// ditto

    return true;			// a valid class descriptor
  }

  // aux inner class to do collection for some getters
  private static class Collector {
    int      n   = 0;
    Object[] obs = new Object[10];

    void collect(Object thing) {
      if (n == obs.length) {
        Object[] newObs = new Object[2 * n];
        System.arraycopy(obs, 0, newObs, 0, n);
        obs = newObs;
      }
      obs[n++] = thing;
    }

    // !!TODO: could be done faster as single call to arraycopy
    void collect(Object[] things) {
      for (int i = 0; i != things.length; i++)
        collect(things[i]);
    }

    // repeat for each class of interest
    //
    Method[] methodArray() {
      Method[] newObs = new Method[n];
      System.arraycopy(obs, 0, newObs, 0, n);
      return newObs;
    }

    Field[] fieldArray() {
      Field[] newObs = new Field[n];
      System.arraycopy(obs, 0, newObs, 0, n);
      return newObs;
    }

    Constructor[] constructorArray() {
      Constructor[] newObs = new Constructor[n];
      System.arraycopy(obs, 0, newObs, 0, n);
      return newObs;
    }
  }
}
