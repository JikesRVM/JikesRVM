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
