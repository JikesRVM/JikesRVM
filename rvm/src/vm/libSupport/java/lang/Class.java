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

import com.ibm.JikesRVM.classloader.VM_Class;
import com.ibm.JikesRVM.classloader.VM_Type;
import com.ibm.JikesRVM.classloader.VM_ReflectionSupport;
import com.ibm.JikesRVM.classloader.VM_SystemClassLoader;

import com.ibm.JikesRVM.VM_Runtime;
import com.ibm.JikesRVM.VM_UnimplementedError;


/**
 * Library support interface of Jikes RVM
 *
 * @author Julian Dolby
 *
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
    return VM_ReflectionSupport.forName(className,initialize,classLoader);
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
    return VM_ReflectionSupport.getConstructor(this,parameterTypes);
  }                                                                                            
  public Constructor[] getConstructors() throws SecurityException {
    return VM_ReflectionSupport.getConstructors(this);
  }

  public Class[] getDeclaredClasses() throws SecurityException {
    return VM_ReflectionSupport.getDeclaredClasses(this);
  }

  public Constructor getDeclaredConstructor(Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
    return VM_ReflectionSupport.getDeclaredConstructor(this,parameterTypes);
  }

  public Constructor[] getDeclaredConstructors() throws SecurityException {
    return VM_ReflectionSupport.getDeclaredConstructors(this);
  }

  public Field getDeclaredField(String name) throws NoSuchFieldException, SecurityException {
    return VM_ReflectionSupport.getDeclaredField(this,name);
  }

  public Field[] getDeclaredFields() throws SecurityException {
    return VM_ReflectionSupport.getDeclaredFields(this);
  }

  public Method getDeclaredMethod(String name, Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
    return VM_ReflectionSupport.getDeclaredMethod(this,name,parameterTypes);
  }

  public Method[] getDeclaredMethods() throws SecurityException {
    return VM_ReflectionSupport.getDeclaredMethods(this);
  }

  public Class getDeclaringClass() {
    return VM_ReflectionSupport.getDeclaringClass(this);
  }

  public Field getField(String name) throws NoSuchFieldException, SecurityException {
    return VM_ReflectionSupport.getField(this,name);
  }

  public Field[] getFields() throws SecurityException {
    return VM_ReflectionSupport.getFields(this);
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
    return VM_ReflectionSupport.getMethod(this,name,parameterTypes);
  }

  public Method[] getMethods() throws SecurityException {
    return VM_ReflectionSupport.getMethods(this);
  }

  public int getModifiers() {
    return VM_ReflectionSupport.getModifiers(this);
  }

  public String getName() {
    return VM_ReflectionSupport.getName(this);
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
    return VM_ReflectionSupport.newInstance(this);
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
}
