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

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Callbacks;
import com.ibm.JikesRVM.VM_Runtime;
import com.ibm.JikesRVM.VM_UnimplementedError;

/**
 * Library support interface of Jikes RVM
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
   * Create a java.lang.Class corresponding to a given VM_Type
   */
  static Class create(VM_Type type) {
    Class c = new Class();
    c.type = type;
    return c;
  }

  void setSigners(Object[] signers) {
    throw new VM_UnimplementedError("Class.setSigners");
  }
   
  public static Class forName(String typeName) throws ClassNotFoundException {
    return forName(typeName, true, VM_Class.getClassLoaderFromStackFrame(1));
  }
    
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

  public ClassLoader getClassLoader() {
    ClassLoader cl = type.getClassLoader();
    return cl == VM_SystemClassLoader.getVMClassLoader() ? null : cl;
  }

  public Class getComponentType() {
    return type.isArrayType() ? type.asArray().getElementType().getClassForType() : null;
  }

  public Class[] getClasses() {
    Vector publicClasses = new Vector();

    for (Class c = this; c != null; c = c.getSuperclass()) {
      Class[] declaredClasses = c.getDeclaredClasses();
      for (int i = 0; i < declaredClasses.length; i++)
	if (Modifier.isPublic(declaredClasses[i].getModifiers()))
	  publicClasses.addElement(declaredClasses[i]);
    }

    Class[] allDeclaredClasses = new Class[publicClasses.size()];
    publicClasses.copyInto(allDeclaredClasses);
    return allDeclaredClasses;
  }

  public Constructor getConstructor(Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
    checkMemberAccess(Member.PUBLIC);

    // Handle the default constructor case upfront
    if(parameterTypes == null || parameterTypes.length == 0) {
      return getConstructor0(new Class[0], Member.PUBLIC);
    }

    return getConstructor0(parameterTypes, Member.PUBLIC);
  }                                                                                            

  public Constructor[] getConstructors() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);
    return getConstructors0(Member.PUBLIC);
  }

  public Class[] getDeclaredClasses() throws SecurityException {
    // Security check
    checkMemberAccess(Member.DECLARED);

    // Am I a class type?
    if (!type.isClassType())
      return new Class[0];

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
	  if (VM.VerifyAssertions) VM._assert(false); // Should never happen?
	}
      }
    }
    
    return result;
  }

  public Constructor getDeclaredConstructor(Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
    checkMemberAccess(Member.DECLARED);

    // Handle the default constructor case upfront
    if(parameterTypes == null || parameterTypes.length == 0) {
      return getConstructor0(new Class[0], Member.DECLARED);
    }

    return getConstructor0(parameterTypes, Member.DECLARED);
  }

  public Constructor[] getDeclaredConstructors() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Constructor[0];
    return getConstructors0(Member.DECLARED);
  }

  public Field getDeclaredField(String name) throws NoSuchFieldException, SecurityException {
    checkMemberAccess(Member.DECLARED);
    return getField0(name, Member.DECLARED);
  }

  public Field[] getDeclaredFields() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Field[0];
    return getFields0(Member.DECLARED);
  }

  public Method getDeclaredMethod(String name, Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
    checkMemberAccess(Member.DECLARED);

    // Handle the no parameter case upfront
    if(parameterTypes == null || parameterTypes.length == 0)
      return getMethod0(name, new Class[0], Method.DECLARED);

    return getMethod0(name, parameterTypes, Method.DECLARED);
  }

  public Method[] getDeclaredMethods() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Method[0];
    return getMethods0(Member.DECLARED);
  }

  public Class getDeclaringClass() {
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

  public Field getField(String name) throws NoSuchFieldException, SecurityException {
    checkMemberAccess(Member.PUBLIC);
    return getField0(name, Member.PUBLIC );
  }

  public Field[] getFields() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);
    return getFields0(Member.PUBLIC);
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

  public Method getMethod(String name, Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
    checkMemberAccess(Member.PUBLIC);

    // Handle the no parameter case upfront
    if(parameterTypes == null || parameterTypes.length == 0)
      return getMethod0(name, new Class[0], Member.PUBLIC);

    return getMethod0(name, parameterTypes, Member.PUBLIC);
  }

  public Method[] getMethods() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);
    return getMethods0(Member.PUBLIC);
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

  public ProtectionDomain getProtectionDomain() {
    return pd;
  }

  public String getPackageName() {
    String name = getName();
    int index = name.lastIndexOf('.');
    if (index >= 0) return name.substring(0, index);
    return "";
  }

  public URL getResource(String resName) {
    ClassLoader loader = this.getClassLoader();
    if (loader == VM_SystemClassLoader.getVMClassLoader())
      return ClassLoader.getSystemResource(this.toResourceName(resName));
    else
      return loader.getResource(this.toResourceName(resName));
  }

  public InputStream getResourceAsStream(String resName) {
    ClassLoader loader = this.getClassLoader();
    if (loader == VM_SystemClassLoader.getVMClassLoader())
      return ClassLoader.getSystemResourceAsStream(this.toResourceName(resName));
    else
      return loader.getResourceAsStream(this.toResourceName(resName));
  }

  public Object[] getSigners() {
    return null;
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

  public Object newInstance() throws IllegalAccessException, InstantiationException {
    checkMemberAccess(Member.PUBLIC);
    Constructor cons;
    try {
      cons = getDeclaredConstructor(new Class[0]);
      return cons.newInstance(new Object[0]);
    } catch (java.lang.reflect.InvocationTargetException e) {
      InstantiationException ex = new InstantiationException(e.getMessage());
      ex.initCause(e);
      throw ex;
    } catch (NoSuchMethodException e) {
      IllegalAccessException ex = new IllegalAccessException(e.getMessage());
      ex.initCause(e);
      throw ex;
    }
  }

  private String toResourceName(String resName) {
    // Turn package name into a directory path
    if (resName.charAt(0) == '/') return resName.substring(1);

    String qualifiedClassName = getName();
    int classIndex = qualifiedClassName.lastIndexOf('.');
    if (classIndex == -1) return resName; // from a default package
    return qualifiedClassName.substring(0, classIndex + 1).replace('.', '/') + resName;
  }

  public String toString() {
    String name = type.toString();
    if (isPrimitive()) {
      return name;
    } else if (type.isArrayType()) {
      return "class " + name;
    } else {
      if (isInterface()) {
	return "interface " + name;
      } else  {
	return "class "     + name;
      }
    }
  }

  public Package getPackage() {
    return new Package(getPackageName(), "", "", "", "", "", "", null);
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


  private Method getMethod0(String name, Class[] parameterTypes, int which)
    throws NoSuchMethodException {
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
  private Method[] getMethods0(int which) {
    Collector coll = new Collector();

    VM_Method vm_virtual_methods[] = null;
    VM_Method vm_other_methods[] = null;
    if (which == Member.PUBLIC ) {
      vm_virtual_methods = type.getVirtualMethods();
      vm_other_methods = type.getStaticMethods();
    } else {
      vm_virtual_methods = new VM_Method[0];
      vm_other_methods = type.asClass().getDeclaredMethods(); 
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

  private Constructor[] getConstructors0(int which) {
    if (!type.isClassType()) return new Constructor[0];
    VM_Method vm_static_methods[] = type.getStaticMethods();
    Collector coll = new Collector();
    for (int i = 0; i<vm_static_methods.length; i++) {
      VM_Method cand = vm_static_methods[i];
      if (cand.isObjectInitializer() && !(which == Member.PUBLIC && !cand.isPublic())) {
	coll.collect(java.lang.reflect.JikesRVMSupport.createConstructor(cand));
      }
    }
    return coll.constructorArray();
  }

  private Constructor getConstructor0(Class parameterTypes[], int which )
    throws NoSuchMethodException {

    if (!type.isClassType()) throw new NoSuchMethodException();

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
      methods = type.getStaticMethods();
    } else {
      methods = type.asClass().getDeclaredMethods();
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

  private Field[] getFields0(int which) {
    Collector coll = new Collector();

    VM_Field vm_instance_fields[] = null;
    VM_Field vm_other_fields[] = null;
    if ( which == Member.PUBLIC ) {
      vm_instance_fields = type.getInstanceFields();
      vm_other_fields = type.getStaticFields();
    } else {
      vm_instance_fields = new VM_Field[0];
      vm_other_fields = type.asClass().getDeclaredFields(); 
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

  private Field getField0(String name, int which)
    throws NoSuchFieldException {

    if (!type.isClassType()) throw new NoSuchFieldException();

    VM_Field vm_instance_fields[] = null;
    VM_Field vm_other_fields[] = null;
    if ( which == Member.PUBLIC ) {
      vm_instance_fields = type.getInstanceFields();
      vm_other_fields = type.getStaticFields();
    } else {
      vm_instance_fields = new VM_Field[0];
      vm_other_fields = type.asClass().getDeclaredFields(); 
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
   * Compare parameter lists for agreement.
   */ 
  private boolean parametersMatch(VM_TypeReference[] lhs, Class[] rhs) {
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
