/* -*-coding: iso-8859-1 -*-
 *
 * Copyright © IBM Corp 2004
 *
 * $Id$
 */
package java.lang;

//-#if !RVM_WITH_OWN_JAVA_LANG_CLASS
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.security.ProtectionDomain;

import com.ibm.JikesRVM.VM_Callbacks;
import com.ibm.JikesRVM.VM_Runtime;

import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_Class;
import com.ibm.JikesRVM.classloader.VM_Field;
import com.ibm.JikesRVM.classloader.VM_Type;
import com.ibm.JikesRVM.classloader.VM_TypeReference;
import com.ibm.JikesRVM.classloader.*;


/** Class methods specific to Jikes RVM, for use with GNU Classpath's version
 * of java/lang/Class.java.
 *
 * We also use this structure to hold data specific to Jikes RVM that should
 * be associated with each class.  The declaration for it in GNU Classpath's
 * Class.java is:
 * transient final Object vmdata;
 * 
 *
 * Regrettably, due to the copyright incompatibility between Jikes RVM's CPL
 * and GNU Classpath's GPL + exception, we do not include the Javadoc comments
 * describing these methods here.  Look at the reference implementation
 * instead, in: classpath/vm/reference/java/lang/VMClass.java
 * 
 * @author Steven Augart
 * @date 22 September 2004
 *
 * The code here is modified from the old java.lang.Class implementation,
 * which had the authors:
 *
 * @author John Barton 
 * @author Julian Dolby
 * @author Stephen Fink
 * @author Eugene Gluzberg
 * @author Dave Grove
 */

final class VMClass 
{
  /**
   * Prevents this class from being instantiated, except by the
   * create method in this class.
   */
  private VMClass() {}

  /**
   * This field holds the VM_Type object for this class.
   */
  VM_Type type;


  static boolean isInstance(Class klass, Object object) {
    if (object == null) return false;
    VM_Type type = getVM_Type(klass);
    if (type.isPrimitiveType())  return false;
    VM_Type obType = getVM_Type(object.getClass());
    return isAssignableFrom(type, obType);
  }

  static boolean isAssignableFrom(Class destClass, Class srcClass) {
    VM_Type destType = getVM_Type(destClass);
    VM_Type srcType = getVM_Type(srcClass);
    return isAssignableFrom(destType, srcType);
  }

  private static boolean isAssignableFrom(VM_Type destType, VM_Type srcType) {
    return destType == srcType 
      || VM_Runtime.isAssignableWith(destType, srcType);
  }

  static boolean isInterface(Class klass) {
    VM_Type type = getVM_Type(klass);
    return type.isClassType() && type.asClass().isInterface();
  }

  static boolean isPrimitive(Class klass) {
    VM_Type type = getVM_Type(klass);
    return type.isPrimitiveType();
  }

  static String getName(Class klass) {
    return getVM_Type(klass).toString();
  }

  static Class getSuperclass(Class klass) {
    VM_Type type = getVM_Type(klass);

    return getSuperclass(type);
  }

  static Class getSuperclass(VM_Type type) {
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

  static Class[] getInterfaces(Class klass) {
    VM_Type type = getVM_Type(klass);
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


  static Class getComponentType(Class klass) {
    VM_Type type = getVM_Type(klass);

    return type.isArrayType() 
      ? type.asArray().getElementType().getClassForType() 
      : null;
  }

  static int getModifiers(Class klass) {
    VM_Type type = getVM_Type(klass);

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


  static Class getDeclaringClass(Class klass) {
    VM_Type type = getVM_Type(klass);

    return getDeclaringClass(type);
  }

  static Class getDeclaringClass(VM_Type type) {
    if (!type.isClassType()) return null;
    VM_TypeReference dc = type.asClass().getDeclaringClass();
    if (dc == null) return null;
    return dc.resolve().getClassForType();

  }

  static Class[] getDeclaredClasses(Class klass, boolean publicOnly) {
    VM_Type type = getVM_Type(klass);

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
        result[count++] = declaredClasses[i].resolve().getClassForType();
      }
    }
    
    return result;
  }


  static Field[] getDeclaredFields(Class klass, boolean publicOnly) {
    VM_Type type = getVM_Type(klass);

    VM_Field[] fields = type.asClass().getDeclaredFields();
    Field[] ans = new Field[fields.length];
    for (int i = 0; i < fields.length; i++) {
      ans[i] = java.lang.reflect.JikesRVMSupport.createField(fields[i]);
    }
    return ans;
  }

  static Method[] getDeclaredMethods(Class klass, boolean publicOnly) {
    VM_Type type = getVM_Type(klass);

    /*  XXX Here, we need to add a check for publicOnly.
        Also, must confirm that
        we don't include
        constructors. */
    if (!type.isClassType()) return new Method[0];

    VM_Method[] methods = type.asClass().getDeclaredMethods();
    Collector coll = new Collector(methods.length);
    for (int i = 0; i < methods.length; i++) {
      VM_Method meth = methods[i];
      if (!meth.isClassInitializer() && !meth.isObjectInitializer()) {
        if (publicOnly && ! meth.isPublic())
          continue;

        coll.collect(java.lang.reflect.JikesRVMSupport.createMethod(meth));
      }
    }
    return coll.methodArray();
  }

  static Constructor[] getDeclaredConstructors(Class klass, 
                                               boolean publicOnly) 
  {
    VM_Type type = getVM_Type(klass);

    if (!type.isClassType()) return new Constructor[0];

    VM_Method methods[] = type.asClass().getConstructorMethods();
    Constructor[] ans = new Constructor[methods.length];
    for (int i = 0; i<methods.length; i++) {
      ans[i] = java.lang.reflect.JikesRVMSupport.createConstructor(methods[i]);
    }
    return ans;
  }

  static ClassLoader getClassLoader(Class klass) {
    VM_Type type = getVM_Type(klass);

    ClassLoader cl = type.getClassLoader();
    return cl == VM_SystemClassLoader.getVMClassLoader() ? null : cl;
  }

  /**
   * Classpath comments say: "VM implementors are free to make this method a noop if 
   * the default implementation is acceptable."
   *
   * XXX It is not clear to me at all what the "default implementation" is
   * that is referred to in the GNU Classpath comment above.
   */
  static Class forName(String typeName) throws ClassNotFoundException {
    ClassLoader parentCL = VM_Class.getClassLoaderFromStackFrame(1);
    return forNameInternal(typeName, true, parentCL);
  }


  static boolean isArray(Class klass) {
    VM_Type type = getVM_Type(klass);

    return type.isArrayType();
  }


  static void initialize(Class klass) {
    VM_Type type = getVM_Type(klass);

    initialize(type);
  }

  private static void initialize(VM_Type type) {
    if (!type.isInitialized()) {
      type.resolve();
      type.instantiate();
      type.initialize();
    }
  }

  static Class loadArrayClass(String name, ClassLoader classLoader)
        throws ClassNotFoundException
  {
    return forNameInternal(name, true /* initialize */, classLoader);
  }

  /**
   * This is supposed to throw a checked exception without declaring it.
   */
  static void throwException(Throwable t)
  {
    VM_Runtime.athrow(t);
  }

  /*
   * IMPLEMENTATION below
   */

  /** Get the VM_Type associated with a Jikes RVM Class.
     
     This deliberately has package (default) access -- we need it for
     java.lang.JikesRVMSupport */
  static VM_Type getVM_Type(Class klass) {
    return java.lang.JikesRVMSupport.getTypeForClass(klass);
  }

  /**
   * Create a java.lang.VMClass corresponding to a given VM_Type
   */
  static VMClass create(VM_Type type) {
    VMClass c = new VMClass();
    c.type = type;
    return c;
  }


  private static Class forNameInternal(String className, 
                                       boolean initialize, 
                                       ClassLoader classLoader)
    throws ClassNotFoundException,
           LinkageError,
           ExceptionInInitializerError {
    try {
      if (className.startsWith("[")) {
        if (!validArrayDescriptor(className)) {
          throw new ClassNotFoundException(className);
        }
      }
      VM_Atom descriptor = VM_Atom
        .findOrCreateAsciiAtom(className.replace('.','/'))
        .descriptorFromClassName();
      VM_TypeReference tRef 
        = VM_TypeReference.findOrCreate(classLoader, descriptor);
      VM_Type ans = tRef.resolve();
      VM_Callbacks.notifyForName(ans);
      if (initialize)
        initialize(ans);
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

  private static boolean validArrayDescriptor (String name) {
    int i;
    int length = name.length();

    for (i = 0; i < length; i++)
      if (name.charAt(i) != '[') break;
    if (i == length) return false;      // string of only ['s

    if (i == length - 1) {
      switch (name.charAt(i)) {
      case 'B': return true;    // byte
      case 'C': return true;    // char
      case 'D': return true;    // double
      case 'F': return true;    // float
      case 'I': return true;    // integer
      case 'J': return true;    // long
      case 'S': return true;    // short
      case 'Z': return true;    // boolean
      default:  return false;
      }
    } else if (name.charAt(i) != 'L') {
      return false;    // not a class descriptor
    } else if (name.charAt(length - 1) != ';') {
      return false;     // ditto
    }
    return true;                        // a valid class descriptor
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
//-#endif
