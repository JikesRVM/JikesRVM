/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.classloader;

import java.io.DataInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.Arrays;
import org.jikesrvm.VM;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.util.ImmutableEntryHashMapRVM;
import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.pragma.Pure;
import org.vmmagic.unboxed.Offset;

/**
 * Internal representation of an annotation. We use a proxy class to implement
 * actual annotations {@link RVMClass}.
 */
public final class RVMAnnotation {
  /**
   * The type of the annotation. This is an interface name that the
   * annotation value will implement
   */
  private final TypeReference type;
  /**
   * Members of this annotation
   */
  private final AnnotationMember[] elementValuePairs;
  /**
   * Remembered unique annotations
   */
  private static final ImmutableEntryHashMapRVM<RVMAnnotation, RVMAnnotation>
    uniqueMap = new ImmutableEntryHashMapRVM<RVMAnnotation, RVMAnnotation>();

  /**
   * The concrete annotation represented by this RVMAnnotation
   */
  private Annotation value;

  /** Encoding of when a result cannot be returned */
  private static final Object NO_VALUE = new Object();

  /**
   * Construct a read annotation
   * @param type the name of the type this annotation's value will
   * implement
   * @param elementValuePairs values for the fields in the annotation
   * that override the defaults
   */
  private RVMAnnotation(TypeReference type, AnnotationMember[] elementValuePairs) {
    this.type = type;
    this.elementValuePairs = elementValuePairs;
  }

  /**
   * Read an annotation attribute from the class file
   *
   * @param constantPool from constant pool being loaded
   * @param input the data being read
   */
  static RVMAnnotation readAnnotation(int[] constantPool, DataInputStream input, ClassLoader classLoader)
      throws IOException, ClassNotFoundException {
    TypeReference type;
    // Read type
    int typeIndex = input.readUnsignedShort();
    type = TypeReference.findOrCreate(classLoader, ClassFileReader.getUtf(constantPool, typeIndex));
    // Read values
    int numAnnotationMembers = input.readUnsignedShort();
    AnnotationMember[] elementValuePairs = new AnnotationMember[numAnnotationMembers];
    for (int i = 0; i < numAnnotationMembers; i++) {
      elementValuePairs[i] = AnnotationMember.readAnnotationMember(type, constantPool, input, classLoader);
    }
    // Arrays.sort(elementValuePairs);
    RVMAnnotation result = new RVMAnnotation(type, elementValuePairs);
    RVMAnnotation unique = uniqueMap.get(result);
    if (unique != null) {
      return unique;
    } else {
      uniqueMap.put(result, result);
      return result;
    }
  }

  /**
   * Return the annotation represented by this RVMAnnotation. If this
   * is the first time this annotation has been accessed the subclass
   * of annotation this class represents needs creating.
   * @return the annotation represented
   */
  Annotation getValue() {
    if (value == null) {
      value = createValue();
    }
    return value;
  }

  /**
   * Create an instance of this type of annotation with the values
   * given in the members
   *
   * @return the created annotation
   */
  private Annotation createValue() {
    // Find the annotation then find its implementing class
    final RVMClass annotationInterface = type.resolve().asClass();
    annotationInterface.resolve();
    Class<?> interfaceClass = annotationInterface.getClassForType();
    ClassLoader classLoader = interfaceClass.getClassLoader();
    if (classLoader == null) {
      classLoader = BootstrapClassLoader.getBootstrapClassLoader();
    }
    return (Annotation) Proxy.newProxyInstance(classLoader, new Class[] { interfaceClass },
        new AnnotationFactory());
  }

  /**
   * Read the element_value field of an annotation
   *
   * @param type the type of the value to read or null
   * @param constantPool the constant pool for the class being read
   * @param input stream to read from
   * @return object representing the value read
   */
  static <T> Object readValue(TypeReference type, int[] constantPool, DataInputStream input, ClassLoader classLoader)
      throws IOException, ClassNotFoundException {
    // Read element value's tag
    byte elementValue_tag = input.readByte();
    return readValue(type, constantPool, input, classLoader, elementValue_tag);
  }
  private static <T> Object readValue(TypeReference type, int[] constantPool, DataInputStream input, ClassLoader classLoader, byte elementValue_tag)
      throws IOException, ClassNotFoundException {
    // decode
    Object value;
    switch (elementValue_tag) {
      case'B': {
        if(VM.VerifyAssertions) VM._assert(type == null || type == TypeReference.Byte);
        Offset offset = ClassFileReader.getLiteralOffset(constantPool, input.readUnsignedShort());
        value = (byte) Statics.getSlotContentsAsInt(offset);
        break;
      }
      case'C': {
        if(VM.VerifyAssertions) VM._assert(type == null || type == TypeReference.Char);
        Offset offset = ClassFileReader.getLiteralOffset(constantPool, input.readUnsignedShort());
        value = (char) Statics.getSlotContentsAsInt(offset);
        break;
      }
      case'D': {
        if(VM.VerifyAssertions) VM._assert(type == null || type == TypeReference.Double);
        Offset offset = ClassFileReader.getLiteralOffset(constantPool, input.readUnsignedShort());
        long longValue = Statics.getSlotContentsAsLong(offset);
        value = Double.longBitsToDouble(longValue);
        break;
      }
      case'F': {
        if(VM.VerifyAssertions) VM._assert(type == null || type == TypeReference.Float);
        Offset offset = ClassFileReader.getLiteralOffset(constantPool, input.readUnsignedShort());
        int intValue = Statics.getSlotContentsAsInt(offset);
        value = Float.intBitsToFloat(intValue);
        break;
      }
      case'I': {
        if(VM.VerifyAssertions) VM._assert(type == null || type == TypeReference.Int);
        Offset offset = ClassFileReader.getLiteralOffset(constantPool, input.readUnsignedShort());
        value = Statics.getSlotContentsAsInt(offset);
        break;
      }
      case'J': {
        if(VM.VerifyAssertions) VM._assert(type == null || type == TypeReference.Long);
        Offset offset = ClassFileReader.getLiteralOffset(constantPool, input.readUnsignedShort());
        value = Statics.getSlotContentsAsLong(offset);
        break;
      }
      case'S': {
        if(VM.VerifyAssertions) VM._assert(type == null || type == TypeReference.Short);
        Offset offset = ClassFileReader.getLiteralOffset(constantPool, input.readUnsignedShort());
        value = (short) Statics.getSlotContentsAsInt(offset);
        break;
      }
      case'Z': {
        if(VM.VerifyAssertions) VM._assert(type == null || type == TypeReference.Boolean);
        Offset offset = ClassFileReader.getLiteralOffset(constantPool, input.readUnsignedShort());
        value = Statics.getSlotContentsAsInt(offset) == 1;
        break;
      }
      case's': {
        if(VM.VerifyAssertions) VM._assert(type == null || type == TypeReference.JavaLangString);
        value = ClassFileReader.getUtf(constantPool, input.readUnsignedShort()).toString();
        break;
      }
      case'e': {
        int typeNameIndex = input.readUnsignedShort();
        @SuppressWarnings("unchecked") Class enumType =
            TypeReference.findOrCreate(classLoader,
                                          ClassFileReader.getUtf(constantPool, typeNameIndex)).resolve().getClassForType();
        int constNameIndex = input.readUnsignedShort();

        //noinspection unchecked
        value = Enum.valueOf(enumType, ClassFileReader.getUtf(constantPool, constNameIndex).toString());
        break;
      }
      case'c': {
        if(VM.VerifyAssertions) VM._assert(type == null || type == TypeReference.JavaLangClass);
        int classInfoIndex = input.readUnsignedShort();
        // Value should be a class but resolving the class at this point could cause infinite recursion in class loading
        TypeReference unresolvedValue = TypeReference.findOrCreate(classLoader, ClassFileReader.getUtf(constantPool, classInfoIndex));
        if (unresolvedValue.peekType() != null) {
          value = unresolvedValue.peekType().getClassForType();
        } else {
          value = unresolvedValue;
        }
        break;
      }
      case'@':
        value = RVMAnnotation.readAnnotation(constantPool, input, classLoader);
        break;
      case'[': {
        int numValues = input.readUnsignedShort();
        if (numValues == 0) {
          if (type != null) {
            value = Array.newInstance(type.resolve().getClassForType(), 0);
          } else {
            value = new Object[0];
          }
        } else {
          byte innerElementValue_tag = input.readByte();
          TypeReference innerType = type == null ? null : type.getArrayElementType();
          switch(innerElementValue_tag) {
          case 'B': {
            byte[] array = new byte[numValues];
            array[0] = (Byte)readValue(innerType, constantPool, input, classLoader, innerElementValue_tag);
            for (int i = 1; i < numValues; i++) {
              array[i] = (Byte)readValue(innerType, constantPool, input, classLoader);
            }
            value = array;
            break;
          }
          case 'C': {
            char[] array = new char[numValues];
            array[0] = (Character)readValue(innerType, constantPool, input, classLoader, innerElementValue_tag);
            for (int i = 1; i < numValues; i++) {
              array[i] = (Character)readValue(innerType, constantPool, input, classLoader);
            }
            value = array;
            break;
          }
          case 'D': {
            double[] array = new double[numValues];
            array[0] = (Double)readValue(innerType, constantPool, input, classLoader, innerElementValue_tag);
            for (int i = 1; i < numValues; i++) {
              array[i] = (Double)readValue(innerType, constantPool, input, classLoader);
            }
            value = array;
            break;
          }
          case 'F': {
            float[] array = new float[numValues];
            array[0] = (Float)readValue(innerType, constantPool, input, classLoader, innerElementValue_tag);
            for (int i = 1; i < numValues; i++) {
              array[i] = (Float)readValue(innerType, constantPool, input, classLoader);
            }
            value = array;
            break;
          }
          case 'I': {
            int[] array = new int[numValues];
            array[0] = (Integer)readValue(innerType, constantPool, input, classLoader, innerElementValue_tag);
            for (int i = 1; i < numValues; i++) {
              array[i] = (Integer)readValue(innerType, constantPool, input, classLoader);
            }
            value = array;
            break;
          }
          case 'J': {
            long[] array = new long[numValues];
            array[0] = (Long)readValue(innerType, constantPool, input, classLoader, innerElementValue_tag);
            for (int i = 1; i < numValues; i++) {
              array[i] = (Long)readValue(innerType, constantPool, input, classLoader);
            }
            value = array;
            break;
          }
          case 'S': {
            short[] array = new short[numValues];
            array[0] = (Short)readValue(innerType, constantPool, input, classLoader, innerElementValue_tag);
            for (int i = 1; i < numValues; i++) {
              array[i] = (Short)readValue(innerType, constantPool, input, classLoader, innerElementValue_tag);
            }
            value = array;
            break;
          }
          case 'Z': {
            boolean[] array = new boolean[numValues];
            array[0] = (Boolean)readValue(innerType, constantPool, input, classLoader, innerElementValue_tag);
            for (int i = 1; i < numValues; i++) {
              array[i] = (Boolean)readValue(innerType, constantPool, input, classLoader);
            }
            value = array;
            break;
          }
          case 's':
          case '@':
          case 'e':
          case '[': {
            Object value1 = readValue(innerType, constantPool, input, classLoader, innerElementValue_tag);
            value = Array.newInstance(value1.getClass(), numValues);
            Array.set(value, 0, value1);
            for (int i = 1; i < numValues; i++) {
              Array.set(value, i, readValue(innerType, constantPool, input, classLoader));
            }
            break;
          }
          case 'c': {
            Object value1 = readValue(innerType, constantPool, input, classLoader, innerElementValue_tag);
            Object[] values = new Object[numValues];
            values[0] = value1;
            boolean allClasses = value1 instanceof Class;
            for (int i = 1; i < numValues; i++) {
              values[i] = readValue(innerType, constantPool, input, classLoader);
              if (allClasses && !(values[i] instanceof Class)) {
                allClasses = false;
              }
            }
            if (allClasses == true) {
              Class<?>[] newValues = new Class[numValues];
              for (int i = 0; i < numValues; i++) {
                newValues[i] = (Class<?>)values[i];
              }
              value = newValues;
            } else {
              value = values;
            }
            break;
          }
          default:
            throw new ClassFormatError("Unknown element_value tag '" + (char) innerElementValue_tag + "'");
          }
        }
        break;
      }
      default:
        throw new ClassFormatError("Unknown element_value tag '" + (char) elementValue_tag + "'");
    }
    return value;
  }

  /** Handle late resolution of class value annotations */
  static Object firstUse(Object value) {
    if (value instanceof TypeReference) {
      return ((TypeReference)value).resolve().getClassForType();
    } else if (value instanceof Object[]) {
      Object[] values = (Object[])value;
      boolean typeChanged = false;
      for (int i=0; i < values.length; i++) {
        Object newVal = firstUse(values[i]);
        if (newVal.getClass() != values[i].getClass()) {
          typeChanged = true;
        }
        values[i] = newVal;
      }
      if (typeChanged) {
        Object[] newValues = (Object[])Array.newInstance(values[0].getClass(), values.length);
        for (int i=0; i < values.length; i++) {
          newValues[i] = values[i];
        }
        return newValues;
      } else {
        return values;
      }
    }
    return value;
  }

  /**
   * Return the TypeReference of the declared annotation, ie an
   * interface and not the class object of this instance
   *
   * @return TypeReferernce of interface annotation object implements
   */
  @Uninterruptible
  TypeReference annotationType() { return type; }

  /*
   * Hash map support
   */
  public int hashCode() {
    return type.hashCode();
  }

  public boolean equals(Object o) {
    if (o instanceof RVMAnnotation) {
      RVMAnnotation that = (RVMAnnotation)o;
      if (type == that.type) {
        if (elementValuePairs.length != that.elementValuePairs.length) {
          return false;
        }
        for (int i=0; i<elementValuePairs.length; i++) {
          if (!elementValuePairs[i].equals(that.elementValuePairs[i])) {
            return false;
          }
        }
        return true;
      } else {
        return false;
      }
    } else {
      return false;
    }
  }

  /**
   * Return a string representation of the annotation of the form
   * "@type(name1=val1, ...nameN=valN)"
   */
  public String toString() {
    RVMClass annotationInterface = type.resolve().asClass();
    RVMMethod[] annotationMethods = annotationInterface.getDeclaredMethods();
    String result = "@" + type.resolve().getClassForType().getName() + "(";
    try {
      for (int i=0; i < annotationMethods.length; i++) {
        String name=annotationMethods[i].getName().toUnicodeString();
        Object value=getElementValue(name, annotationMethods[i].getReturnType().resolve().getClassForType());
        result += elementString(name, value);
        if (i < (annotationMethods.length - 1)) {
          result += ", ";
        }
      }
    } catch (java.io.UTFDataFormatException e) {
      throw new Error(e);
    }
    result += ")";
    return result;
  }

  /**
   * String representation of the value pair of the form
   * "name=value"
   */
  private String elementString(String name, Object value) {
    return name + "=" + toStringHelper(value);
  }
  private static String toStringHelper(Object value) {
    if (value instanceof Object[]) {
      StringBuilder result = new StringBuilder("[");
      Object[] a = (Object[]) value;
      for (int i = 0; i < a.length; i++) {
        result.append(toStringHelper(a[i]));
        if (i < (a.length - 1)) {
          result.append(", ");
        }
      }
      result.append("]");
      return result.toString();
    } else {
      return value.toString();
    }
  }

  /** Find the value for an annotation */
  private Object getElementValue(String name, Class<?> valueType) {
    for (AnnotationMember evp : elementValuePairs) {
      String evpFieldName = evp.getName().toString();
      if (name.equals(evpFieldName)) {
        return evp.getValue();
      }
    }
    MethodReference methRef = MemberReference.findOrCreate(
        type,
        Atom.findOrCreateAsciiAtom(name),
        Atom.findOrCreateAsciiAtom("()" + TypeReference.findOrCreate(valueType).getName())
    ).asMethodReference();
    try {
      return methRef.resolve().getAnnotationDefault();
    } catch (Throwable t) {
      return NO_VALUE;
    }
  }

  /** Hash code for annotation value */
  private int annotationHashCode() {
    RVMClass annotationInterface = type.resolve().asClass();
    RVMMethod[] annotationMethods = annotationInterface.getDeclaredMethods();
    String typeString = type.toString();
    int result = typeString.substring(1, typeString.length() - 1).hashCode();
    try {
      for (RVMMethod method : annotationMethods) {
        String name = method.getName().toUnicodeString();
        Object value = getElementValue(name, method.getReturnType().resolve().getClassForType());
        int part_result = name.hashCode() * 127;
        if (value.getClass().isArray()) {
          if (value instanceof Object[]) {
            part_result ^= Arrays.hashCode((Object[]) value);
          } else if (value instanceof boolean[]) {
            part_result ^= Arrays.hashCode((boolean[]) value);
          } else if (value instanceof byte[]) {
            part_result ^= Arrays.hashCode((byte[]) value);
          } else if (value instanceof char[]) {
              part_result ^= Arrays.hashCode((char[]) value);
          } else if (value instanceof short[]) {
            part_result ^= Arrays.hashCode((short[]) value);
          } else if (value instanceof int[]) {
            part_result ^= Arrays.hashCode((int[]) value);
          } else if (value instanceof long[]) {
            part_result ^= Arrays.hashCode((long[]) value);
          } else if (value instanceof float[]) {
            part_result ^= Arrays.hashCode((float[]) value);
          } else if (value instanceof double[]) {
            part_result ^= Arrays.hashCode((double[]) value);
          }
        } else {
          part_result ^= value.hashCode();
        }
        result += part_result;
      }
    } catch (java.io.UTFDataFormatException e) {
      throw new Error(e);
    }
    return result;
  }

  /** Are two annotations equal? */
  private boolean annotationEquals(Annotation a, Annotation b) {
    if (a == b) {
      return true;
    } else if (a.getClass() != b.getClass()) {
      return false;
    } else {
      RVMClass annotationInterface = type.resolve().asClass();
      RVMMethod[] annotationMethods = annotationInterface.getDeclaredMethods();
      AnnotationFactory afB = (AnnotationFactory)Proxy.getInvocationHandler(b);
      try {
        for (RVMMethod method : annotationMethods) {
          String name = method.getName().toUnicodeString();
          Object objA = getElementValue(name, method.getReturnType().resolve().getClassForType());
          Object objB = afB.getValue(name, method.getReturnType().resolve().getClassForType());
          if (!objA.getClass().isArray()) {
            if (!objA.equals(objB)) {
              return false;
            }
          } else {
            if(!Arrays.equals((Object[]) objA, (Object[]) objB)) {
              return false;
            }
          }
        }
      } catch (java.io.UTFDataFormatException e) {
        throw new Error(e);
      }
      return true;
    }
  }

  /**
   * Class used to implement annotations as proxies
   */
  private final class AnnotationFactory implements InvocationHandler {
    /** Cache of hash code */
    private int cachedHashCode;

    AnnotationFactory() {
    }

    /** Entry point to factory */
    public Object invoke(Object proxy, Method method, Object[] args) {
      if (method.getName().equals("annotationType")) {
        return type.resolve().getClassForType();
      }
      if (method.getName().equals("hashCode")) {
        if (cachedHashCode == 0) {
          cachedHashCode = annotationHashCode();
        }
        return cachedHashCode;
      }
      if (method.getName().equals("equals")) {
        return annotationEquals((Annotation)proxy, (Annotation)args[0]);
      }
      if (method.getName().equals("toString")) {
        return RVMAnnotation.this.toString();
      }
      Object value = getValue(method.getName(), method.getReturnType());
      if (value != NO_VALUE) {
        return value;
      }
      throw new IllegalArgumentException("Invalid method for annotation type: " + method);
    }

    private Object getValue(String name, Class<?> valueType) {
      return RVMAnnotation.this.getElementValue(name, valueType);
    }

  }
  /**
   * A class to decode and hold the name and its associated value for
   * an annotation member
   */
  private static final class AnnotationMember implements Comparable<AnnotationMember> {
    /**
     * Name of element
     */
    private final MethodReference meth;
    /**
     * Elements value, decoded from its tag
     */
    private Object value;
    /**
     * Is this not the first use of the member?
     */
    private boolean notFirstUse = false;
    /**
     * Construct a read value pair
     */
    private AnnotationMember(MethodReference meth, Object value) {
      this.meth = meth;
      this.value = value;
    }

    /**
     * Read the pair from the input stream and create object
     * @param constantPool the constant pool for the class being read
     * @param input stream to read from
     * @param classLoader the class loader being used to load this annotation
     * @return a newly created annotation member
     */
    static AnnotationMember readAnnotationMember(TypeReference type, int[] constantPool, DataInputStream input, ClassLoader classLoader)
        throws IOException, ClassNotFoundException {
      // Read name of pair
      int elemNameIndex = input.readUnsignedShort();
      Atom name = ClassFileReader.getUtf(constantPool, elemNameIndex);
      MethodReference meth;
      Object value;
      if (type.isResolved()) {
        meth = type.resolve().asClass().findDeclaredMethod(name).getMemberRef().asMethodReference();
        value = RVMAnnotation.readValue(meth.getReturnType(), constantPool, input, classLoader);
      } else {
        value = RVMAnnotation.readValue(null, constantPool, input, classLoader);
        meth = MemberReference.findOrCreate(type, name,
            Atom.findOrCreateAsciiAtom("()"+TypeReference.findOrCreate(value.getClass()).getName())
        ).asMethodReference();
      }
      return new AnnotationMember(meth, value);
    }

    /** @return the name of the of the given pair */
    Atom getName() {
      return meth.getName();
    }
    /** @return the value of the of the given pair */
    @Pure
    Object getValue() {
      if (!notFirstUse) {
        synchronized(this) {
          value = firstUse(value);
          notFirstUse = true;
        }
      }
      return value;
    }

    /**
     * Are two members equivalent?
     */
    public boolean equals(Object o) {
      if (o instanceof AnnotationMember) {
        AnnotationMember that = (AnnotationMember)o;
        return that.meth == meth && that.value.equals(value);
      } else {
        return false;
      }
    }

    /**
     * Compute hashCode from meth
     */
    public int hashCode() {
      return meth.hashCode();
    }

    /**
     * Ordering for sorted annotation members
     */
    public int compareTo(AnnotationMember am) {
      if (am.meth != this.meth) {
        return am.getName().toString().compareTo(this.getName().toString());
      } else {
        if (value.getClass().isArray()) {
          return Arrays.hashCode((Object[]) value) - Arrays.hashCode((Object[]) am.value);
        } else {
          @SuppressWarnings("unchecked") // True generic programming, we can't type check it in Java
          Comparable<Object> cValue = (Comparable) value;
          return cValue.compareTo(am.value);
        }
      }
    }
  }
}
