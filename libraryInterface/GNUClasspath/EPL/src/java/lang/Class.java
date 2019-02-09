/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package java.lang;

import java.io.InputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.JikesRVMSupport;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.URL;
import java.security.AccessController;
import java.security.AllPermission;
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.jikesrvm.classlibrary.JavaLangSupport;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.BootstrapClassLoader;
import org.jikesrvm.classloader.MethodReference;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMClassLoader;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.runtime.Callbacks;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.runtime.RuntimeEntrypoints;
import org.jikesrvm.runtime.StackBrowser;
import org.vmmagic.pragma.Inline;
import org.vmmagic.pragma.NoInline;
import org.vmmagic.pragma.Pure;

/**
 * Implementation of java.lang.Class for JikesRVM with GNU Classpath.
 *
 * By convention, order methods in the same order
 * as they appear in the method summary list of Sun's 1.4 Javadoc API.
 */
public final class Class<T> implements Serializable, Type, AnnotatedElement, GenericDeclaration {
  private static final class StaticData {
    static final ProtectionDomain unknownProtectionDomain;

    static {
      Permissions permissions = new Permissions();
      permissions.add(new AllPermission());
      unknownProtectionDomain = new ProtectionDomain(null, permissions);
    }
  }

  static final long serialVersionUID = 3206093459760846163L;

  /**
   * This field holds the RVMType object for this class.
   */
  final RVMType type;

  /**
   * This field holds the protection domain of this class.
   */
  ProtectionDomain pd;

  /**
   * The signers of this class
   */
  Object[] signers;

  /**
   * Cached default constructor value
   */
  RVMMethod defaultConstructor;

  /**
   * Prevents this class from being instantiated, except by the
   * create method in this class.
   */
  private Class(RVMType type) {
    this.type = type;
  }

  /**
   * Create a java.lang.Class corresponding to a given RVMType
   */
  static <T> Class<T> create(RVMType type) {
    Class<T> c = new Class<T>(type);
    return c;
  }

  void setSigners(Object[] signers) {
    this.signers = signers;
  }

  public boolean desiredAssertionStatus() {
    return type.getDesiredAssertionStatus();
  }

  @Pure
  public int getModifiers() {
    return JavaLangSupport.getModifiersFromRvmType(type);
  }

  @Pure
  public String getName() {
    return type.toString();
  }

  public Package getPackage() {
    ClassLoader cl = type.getClassLoader();
    return cl.getPackage(getPackageName());
  }

  public ProtectionDomain getProtectionDomain() {
    SecurityManager security = System.getSecurityManager();
    if (security != null) {
      security.checkPermission(new RuntimePermission("getProtectionDomain"));
    }
    return pd == null ? StaticData.unknownProtectionDomain : pd;
  }

  void setProtectionDomain(ProtectionDomain protectionDomain) {
    pd = protectionDomain;
  }

  public URL getResource(String resName) {
    throwNPEWhenNameIsNull(resName);
    ClassLoader loader = type.getClassLoader();
    if (loader == BootstrapClassLoader.getBootstrapClassLoader())
      return ClassLoader.getSystemResource(toResourceName(resName));
    else
      return loader.getResource(toResourceName(resName));
  }

  public InputStream getResourceAsStream(String resName) {
    ClassLoader loader = type.getClassLoader();
    if (loader == BootstrapClassLoader.getBootstrapClassLoader())
      return ClassLoader.getSystemResourceAsStream(toResourceName(resName));
    else
      return loader.getResourceAsStream(toResourceName(resName));
  }

  public Object[] getSigners() {
    if (signers == null) {
      return null;
    } else {
      return signers.clone();
    }
  }

  @SuppressWarnings("unchecked")
  @Pure
  public Class<? super T> getSuperclass() {
    if (type.isArrayType()) {
      return Object.class;
    } else if (type.isClassType()) {
      RVMClass myClass = type.asClass();
      if (myClass.isInterface()) return null;
      RVMType supe = myClass.getSuperClass();
      return supe == null ? null : (Class<? super T>) supe.getClassForType();
    } else {
      return null;
    }
  }

  @Pure
  public boolean isAnnotation() {
    return type.isClassType() && type.asClass().isAnnotation();
  }

  @Pure
  public boolean isArray() {
    return type.isArrayType();
  }

  @Pure
  public boolean isAssignableFrom(Class<?> cls) {
    return type.isAssignableFrom(cls.type);
  }

  @Pure
  public boolean isInstance(Object object) {
    if (object == null) return false;
    if (isPrimitive())  return false;
    return isAssignableFrom(object.getClass());
  }

  @Pure
  public boolean isInterface() {
    return type.isClassType() && type.asClass().isInterface();
  }

  @Pure
  public boolean isPrimitive() {
    return type.isPrimitiveType();
  }

  @Pure
  public boolean isSynthetic() {
    return type.isClassType() && type.asClass().isSynthetic();
  }

  @Pure
  public boolean isAnonymousClass() {
    if (type.isClassType()) {
      return type.asClass().isAnonymousClass();
    } else {
      return false;
    }
  }

  @Pure
  public boolean isLocalClass() {
    if (type.isClassType()) {
      return type.asClass().isLocalClass();
    } else {
      return false;
    }
  }

  @Pure
  public boolean isMemberClass() {
    if (type.isClassType()) {
      return type.asClass().isMemberClass();
    } else {
      return false;
    }
  }

  /**
   * Utility method for use by classes in this package.
   */
  static void setAccessible(final AccessibleObject obj) {
    AccessController.doPrivileged(new PrivilegedAction<Object>() {
        @Override
        public Object run() {
            obj.setAccessible(true);
            return null;
          }
      });
  }

  public Type[] getGenericInterfaces()  {
    if (type.isPrimitiveType() || type.isUnboxedType()) {
      return new Type[0];
    } else if (type.isArrayType()) {
      // arrays implement JavaLangSerializable & JavaLangCloneable
      return new Class[] { RVMType.JavaLangCloneableType.getClassForType(),
                           RVMType.JavaIoSerializableType.getClassForType()};
    } else {
      RVMClass klass = type.asClass();
      Atom sig = klass.getSignature();
      if (sig == null) {
        return getInterfaces();
      } else {
        return JikesRVMHelpers.getInterfaceTypesFromSignature(this, sig);
      }
    }
  }

  public Type getGenericSuperclass() {
    if (type.isArrayType()) {
      return Object.class;
     } else if (type.isPrimitiveType() || type.isUnboxedType() ||
               (type.isClassType() && type.asClass().isInterface()) ||
               this == Object.class) {
      return null;
     } else {
       RVMClass klass = type.asClass();
       Atom sig = klass.getSignature();
       if (sig == null) {
         return getSuperclass();
       } else {
         return JikesRVMHelpers.getSuperclassType(this, sig);
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public TypeVariable<Class<T>>[] getTypeParameters() {
    if (!type.isClassType()) {
      return new TypeVariable[0];
    } else {
      RVMClass klass = type.asClass();
      Atom sig = klass.getSignature();
      if (sig == null) {
        return new TypeVariable[0];
      } else {
        return JikesRVMHelpers.getTypeParameters(this, sig);
      }
    }
  }

  @Pure
  public String getSimpleName() {
    if (type.isArrayType()) {
      return getComponentType().getSimpleName() + "[]";
    } else {
      String fullName = getName();
      return fullName.substring(fullName.lastIndexOf('.') + 1);
    }
  }

  @Pure
  public String getCanonicalName() {
    if (type.isArrayType()) {
      String componentName = getComponentType().getCanonicalName();
      if (componentName != null)
        return componentName + "[]";
    }
    if (isMemberClass()) {
      String memberName = getDeclaringClass().getCanonicalName();
      if (memberName != null)
        return memberName + "." + getSimpleName();
    }
    if (isLocalClass() || isAnonymousClass())
      return null;
    return getName();
  }

  @Override
  @Pure
  public String toString() {
    String name = getName();
    if (isPrimitive()) {
      return name;
    } else if (isInterface()) {
      return "interface " + name;
    } else {
      return "class " + name;
    }
  }

  @SuppressWarnings("unchecked")
  public T cast(Object obj) {
    if (obj != null && ! isInstance(obj))
      throw new ClassCastException();
    return (T)obj;
  }


  @SuppressWarnings("unchecked")
  public <U> Class<? extends U> asSubclass(Class<U> klass) {
    if (! klass.isAssignableFrom(this))
      throw new ClassCastException();
    return (Class<? extends U>) this;
  }

  public Class<?> getDeclaringClass() {
    if (!type.isClassType()) return null;
    TypeReference dc = type.asClass().getDeclaringClass();
    if (dc == null) return null;
    return dc.resolve().getClassForType();
  }

  public Class<?>[] getClasses() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType()) return new Class[0];

    ArrayList<Class<?>> publicClasses = new ArrayList<Class<?>>();
    for (Class<?> c = this; c != null; c = c.getSuperclass()) {
      c.checkMemberAccess(Member.PUBLIC);
      TypeReference[] declaredClasses = c.type.asClass().getDeclaredClasses();
      if (declaredClasses != null) {
        for (TypeReference declaredClass : declaredClasses) {
          if (declaredClass != null) {
            RVMClass dc = declaredClass.resolve().asClass();
            if (dc.isPublic()) {
              publicClasses.add(dc.getClassForType());
            }
          }
        }
      }
    }
    Class<?>[] result = new Class[publicClasses.size()];
    result = publicClasses.toArray(result);
    return result;
  }

  public ClassLoader getClassLoader() {
    SecurityManager security = System.getSecurityManager();
    if (security != null) {
      ClassLoader parentCL = RVMClass.getClassLoaderFromStackFrame(1);
      if (parentCL != null) {
        security.checkPermission(new RuntimePermission("getClassLoader"));
      }
    }
    ClassLoader cl = type.getClassLoader();
    return cl == BootstrapClassLoader.getBootstrapClassLoader() ? null : cl;
  }

  public Class<?> getComponentType() {
    return type.isArrayType() ? type.asArray().getElementType().getClassForType() : null;
  }

  public Class<?>[] getDeclaredClasses() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Class[0];

    // Get array of declared classes from RVMClass object
    RVMClass cls = type.asClass();
    TypeReference[] declaredClasses = cls.getDeclaredClasses();

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
    Class<?>[] result = new Class[count];
    count = 0;
    for (int i = 0; i < length; ++i) {
      if (declaredClasses[i] != null) {
        result[count++] = declaredClasses[i].resolve().getClassForType();
      }
    }

    return result;
  }

  public Class<?>[] getInterfaces() {
    if (type.isArrayType()) {
      // arrays implement JavaLangSerializable & JavaLangCloneable
      return new Class[] { RVMType.JavaLangCloneableType.getClassForType(),
                           RVMType.JavaIoSerializableType.getClassForType() };
    } else if (type.isClassType()) {
      RVMClass[] interfaces  = type.asClass().getDeclaredInterfaces();
      Class<?>[] jinterfaces = new Class[interfaces.length];
      for (int i = 0; i != interfaces.length; i++)
        jinterfaces[i] = interfaces[i].getClassForType();
      return jinterfaces;
    } else {
      return new Class[0];
    }
  }

  // --- Utilities ---

  /**
   * Utility for security checks
   */
  private void checkMemberAccess(int type) {
    SecurityManager security = System.getSecurityManager();
    if (security != null) {
      security.checkMemberAccess(this, type);
      String packageName = getPackageName();
      if (!packageName.isEmpty()) {
        security.checkPackageAccess(packageName);
      }
    }
  }

  /**
   * Compare parameter lists for agreement.
   */
  private boolean parametersMatch(TypeReference[] lhs, Class<?>[] rhs) {
    if (rhs == null) return lhs.length == 0;
    if (lhs.length != rhs.length) return false;

    for (int i = 0, n = lhs.length; i < n; ++i) {
      if (rhs[i] == null) return false;
      if (lhs[i].resolve() != rhs[i].type) {
        return false;
      }
    }
    return true;
  }

  @Pure
  private String getPackageName() {
    String name = getName();
    int index = name.lastIndexOf('.');
    if (index >= 0) return name.substring(0, index);
    return "";
  }

  @Pure
  private String toResourceName(String resName) {
    // Turn package name into a directory path
    if (resName.charAt(0) == '/') return resName.substring(1);

    String qualifiedClassName = getName();
    int classIndex = qualifiedClassName.lastIndexOf('.');
    if (classIndex == -1) return resName; // from a default package
    return qualifiedClassName.substring(0, classIndex + 1).replace('.', '/') + resName;
  }

  @NoInline
  private void throwNoSuchMethodException(String name, Class<?>... parameterTypes) throws NoSuchMethodException {
    StringBuilder typeString;
    if (parameterTypes == null || parameterTypes.length == 0) {
      typeString = new StringBuilder("()");
    } else {
      typeString = new StringBuilder("(");
      for (int i = 0; i < parameterTypes.length - 1; i++) {
        Class<?> c = parameterTypes[i];
        typeString.append(c.toString());
        typeString.append(", ");
      }
      typeString.append(parameterTypes[parameterTypes.length - 1]);
      typeString.append(')');
    }
    throw new NoSuchMethodException(name + typeString);
  }

  @NoInline
  private void throwNoSuchFieldException(String name) throws NoSuchFieldException {
    throw new NoSuchFieldException(name);
  }

  // --- Harmony --

  // TODO: Harmony
  ClassLoader getClassLoaderImpl() {
    return null;
  }

  // TODO: Harmony
  static Class<?>[] getStackClasses(int maxDepth, boolean stopAtPrivileged) {
    StackBrowser browser = new StackBrowser();
    if (maxDepth == -1) {
      browser.init();
      maxDepth = browser.countFrames();
    }
    if (maxDepth == 0) return new Class[0];
    else if (maxDepth < 0) {
      throw new Error("Unexpected negative call stack size" + maxDepth);
    }
    Class<?>[] result = new Class[maxDepth];
    browser.init();
    for (int i = 0; i < maxDepth; i++) {
      result[i] = browser.getCurrentClass().getClassForType();
      browser.up();
    }
    return result;
  }

  // --- AnnotatedElement interface ---

  @Override
  public Annotation[] getDeclaredAnnotations() {
    return type.getDeclaredAnnotations();
  }

  @Override
  public Annotation[] getAnnotations() {
    return type.getAnnotations();
  }

  @Override
  public <U extends Annotation> U getAnnotation(Class<U> annotationClass) {
    return type.getAnnotation(annotationClass);
  }

  @Override
  public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
    return type.isAnnotationPresent(annotationClass);
  }

  // --- Enclosing support ---

  public Class<?> getEnclosingClass() {
    if (type.isClassType()) {
      TypeReference enclosingClass = type.asClass().getEnclosingClass();
      if (enclosingClass != null) {
        return enclosingClass.resolve().getClassForType();
      } else {
        return null;
      }
    } else {
      return null;
    }
  }

  public Constructor<?> getEnclosingConstructor() {
    if (!(isAnonymousClass() || isLocalClass())) {
      return null;
    }

    MethodReference enclosingMethodRef = type.asClass().getEnclosingMethod();
    if (enclosingMethodRef == null) {
      return null;
    }
    RVMMethod method = enclosingMethodRef.resolve();
    if (!method.isObjectInitializer()) {
      return null;
    }
    return JikesRVMSupport.createConstructor(method);
  }

  public Method getEnclosingMethod() {
    if (!(isAnonymousClass() || isLocalClass())) {
      return null;
    }

    MethodReference enclosingMethodRef = type.asClass().getEnclosingMethod();
    if (enclosingMethodRef == null) {
      return null;
    } else {
      RVMMethod method = enclosingMethodRef.resolve();
      if (method.isObjectInitializer() || method.isClassInitializer()) {
        return null;
      }
      return JikesRVMSupport.createMethod(method);
    }
  }

  // --- Enumeration support ---

  @SuppressWarnings("unchecked")
  public T[] getEnumConstants() {
    if (isEnum()) {
      try {
        return (T[])getMethod("values", new Class[0]).invoke(null, new Object[0]);
      } catch (NoSuchMethodException exception) {
        throw new Error("Enum lacks values() method");
      } catch (IllegalAccessException exception) {
        throw new Error("Unable to access Enum class");
      } catch (InvocationTargetException exception) {
        throw new RuntimeException("The values method threw an exception",
                                   exception);
      }
    } else {
      return null;
    }
  }


  @Pure
  public boolean isEnum() {
    if (type.isClassType()) {
      return type.asClass().isEnum();
    } else {
      return false;
    }
  }

  // --- Constructors ---

  @Pure
  private RVMMethod getDefaultConstructor() {
    if (this.defaultConstructor == null) {
      RVMMethod defaultConstructor = null;
      RVMMethod[] methods = type.asClass().getConstructorMethods();
      for (RVMMethod method : methods) {
        if (method.getParameterTypes().length == 0) {
          defaultConstructor = method;
          break;
        }
      }
      this.defaultConstructor = defaultConstructor;
    }
    return this.defaultConstructor;
  }

  public Constructor<T> getConstructor(Class<?>... parameterTypes) throws NoSuchMethodException, SecurityException {
    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType())
      throwNoSuchMethodException("<init>", parameterTypes);

    RVMMethod answer = null;
    if (parameterTypes == null || parameterTypes.length == 0) {
      answer = getDefaultConstructor();
    } else {
      RVMMethod[] methods = type.asClass().getConstructorMethods();
      for (RVMMethod method : methods) {
        if (method.isPublic() &&
            parametersMatch(method.getParameterTypes(), parameterTypes)) {
          answer = method;
          break;
        }
      }
    }
    if (answer == null) {
      throwNoSuchMethodException("<init>", parameterTypes);
    }
    return JikesRVMSupport.createConstructor(answer);
  }

  public Constructor<T> getDeclaredConstructor(Class<?>... parameterTypes) throws NoSuchMethodException, SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType())
      throwNoSuchMethodException("<init>", parameterTypes);

    RVMMethod answer = null;
    if (parameterTypes == null || parameterTypes.length == 0) {
      answer = getDefaultConstructor();
    } else {
      RVMMethod[] methods = type.asClass().getConstructorMethods();
      for (RVMMethod method : methods) {
        if (parametersMatch(method.getParameterTypes(), parameterTypes)) {
          answer = method;
          break;
        }
      }
    }
    if (answer == null) {
      throwNoSuchMethodException("<init>", parameterTypes);
    }
    return JikesRVMSupport.createConstructor(answer);
  }

  public Constructor<?>[] getConstructors() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType()) return new Constructor[0];

    RVMMethod[] methods = type.asClass().getConstructorMethods();
    ArrayList<Constructor<T>> coll = new ArrayList<Constructor<T>>(methods.length);
    for (RVMMethod method : methods) {
      if (method.isPublic()) {
        @SuppressWarnings("unchecked")
        Constructor<T> x = (Constructor<T>)JikesRVMSupport.createConstructor(method);
        coll.add(x);
      }
    }
    return coll.toArray(new Constructor[coll.size()]);
  }

  public Constructor<?>[] getDeclaredConstructors() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Constructor[0];

    RVMMethod[] methods = type.asClass().getConstructorMethods();
    Constructor<?>[] ans = new Constructor[methods.length];
    for (int i = 0; i < methods.length; i++) {
      ans[i] = JikesRVMSupport.createConstructor(methods[i]);
    }
    return ans;
  }

  // --- ForName ---

  @Inline
  public static Class<?> forName(String typeName) throws ClassNotFoundException {
    throwNPEWhenNameIsNull(typeName);
    ClassLoader parentCL = RVMClass.getClassLoaderFromStackFrame(1);
    return forNameInternal(typeName, true, parentCL);
  }

  public static Class<?> forName(String className, boolean initialize, ClassLoader classLoader)
    throws ClassNotFoundException,
           LinkageError,
           ExceptionInInitializerError {
    throwNPEWhenNameIsNull(className);
    if (classLoader == null) {
      SecurityManager security = System.getSecurityManager();
      if (security != null) {
        ClassLoader parentCL = RVMClass.getClassLoaderFromStackFrame(1);
        if (parentCL != null) {
          try {
            security.checkPermission(new RuntimePermission("getClassLoader"));
          } catch (SecurityException e) {
            throw new ClassNotFoundException("Security exception when" +
                                             " trying to get a classloader so we can load the" +
                                             " class named \"" + className + "\"", e);
          }
        }
      }
      classLoader = BootstrapClassLoader.getBootstrapClassLoader();
    }
    return forNameInternal(className, initialize, classLoader);
  }

  private static Class<?> forNameInternal(String className, boolean initialize, ClassLoader classLoader)
      throws ClassNotFoundException, LinkageError, ExceptionInInitializerError {
    if (className == null) {
      throw new ClassNotFoundException("Cannot load a class with a name of null");
    }

    try {
      if (className.startsWith("[")) {
        if (!JavaLangSupport.validArrayDescriptor(className)) {
          throw new ClassNotFoundException(className);
        }
      }
      Atom descriptor = Atom.findOrCreateAsciiAtom(className.replace('.','/')).descriptorFromClassName();
      TypeReference tRef = TypeReference.findOrCreate(classLoader, descriptor);
      RVMType ans = tRef.resolve();
      Callbacks.notifyForName(ans);
      if (initialize && !ans.isInitialized()) {
        ans.prepareForFirstUse();
      }
      return ans.getClassForType();
    } catch (NoClassDefFoundError ncdfe) {
      Throwable cause2 = ncdfe.getCause();
      ClassNotFoundException cnf;
      // If we get a NCDFE that was caused by a CNFE, throw the original CNFE.
      if (cause2 instanceof ClassNotFoundException)
        cnf = (ClassNotFoundException) cause2;
      else
        cnf = new ClassNotFoundException(className, ncdfe);
      throw cnf;
    }
  }

  // --- newInstance ---

  @Inline(value = Inline.When.ArgumentsAreConstant, arguments = {0})
  public T newInstance() throws IllegalAccessException, InstantiationException,
    ExceptionInInitializerError, SecurityException {

    // Basic checks
    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType())
      throw new InstantiationException();

    RVMClass cls = type.asClass();

    if (cls.isAbstract() || cls.isInterface())
      throw new InstantiationException();

    // Ensure that the class is initialized
    if (!cls.isInitialized()) {
      RuntimeEntrypoints.initializeClassForDynamicLink(cls);
    }

    // Find the defaultConstructor
    RVMMethod defaultConstructor = getDefaultConstructor();
    if (defaultConstructor == null)
      throw new InstantiationException();

    // Check that caller is allowed to access it
    if (!defaultConstructor.isPublic()) {
      RVMClass accessingClass = RVMClass.getClassFromStackFrame(1);
      JavaLangSupport.checkAccess(defaultConstructor, accessingClass);
    }

    // Allocate an uninitialized instance;
    @SuppressWarnings("unchecked") // yes, we're giving an anonymous object a type.
    T obj = (T)RuntimeEntrypoints.resolvedNewScalar(cls);

    // Run the default constructor on the it.
    Reflection.invoke(defaultConstructor, null, obj, null, true);

    return obj;
  }

  // --- Methods ---

  @Pure
  private RVMMethod getMethodInternal1(Atom aName, Class<?>... parameterTypes) {
    RVMMethod answer = null;
    for (RVMClass current = type.asClass(); current != null && answer == null; current = current.getSuperClass()) {
      RVMMethod[] methods = current.getDeclaredMethods();
      for (RVMMethod meth : methods) {
        if (meth.getName() == aName && meth.isPublic() &&
            parametersMatch(meth.getParameterTypes(), parameterTypes)) {
          if (answer == null) {
            answer = meth;
          } else {
            RVMMethod m2 = meth;
            if (answer.getReturnType().resolve().isAssignableFrom(m2.getReturnType().resolve())) {
              answer = m2;
            }
          }
        }
      }
    }
    return answer;
  }

  @Pure
  private RVMMethod getMethodInternal2(Atom aName, Class<?>... parameterTypes) {
    RVMMethod answer = null;
    RVMMethod[] methods = type.asClass().getVirtualMethods();
    for (RVMMethod meth : methods) {
      if (meth.getName() == aName && meth.isPublic() &&
          parametersMatch(meth.getParameterTypes(), parameterTypes)) {
        if (answer == null) {
          answer = meth;
        } else {
          RVMMethod m2 = meth;
          if (answer.getReturnType().resolve().isAssignableFrom(m2.getReturnType().resolve())) {
            answer = m2;
          }
        }
      }
    }
    return answer;
  }

  public Method getMethod(String name, Class<?>... parameterTypes) throws NoSuchMethodException, SecurityException {
    throwNPEWhenNameIsNull(name);

    checkMemberAccess(Member.PUBLIC);

    if (!type.isClassType()) throwNoSuchMethodException(name, parameterTypes);

    Atom aName = Atom.findOrCreateUnicodeAtom(name);
    if (aName == RVMClassLoader.StandardClassInitializerMethodName ||
        aName == RVMClassLoader.StandardObjectInitializerMethodName) {
      // <init> and <clinit> are not methods.
      throwNoSuchMethodException(name, parameterTypes);
    }

    // (1) Scan the declared public methods of this class and each of its superclasses
    RVMMethod answer = getMethodInternal1(aName, parameterTypes);
    if (answer == null) {
      // (2) Now we need to consider methods inherited from interfaces.
      //     Because we inject the requisite Miranda methods, we can do this simply
      //     by looking at this class's virtual methods instead of searching interface hierarchies.
      answer = getMethodInternal2(aName, parameterTypes);
    }
    if (answer == null) {
      throwNoSuchMethodException(name, parameterTypes);
    }
    return JikesRVMSupport.createMethod(answer);
  }

  private static void throwNPEWhenNameIsNull(String name) {
    if (name == null) {
      throw new NullPointerException("Name parameter must not be null (but was)!");
    }
  }

  public Method getDeclaredMethod(String name, Class<?>... parameterTypes)
  throws NoSuchMethodException, SecurityException {
    throwNPEWhenNameIsNull(name);

    checkMemberAccess(Member.DECLARED);

    if (!type.isClassType()) throwNoSuchMethodException(name, parameterTypes);

    if (name == null) {
      throwNoSuchMethodException(name, parameterTypes);
    }
    Atom aName = Atom.findOrCreateUnicodeAtom(name);
    if (aName == RVMClassLoader.StandardClassInitializerMethodName ||
        aName == RVMClassLoader.StandardObjectInitializerMethodName) {
      // <init> and <clinit> are not methods.
      throwNoSuchMethodException(name, parameterTypes);
    }

    RVMMethod[] methods = type.asClass().getDeclaredMethods();
    RVMMethod answer = null;
    for (RVMMethod meth : methods) {
      if (meth.getName() == aName &&
          parametersMatch(meth.getParameterTypes(), parameterTypes)) {
        if (answer == null) {
          answer = meth;
        } else {
          RVMMethod m2 = meth;
          if (answer.getReturnType().resolve().isAssignableFrom(m2.getReturnType().resolve())) {
            answer = m2;
          }
        }
      }
    }
    if (answer == null) {
      throwNoSuchMethodException(name, parameterTypes);
    }
    return JikesRVMSupport.createMethod(answer);
  }

  public Method[] getDeclaredMethods() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Method[0];

    RVMMethod[] methods = type.asClass().getDeclaredMethods();
    ArrayList<Method> coll = new ArrayList<Method>(methods.length);
    for (RVMMethod meth : methods) {
      if (!meth.isClassInitializer() && !meth.isObjectInitializer()) {
        coll.add(JikesRVMSupport.createMethod(meth));
      }
    }
    return coll.toArray(new Method[coll.size()]);
  }

  public Method[] getMethods() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);

    RVMMethod[] static_methods = type.getStaticMethods();
    RVMMethod[] virtual_methods = type.getVirtualMethods();
    HashSet<Method> coll = new HashSet<Method>(static_methods.length +
        virtual_methods.length);
    for (RVMMethod meth : static_methods) {
      if (meth.isPublic()) {
        coll.add(JikesRVMSupport.createMethod(meth));
      }
    }
    for (RVMMethod meth : virtual_methods) {
      if (meth.isPublic()) {
        coll.add(JikesRVMSupport.createMethod(meth));
      }
    }
    // The Java API says that duplicate versions are returned if multiple
    // versions of a method are defined by a class. This only applies to
    // abstract classes and interfaces because normal classes always have
    // exactly one definition for a given signature-name pair.
    RVMClass thisClass = type.asClass();
    boolean isAbstract = thisClass.isAbstract();
    if (isInterface() || isAbstract) {
      // For each virtual method , search all superinterfaces
      // to find all declarations that aren't shadowed by superinterfaces and
      // add those to the set of methods.
      HashSet<Method> methods = new HashSet<Method>();
      for (RVMMethod m : virtual_methods) {
        Atom name = m.getName();
        Atom desc = m.getDescriptor();
        if (isAbstract && !m.getDeclaringClass().isInterface()) {
          // If the method is declared by a class (and not an interface),
          // only that declaration is relevant. Declarations that may come
          // from interfaces are overridden by the class' definition. That
          // definition is already in the virtual methods. Therefore, it's
          // unnecessary to search for additional declarations for that
          // method.
          continue;
        }
        collectDeclarations(thisClass, name, desc, methods);
      }
      coll.addAll(methods);
    }

    return coll.toArray(new Method[coll.size()]);
  }

  private static void collectDeclarations(RVMClass i, Atom name, Atom desc, Set<Method> methods) {
    for (RVMMethod declared : i.getDeclaredMethods()) {
      if (declared.getName() == name && declared.getDescriptor() == desc) {
        methods.add(JikesRVMSupport.createMethod(declared));
        return;
      }
    }
    for (RVMClass declardInterface : i.getDeclaredInterfaces()) {
      collectDeclarations(declardInterface, name, desc, methods);
    }
  }

  // --- Fields ---

  @Pure
  private RVMField getFieldInternal(Atom name) {
    RVMClass ctype = type.asClass();
    // (1) Check my public declared fields
    RVMField[] fields = ctype.getDeclaredFields();
    for (RVMField field : fields) {
      if (field.isPublic() && field.getName() == name) {
        return field;
      }
    }

    // (2) Check superinterfaces
    RVMClass[] interfaces = ctype.getDeclaredInterfaces();
    for (RVMClass anInterface : interfaces) {
      RVMField ans = anInterface.getClassForType().getFieldInternal(name);
      if (ans != null) return ans;
    }

    // (3) Check superclass (if I have one).
    if (ctype.getSuperClass() != null) {
      return ctype.getSuperClass().getClassForType().getFieldInternal(name);
    }

    return null;
  }

  public Field getField(String name) throws NoSuchFieldException, SecurityException {
    throwNPEWhenNameIsNull(name);

    checkMemberAccess(Member.PUBLIC);
    if (!type.isClassType()) throw new NoSuchFieldException();

    Atom aName = Atom.findUnicodeAtom(name);
    if (aName == null) throwNoSuchFieldException(name);

    RVMField answer = getFieldInternal(aName);

    if (answer == null) {
      throwNoSuchFieldException(name);
    }
    return JikesRVMSupport.createField(answer);
  }

  public Field getDeclaredField(String name)
    throws NoSuchFieldException, SecurityException {
    throwNPEWhenNameIsNull(name);

    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType() || name == null) throwNoSuchFieldException(name);

    Atom aName = Atom.findOrCreateUnicodeAtom(name);
    RVMField answer = type.asClass().findDeclaredField(aName);
    if (answer == null) {
      throwNoSuchFieldException(name);
    }
    return JikesRVMSupport.createField(answer);
  }

  public Field[] getFields() throws SecurityException {
    checkMemberAccess(Member.PUBLIC);

    RVMField[] static_fields = type.getStaticFields();
    RVMField[] instance_fields = type.getInstanceFields();
    ArrayList<Field> coll = new ArrayList<Field>(static_fields.length + instance_fields.length);
    for (RVMField field : static_fields) {
      if (field.isPublic()) {
        coll.add(JikesRVMSupport.createField(field));
      }
    }
    for (RVMField field : instance_fields) {
      if (field.isPublic()) {
        coll.add(JikesRVMSupport.createField(field));
      }
    }

    return coll.toArray(new Field[coll.size()]);
  }

  public Field[] getDeclaredFields() throws SecurityException {
    checkMemberAccess(Member.DECLARED);
    if (!type.isClassType()) return new Field[0];

    RVMField[] fields = type.asClass().getDeclaredFields();
    Field[] ans = new Field[fields.length];
    for (int i = 0; i < fields.length; i++) {
      ans[i] = JikesRVMSupport.createField(fields[i]);
    }
    return ans;
  }
}
