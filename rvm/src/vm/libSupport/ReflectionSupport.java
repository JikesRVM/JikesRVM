/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.librarySupport;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.classloader.*;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Callbacks;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Runtime;
import com.ibm.JikesRVM.VM_Scheduler;
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
    VM_Type A_vm = java.lang.JikesRVMSupport.getTypeForClass(A);
    VM_Type B_vm = java.lang.JikesRVMSupport.getTypeForClass(B);
    return A_vm == B_vm || VM_Runtime.isAssignableWith(A_vm, B_vm);
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
    VM_Type myType = java.lang.JikesRVMSupport.getTypeForClass(C);
    if (myType.isArrayType()) {
      // arrays implement JavaLangSerializable & JavaLangCloneable
      return new Class[] { VM_Type.JavaLangCloneableType.getClassForType(),
			   VM_Type.JavaIoSerializableType.getClassForType() };
    } else if (myType.isClassType()) {
      VM_Class[] interfaces  = myType.asClass().getDeclaredInterfaces();
      Class[]    jinterfaces = new Class[interfaces.length];
      for (int i = 0; i != interfaces.length; i++)
	jinterfaces[i] = interfaces[i].getClassForType();
      return jinterfaces;
    } else {
      return new Class[0];
    }
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
    if (java.lang.JikesRVMSupport.getTypeForClass(C).isClassType()) {
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
  public static boolean isPrimitive(Class C) {
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
  public static boolean isInstance(Class C, Object object) {
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
    if (C.isPrimitive()) {
      return getName(C);
    } else if (java.lang.JikesRVMSupport.getTypeForClass(C).isArrayType()) {
      return "class " + getName(C);
    } else {
      if (C.isInterface()) {
	return "interface " + getName(C);
      } else  {
	return "class "     + getName(C);
      }
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
  public static Class getSuperclass(Class C)  {
    VM_Type myType = java.lang.JikesRVMSupport.getTypeForClass(C);
    if (myType.isArrayType()) {
      return VM_Type.JavaLangObjectType.getClassForType();
    } else if (myType.isClassType()) {
      VM_Class myClass = myType.asClass();
      if (myClass.isInterface()) return null;
      VM_Type supe = myClass.getSuperClass();
      return supe == null ? null : supe.getClassForType();
    } else {
      return null;
    }
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
      } catch (Throwable e) {
	InstantiationException ex = new InstantiationException();
	ex.initCause( e );
	throw ex;
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

    return VM_Runtime.buildMultiDimensionalArray(dimensions, 0, arrayType);
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

    VM_Array arrayType = java.lang.JikesRVMSupport.getTypeForClass(componentType).getArrayTypeForElementType();
    if (!arrayType.isInitialized()) {
      arrayType.resolve();
      arrayType.instantiate();
      arrayType.initialize();
    }

    Object[] tib = arrayType.getTypeInformationBlock();
    int allocator = VM_Interface.pickAllocator(arrayType);
    return VM_Runtime.resolvedNewArray(size, arrayType.getInstanceSize(size), tib, allocator);
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

    if (VM.VerifyAssertions) VM._assert(classLoader != null);

    if (className.startsWith("[")) {
      if (!validArrayDescriptor(className)) throw new ClassNotFoundException();
    }
    VM_Atom descriptor = VM_Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
    VM_TypeReference tRef = VM_TypeReference.findOrCreate(classLoader, descriptor);
    VM_Type ans = tRef.resolve();
    VM_Callbacks.notifyForName(ans);
    if (initialize && !ans.isInitialized()) {
      ans.resolve();
      ans.instantiate();
      ans.initialize();
    }
    return ans.getClassForType();
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
  public static Method getMethod(Class C, String name, Class parameterTypes[]) 
    throws NoSuchMethodException, SecurityException {
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
   * Get declaring class (ie. the outer class that contains this class if it is an inner/nested class).
   * To support <code>java.lang.Class.getDeclaringClass()</code>.
   */
  public static Class getDeclaringClass(Class C) {
    VM_Type type = java.lang.JikesRVMSupport.getTypeForClass(C);
    if (!type.isClassType()) return null;
    VM_TypeReference dc = type.asClass().getDeclaringClass();
    if (dc == null) return null;
    try {
      return dc.resolve().getClassForType();
    } catch (ClassNotFoundException e) {
      if (VM.VerifyAssertions) VM._assert(false); // Should never happen?
    }
    return null;
  }
  
  /**
   * Get declared class members (i.e., named inner and static classes)
   * for given class.  To support <code>java.lang.Class.getDeclaredClasses()</code>.
   */
  public static Class[] getDeclaredClasses(Class C) throws SecurityException {
    // Security check
    checkMemberAccess(C, Member.DECLARED);

    // Is it a class type?
    if (!java.lang.JikesRVMSupport.getTypeForClass(C).isClassType())
      return new Class[0];

    // Get array of declared classes from VM_Class object
    VM_Class cls = java.lang.JikesRVMSupport.getTypeForClass(C).asClass();
    VM_TypeReference[] declaredClasses = cls.getDeclaredClasses();

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
      if (declaredClasses[i] != null) {
	try {
	  result[count++] = declaredClasses[i].resolve().getClassForType();
	} catch (ClassNotFoundException e) {
	  if (VM.VerifyAssertions) VM._assert(false); // Should never happen?
	}
      }
    }

    return result;
  }

  private static Method getMethod0(Class C, String name, Class[] parameterTypes, int which)
    throws NoSuchMethodException {
    VM_Type type = java.lang.JikesRVMSupport.getTypeForClass(C);

    if (!type.isClassType()) throw new NoSuchMethodException();

    VM_Method vm_virtual_methods[];
    VM_Method vm_other_methods[];
    if (which == Member.PUBLIC) {
      vm_virtual_methods = type.getVirtualMethods();
      vm_other_methods = type.getStaticMethods();
    } else {
      vm_virtual_methods = new VM_Method[0];
      vm_other_methods = type.asClass().getDeclaredMethods(); 
    }

    // TODO: Problem here with User flooding the atom dictionary if
    // getMethod used too many times with different not existing names
    // Solution: create a VM_Atom.findUnicodeAtom method.. to be done later.
    // - Eugene
    VM_Atom aName = VM_Atom.findOrCreateUnicodeAtom(name);

    for (int j = 0; j < vm_virtual_methods.length; j++) {
      VM_Method meth = vm_virtual_methods[j];
      if (!meth.isObjectInitializer() && 
	  meth.getName() == aName &&
          !(which == Member.PUBLIC && !meth.isPublic()) && 
	  parametersMatch(meth.getParameterTypes(), parameterTypes)) {
	return java.lang.reflect.JikesRVMSupport.createMethod(meth);
      }
    }

    // TODO: Object initializers should not be returned by getStaticMethods
    // - Eugene
    for (int j = 0; j < vm_other_methods.length; j++) {
      VM_Method meth = vm_other_methods[j];
      if (!meth.isClassInitializer() && !meth.isObjectInitializer() &&
	  meth.getName() == aName &&
          !(which == Member.PUBLIC && !meth.isPublic()) && 
	  parametersMatch(meth.getParameterTypes(), parameterTypes)) {
	return java.lang.reflect.JikesRVMSupport.createMethod(meth);
      }
    }
      
    throw new NoSuchMethodException(name + parameterTypes);
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
  public static void setField(Field f, Object object, Object value) throws IllegalAccessException, IllegalArgumentException {
    VM_Field vm_field = java.lang.reflect.JikesRVMSupport.getFieldOf(f);
    VM_TypeReference type = vm_field.getType();
    if (type.isReferenceType())      vm_field.setObjectValue(object, value);
    else if (type.isCharType())      vm_field.setCharValue(object, VM_Reflection.unwrapChar(value));
    else if (type.isDoubleType())    vm_field.setDoubleValue(object, VM_Reflection.unwrapDouble(value));
    else if (type.isFloatType())     vm_field.setFloatValue(object, VM_Reflection.unwrapFloat(value));
    else if (type.isLongType())      vm_field.setLongValue(object, VM_Reflection.unwrapLong(value));
    else if (type.isIntType())       vm_field.setIntValue(object, VM_Reflection.unwrapInt(value));
    else if (type.isShortType())     vm_field.setShortValue(object, VM_Reflection.unwrapShort(value));
    else if (type.isByteType())      vm_field.setByteValue(object, VM_Reflection.unwrapByte(value));
    else if (type.isBooleanType())   vm_field.setBooleanValue(object, VM_Reflection.unwrapBoolean(value));
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
    throws IllegalAccessException, 
	   IllegalArgumentException, 
	   InvocationTargetException {
    VM_Method method = java.lang.reflect.JikesRVMSupport.getMethodOf(m);
    VM_TypeReference[] parameterTypes = method.getParameterTypes();

    // validate "this" argument
    //
    if (!method.isStatic()) {
      if (receiver == null) throw new NullPointerException();
      receiver = makeArgumentCompatible(method.getDeclaringClass(), receiver);
    }
    
    // validate number and types of remaining arguments
    //
    if (args == null) {
      if (parameterTypes.length != 0) {
	throw new IllegalArgumentException("argument count mismatch");
      }
    } else if (args.length != parameterTypes.length) {
      throw new IllegalArgumentException("argument count mismatch");
    }
    for (int i = 0, n = parameterTypes.length; i < n; ++i) {
      try {
	args[i] = makeArgumentCompatible(parameterTypes[i].resolve(), args[i]);
      } catch (ClassNotFoundException e) {
	if (VM.VerifyAssertions) VM._assert(false); // Should never happen?
      }
    }

    // invoke method
    // Note that we catch all possible exceptions, not just Error's and RuntimeException's,
    // for compatibility with jdk behavior (which even catches a "throw new Throwable()").
    //
    try {
      return VM_Reflection.invoke(method, receiver, args);
    } catch (Throwable e) {
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
  public static Class[] getExceptionTypes(Method m) {
    VM_TypeReference[] exceptionTypes = java.lang.reflect.JikesRVMSupport.getMethodOf(m).getExceptionTypes();
    if (exceptionTypes == null) {
      return new Class[0];
    } else {
      return typesToClasses(exceptionTypes);
    }
  }

  /**
   * Return an array of the java.lang.Class objects associated with the
   * exceptions declared to be thrown by this constructor. If the method was
   * not declared to throw any exceptions, the array returned will be
   * empty.
   *
   * @return		the declared exception classes
   */
  public static Class[] getExceptionTypes(Constructor c) {
    VM_TypeReference[] exceptionTypes = java.lang.reflect.JikesRVMSupport.getMethodOf(c).getExceptionTypes();
    if (exceptionTypes == null) {
      return new Class[0];
    } else {
      return typesToClasses(exceptionTypes);
    }
  }

  /**
   * Return the java.lang.Class associated with the return type of
   * this method.
   *
   * @return		the return type
   */
  public static Class getReturnType(Method m) {
    try {
      return java.lang.reflect.JikesRVMSupport.getMethodOf(m).getReturnType().resolve().getClassForType();
    } catch (ClassNotFoundException e) {
      if (VM.VerifyAssertions) VM._assert(false); // Should never happen?
      return null;
    }
  }


  /**
   * Return the java.lang.Class associated with the class that defined
   * this constructor.
   *
   * @return		the declaring class
   */
  public static Class getDeclaringClass(Constructor c) {
    return java.lang.reflect.JikesRVMSupport.getMethodOf(c).getDeclaringClass().getClassForType();
  }
  /**
   * Return the java.lang.Class associated with the class that defined
   * this method.
   *
   * @return		the declaring class
   */

  public static Class getDeclaringClass(Method m) {
    return java.lang.reflect.JikesRVMSupport.getMethodOf(m).getDeclaringClass().getClassForType();
  }

  /**
   * Return an array of the java.lang.Class objects associated with the
   * parameter types of this constructor. If the method was declared with no
   * parameters, the array returned will be empty.
   *
   * @return		the parameter types
   */
  public static Class[] getParameterTypes(Constructor c) {
    return typesToClasses(java.lang.reflect.JikesRVMSupport.getMethodOf(c).getParameterTypes());
  }

  /**
   * Return an array of the java.lang.Class objects associated with the
   * parameter types of this method. If the method was declared with no
   * parameters, the array returned will be empty.
   *
   * @return		the parameter types
   */
  public static Class[] getParameterTypes(Method m) {
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

    for (int j = 0; j < vm_other_methods.length; j++) {
      // Java Language Spec 8.2: class and initializers are not members thus not methods.
      // TODO: Object initializers should not be returned by getStaticMethods
      // - Eugene
      if (!vm_other_methods[j].isClassInitializer() &&
          !vm_other_methods[j].isObjectInitializer() &&
          !(which == Member.PUBLIC && !vm_other_methods[j].isPublic())) {
        coll.collect(java.lang.reflect.JikesRVMSupport.createMethod(vm_other_methods[j]));
      }
    }

    for (int j = 0; j < vm_virtual_methods.length; j++) {
      if (!vm_virtual_methods[j].isObjectInitializer() &&
          ! (which == Member.PUBLIC && !vm_virtual_methods[j].isPublic())) {
        coll.collect(java.lang.reflect.JikesRVMSupport.createMethod(vm_virtual_methods[j]));
      }
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
    return getConstructors0(C, Member.PUBLIC);
  }

  private static Constructor[] getConstructors0(Class C, int which) {
    VM_Type myType = java.lang.JikesRVMSupport.getTypeForClass(C);
    if (!myType.isClassType()) return new Constructor[0];
    VM_Method vm_static_methods[] = myType.getStaticMethods();
    Collector coll = new Collector();
    for (int i = 0; i<vm_static_methods.length; i++) {
      VM_Method cand = vm_static_methods[i];
      if (cand.isObjectInitializer() && !(which == Member.PUBLIC && !cand.isPublic())) {
	coll.collect(java.lang.reflect.JikesRVMSupport.createConstructor(cand));
      }
    }
    return coll.constructorArray();
  }

  private static Constructor getConstructor0(Class C, Class parameterTypes[], int which )
    throws NoSuchMethodException {

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

    if (which == Member.PUBLIC ) {
      methods = java.lang.JikesRVMSupport.getTypeForClass(C).getStaticMethods();
    } else {
      methods = java.lang.JikesRVMSupport.getTypeForClass(C).asClass().getDeclaredMethods();
    }

    for (int i = 0, n = methods.length; i < n; ++i) {
      VM_Method method = methods[i];
      if (method.isObjectInitializer() &&
	  !( which == Member.PUBLIC && !method.isPublic() )
	  && parametersMatch(method.getParameterTypes(), parameterTypes)) {
	return java.lang.reflect.JikesRVMSupport.createConstructor(method);
      }
    }

    throw new NoSuchMethodException("<init> " + parameterTypes );
  }

  private static Field[] getFields0(Class C, int which) {
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

    for (int j = 0; j < vm_other_fields.length; j++) {
      if (!(which == Member.PUBLIC && !vm_other_fields[j].isPublic())) {
        coll.collect(java.lang.reflect.JikesRVMSupport.createField(vm_other_fields[j]));
      }
    }

    for (int j = 0; j < vm_instance_fields.length; j++) {
      if (!(which == Member.PUBLIC && !vm_instance_fields[j].isPublic())) {
        coll.collect(java.lang.reflect.JikesRVMSupport.createField(vm_instance_fields[j]));
      }
    }

    return coll.fieldArray();
  }

  private static Field getField0(Class C, String name, int which)
    throws NoSuchFieldException {

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

    for (int j = 0; j < vm_other_fields.length; j++) {
      if ( ! ( which == Member.PUBLIC && !vm_other_fields[j].isPublic() ) &&
	   ( vm_other_fields[j].getName() == aName ) ) {
	return java.lang.reflect.JikesRVMSupport.createField( vm_other_fields[j] );
      }
    }

    for (int j = 0; j < vm_instance_fields.length; j++) {
      if (!( which == Member.PUBLIC && !vm_instance_fields[j].isPublic()) &&
	  (vm_instance_fields[j].getName() == aName)) {
	return java.lang.reflect.JikesRVMSupport.createField(vm_instance_fields[j]);
      }
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
    VM_Type type = java.lang.JikesRVMSupport.getTypeForClass(C);
    return type.isArrayType() ? type.asArray().getElementType().getClassForType() : null;
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
  public static Constructor getDeclaredConstructor(Class C, Class parameterTypes[]) 
    throws NoSuchMethodException, SecurityException {
    checkMemberAccess(C, Member.DECLARED);

    // Handle the default constructor case upfront
    if(parameterTypes == null || parameterTypes.length == 0) {
      return getConstructor0(C, new Class[0], Member.DECLARED);
    }

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
  public static Constructor getConstructor(Class C, Class parameterTypes[]) 
    throws NoSuchMethodException, SecurityException {
    checkMemberAccess(C, Member.PUBLIC);

    // Handle the default constructor case upfront
    if(parameterTypes == null || parameterTypes.length == 0) {
      return getConstructor0(C, new Class[0], Member.PUBLIC);
    }

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
  public static int getModifiers(Method m) {
    return java.lang.reflect.JikesRVMSupport.getMethodOf(m).getModifiers();
  }

  /**
   * Return the modifiers for the modelled constructor.
   * The Modifier class should be used to decode the result.
   *
   * @return		the modifiers
   */
  public static int getModifiers(Constructor c) {
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
  public static boolean methodEquals(Method m,Object object) {
    VM_Method method = java.lang.reflect.JikesRVMSupport.getMethodOf(m);
    if (object != null && object instanceof Method) {
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
    return java.lang.JikesRVMSupport.getTypeForClass(C).toString();
  }

  /**
   * Return the name of the modelled method.
   *
   * @return		the name
   */
  public static String getName(Method m) {
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
  public static Object newInstance(Class C) 
    throws IllegalAccessException, 
	   InstantiationException {
    checkMemberAccess(C, Member.PUBLIC);
    Constructor cons;
    try {
      cons = getDeclaredConstructor(C, new Class[0]);
      return cons.newInstance(new Object[0]);
    } catch (java.lang.reflect.InvocationTargetException e) {
      InstantiationException ex = new InstantiationException(e.getMessage());

      e.printStackTrace();

      ex.initCause(e);
      throw ex;
    } catch (NoSuchMethodException e) {
      IllegalAccessException ex = new IllegalAccessException(e.getMessage());
      ex.initCause(e);
      throw ex;
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
    } catch (ClassNotFoundException e) {
      return false;
    }

    // get Method, if it exists
    try {
      method = theClass.getDeclaredMethod("readObject", argTypes);
    } catch ( NoSuchMethodException e ) {
      return false;  // tells caller method not defined for the class
    } catch ( SecurityException e ) {
      VM.sysWrite("SecurityException in ObjectInputStream.executeReadObject");
      return false;
    }

    Object[] methodArgs = new Object[1];
    methodArgs[0] = ois;   // arg to writeObject is this stream

    try {
      method.setAccessible( true );     // allow access to private method
      method.invoke( obj, methodArgs );
    } catch ( Exception e ) {
      VM.sysWrite("unexpected exception in .executeReadObject = " + e );
      return false;
    }

    return true;    // tells caller method exists & was invoked
  }

  /**
   * Compare parameter lists for agreement.
   */ 
  private static boolean parametersMatch(VM_TypeReference[] lhs, Class[] rhs) {
    if (lhs.length != rhs.length)
      return false;

    for (int i = 0, n = lhs.length; i < n; ++i) {
      if (rhs[i] == null) return false;
      try {
	if (lhs[i].resolve() != java.lang.JikesRVMSupport.getTypeForClass(rhs[i])) {
	  return false;
	}
      } catch (ClassNotFoundException e) {
	if (VM.VerifyAssertions) VM._assert(false); // Can't happen?
      }
    }	
    return true;
  }

  // Make possibly wrapped method argument compatible with expected type
  // throwing IllegalArgumentException if it cannot be.
  //
  private static Object makeArgumentCompatible(VM_Type expectedType, Object arg) {
    if (expectedType.isPrimitiveType()) { 
      if (arg instanceof java.lang.Void) {
	if (expectedType.isVoidType()) return arg;
      } else if (arg instanceof java.lang.Boolean) {
	if (expectedType.isBooleanType()) return arg;
      } else if (arg instanceof java.lang.Byte) {
	if (expectedType.isByteType()) return arg;
	if (expectedType.isShortType()) return new Short(((java.lang.Byte)arg).byteValue());
	if (expectedType.isIntType()) return new Integer(((java.lang.Byte)arg).byteValue());
	if (expectedType.isLongType()) return new Long(((java.lang.Byte)arg).byteValue());
      } else if (arg instanceof java.lang.Short) {
	if (expectedType.isShortType()) return arg;
	if (expectedType.isIntType()) return new Integer(((java.lang.Short)arg).shortValue());
	if (expectedType.isLongType()) return new Long(((java.lang.Short)arg).shortValue());
      } else if (arg instanceof java.lang.Character) {
	if (expectedType.isCharType()) return arg;
	if (expectedType.isIntType()) return new Integer(((java.lang.Character)arg).charValue());
	if (expectedType.isLongType()) return new Long(((java.lang.Character)arg).charValue());
      } else if (arg instanceof java.lang.Integer) {
	if (expectedType.isIntType()) return arg;
	if (expectedType.isLongType()) return new Long(((java.lang.Integer)arg).intValue());
      } else if (arg instanceof java.lang.Long) {
	if (expectedType.isLongType()) return arg;
      } else if (arg instanceof java.lang.Float) {
	if (expectedType.isFloatType()) return arg;
	if (expectedType.isDoubleType()) return new Double(((java.lang.Integer)arg).floatValue());
      } else if (arg instanceof java.lang.Double) {
	if (expectedType.isDoubleType()) return arg;
      }
    } else {
      if (arg == null) return arg; // null is always ok
      VM_Type actualType = VM_Magic.getObjectType(arg);
      if (expectedType == actualType || 
	  expectedType == VM_Type.JavaLangObjectType ||
	  VM_Runtime.isAssignableWith(expectedType, actualType)) {
	return arg;
      }
    } 
    throw new IllegalArgumentException();
  }

  /**
   * Initialize a stream for reading primitive data stored in <code>byte[] data</code>
   *
   * @param		data		The primitive data as raw bytes.
   * @exception	IOException		If an IO exception happened when creating internal streams to hold primitive data
   */
  public static void initPrimitiveTypes(ObjectInputStream ois, byte[] data) throws IOException {
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
      Field f = declaringClass.getDeclaredField(fieldName);
      f.setAccessible( true );     // to allow access to private fields
      f.setByte(instance,value);
    } catch ( Exception e ) {
      VM.sysWrite( "exception in ObjectInputStream.setField(..,byte): " + e );
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
      Field f = declaringClass.getDeclaredField(fieldName);
      f.setAccessible( true );     // to allow access to private fields
      f.setChar(instance,value);
    } catch ( Exception e ) {
      VM.sysWrite( "exception in ObjectInputStream.setField(..,char): " + e );
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
    } catch ( Exception e ) {
      VM.sysWrite( "exception in ObjectInputStream.setField(..,double): " + e );
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
    } catch ( Exception e ) {
      VM.sysWrite( "exception in ObjectInputStream.setField(..,float): " + e );
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
    } catch ( Exception e ) {
      VM.sysWrite( "exception in ObjectInputStream.setField(..,int): " + e );
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
    } catch ( Exception e ) {
      VM.sysWrite( "exception in ObjectInputStream.setField(..,long): " + e );
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
    } catch ( Exception e ) {
      VM.sysWrite( "exception in ObjectInputStream.setField(..,Object): " + e );
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
    } catch ( Exception e ) {
      VM.sysWrite( "exception in ObjectInputStream.setField(..,short): " + e );
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
      VM.sysWrite( "exception in ObjectInputStream.setField(..,boolean): " + e );
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
    } catch ( ClassNotFoundException e ) {
      return false;
    }

    // get Method, if it exists
    try {
      method = theClass.getDeclaredMethod("writeObject", argTypes);
    } catch ( NoSuchMethodException e ) {
      return false;  // tells caller method not defined for the class
    } catch ( SecurityException e ) {
      VM.sysWrite("SecurityException in ObjectOutputStream.executeWriteObject");
      return false;
    }

    Object[] methodArgs = new Object[1];
    methodArgs[0] = oos;   // arg to writeObject is this stream

    try {
      method.setAccessible( true );     // allow access to private method
      method.invoke( obj, methodArgs );
    } catch ( Exception e ) {
      VM.sysWrite("unexpected exception in ObjectOutputStream.executeWriteObject = " + e );
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
    } catch ( Exception e ) {
      VM.sysWrite( "exception in ObjectOutputStream.getFieldBool(): " + e );
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
    } catch ( Exception e ) {
      VM.sysWrite( "exception in ObjectOutputStream.getFieldByte(): " + e );
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
      VM.sysWrite( "exception in ObjectOutputStream.getFieldChar(): " + e );
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
      VM.sysWrite( "exception in ObjectOutputStream.getFieldDouble(): " + e );
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
      VM.sysWrite( "exception in ObjectOutputStream.getFieldFloat(): " + e );
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
      VM.sysWrite( "exception in ObjectOutputStream.getFieldInt(): " + e );
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
      VM.sysWrite( "exception in ObjectOutputStream.getFieldLong(): " + e );
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
      VM.sysWrite( "exception in ObjectOutputStream.getFieldObj(): " + e );
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
      VM.sysWrite( "exception in ObjectOutputStream.getFieldShort(): " + e );
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
      VM.sysWrite( "exception in ObjectStreamClass.getConstructorSignature(): " + e );
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
      VM.sysWrite( "exception in ObjectStreamClass.getFieldSignature(): " + e );
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
      VM.sysWrite( "exception in ObjectStreamClass.getMethodSignature(): " + e );
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
  private static Class[] typesToClasses(VM_TypeReference[] types) {
    Class[] classes = new Class[types.length];
    for (int i = 0; i < types.length; i++) {
      try {
	classes[i] = types[i].resolve().getClassForType();
      } catch (ClassNotFoundException e) {
	if (VM.VerifyAssertions) VM._assert(false); // Should never happen?
      }
    }
    return classes;
  }

  private static boolean validArrayDescriptor (String name) {
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
