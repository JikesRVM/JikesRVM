/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$

package com.ibm.JikesRVM.librarySupport;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

import java.util.HashMap;
import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Atom;
import com.ibm.JikesRVM.VM_Array;
import com.ibm.JikesRVM.VM_Callbacks;
import com.ibm.JikesRVM.VM_Class;
import com.ibm.JikesRVM.VM_Field;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Method;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_ResolutionException;
import com.ibm.JikesRVM.VM_Runtime;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_SystemClassLoader;
import com.ibm.JikesRVM.VM_Type;
import com.ibm.JikesRVM.VM_UnimplementedError;
import java.lang.reflect.*;
import java.io.*;

/**
 * This class provides a set of static method entrypoints used in the
 * implementation of standard library type operations.
 *
 * @author John Barton 
 * @author Stephen Fink
 * @author Eugene Gluzberg
 */
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
      return java.lang.JikesRVMSupport.getTypeForClass(A) == java.lang.JikesRVMSupport.getTypeForClass(B) ||
        VM_Runtime.isAssignableWith(java.lang.JikesRVMSupport.getTypeForClass(A), java.lang.JikesRVMSupport.getTypeForClass(B));
    } catch (VM_ResolutionException e) {
      throw new NoClassDefFoundError(e.getException().toString());
    }
  }

  /**
   * Load the VM_Type for Class C.
   */ 
  private static void loadType(Class C) {
    if (java.lang.JikesRVMSupport.getTypeForClass(C).isLoaded()) return;
    try { 
      java.lang.JikesRVMSupport.getTypeForClass(C).load();
    } catch (VM_ResolutionException e) {
      throw new NoClassDefFoundError(e.getException().toString());
    }
  }

  /**
   * Load and resolve the VM_Type for Class C.
   */ 
  private static void loadAndResolveType(Class C) {
    if (java.lang.JikesRVMSupport.getTypeForClass(C).isResolved()) return;
    try {
      java.lang.JikesRVMSupport.getTypeForClass(C).load();
      java.lang.JikesRVMSupport.getTypeForClass(C).resolve();
    } catch (VM_ResolutionException e) {
      throw new NoClassDefFoundError(e.getException().toString());
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
    if (!java.lang.JikesRVMSupport.getTypeForClass(C).isClassType())
      return new Class[0];
    loadType(C);
    VM_Class[] interfaces  = java.lang.JikesRVMSupport.getTypeForClass(C).asClass().getDeclaredInterfaces();
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
    if (java.lang.JikesRVMSupport.getTypeForClass(C).isClassType())
    {
      loadType(C);
      return java.lang.JikesRVMSupport.getTypeForClass(C).asClass().isInterface();
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
    return java.lang.JikesRVMSupport.getTypeForClass(C).isPrimitiveType();
  }
  /**
   * Return true if the typecode <code>typecode<code> describes a primitive type
   *
   * @author		OTI
   * @version		initial
   *
   * @param		typecode		a char describing the typecode
   * @return		<code>true</code>
   *					if the typecode represents a primitive type
   *				<code>false</code>
   *					if the typecode represents an Object type (including arrays)
   */
  static boolean isPrimitiveType(char typecode) {
    return !(typecode == '[' || typecode == 'L');
  }

  /**
   * Answers true if the argument is non-null and can be
   * cast to the type of the receiver. This is the runtime
   * version of the <code>instanceof</code> operator.
   *
   * @author		OTI
   * @version		initial
   *
   * @return		<code>true</code>
   *					the argument can be cast to the type of the receiver
   *              <code>false</code>
   *					the argument is null or cannot be cast to the
   *					type of the receiver
   *
   * @param		object Object
   *					the object to test
   */
  public static boolean isInstance(Class C, Object object)
  {
    if (object == null)     return false;
    if (C.isPrimitive()) return false;
    return isAssignableFrom(C,object.getClass());
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
    return java.lang.JikesRVMSupport.getTypeForClass(C).isArrayType();
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
    else if (java.lang.JikesRVMSupport.getTypeForClass(C).isArrayType())
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
    if (java.lang.JikesRVMSupport.getTypeForClass(C).isArrayType()) {
      return VM_Type.JavaLangObjectType.getClassForType();
    }
    if (!java.lang.JikesRVMSupport.getTypeForClass(C).isClassType()) return null;

    VM_Type supe = java.lang.JikesRVMSupport.getTypeForClass(C).asClass().getSuperClass();
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
    */
    public static Object newInstance(Constructor c, Object args[])
    throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
      // TODO check access on ctor

      // Get a new instance, uninitialized.
      //
      VM_Method constructor = java.lang.reflect.JikesRVMSupport.getMethodOf(c);
      VM_Class cls = constructor.getDeclaringClass();
      if (!cls.isInitialized()) {
        try {
          VM_Runtime.initializeClassForDynamicLink(cls);
        } catch (VM_ResolutionException e) {

	    e.printStackTrace();

          throw new InstantiationException();
        }
      }

      int      size = cls.getInstanceSize();
      Object[] tib  = cls.getTypeInformationBlock();
      boolean  hasFinalizer = cls.hasFinalizer();
      int allocator = VM_Interface.pickAllocator(cls);
      Object   obj  = VM_Runtime.resolvedNewScalar(size, tib, hasFinalizer, allocator);

      // Run <init> on the instance.
      //
      Method m = java.lang.reflect.JikesRVMSupport.createMethod(constructor);

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
      componentVMType = java.lang.JikesRVMSupport.getTypeForClass(componentType);
      arrayType = componentVMType.getArrayTypeForElementType();
      for ( i = 0; i < length-1; i++ ) {
        arrayType = arrayType.getArrayTypeForElementType();
      }

      try {
	return VM_Runtime.buildMultiDimensionalArray(dimensions, 0, arrayType);
      } catch (VM_ResolutionException e) {
	throw new NoClassDefFoundError(e.getException().toString());
      }
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
      arrayType = java.lang.JikesRVMSupport.getTypeForClass(componentType).getArrayTypeForElementType();
      try {
	arrayType.load();
	arrayType.resolve();
      } catch (VM_ResolutionException e) {
	throw new NoClassDefFoundError(e.getException().toString());
      }
      arrayType.instantiate();
      arrayClassCache.put(componentType, arrayType);
    }

    Object[] tib = arrayType.getTypeInformationBlock();
    int allocator = VM_Interface.pickAllocator(arrayType);
    Object   obj = VM_Runtime.resolvedNewArray(size, arrayType.getInstanceSize(size), tib, allocator);
    return obj;
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
   */
  public static Class forName(String className, boolean initialize, ClassLoader classLoader) throws ClassNotFoundException {
    SecurityManager security = System.getSecurityManager();
    boolean DEBUG = false;
    if (security != null)
      throw new VM_UnimplementedError("Classloading with security manager");
      
    if ( (initialize == true) 
	 && ( (classLoader == null) 
	      || (classLoader instanceof VM_SystemClassLoader) ) ) {
	  
      Class guess = (Class) classCache.get( className );
      if (guess != null) {
	VM_Callbacks.notifyForName( java.lang.JikesRVMSupport.getTypeForClass(guess) );
	return guess;
      }
	  
      try {
	if (className.startsWith("[")) {
	  if (!validArrayDescriptor(className)) throw new IllegalArgumentException();
	  classCache.put(className, VM_Array.forName(className).getClassForType());
	  VM_Callbacks.notifyForName( java.lang.JikesRVMSupport.getTypeForClass( ((Class)classCache.get(className)) ) );
	  return (Class) classCache.get( className );
	} else {
	  classCache.put(className, VM_Class.forName(className).getClassForType());
	  VM_Callbacks.notifyForName( java.lang.JikesRVMSupport.getTypeForClass( ((Class)classCache.get(className)) ) );
	  return (Class) classCache.get( className );
	}
      }
      catch (VM_ResolutionException e) {
	throw new ClassNotFoundException(className);
      }
    } else {
      if (DEBUG) VM_Scheduler.trace("Class.forName 3 args, loading", className);
      Class klass = classLoader.loadClass(className);
      if (initialize) {
	  VM_Type kls = java.lang.JikesRVMSupport.getTypeForClass(klass);
	  try {
	      kls.resolve();
	      kls.instantiate();
	      kls.initialize();
	  } catch (VM_ResolutionException e) {
	      throw new ClassNotFoundException(className);
	  }
      }
      VM_Callbacks.notifyForName( java.lang.JikesRVMSupport.getTypeForClass(klass) );
      return klass;
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
   */
  public static Method[] getDeclaredMethods(Class C) throws SecurityException {
    checkMemberAccess(C,Member.DECLARED);
    if (!java.lang.JikesRVMSupport.getTypeForClass(C).isClassType()) return new Method[0];
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
   */
  public static Field[] getDeclaredFields(Class C) throws SecurityException {
    checkMemberAccess(C, Member.DECLARED);
    if (!java.lang.JikesRVMSupport.getTypeForClass(C).isClassType()) return new Field[0];

    return getFields0(C, Member.DECLARED);
  }


  /**
   * Get declared class members (i.e., named inner and static classes)
   * for given class.  To support <code>java.lang.Class.getDeclaredClasses()</code>.
   */
  public static Class[] getDeclaredClasses(Class C) throws SecurityException {
    // Security check
    checkMemberAccess(C, Member.DECLARED);

    // Make sure type is loaded
    loadType(C);

    // Is it a class type?
    if (!java.lang.JikesRVMSupport.getTypeForClass(C).isClassType())
      return new Class[0];

    // Get array of declared classes from VM_Class object
    VM_Class cls = java.lang.JikesRVMSupport.getTypeForClass(C).asClass();
    VM_Class[] declaredClasses = cls.getDeclaredClasses();

    // The array can be null if the class has no declared inner class members
    if (declaredClasses == null)
      return new Class[0];

    // Count the number of actual declared inner and static classes.
    // (The array may contain null elements, which we want to skip.)
    int count = 0;
    int length = declaredClasses.length;
    for (int i = 0; i < length; ++i) {
      if (declaredClasses[i] != null)
	++count;
    }

    // Now build actual result array.
    Class[] result = new Class[count];
    count = 0;
    for (int i = 0; i < length; ++i) {
      if (declaredClasses[i] != null)
	result[count++] = declaredClasses[i].getClassForType();
    }

    return result;
  }


  private static Method getMethod0(Class C, String name, Class[] parameterTypes, int which)
    throws NoSuchMethodException
    {
      loadAndResolveType(C);

      if (!java.lang.JikesRVMSupport.getTypeForClass(C).isClassType()) throw new NoSuchMethodException();

      VM_Method vm_virtual_methods[] = null;
      VM_Method vm_other_methods[] = null;

      if ( which == Member.PUBLIC ) {
        vm_virtual_methods = java.lang.JikesRVMSupport.getTypeForClass(C).getVirtualMethods();
        vm_other_methods = java.lang.JikesRVMSupport.getTypeForClass(C).getStaticMethods();
      } else {
        vm_virtual_methods = new VM_Method[0];
        vm_other_methods = java.lang.JikesRVMSupport.getTypeForClass(C).asClass().getDeclaredMethods(); 
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
          return java.lang.reflect.JikesRVMSupport.createMethod(meth);
      }

      // TODO: Object initializers should not be returned by getStaticMethods
      // - Eugene
      for (int j = 0; j < vm_other_methods.length; j++) {
        VM_Method meth = vm_other_methods[j];
        if (!meth.isClassInitializer() && !meth.isObjectInitializer() &&
            ( meth.getName() == aName ) &&
            parametersMatch(meth.getParameterTypes(), parameterTypes))
          return java.lang.reflect.JikesRVMSupport.createMethod(meth);
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
    VM_Type type = java.lang.reflect.JikesRVMSupport.getFieldOf(f).getType();

    if (type.isReferenceType())      java.lang.reflect.JikesRVMSupport.getFieldOf(f).setObjectValue(object, value);
    else if (type.isCharType())      java.lang.reflect.JikesRVMSupport.getFieldOf(f).setCharValue(object, VM_Reflection.unwrapChar(value));
    else if (type.isDoubleType())    java.lang.reflect.JikesRVMSupport.getFieldOf(f).setDoubleValue(object, VM_Reflection.unwrapDouble(value));
    else if (type.isFloatType())     java.lang.reflect.JikesRVMSupport.getFieldOf(f).setFloatValue(object, VM_Reflection.unwrapFloat(value));
    else if (type.isLongType())      java.lang.reflect.JikesRVMSupport.getFieldOf(f).setLongValue(object, VM_Reflection.unwrapLong(value));
    else if (type.isIntType())       java.lang.reflect.JikesRVMSupport.getFieldOf(f).setIntValue(object, VM_Reflection.unwrapInt(value));
    else if (type.isShortType())     java.lang.reflect.JikesRVMSupport.getFieldOf(f).setShortValue(object, VM_Reflection.unwrapShort(value));
    else if (type.isByteType())      java.lang.reflect.JikesRVMSupport.getFieldOf(f).setByteValue(object, VM_Reflection.unwrapByte(value));
    else if (type.isBooleanType())   java.lang.reflect.JikesRVMSupport.getFieldOf(f).setBooleanValue(object, VM_Reflection.unwrapBoolean(value));

  }
  /**
   * Return the result of dynamically invoking the modelled method.
   * This reproduces the effect of
   *		<code>receiver.methodName(arg1, arg2, ... , argN)</code>
   *
   * This method performs the following:
   * <ul>
   * <li>If the modelled method is static, the receiver argument is ignored.</li>
   * <li>Otherwise, if the receiver is null, a NullPointerException is thrown.
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
    */
    public static Object invoke(Method m, Object receiver, Object args[])
    throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
      VM_Method method = java.lang.reflect.JikesRVMSupport.getMethodOf(m);
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

	  e.printStackTrace( System.err );

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
    VM_Type[] exceptionTypes = java.lang.reflect.JikesRVMSupport.getMethodOf(m).getExceptionTypes();
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
    VM_Type[] exceptionTypes = java.lang.reflect.JikesRVMSupport.getMethodOf(c).getExceptionTypes();
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
    return java.lang.reflect.JikesRVMSupport.getMethodOf(m).getReturnType().getClassForType();
  }


  /**
   * Return the java.lang.Class associated with the class that defined
   * this constructor.
   *
   * @return		the declaring class
   */
  public static Class getDeclaringClass(Constructor c)
  {
    return java.lang.reflect.JikesRVMSupport.getMethodOf(c).getDeclaringClass().getClassForType();
  }
  /**
   * Return the java.lang.Class associated with the class that defined
   * this method.
   *
   * @return		the declaring class
   */
  public static Class getDeclaringClass(Method m)
  {
    return java.lang.reflect.JikesRVMSupport.getMethodOf(m).getDeclaringClass().getClassForType();
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
    return typesToClasses(java.lang.reflect.JikesRVMSupport.getMethodOf(c).getParameterTypes());
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
    return typesToClasses(java.lang.reflect.JikesRVMSupport.getMethodOf(m).getParameterTypes());
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
      vm_virtual_methods = java.lang.JikesRVMSupport.getTypeForClass(C).getVirtualMethods();
      vm_other_methods = java.lang.JikesRVMSupport.getTypeForClass(C).getStaticMethods();
    } else {
      vm_virtual_methods = new VM_Method[0];
      vm_other_methods = java.lang.JikesRVMSupport.getTypeForClass(C).asClass().getDeclaredMethods(); 
    }

    for (int j = 0; j < vm_other_methods.length; j++)
    {
      // Java Language Spec 8.2: class and initializers are not members thus not methods.
      // TODO: Object initializers should not be returned by getStaticMethods
      // - Eugene
      if (!vm_other_methods[j].isClassInitializer() &&
          !vm_other_methods[j].isObjectInitializer() &&
          ! ( which == Member.PUBLIC && !vm_other_methods[j].isPublic() )  )
        coll.collect(java.lang.reflect.JikesRVMSupport.createMethod(vm_other_methods[j]));
    }

    for (int j = 0; j < vm_virtual_methods.length; j++)
    {
      if (!vm_virtual_methods[j].isObjectInitializer() &&
          ! ( which == Member.PUBLIC && !vm_virtual_methods[j].isPublic() )
         )
        coll.collect(java.lang.reflect.JikesRVMSupport.createMethod(vm_virtual_methods[j]));
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
    VM_Method vm_static_methods[] = java.lang.JikesRVMSupport.getTypeForClass(C).getStaticMethods();
    Collector coll = new Collector();
    for (int i = 0; i < vm_static_methods.length; i++) 
    {
      if (vm_static_methods[i].isObjectInitializer() &&
          ! ( which == Member.PUBLIC && !vm_static_methods[i].isPublic() ) )
        coll.collect(java.lang.reflect.JikesRVMSupport.createConstructor(vm_static_methods[i]));
    }
    return coll.constructorArray();
  }

  private static Constructor getConstructor0(Class C, Class parameterTypes[], int which )
    throws NoSuchMethodException
    {
      loadAndResolveType(C);

      if (!java.lang.JikesRVMSupport.getTypeForClass(C).isClassType()) throw new NoSuchMethodException();

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
        methods = java.lang.JikesRVMSupport.getTypeForClass(C).getStaticMethods();
      } else {
        methods = java.lang.JikesRVMSupport.getTypeForClass(C).asClass().getDeclaredMethods();
      }

      for (int i = 0, n = methods.length; i < n; ++i)
      {
        VM_Method method = methods[i];
        if (method.isObjectInitializer() &&
            ! ( which == Member.PUBLIC && !method.isPublic() )
            && parametersMatch(method.getParameterTypes(), parameterTypes))
          return java.lang.reflect.JikesRVMSupport.createConstructor(method);
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
      vm_instance_fields = java.lang.JikesRVMSupport.getTypeForClass(C).getInstanceFields();
      vm_other_fields = java.lang.JikesRVMSupport.getTypeForClass(C).getStaticFields();
    } else {
      vm_instance_fields = new VM_Field[0];
      vm_other_fields = java.lang.JikesRVMSupport.getTypeForClass(C).asClass().getDeclaredFields(); 
    }

    for (int j = 0; j < vm_other_fields.length; j++)
    {
      if ( ! ( which == Member.PUBLIC && !vm_other_fields[j].isPublic() )  )
        coll.collect(java.lang.reflect.JikesRVMSupport.createField(vm_other_fields[j]));
    }

    for (int j = 0; j < vm_instance_fields.length; j++)
    {
      if ( ! ( which == Member.PUBLIC && !vm_instance_fields[j].isPublic() )
         )
        coll.collect(java.lang.reflect.JikesRVMSupport.createField(vm_instance_fields[j]));
    }

    return coll.fieldArray();
  }

  private static Field getField0(Class C, String name, int which)
    throws NoSuchFieldException
    {
      loadAndResolveType(C);

      if (!java.lang.JikesRVMSupport.getTypeForClass(C).isClassType()) throw new NoSuchFieldException();

      VM_Field vm_instance_fields[] = null;
      VM_Field vm_other_fields[] = null;
      if ( which == Member.PUBLIC ) {
        vm_instance_fields = java.lang.JikesRVMSupport.getTypeForClass(C).getInstanceFields();
        vm_other_fields = java.lang.JikesRVMSupport.getTypeForClass(C).getStaticFields();
      } else {
        vm_instance_fields = new VM_Field[0];
        vm_other_fields = java.lang.JikesRVMSupport.getTypeForClass(C).asClass().getDeclaredFields(); 
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
          return java.lang.reflect.JikesRVMSupport.createField( vm_other_fields[j] );
      }

      for (int j = 0; j < vm_instance_fields.length; j++)
      {
        if ( ! ( which == Member.PUBLIC && !vm_instance_fields[j].isPublic() ) &&
             ( vm_instance_fields[j].getName() == aName ) )
          return java.lang.reflect.JikesRVMSupport.createField(vm_instance_fields[j]);
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
   */
  public static Class getComponentType(Class C) {
    if (java.lang.JikesRVMSupport.getTypeForClass(C).isArrayType()) return java.lang.JikesRVMSupport.getTypeForClass(C).asArray().getElementType().getClassForType();
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
   */
  public static Constructor[] getDeclaredConstructors(Class C) throws SecurityException {
    checkMemberAccess(C, Member.DECLARED);
    if (!java.lang.JikesRVMSupport.getTypeForClass(C).isClassType()) return new Constructor[0];

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
    if (java.lang.JikesRVMSupport.getTypeForClass(C).isClassType()) {
      return java.lang.JikesRVMSupport.getTypeForClass(C).asClass().getModifiers();
    } else if (java.lang.JikesRVMSupport.getTypeForClass(C).isArrayType()) {
      int result = java.lang.JikesRVMSupport.getTypeForClass(C).asArray().getElementType().getClassForType().getModifiers();  
      result |= Modifier.FINAL;
      result |= ~Modifier.INTERFACE;
      return result;
    } else {  // primitive or reference type;
      int result = Modifier.PUBLIC | Modifier.FINAL;
      return result;
    }
  }

  /**
   * Return the modifiers for a method
   * The Modifier class should be used to decode the result.
   *
   * @return		the modifiers
   */
  public static int getModifiers(Method m)
  {
    return java.lang.reflect.JikesRVMSupport.getMethodOf(m).getModifiers();
  }
  /**
   * Return the modifiers for the modelled constructor.
   * The Modifier class should be used to decode the result.
   *
   * @return		the modifiers
   */
  public static int getModifiers(Constructor c)
  {
    return java.lang.reflect.JikesRVMSupport.getMethodOf(c).getModifiers();
  }
  /**
   */ 
  public static String getSignature(Constructor c) {
    return java.lang.reflect.JikesRVMSupport.getMethodOf(c).getDescriptor().toString();
  } 
  /**
   */ 
  public static String getSignature(Method m) {
    return java.lang.reflect.JikesRVMSupport.getMethodOf(m).getDescriptor().toString();
  } 

  /**
   * Compares the specified object to this Method and answer if they
   * are equal. The object must be an instance of Method with the same
   * defining class and parameter types.
   *
   * @param		object	the object to compare
   * @return		true if the specified object is equal to this Method, false otherwise
   */
  public static boolean methodEquals(Method m,Object object)
  {
    VM_Method method = java.lang.reflect.JikesRVMSupport.getMethodOf(m);
    if (object != null && object instanceof Method)
    {
      Method other = (Method) object;
      if (method != null)
        return method.equals(java.lang.reflect.JikesRVMSupport.getMethodOf(other));
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
   */
  public static String getName(Class C) {
    return java.lang.JikesRVMSupport.getTypeForClass(C).getName();
  }

  /**
   * Return the name of the modelled method.
   *
   * @return		the name
   */
  public static String getName(Method m)
  {
    return java.lang.reflect.JikesRVMSupport.getMethodOf(m).getName().toString();
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
	e.printStackTrace();
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
    VM_Class iKlass = java.lang.JikesRVMSupport.getTypeForClass(instantiationClass).asClass();
    if (!iKlass.isInstantiated()) {
      iKlass.instantiate();
      iKlass.initialize();
    }

    // allocate the object
    int size     = iKlass.getInstanceSize();
    Object[] tib = iKlass.getTypeInformationBlock();
    boolean  hasFinalizer = iKlass.hasFinalizer();
    int allocator = VM_Interface.pickAllocator(iKlass);
    Object obj   = VM_Runtime.resolvedNewScalar(size,tib,hasFinalizer,allocator);

    // run the constructor 
    Constructor cons = constructorClass.getDeclaredConstructor(new Class[0]);
    VM_Method cm = java.lang.reflect.JikesRVMSupport.getMethodOf(cons);
    Method m = java.lang.reflect.JikesRVMSupport.createMethod(cm);
    m.invoke(obj,null);
    return obj;
  }

  /**
   * Attempts to run private instance method <code>readObject</code> defined in class <code>theClass</code> having
   * object <code>obj</code> as receiver. If no such method exists, return false. If
   * the method is run, returns true.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Method). Otherwise Serialization could not run private methods, except
   * by the use of a native method like this one.
   *
   * @param	obj			receiver for the readObject() message
   * @param	theClass	Class where method readObject() must have been defined
   *
   * @return		<code>true</code>
   *					if the method was found and run
   *				<code>false</code>
   *					if the method was not found and therefore not run
   *
   */
  public static boolean executeReadObject(ObjectInputStream ois, Object obj, Class theClass) {
    Method method;

    // need type of arg to writeObject, which is ObjectInputStream
    Class[] argTypes = new Class[1];
    //argTypes[0] = this.getClass();
    try {
      argTypes[0] = Class.forName("java.io.ObjectInputStream");
    }
    catch (ClassNotFoundException e) {
      return false;
    }

    // get Method, if it exists
    try {
      method = theClass.getDeclaredMethod("readObject", argTypes);
    }
    catch ( NoSuchMethodException e ) {
      return false;  // tells caller method not defined for the class
    }
    catch ( SecurityException e ) {
      System.out.println("SecurityException in ObjectInputStream.executeReadObject");
      return false;
    }

    Object[] methodArgs = new Object[1];
    methodArgs[0] = ois;   // arg to writeObject is this stream

    try {
      method.setAccessible( true );     // allow access to private method
      method.invoke( obj, methodArgs );
    }
    catch ( Exception e ) {
      System.out.println("unexpected exception in .executeReadObject = " + e );
      return false;
    }

    return true;    // tells caller method exists & was invoked
  }

  /**
   * Compare parameter lists for agreement.
   */ 
  private static boolean parametersMatch(VM_Type[] lhs, Class[] rhs) {
    if (lhs.length != rhs.length)
      return false;

    for (int i = 0, n = lhs.length; i < n; ++i)
	if (lhs[i] != java.lang.JikesRVMSupport.getTypeForClass(rhs[i]))
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
   * Initialize a stream for reading primitive data stored in <code>byte[] data</code>
   *
   * @param		data		The primitive data as raw bytes.
   * @exception	IOException		If an IO exception happened when creating internal streams to hold primitive data
   */
  public static void initPrimitiveTypes(ObjectInputStream ois, byte[] data) throws IOException {
//-#if RVM_WITH_GNU_CLASSPATH
//-#else
      ois.primitiveTypes = new DataInputStream(new ByteArrayInputStream(data));
//-#endif
  }
  /**
   * Reads a collection of field descriptors (name, type name, etc) for the class descriptor
   * <code>cDesc</code> (an <code>ObjectStreamClass</code>)
   *
   * @param		cDesc	The class descriptor (an <code>ObjectStreamClass</code>) for which to write field information
   *
   * @exception	IOException	If an IO exception happened when reading the field descriptors.
   * @exception	ClassNotFoundException	If a class for one of the field types could not be found
   */
  public static void readFieldDescriptors(ObjectInputStream ois, ObjectStreamClass cDesc) throws ClassNotFoundException, IOException {
//-#if RVM_WITH_GNU_CLASSPATH
//-#else
    ObjectStreamField f;
    short numFields = ois.input.readShort();
    ObjectStreamField[] fields = new ObjectStreamField [numFields];

    // We set it now, but each element will be inserted in the array further down
    cDesc.setLoadFields (fields);

    // Check ObjectOutputStream.writeFieldDescriptors
    for (short i = 0 ; i < numFields; i++) {
      char typecode = (char) ois.input.readByte();
      String fieldName = ois.input.readUTF();
      boolean isPrimType = isPrimitiveType(typecode);
      String classSig;
      if (isPrimType) {
        char [] oneChar = new char[1];
        oneChar[0]=typecode;
        classSig = new String (oneChar);

        Class typeClass;
        switch (typecode) {
          case 'Z' :
            typeClass = boolean.class;
            break;
          case 'B' :
            typeClass = byte.class;
            break;
          case 'C' :
            typeClass = char.class;
            break;
          case 'S' :
            typeClass = short.class;
            break;
          case 'I' :
            typeClass = int.class;
            break;
          case 'J' :
            typeClass = long.class;
            break;
          case 'F' :
            typeClass = float.class;
            break;
          case 'D' :
            typeClass = double.class;
            break;
          default :
            throw new IOException("Invalid Typecode " + typecode);
        }

        f = new ObjectStreamField(fieldName, typeClass);

      } else {
        // The spec says it is a UTF, but experience shows they dump this String
        // using writeObject (unlike the field name, which is saved with writeUTF)
        classSig = (String) ois.readObject ();

        // strip off leading L and trailing ; to get class name
        if (classSig.charAt(0) == 'L')
          classSig = classSig.substring(1,classSig.length()-1);

        f = new ObjectStreamField(fieldName, Class.forName(classSig));
      }

      fields [i] = f;
      }
//-#endif
  }
  /**
   * Set a given declared field named <code>fieldName</code> of <code>instance</code> to
   * the new <code>byte</code> value <code>value</code>.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not set private fields, except
   * by the use of a native method like this one.
   *
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance		Object whose field to set
   * @param		fieldName		Name of the field to set
   * @param		value			New value for the field
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static void setField(Object instance, Class declaringClass, String fieldName, byte value) throws NoSuchFieldError {

    try {
      // following may throw NoSuchFieldError;
      Field f = declaringClass.getDeclaredField( fieldName );

      f.setAccessible( true );     // to allow access to private fields
      f.setByte(instance,value);
    }
    catch ( Exception e ) {
      System.out.println( "exception in ObjectInputStream.setField(..,byte): " + e );
    }
  }

  /**
   * Set a given declared field named <code>fieldName</code> of <code>instance</code> to
   * the new <code>char</code> value <code>value</code>.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not set private fields, except
   * by the use of a native method like this one.
   *
   * @param		instance		Object whose field to set
   * @param		fieldName		Name of the field to set
   * @param		value			New value for the field
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static void setField(Object instance, Class declaringClass, String fieldName, char value) throws NoSuchFieldError {

    try {
      // following may throw NoSuchFieldError;
      Field f = declaringClass.getDeclaredField( fieldName );

      f.setAccessible( true );     // to allow access to private fields
      f.setChar(instance,value);
    }
    catch ( Exception e ) {
      System.out.println( "exception in ObjectInputStream.setField(..,char): " + e );
    }
  }
  /**
   * Set a given declared field named <code>fieldName</code> of <code>instance</code> to
   * the new <code>double</code> value <code>value</code>.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not set private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance		Object whose field to set
   * @param		fieldName		Name of the field to set
   * @param		value			New value for the field
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static void setField(Object instance, Class declaringClass, String fieldName, double value) throws NoSuchFieldError {

    try {
      // following may throw NoSuchFieldError;
      Field f = declaringClass.getDeclaredField( fieldName );

      f.setAccessible( true );     // to allow access to private fields
      f.setDouble(instance,value);
    }
    catch ( Exception e ) {
      System.out.println( "exception in ObjectInputStream.setField(..,double): " + e );
    }
  }

  /**
   * Set a given declared field named <code>fieldName</code> of <code>instance</code> to
   * the new <code>float</code> value <code>value</code>.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not set private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance		Object whose field to set
   * @param		fieldName		Name of the field to set
   * @param		value			New value for the field
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static void setField (Object instance, Class declaringClass, String fieldName, float value) throws NoSuchFieldError {

    try {
      // following may throw NoSuchFieldError;
      Field f = declaringClass.getDeclaredField( fieldName );

      f.setAccessible( true );     // to allow access to private fields
      f.setFloat(instance,value);
    }
    catch ( Exception e ) {
      System.out.println( "exception in ObjectInputStream.setField(..,float): " + e );
    }
  }

  /**
   * Set a given declared field named <code>fieldName</code> of <code>instance</code> to
   * the new <code>int</code> value <code>value</code>.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not set private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance		Object whose field to set
   * @param		fieldName		Name of the field to set
   * @param		value			New value for the field
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static void setField (Object instance, Class declaringClass, String fieldName, int value) throws NoSuchFieldError {

    try {
      // following may throw NoSuchFieldError;
      Field f = declaringClass.getDeclaredField( fieldName );

      f.setAccessible( true );     // to allow access to private fields
      f.setInt(instance,value);
    }
    catch ( Exception e ) {
      System.out.println( "exception in ObjectInputStream.setField(..,int): " + e );
    }
  }

  /**
   * Set a given declared field named <code>fieldName</code> of <code>instance</code> to
   * the new <code>long</code> value <code>value</code>.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not set private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance		Object whose field to set
   * @param		fieldName		Name of the field to set
   * @param		value			New value for the field
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static void setField (Object instance, Class declaringClass, String fieldName, long value) throws NoSuchFieldError {

    try {
      // following may throw NoSuchFieldError;
      Field f = declaringClass.getDeclaredField( fieldName );

      f.setAccessible( true );     // to allow access to private fields
      f.setLong(instance,value);
    }
    catch ( Exception e ) {
      System.out.println( "exception in ObjectInputStream.setField(..,long): " + e );
    }
  }

  /**
   * Set a given declared field named <code>fieldName</code> of <code>instance</code> to
   * the new value <code>value</code>.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not set private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance		Object whose field to set
   * @param		declaringClass	Class which delares the field
   * @param		fieldName		Name of the field to set
   * @param		fieldTypeName	Name of the class defining the type of the field
   * @param		value			New value for the field
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static void setField (Object instance, Class declaringClass, String fieldName, String fieldTypeName, Object value) throws NoSuchFieldError {

    try {
      // following may throw NoSuchFieldError;
      Field f = declaringClass.getDeclaredField( fieldName );

      f.setAccessible( true );     // to allow access to private fields
      f.set(instance,value);
    }
    catch ( Exception e ) {
      System.out.println( "exception in ObjectInputStream.setField(..,Object): " + e );
    }
  }

  /**
   * Set a given declared field named <code>fieldName</code> of <code>instance</code> to
   * the new <code>short</code> value <code>value</code>.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not set private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance		Object whose field to set
   * @param		fieldName		Name of the field to set
   * @param		value			New value for the field
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static void setField (Object instance, Class declaringClass, String fieldName, short value) throws NoSuchFieldError {

    try {
      // following may throw NoSuchFieldError;
      Field f = declaringClass.getDeclaredField( fieldName );

      f.setAccessible( true );     // to allow access to private fields
      f.setShort(instance,value);
    }
    catch ( Exception e ) {
      System.out.println( "exception in ObjectInputStream.setField(..,short): " + e );
    }
  }

  /**
   * Set a given declared field named <code>fieldName</code> of <code>instance</code> to
   * the new <code>boolean</code> value <code>value</code>.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not set private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance		Object whose field to set
   * @param		fieldName		Name of the field to set
   * @param		value			New value for the field
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static void setField (Object instance, Class declaringClass, String fieldName, boolean value) throws NoSuchFieldError {

    try {
      // following may throw NoSuchFieldError;
      Field f = declaringClass.getDeclaredField( fieldName );

      f.setAccessible( true );     // to allow access to private fields
      f.setBoolean(instance,value);
    }
    catch ( Exception e ) {
      System.out.println( "exception in ObjectInputStream.setField(..,boolean): " + e );
    }
  }

  /**
   * Executes the private, instance method <code>writeObject</code> defined in class
   * <code>theClass</code> having <code>obj</code> as receiver.
   * a boolean.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Method). Otherwise Serialization could not run private methods, except
   * by the use of a native method like this one.
   *
   * @param		obj					Receiver object for method <code>writeObject</code>
   * @param		theClass			The class that declares the method <code>writeObject</code>
   *
   * @return		boolean, indicating whether the method was found and executed or not.
   */
  public static boolean executeWriteObject (ObjectOutputStream oos, Object obj, Class theClass) {
    Method method;

    // need type of arg to writeObject, which is ObjectOutputStream
    Class[] argTypes = new Class[1];
    //argTypes[0] = this.getClass();
    try {
      argTypes[0] = Class.forName("java.io.ObjectOutputStream");
    }
    catch ( ClassNotFoundException e ) {
      return false;
    }

    // get Method, if it exists
    try {
      method = theClass.getDeclaredMethod("writeObject", argTypes);
    }
    catch ( NoSuchMethodException e ) {
      return false;  // tells caller method not defined for the class
    }
    catch ( SecurityException e ) {
      System.out.println("SecurityException in ObjectOutputStream.executeWriteObject");
      return false;
    }

    Object[] methodArgs = new Object[1];
    methodArgs[0] = oos;   // arg to writeObject is this stream

    try {
      method.setAccessible( true );     // allow access to private method
      method.invoke( obj, methodArgs );
    }
    catch ( Exception e ) {
      System.out.println("unexpected exception in ObjectOutputStream.executeWriteObject = " + e );
      return false;
    }

    return true;    // tells caller method exists & was invoked
  }

  /**
   * Get the value of field named <code>fieldName<code> of object <code>instance</code>. The
   * field is declared by class <code>declaringClass</code>. The field is supposed to be
   * a boolean.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not fetch private fields, except
   * by the use of a native method like this one.
   *
   * @param		instance			Object whose field value we want to fetch
   * @param		declaringClass		The class that declares the field
   * @param		fieldName			Name of the field we want to fetch
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static boolean getFieldBool (Object instance, Class declaringClass, String fieldName)
    throws NoSuchFieldError {

      boolean b = true;

      try {
        // following may throw NoSuchFieldError;
        Field f = declaringClass.getDeclaredField( fieldName );

        f.setAccessible( true );     // to allow access to private fields
        b = f.getBoolean(instance);
      }
      catch ( Exception e ) {
        System.out.println( "exception in ObjectOutputStream.getFieldBool(): " + e );
      }

      return b;
    }
  /**
   * Get the value of field named <code>fieldName<code> of object <code>instance</code>. The
   * field is declared by class <code>declaringClass</code>. The field is supposed to be
   * a byte
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not fetch private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance			Object whose field value we want to fetch
   * @param		declaringClass		The class that declares the field
   * @param		fieldName			Name of the field we want to fetch
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static byte getFieldByte (Object instance, Class declaringClass, String fieldName)
    throws NoSuchFieldError {

      byte b = 0;

      try {
        // following may throw NoSuchFieldError;
        Field f = declaringClass.getDeclaredField( fieldName );

        f.setAccessible( true );     // to allow access to private fields
        b = f.getByte(instance);
      }
      catch ( Exception e ) {
        System.out.println( "exception in ObjectOutputStream.getFieldByte(): " + e );
      }

      return b;
    }

  /**
   * Get the value of field named <code>fieldName<code> of object <code>instance</code>. The
   * field is declared by class <code>declaringClass</code>. The field is supposed to be
   * a char.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not fetch private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance			Object whose field value we want to fetch
   * @param		declaringClass		The class that declares the field
   * @param		fieldName			Name of the field we want to fetch
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static char getFieldChar (Object instance, Class declaringClass, String fieldName)
    throws NoSuchFieldError {

      char c = '0';

      try {
        // following may throw NoSuchFieldError;
        Field f = declaringClass.getDeclaredField( fieldName );

        f.setAccessible( true );     // to allow access to private fields
        c = f.getChar(instance);
      }
      catch ( Exception e ) {
        System.out.println( "exception in ObjectOutputStream.getFieldChar(): " + e );
      }

      return c;
    }

  /**
   * Get the value of field named <code>fieldName<code> of object <code>instance</code>. The
   * field is declared by class <code>declaringClass</code>. The field is supposed to be
   * a double.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not fetch private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance			Object whose field value we want to fetch
   * @param		declaringClass		The class that declares the field
   * @param		fieldName			Name of the field we want to fetch
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static double getFieldDouble (Object instance, Class declaringClass, String fieldName)
    throws NoSuchFieldError {

      double d = 0.0;

      try {
        // following may throw NoSuchFieldError;
        Field f = declaringClass.getDeclaredField( fieldName );

        f.setAccessible( true );     // to allow access to private fields
        d = f.getDouble(instance);
      }
      catch ( Exception e ) {
        System.out.println( "exception in ObjectOutputStream.getFieldDouble(): " + e );
      }

      return d;
    }

  /**
   * Get the value of field named <code>fieldName<code> of object <code>instance</code>. The
   * field is declared by class <code>declaringClass</code>. The field is supposed to be
   * a float.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not fetch private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance			Object whose field value we want to fetch
   * @param		declaringClass		The class that declares the field
   * @param		fieldName			Name of the field we want to fetch
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static float getFieldFloat (Object instance, Class declaringClass, String fieldName)
    throws NoSuchFieldError {

      float ff = (float)0.0;

      try {
        // following may throw NoSuchFieldError;
        Field f = declaringClass.getDeclaredField( fieldName );

        f.setAccessible( true );     // to allow access to private fields
        ff = f.getFloat(instance);
      }
      catch ( Exception e ) {
        System.out.println( "exception in ObjectOutputStream.getFieldFloat(): " + e );
      }

      return ff;
    }

  /**
   * Get the value of field named <code>fieldName<code> of object <code>instance</code>. The
   * field is declared by class <code>declaringClass</code>. The field is supposed to be
   * an int.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not fetch private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance			Object whose field value we want to fetch
   * @param		declaringClass		The class that declares the field
   * @param		fieldName			Name of the field we want to fetch
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static int getFieldInt (Object instance, Class declaringClass, String fieldName)
    throws NoSuchFieldError {

      int i = 0;

      try {
        // following may throw NoSuchFieldError;
        Field f = declaringClass.getDeclaredField( fieldName );

        f.setAccessible( true );     // to allow access to private fields
        i = f.getInt(instance);
      }
      catch ( Exception e ) {
        System.out.println( "exception in ObjectOutputStream.getFieldInt(): " + e );
      }

      return i;
    }

  /**
   * Get the value of field named <code>fieldName<code> of object <code>instance</code>. The
   * field is declared by class <code>declaringClass</code>. The field is supposed to be
   * a long.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not fetch private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance			Object whose field value we want to fetch
   * @param		declaringClass		The class that declares the field
   * @param		fieldName			Name of the field we want to fetch
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static long getFieldLong (Object instance, Class declaringClass, String fieldName)
    throws NoSuchFieldError {

      long l = 0;

      try {
        // following may throw NoSuchFieldError;
        Field f = declaringClass.getDeclaredField( fieldName );

        f.setAccessible( true );     // to allow access to private fields
        l = f.getLong(instance);
      }
      catch ( Exception e ) {
        System.out.println( "exception in ObjectOutputStream.getFieldLong(): " + e );
      }

      return l;
    }

  /**
   * Get the value of field named <code>fieldName<code> of object <code>instance</code>. The
   * field is declared by class <code>declaringClass</code>. The field is supposed to be
   * an Object type whose name is <code>fieldTypeName</code>.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not fetch private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance			Object whose field value we want to fetch
   * @param		declaringClass		The class that declares the field
   * @param		fieldName			Name of the field we want to fetch
   * @param		fieldTypeName		Name of the class that defines the type of this field
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static Object getFieldObj (Object instance, Class declaringClass, String fieldName, String fieldTypeName)
    throws NoSuchFieldError {

      Object obj = null;

      try {
        // following may throw NoSuchFieldError;
        Field f = declaringClass.getDeclaredField( fieldName );

        f.setAccessible( true );     // to allow access to private fields
        obj = f.get(instance);
      }
      catch ( Exception e ) {
        System.out.println( "exception in ObjectOutputStream.getFieldObj(): " + e );
      }

      return obj;
    }

  /**
   * Get the value of field named <code>fieldName<code> of object <code>instance</code>. The
   * field is declared by class <code>declaringClass</code>. The field is supposed to be
   * a short.
   *
   * This method could be implemented non-natively on top of java.lang.reflect implementations
   * that support the <code>setAccessible</code> API, at the expense of extra object creation
   * (java.lang.reflect.Field). Otherwise Serialization could not fetch private fields, except
   * by the use of a native method like this one.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		instance			Object whose field value we want to fetch
   * @param		declaringClass		The class that declares the field
   * @param		fieldName			Name of the field we want to fetch
   *
   * @exception	NoSuchFieldError	If the field does not exist.
   */
  public static short getFieldShort (Object instance, Class declaringClass, String fieldName)
    throws NoSuchFieldError {

      short s = 0;

      try {
        // following may throw NoSuchFieldError;
        Field f = declaringClass.getDeclaredField( fieldName );

        // call setAccessible here, to get access to private field values
        f.setAccessible( true );     // to allow access to private fields
        s = f.getShort(instance);
      }
      catch ( Exception e ) {
        System.out.println( "exception in ObjectOutputStream.getFieldShort(): " + e );
      }

      return s;
    }

  /**
   * Return a String representing the signature for a Constructor <code>c</code>.
   *
   * @author		OTI
   * @version		initial
   *
   * @param		c		a java.lang.reflect.Constructor for which to compute the signature
   * @return		String 	The constructor's signature
   *
   */
  public static String getConstructorSignature (Constructor c) {
    String signature = null;

    try {
      c.setAccessible( true );     // to allow access to private fields
      signature = c.getSignature();
    }
    catch ( Exception e ) {
      System.out.println( "exception in ObjectStreamClass.getConstructorSignature(): " + e );
    }

    return signature;
  }

  /**
   * Return a String representing the signature for a field <code>f</code>.
   *
   * @param		f		a java.lang.reflect.Field for which to compute the signature
   * @return		String 	The field's signature
   *
   */
  public static String getFieldSignature (Field f) {
    String signature = null;

    try {
      f.setAccessible( true );     // to allow access to private fields
      signature = f.getSignature();
    }
    catch ( Exception e ) {
      System.out.println( "exception in ObjectStreamClass.getFieldSignature(): " + e );
    }

    return signature;
  }

  /**
   * Return a String representing the signature for a method <code>m</code>.
   *
   * @param		m		a java.lang.reflect.Method for which to compute the signature
   * @return		String 	The method's signature
   *
   */
  public static String getMethodSignature (Method m) {
    String signature = null;

    try {
      m.setAccessible( true );     // to allow access to private fields
      signature = m.getSignature();
    }
    catch ( Exception e ) {
      System.out.println( "exception in ObjectStreamClass.getMethodSignature(): " + e );
    }

    return signature;
  }
  /**
   * Return true if the given class <code>cl</code> has the compiler-generated method <code>clinit</code>.
   * Even though it is compiler-generated, it is used by the serialization code
   * to compute SUID. This is unfortunate, since it may depend on compiler optimizations
   * in some cases.
   *
   * @param		cl		a java.lang.Class which to test
   * @return		<code>true</code>
   *					if the class has <clinit>
   *				<code>false</code>
   *					if the class does not have <clinit>
   */
  public static boolean hasClinit(Class cl) {
    try {
      // following will either return Method or throw exception
      Method method = cl.getDeclaredMethod("<clinit>", null);
    } catch (NoSuchMethodException nsm) {
      return false;
    }
    return true;  // no exception thrown, clint was found
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
