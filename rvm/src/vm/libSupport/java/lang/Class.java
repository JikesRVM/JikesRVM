/*
 * (C) Copyright IBM Corp 2002
 */
//$Id$
package java.lang;

import java.io.InputStream;
import java.security.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.Vector;
import java.util.HashMap;

import com.ibm.JikesRVM.classloader.*;

import com.ibm.JikesRVM.VM_Callbacks;
import com.ibm.JikesRVM.VM_Reflection;
import com.ibm.JikesRVM.VM_Runtime;
import com.ibm.JikesRVM.VM_UnimplementedError;
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;

/**
 * Implementation of java.lang.Class for JikesRVM.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API. 
 * 
 * @author John Barton 
 * @author Julian Dolby
 * @author Stephen Fink
 * @author Eugene Gluzberg
 * @author Dave Grove
 */
public final class Class implements java.io.Serializable {
  static final long serialVersionUID = 3206093459760846163L;
    
  /**
   * Prevents this class from being instantiated, except by the
   * create method in this class.
   */
  private Class() {}

  /**
   * This field holds the VM_Type object for this class.
   */
  VM_Type type;

  /**
   * This field holds the protection domain of this class.
   */
  ProtectionDomain pd;

  /**
   * The signers of this class
   */
  Object[] signers;

  public boolean desiredAssertionStatus() {
    throw new VM_UnimplementedError();
  }
  
  public static Class forName(String typeName) throws ClassNotFoundException {
    return forNameInternal(typeName, true, VM_Class.getClassLoaderFromStackFrame(1));
  }

  public static Class forName(String className, 
			      boolean initialize, 
			      ClassLoader classLoader) throws ClassNotFoundException,
							      LinkageError,
							      ExceptionInInitializerError,
							      SecurityException {
    if (classLoader == null) {
      SecurityManager security = System.getSecurityManager();
      if (security != null) {
	ClassLoader parentCL = VM_Class.getClassLoaderFromStackFrame(1);
	if (parentCL != null) {
	  security.checkPermission(new RuntimePermission("getClassLoader"));
	}
      }
      classLoader = VM_SystemClassLoader.getVMClassLoader();
    }
    return forNameInternal(className, initialize, classLoader);
  }

  public Class[] getClasses() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType()) return new Class[0];
    
    Vector publicClasses = new Vector();
    for (Class c = this; c != null; c = c.getSuperclass()) {
      c.checkMemberAccess(Member.PUBLIC);
      VM_TypeReference[] declaredClasses = c.type.asClass().getDeclaredClasses();
      if (declaredClasses != null) {
	for (int i=0; i<declaredClasses.length; i++) {
	  if (declaredClasses[i] != null) {
	    try {
	      VM_Class dc = declaredClasses[i].resolve().asClass();
	      if (dc.isPublic()) {
		publicClasses.add(dc.getClassForType());
	      }
	    } catch (ClassNotFoundException e) {
	      InternalError e2 = new InternalError();
	      e2.initCause(e);
	      throw e2;
	    }
	  }
	}
      }
    }
    
    Class[] ans = new Class[publicClasses.size()];
    publicClasses.copyInto(ans);
    return ans;
  }

  public ClassLoader getClassLoader() {
    SecurityManager security = System.getSecurityManager();
    if (security != null) {
      ClassLoader parentCL = VM_Class.getClassLoaderFromStackFrame(1);
      if (parentCL != null) {
	security.checkPermission(new RuntimePermission("getClassLoader"));
      }
    }
    ClassLoader cl = type.getClassLoader();
    return cl == VM_SystemClassLoader.getVMClassLoader() ? null : cl;
  }

  public Class getComponentType() {
    return type.isArrayType() ? type.asArray().getElementType().getClassForType() : null;
  }

  public Constructor getConstructor(Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType()) throw new NoSuchMethodException();

    VM_Method methods[] = type.asClass().getConstructorMethods();
    for (int i = 0; i<methods.length; i++) {
      VM_Method method = methods[i];
      if (method.isPublic() && 
	  parametersMatch(method.getParameterTypes(), parameterTypes)) {
	return java.lang.reflect.JikesRVMSupport.createConstructor(method);
      }
    }

    throw new NoSuchMethodException("<init> " + parameterTypes );
  }                                                                                            

  public Constructor[] getConstructors() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType()) return new Constructor[0];

    VM_Method methods[] = type.asClass().getConstructorMethods();
    Collector coll = new Collector(methods.length);
    for (int i = 0; i<methods.length; i++) {
      VM_Method method = methods[i];
      if (method.isPublic()) {
	coll.collect(java.lang.reflect.JikesRVMSupport.createConstructor(method));
      }
    }
    return coll.constructorArray();
  }

  public Class[] getDeclaredClasses() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Class[0];

    // Get array of declared classes from VM_Class object
    VM_Class cls = type.asClass();
    VM_TypeReference[] declaredClasses = cls.getDeclaredClasses();

    // The array can be null if the class has no declared inner class members
    if (declaredClasses == null)
      return new Class[0];

    // Count the number of actual declared inner and static classes.
    // (The array may contain null elements, which we want to skip.)
    int count = 0;
    int length = declaredClasses.length;
    for (int i = 0; i < length; ++i) {
      if (declaredClasses[i] != null) {
	++count;
      }
    }

    // Now build actual result array.
    Class[] result = new Class[count];
    count = 0;
    for (int i = 0; i < length; ++i) {
      if (declaredClasses[i] != null) {
	try {
	  result[count++] = declaredClasses[i].resolve().getClassForType();
	} catch (ClassNotFoundException e) {
	  throw new InternalError();
	}
      }
    }
    
    return result;
  }

  public Constructor getDeclaredConstructor(Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) throw new NoSuchMethodException();

    VM_Method methods[] = type.asClass().getConstructorMethods();
    for (int i = 0, n = methods.length; i < n; ++i) {
      VM_Method method = methods[i];
      if (parametersMatch(method.getParameterTypes(), parameterTypes)) {
	return java.lang.reflect.JikesRVMSupport.createConstructor(method);
      }
    }

    throw new NoSuchMethodException("<init> " + parameterTypes );
  }

  public Constructor[] getDeclaredConstructors() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Constructor[0];

    VM_Method methods[] = type.asClass().getConstructorMethods();
    Constructor[] ans = new Constructor[methods.length];
    for (int i = 0; i<methods.length; i++) {
      ans[i] = java.lang.reflect.JikesRVMSupport.createConstructor(methods[i]);
    }
    return ans;
  }

  public Field getDeclaredField(String name) throws NoSuchFieldException, SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) throw new NoSuchFieldException();

    VM_Atom aName = VM_Atom.findUnicodeAtom(name);
    if (aName == null) throw new NoSuchFieldException(name);
    VM_Field[] fields = type.asClass().getDeclaredFields();
    for (int i = 0; i < fields.length; i++) {
      VM_Field field = fields[i];
      if (field.getName() == aName) {
	return java.lang.reflect.JikesRVMSupport.createField(field);
      }
    }
    
    throw new NoSuchFieldException(name);
  }

  public Field[] getDeclaredFields() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Field[0];

    VM_Field[] fields = type.asClass().getDeclaredFields();
    Field[] ans = new Field[fields.length];
    for (int i = 0; i < fields.length; i++) {
      ans[i] = java.lang.reflect.JikesRVMSupport.createField(fields[i]);
    }
    return ans;
  }

  public Method getDeclaredMethod(String name, Class parameterTypes[]) 
    throws NoSuchMethodException, SecurityException {
    checkMemberAccess(Member.DECLARED);

    if (!type.isClassType())  {
      throw new NoSuchMethodException(name + parameterTypes);
    }

    VM_Atom aName = VM_Atom.findUnicodeAtom(name);
    if (aName == null ||
	aName == VM_ClassLoader.StandardClassInitializerMethodName ||
	aName == VM_ClassLoader.StandardObjectInitializerMethodName) {
      // null means that we don't have such an atom; <init> and <clinit> are not methods.
      throw new NoSuchMethodException(name + parameterTypes);
    }

    // TODO: 1.4 SPEC states that if there are multiple matching methods
    // then we should return the one with the most specific return type.
    VM_Method[] methods = type.asClass().getDeclaredMethods(); 
    for (int i = 0; i<methods.length; i++) {
      VM_Method meth = methods[i];
      if (meth.getName() == aName && 
	  parametersMatch(meth.getParameterTypes(), parameterTypes)) {
	return java.lang.reflect.JikesRVMSupport.createMethod(meth);
      }
    }
    
    throw new NoSuchMethodException(name + parameterTypes);
  }

  public Method[] getDeclaredMethods() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Method[0];

    VM_Method[] methods = type.asClass().getDeclaredMethods();
    Collector coll = new Collector(methods.length);
    for (int i = 0; i < methods.length; i++) {
      VM_Method meth = methods[i];
      if (!meth.isClassInitializer() && !meth.isObjectInitializer()) {
	coll.collect(java.lang.reflect.JikesRVMSupport.createMethod(meth));
      }
    }
    return coll.methodArray();
  }

  public Class getDeclaringClass() {
    if (!type.isClassType()) return null;
    VM_TypeReference dc = type.asClass().getDeclaringClass();
    if (dc == null) return null;
    try {
      return dc.resolve().getClassForType();
    } catch (ClassNotFoundException e) {
      throw new InternalError();
    }
  }

  // TODO: Search algorithm is wrong.  Must search in the following order.
  //         (1) declared fields of this
  //         (2) superinterfaces of this (recursively)
  //         (3) superclass of this
  public Field getField(String name) throws NoSuchFieldException, SecurityException {
    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType()) throw new NoSuchFieldException();

    VM_Atom aName = VM_Atom.findUnicodeAtom(name);
    if (aName == null) throw new NoSuchFieldException(name);

    VM_Field static_fields[] = type.getStaticFields();
    for (int i = 0; i < static_fields.length; i++) {
      VM_Field field = static_fields[i];
      if (field.isPublic() && field.getName() == aName) {
	return java.lang.reflect.JikesRVMSupport.createField(field);
      }
    }
    VM_Field instance_fields[] = type.getInstanceFields();
    for (int i = 0; i < instance_fields.length; i++) {
      VM_Field field = instance_fields[i];
      if (field.isPublic() && field.getName() == aName) {
	return java.lang.reflect.JikesRVMSupport.createField(field);
      }
    }
    throw new NoSuchFieldException(name);
  }

  public Field[] getFields() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);
    
    VM_Field static_fields[] = type.getStaticFields();
    VM_Field instance_fields[] = type.getInstanceFields();
    Collector coll = new Collector(static_fields.length + instance_fields.length);
    for (int i = 0; i < static_fields.length; i++) {
      VM_Field field = static_fields[i];
      if (field.isPublic()) {
        coll.collect(java.lang.reflect.JikesRVMSupport.createField(field));
      }
    }
    for (int i = 0; i < instance_fields.length; i++) {
      VM_Field field = instance_fields[i];
      if (field.isPublic()) {
        coll.collect(java.lang.reflect.JikesRVMSupport.createField(field));
      }
    }

    return coll.fieldArray();
  }

  public Class[] getInterfaces () {
    if (type.isArrayType()) {
      // arrays implement JavaLangSerializable & JavaLangCloneable
      return new Class[] { VM_Type.JavaLangCloneableType.getClassForType(),
			   VM_Type.JavaIoSerializableType.getClassForType() };
    } else if (type.isClassType()) {
      VM_Class[] interfaces  = type.asClass().getDeclaredInterfaces();
      Class[]    jinterfaces = new Class[interfaces.length];
      for (int i = 0; i != interfaces.length; i++)
	jinterfaces[i] = interfaces[i].getClassForType();
      return jinterfaces;
    } else {
      return new Class[0];
    }
  }

  // TODO: Search algorithm is wrong.
  //        (1) declared methods of this
  //        (2) this's superclass (recursively)
  //        (3) superinterfaces of this.
  public Method getMethod(String name, Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
    checkMemberAccess(Member.PUBLIC);

    if (!type.isClassType()) throw new NoSuchMethodException(name + parameterTypes);

    VM_Atom aName = VM_Atom.findUnicodeAtom(name);
    if (aName == null || 
	aName == VM_ClassLoader.StandardClassInitializerMethodName ||
	aName == VM_ClassLoader.StandardObjectInitializerMethodName) {
      // null means that we don't have such an atom; <init> and <clinit> are not methods.
      throw new NoSuchMethodException(name + parameterTypes);
    }

    // TODO: This implementation doesn't match the 1.4 SPEC in several
    //       ways. (1) multiple matching methods must pick most specific
    //       return type. (2) Maybe we need to look at superclass's static methods?
    VM_Method static_methods[] = type.getStaticMethods();
    for (int i = 0; i < static_methods.length; i++) {
      VM_Method meth = static_methods[i];
      if (meth.getName() == aName && meth.isPublic() && 
	  parametersMatch(meth.getParameterTypes(), parameterTypes)) {
	return java.lang.reflect.JikesRVMSupport.createMethod(meth);
      }
    }
    VM_Method virtual_methods[] = type.getVirtualMethods();
    for (int i = 0; i < virtual_methods.length; i++) {
      VM_Method meth = virtual_methods[i];
      if (meth.getName() == aName && meth.isPublic() && 
	  parametersMatch(meth.getParameterTypes(), parameterTypes)) {
	return java.lang.reflect.JikesRVMSupport.createMethod(meth);
      }
    }

    throw new NoSuchMethodException(name + parameterTypes);
  }

  public Method[] getMethods() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);
 
    VM_Method static_methods[] = type.getStaticMethods();
    VM_Method virtual_methods[] = type.getVirtualMethods();
    Collector coll = new Collector(static_methods.length + virtual_methods.length);
    for (int i = 0; i < static_methods.length; i++) {
      VM_Method meth = static_methods[i];
      if (meth.isPublic()) {
        coll.collect(java.lang.reflect.JikesRVMSupport.createMethod(meth));
      }
    }
    for (int i = 0; i < virtual_methods.length; i++) {
      VM_Method meth = virtual_methods[i];
      if (meth.isPublic()) {
        coll.collect(java.lang.reflect.JikesRVMSupport.createMethod(meth));
      }
    }
    return coll.methodArray();
  }

  public int getModifiers() {
    if (type.isClassType()) {
      return type.asClass().getModifiers();
    } else if (type.isArrayType()) {
      VM_Type innermostElementType = type.asArray().getInnermostElementType();
      int result = Modifier.FINAL;
      if (innermostElementType.isClassType()) {
	int component = innermostElementType.asClass().getModifiers();
	result |= (component & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE));
      } else {
	result |= Modifier.PUBLIC; // primitive
      }
      return result;
    } else {
      return Modifier.PUBLIC | Modifier.FINAL;
    }
  }

  public String getName() {
    return type.toString();
  }

  // TODO: This seems bogus.
  public Package getPackage() {
    return new Package(getPackageName(), "", "", "", "", "", "", null);
  }

  public ProtectionDomain getProtectionDomain() {
    SecurityManager security = System.getSecurityManager();
    if (security != null) {
      security.checkPermission(new RuntimePermission("getProtectionDomain"));
    }
    return pd;
  }

  public URL getResource(String resName) {
    ClassLoader loader = getClassLoader();
    if (loader == VM_SystemClassLoader.getVMClassLoader())
      return ClassLoader.getSystemResource(toResourceName(resName));
    else
      return loader.getResource(toResourceName(resName));
  }

  public InputStream getResourceAsStream(String resName) {
    ClassLoader loader = getClassLoader();
    if (loader == VM_SystemClassLoader.getVMClassLoader())
      return ClassLoader.getSystemResourceAsStream(toResourceName(resName));
    else
      return loader.getResourceAsStream(toResourceName(resName));
  }

  public Object[] getSigners() {
    if (signers == null) {
      return null;
    } else {
      return (Object[])signers.clone();
    }
  }

  public Class getSuperclass () {
    if (type.isArrayType()) {
      return VM_Type.JavaLangObjectType.getClassForType();
    } else if (type.isClassType()) {
      VM_Class myClass = type.asClass();
      if (myClass.isInterface()) return null;
      VM_Type supe = myClass.getSuperClass();
      return supe == null ? null : supe.getClassForType();
    } else {
      return null;
    }
  }
    
  public boolean isArray() {
    return type.isArrayType();
  }

  public boolean isAssignableFrom(Class cls) {
    return type == cls.type || VM_Runtime.isAssignableWith(type, cls.type);
  }

  public boolean isInstance(Object object) {
    if (object == null) return false;
    if (isPrimitive())  return false;
    return isAssignableFrom(object.getClass());
  }

  public boolean isInterface() {
    return type.isClassType() && type.asClass().isInterface();
  }

  public boolean isPrimitive() {
    return type.isPrimitiveType();
  }

  public Object newInstance() throws IllegalAccessException, 
				     InstantiationException,
				     ExceptionInInitializerError,
				     SecurityException {
    // Basic checks
    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType()) 
      throw new InstantiationException();

    VM_Class cls = type.asClass();

    if (cls.isAbstract() || cls.isInterface())
      throw new InstantiationException();
    
    // Find the defaultConstructor
    VM_Method defaultConstructor = null;
    VM_Method methods[] = type.asClass().getConstructorMethods();
    for (int i = 0; i < methods.length; i++) {
      VM_Method method = methods[i];
      if (method.getParameterTypes().length == 0) {
	defaultConstructor = method;
	break;
      }
    }
    if (defaultConstructor == null) 
      throw new InstantiationException();

    // Check that caller is allowed to access it
    if (!defaultConstructor.isPublic()) {
      VM_Class accessingClass = VM_Class.getClassFromStackFrame(1);
      java.lang.reflect.JikesRVMSupport.checkAccess(defaultConstructor, accessingClass);
    }

    // Ensure that the class is initialized
    if (!cls.isInitialized()) {
      try {
	VM_Runtime.initializeClassForDynamicLink(cls);
      } catch (Throwable e) {
	ExceptionInInitializerError ex = new ExceptionInInitializerError();
	ex.initCause(e);
	throw ex;
      }
    }

    // Allocate an uninitialized instance;
    int      size = cls.getInstanceSize();
    Object[] tib  = cls.getTypeInformationBlock();
    boolean  hasFinalizer = cls.hasFinalizer();
    int allocator = VM_Interface.pickAllocator(cls);
    Object   obj  = VM_Runtime.resolvedNewScalar(size, tib, hasFinalizer, allocator);

    // Run the default constructor on the it.
    try {
      VM_Reflection.invoke(defaultConstructor, obj, null);
    } catch (Throwable e) {
      InstantiationException ex = new InstantiationException();
      ex.initCause(e);
      throw ex;
    }
    return obj;
  }

  public String toString() {
    String name = getName();
    if (isPrimitive()) {
      return name;
    } else if (isInterface()) {
      return "interface "+name;
    } else {
      return "class "+name;
    }
  }

  /*
   * Implementation below
   */

  /**
   * Create a java.lang.Class corresponding to a given VM_Type
   */
  static Class create(VM_Type type) {
    Class c = new Class();
    c.type = type;
    return c;
  }

  void setSigners(Object[] signers) {
    signers = signers;
    throw new VM_UnimplementedError("Class.setSigners");
  }
   
  // TODO: (1) If className denotes a primitive type or void, then
  //           we need to look for a user-defined class in the unnamed package
  //           of that name.  This method should not return the Class objects
  //           for primitive types of void.
  private static Class forNameInternal(String className, 
				       boolean initialize, 
				       ClassLoader classLoader) throws ClassNotFoundException,
								       LinkageError,
								       ExceptionInInitializerError {
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
      try {
	ans.initialize();
      } catch (Exception e) {
	throw new ExceptionInInitializerError(e);
      }
    }
    return ans.getClassForType();
  }

  private String getPackageName() {
    String name = getName();
    int index = name.lastIndexOf('.');
    if (index >= 0) return name.substring(0, index);
    return "";
  }

  private String toResourceName(String resName) {
    // Turn package name into a directory path
    if (resName.charAt(0) == '/') return resName.substring(1);

    String qualifiedClassName = getName();
    int classIndex = qualifiedClassName.lastIndexOf('.');
    if (classIndex == -1) return resName; // from a default package
    return qualifiedClassName.substring(0, classIndex + 1).replace('.', '/') + resName;
  }
  
  private static boolean validArrayDescriptor (String name) {
    int i;
    int length = name.length();

    for (i = 0; i < length; i++)
      if (name.charAt(i) != '[') break;
    if (i == length) return false;	// string of only ['s

    if (i == length - 1) {
      switch (name.charAt(i)) {
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
    } else if (name.charAt(i) != 'L') {
      return false;    // not a class descriptor
    } else if (name.charAt(length - 1) != ';') {
      return false;	// ditto
    }
    return true;			// a valid class descriptor
  }

  /**
   * Utility for security checks
   */
  private void checkMemberAccess(int type) {
    SecurityManager security = System.getSecurityManager();
    if (security != null) {
      security.checkMemberAccess(this, type);
      String packageName = getPackageName();
      if(packageName != "") {
        security.checkPackageAccess(packageName);
      }
    }
  }

  /**
   * Compare parameter lists for agreement.
   */ 
  private boolean parametersMatch(VM_TypeReference[] lhs, Class[] rhs) {
    if (rhs == null) return lhs.length == 0;
    if (lhs.length != rhs.length) return false;

    for (int i = 0, n = lhs.length; i < n; ++i) {
      if (rhs[i] == null) return false;
      try {
	if (lhs[i].resolve() != rhs[i].type) {
	  return false;
	}
      } catch (ClassNotFoundException e) {
	throw new InternalError();
      }
    }	
    return true;
  }

  // aux class to build up collections of things
  private static final class Collector {
    private int n = 0;
    private final Object[] coll;

    Collector(int max) {
      coll = new Object[max];
    }

    void collect(Object thing) {
      coll[n++] = thing;
    }

    // repeat for each class of interest
    //
    Method[] methodArray() {
      Method[] ans = new Method[n];
      System.arraycopy(coll, 0, ans, 0, n);
      return ans;
    }

    Field[] fieldArray() {
      Field[] ans = new Field[n];
      System.arraycopy(coll, 0, ans, 0, n);
      return ans;
    }

    Constructor[] constructorArray() {
      Constructor[] ans = new Constructor[n];
      System.arraycopy(coll, 0, ans, 0, n);
      return ans;
    }
  }
}

