/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM.classloader;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.*;

import java.lang.reflect.*;
import java.io.*;

/**
 * This class provides a set utility functions to be used in the
 * implementation of reflection operations in the standard library.
 *
 * @author John Barton 
 * @author Stephen Fink
 * @author Eugene Gluzberg
 * @author Dave Grove
 */
public class VM_ReflectionSupport {

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
   * Return the java.lang.Class associated with the class that defined
   * this constructor.
   *
   * @return		the declaring class
   */
  public static Class getDeclaringClass(Constructor c) {
    return java.lang.reflect.JikesRVMSupport.getMethodOf(c).getDeclaringClass().getClassForType();
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
