/*
 * Copyright IBM Corp 2002
 */
package java.lang;

import java.io.InputStream;
import java.security.*;
import java.lang.reflect.*;
import java.net.URL;
import java.util.Vector;
import java.util.HashMap;
import com.ibm.JikesRVM.VM_Type;
import com.ibm.JikesRVM.librarySupport.ClassLoaderSupport;
import com.ibm.JikesRVM.librarySupport.ReflectionSupport;
import com.ibm.JikesRVM.librarySupport.UnimplementedError;

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
     * This field holds the protection domain of this clas
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
	UnimplementedError.unimplemented("Class.getDeclaringClass");
    }
   
    public static Class forName(String typeName) throws ClassNotFoundException {
	return forName(typeName, true, ClassLoaderSupport.getClassLoaderFromStackFrame(1));
    }
    
    public static Class forName(String className, boolean initialize, ClassLoader classLoader) throws ClassNotFoundException {
	return ReflectionSupport.forName(className,initialize,classLoader);
    }

    public ClassLoader getClassLoader() {
	return ClassLoaderSupport.getClassLoader(this);
    }

    public Class getComponentType() {
	return ReflectionSupport.getComponentType(this);
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
	return ReflectionSupport.getConstructor(this,parameterTypes);
    }                                                                                            
    public Constructor[] getConstructors() throws SecurityException {
	return ReflectionSupport.getConstructors(this);
    }

    public Class[] getDeclaredClasses() throws SecurityException {
	return ReflectionSupport.getDeclaredClasses(this);
    }

    public Constructor getDeclaredConstructor(Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
	return ReflectionSupport.getDeclaredConstructor(this,parameterTypes);
    }

    public Constructor[] getDeclaredConstructors() throws SecurityException {
	return ReflectionSupport.getDeclaredConstructors(this);
    }

    public Field getDeclaredField(String name) throws NoSuchFieldException, SecurityException {
	return ReflectionSupport.getDeclaredField(this,name);
    }

    public Field[] getDeclaredFields() throws SecurityException {
	return ReflectionSupport.getDeclaredFields(this);
    }

    public Method getDeclaredMethod(String name, Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
	return ReflectionSupport.getDeclaredMethod(this,name,parameterTypes);
    }

    public Method[] getDeclaredMethods() throws SecurityException {
	return ReflectionSupport.getDeclaredMethods(this);
    }

    public Class getDeclaringClass() {
	UnimplementedError.unimplemented("Class.getDeclaringClass");
	return null;
    }

    public Field getField(String name) throws NoSuchFieldException, SecurityException {
	return ReflectionSupport.getField(this,name);
    }

    public Field[] getFields() throws SecurityException {
	return ReflectionSupport.getFields(this);
    }

    public Class[] getInterfaces () {
	return ReflectionSupport.getInterfaces(this);
    }

    public Method getMethod(String name, Class parameterTypes[]) throws NoSuchMethodException, SecurityException {
	return ReflectionSupport.getMethod(this,name,parameterTypes);
    }

    public Method[] getMethods() throws SecurityException {
	return ReflectionSupport.getMethods(this);
    }

    public int getModifiers() {
	return ReflectionSupport.getModifiers(this);
    }

    public String getName() {
	return ReflectionSupport.getName(this);
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
	if (loader == ClassLoaderSupport.getSystemClassLoader())
	    return ClassLoader.getSystemResource(this.toResourceName(resName));
	else
	    return loader.getResource(this.toResourceName(resName));
    }

    public InputStream getResourceAsStream(String resName) {
	ClassLoader loader = this.getClassLoader();
	if (loader == ClassLoaderSupport.getSystemClassLoader())
	    return ClassLoader.getSystemResourceAsStream(this.toResourceName(resName));
	else
	    return loader.getResourceAsStream(this.toResourceName(resName));
    }

    public Object[] getSigners() {
	return null;
    }

    public Class getSuperclass () {
	return ReflectionSupport.getSuperclass(this);
    }
    
    public boolean isArray() {
	return ReflectionSupport.isArray(this);     
    }

    public boolean isAssignableFrom(Class cls) {
	return ReflectionSupport.isAssignableFrom(this,cls);
    }

    public boolean isInstance(Object object) {
	return ReflectionSupport.isInstance(this,object);
    }

    public boolean isInterface() {
	return ReflectionSupport.isInterface(this);
    }

    public boolean isPrimitive() {
	return ReflectionSupport.isPrimitive(this);
    }

    public Object newInstance() throws IllegalAccessException, InstantiationException {
	return ReflectionSupport.newInstance(this);
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
	return ReflectionSupport.classToString(this);
    }

    public Package getPackage() {
	return new Package(getPackageName(), "", "", "", "", "", "", null);
    }
}
