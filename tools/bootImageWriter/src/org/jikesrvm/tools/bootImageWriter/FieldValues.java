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
package org.jikesrvm.tools.bootImageWriter;

import static java.lang.reflect.Modifier.ABSTRACT;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.NATIVE;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PROTECTED;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static java.lang.reflect.Modifier.SYNCHRONIZED;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterConstants.OBJECT_NOT_ALLOCATED;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterConstants.OBJECT_NOT_PRESENT;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterMessages.fail;
import static org.jikesrvm.tools.bootImageWriter.BootImageWriterMessages.say;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.ADDRESSES;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.DETAILED;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.BitSet;
import java.util.HashSet;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.mm.mminterface.AlignmentEncoding;
import org.jikesrvm.runtime.Magic;
import org.jikesrvm.runtime.Statics;
import org.jikesrvm.tools.bootImageWriter.types.BootImageTypes;
import org.jikesrvm.util.Services;
import org.vmmagic.pragma.Untraced;
import org.vmmagic.unboxed.Address;
import org.vmmagic.unboxed.Offset;

public class FieldValues {

  /**
   * If we can't find a field via reflection we may still determine
   * and copy a value because we know the internals of Classpath.
   * @param jdkObject the object containing the field
   * @param rvmFieldName the name of the field
   * @param rvmFieldType the type reference of the field
   * @param rvmFieldAddress the address that the field is being written to
   */
  static boolean copyKnownValueForInstanceField(Object jdkObject, String rvmFieldName, TypeReference rvmFieldType, Address rvmFieldAddress)
    throws IllegalAccessException {

    // Class library independent objects
    if (jdkObject instanceof java.lang.Class)   {
      Class<?> jdkObjectAsClass = (Class<?>) jdkObject;
      Object value = null;
      String fieldName = null;
      boolean fieldIsFinal = false;
      if (rvmFieldName.equals("type")) {
        // Looks as though we're trying to write the type for Class,
        // lets go over the common ones
        if (jdkObject == java.lang.Boolean.TYPE) {
          value = RVMType.BooleanType;
        } else if (jdkObject == java.lang.Byte.TYPE) {
          value = RVMType.ByteType;
        } else if (jdkObject == java.lang.Character.TYPE) {
          value = RVMType.CharType;
        } else if (jdkObject == java.lang.Double.TYPE) {
          value = RVMType.DoubleType;
        } else if (jdkObject == java.lang.Float.TYPE) {
          value = RVMType.FloatType;
        } else if (jdkObject == java.lang.Integer.TYPE) {
          value = RVMType.IntType;
        } else if (jdkObject == java.lang.Long.TYPE) {
          value = RVMType.LongType;
        } else if (jdkObject == java.lang.Short.TYPE) {
          value = RVMType.ShortType;
        } else if (jdkObject == java.lang.Void.TYPE) {
          value = RVMType.VoidType;
        } else if (jdkObjectAsClass.getName().startsWith("com.sun.proxy")) {
          // FIXME OPENJDK/ICEDTEA will probably lead to problems at runtime
          if (VM.VerifyAssertions) VM._assert(VM.BuildForOpenJDK);
          say("Doing nothing for proxy " + jdkObject + " for now");
        } else {
          value = TypeReference.findOrCreate((Class<?>)jdkObject).peekType();
          if (value == null) {
            fail("Failed to populate Class.type for " + jdkObject);
          }
        }
        fieldName = "type";
        fieldIsFinal = true;
      }
      if ((fieldName != null) && (value != null)) {
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push(value.getClass().getName(),
                                            "java.lang.Class",
                                            fieldName);
        Address imageAddress = BootImageMap.findOrCreateEntry(value).imageAddress;
        if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
          // object not part of bootimage: install null reference
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceObjectNotInBootImage();
          BootImageWriter.bootImage().setNullAddressWord(rvmFieldAddress, true, true, false);
        } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
          imageAddress = BootImageWriter.copyToBootImage(value, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
          if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
          BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, !fieldIsFinal);
        } else {
          if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
          BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, !fieldIsFinal);
        }
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
        return true;
      } else {
        // Unknown Class field or value for type
        return false;
      }
    } else if ((jdkObject instanceof java.lang.String) &&
        (rvmFieldName.equals("count")) && (rvmFieldType.isIntType())) {
      // The fields "count" and "offset" are not guaranteed to be present in
      // the String implementation in the class library (case in point: IcedTea 7).
      // We don't need to do anything for "offset" (the default value of 0 is correct)
      // but we need to ensure that "count" has the correct value.
      BootImageWriter.bootImage().setFullWord(rvmFieldAddress, ((java.lang.String) jdkObject).length());
      return true;
    }
    // Class library dependent fields
    if (BootImageWriter.classLibrary() == "classpath") {
      if ((jdkObject instanceof java.lang.String) &&
          (rvmFieldName.equals("cachedHashCode")) &&
          (rvmFieldType.isIntType())
          ) {
        // Populate String's cachedHashCode value
        BootImageWriter.bootImage().setFullWord(rvmFieldAddress, jdkObject.hashCode());
        return true;
      } else if (jdkObject instanceof java.lang.reflect.Constructor)   {
        Constructor<?> cons = (Constructor<?>)jdkObject;
        if (rvmFieldName.equals("cons")) {
          // fill in this RVMMethod field
          String typeName = "L" + cons.getDeclaringClass().getName().replace('.','/') + ";";
          RVMType type = TypeReference.findOrCreate(typeName).peekType();
          if (type == null) {
            throw new Error("Failed to find type for Constructor.constructor: " + cons + " " + typeName);
          }
          final RVMClass klass = type.asClass();
          Class<?>[] consParams = cons.getParameterTypes();
          RVMMethod constructor = null;
          loop_over_all_constructors:
          for (RVMMethod vmCons : klass.getConstructorMethods()) {
            TypeReference[] vmConsParams = vmCons.getParameterTypes();
            if (vmConsParams.length == consParams.length) {
              for (int j = 0; j < vmConsParams.length; j++) {
                if (!consParams[j].equals(vmConsParams[j].resolve().getClassForType())) {
                  continue loop_over_all_constructors;
                }
              }
              constructor = vmCons;
              break;
            }
          }
          if (constructor == null) {
            throw new Error("Failed to populate Constructor.cons for " + cons);
          }
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push("VMConstructor",
                                              "java.lang.Constructor",
                                              "cons");
          Object vmcons = java.lang.reflect.JikesRVMSupport.createVMConstructor(constructor);
          Address imageAddress = BootImageMap.findOrCreateEntry(vmcons).imageAddress;
          if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
            // object not part of bootimage: install null reference
            if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceObjectNotInBootImage();
            BootImageWriter.bootImage().setNullAddressWord(rvmFieldAddress, true, false, false);
          } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            imageAddress = BootImageWriter.copyToBootImage(vmcons, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          } else {
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          }
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
          return true;
        } else if (rvmFieldName.equals("flag")) {
          // This field is inherited accesible flag is actually part of
          // AccessibleObject
          BootImageWriter.bootImage().setByte(rvmFieldAddress, cons.isAccessible() ? 1 : 0);
          return true;
        } else {
          // Unknown Constructor field
          return false;
        }
      } else if (jdkObject instanceof java.lang.ref.ReferenceQueue) {
        if (rvmFieldName.equals("lock")) {
          VM.sysWriteln("writing the lock field.");
          Object value = new org.jikesrvm.scheduler.LightMonitor();
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push(value.getClass().getName(),
                                            "java.lang.ref.ReferenceQueue",
                                            "lock");
          Address imageAddress = BootImageMap.findOrCreateEntry(value).imageAddress;
          if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
            if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceObjectNotInBootImage();
            throw new Error("Failed to populate lock in ReferenceQueue");
          } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            imageAddress = BootImageWriter.copyToBootImage(value, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          } else {
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), true, false);
          }
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
          return true;
        } else if (rvmFieldName.equals("first")) {
          return false;
        } else {
          throw new Error("Unknown field " + rvmFieldName + " in java.lang.ref.ReferenceQueue");
        }
      } else if (jdkObject instanceof java.util.BitSet) {
        BitSet bs = (BitSet)jdkObject;
        if (rvmFieldName.equals("bits")) {
          int max = 0; // highest bit set in set
          for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            max = i;
          }
          long[] bits = new long[(max + 63) / 64];
          for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
            bits[i / 64] |= 1L << (i & 63);
          }
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push("[J", "java.util.BitSet", "bits");
          Address imageAddress = BootImageMap.findOrCreateEntry(bits).imageAddress;
          if (imageAddress.EQ(OBJECT_NOT_PRESENT)) {
            // object not part of bootimage: install null reference
            if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceObjectNotInBootImage();
            BootImageWriter.bootImage().setNullAddressWord(rvmFieldAddress, true, false);
          } else if (imageAddress.EQ(OBJECT_NOT_ALLOCATED)) {
            imageAddress = BootImageWriter.copyToBootImage(bits, false, Address.max(), jdkObject, false, AlignmentEncoding.ALIGN_CODE_NONE);
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), false, false);
          } else {
            if (BootImageWriter.verbosity().isAtLeast(ADDRESSES)) BootImageWriter.traceContext().traceObjectFoundThroughKnown();
            BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, imageAddress.toWord(), false, false);
          }
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
          return true;
        } else {
          // Unknown BitSet field
          return false;
        }
      } else {
        // Unknown field
        return false;
      }
    } else if (BootImageWriter.classLibrary() == "openjdk") {
      return false;
    } else {
      throw new Error("Unknown class library: \"" + BootImageWriter.classLibrary() + "\"");
    }
  }

  /**
   * If we can't find a field via reflection we may still determine
   * and copy a value because we know the internals of Classpath.
   * @param jdkType the class containing the field
   * @param rvmFieldName the name of the field
   * @param rvmFieldType the type reference of the field
   */
  private static boolean copyKnownValueForStaticField(Class<?> jdkType, String rvmFieldName,
                                              TypeReference rvmFieldType,
                                              Offset rvmFieldOffset) {
    if (BootImageWriter.classLibrary() == "classpath") {
      if (jdkType.equals(java.lang.Number.class)) {
        if (rvmFieldName.equals("digits") && rvmFieldType.isArrayType()) {
          char[] java_lang_Number_digits = new char[]{
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
            'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
            'u', 'v', 'w', 'x', 'y', 'z'
          };
          Statics.setSlotContents(rvmFieldOffset, java_lang_Number_digits);
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Number " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Boolean.class)) {
        throw new Error("Unknown field in java.lang.Boolean " + rvmFieldName + " " + rvmFieldType);
      } else if (jdkType.equals(java.lang.Byte.class)) {
        if (rvmFieldName.equals("byteCache") && rvmFieldType.isArrayType()) {
          Byte[] java_lang_Byte_byteCache = new Byte[256];
          // Populate table
          for (int i = -128; i < 128; i++) {
            Byte value = (byte) i;
            BootImageMap.findOrCreateEntry(value);
            java_lang_Byte_byteCache[128 + i] = value;
          }
          Statics.setSlotContents(rvmFieldOffset, java_lang_Byte_byteCache);
          return true;
        } else if (rvmFieldName.equals("MIN_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, -128);
          return true;
        } else if (rvmFieldName.equals("MAX_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 127);
          return true;
        } else if (rvmFieldName.equals("SIZE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 8); // NB not present in Java 1.4
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Byte " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Double.class)) {
        if (rvmFieldName.equals("ZERO")) {
          Statics.setSlotContents(rvmFieldOffset, Double.valueOf(0.0));
          return true;
        } else if (rvmFieldName.equals("ONE")) {
          Statics.setSlotContents(rvmFieldOffset, Double.valueOf(1.0));
          return true;
        } else if (rvmFieldName.equals("SIZE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 64); // NB not present in Java 1.4
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Double " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Float.class)) {
        if (rvmFieldName.equals("ZERO")) {
          Statics.setSlotContents(rvmFieldOffset, Float.valueOf(0.0f));
          return true;
        } else if (rvmFieldName.equals("ONE")) {
          Statics.setSlotContents(rvmFieldOffset, Float.valueOf(1.0f));
          return true;
        } else if (rvmFieldName.equals("SIZE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 32); // NB not present in Java 1.4
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Float " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Integer.class)) {
        if (rvmFieldName.equals("intCache") && rvmFieldType.isArrayType()) {
          Integer[] java_lang_Integer_intCache = new Integer[256];
          // Populate table
          for (int i = -128; i < 128; i++) {
            Integer value = i;
            java_lang_Integer_intCache[128 + i] = value;
          }
          Statics.setSlotContents(rvmFieldOffset, java_lang_Integer_intCache);
          return true;
        } else if (rvmFieldName.equals("MIN_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, -128);
          return true;
        } else if (rvmFieldName.equals("MAX_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 127);
          return true;
        } else if (rvmFieldName.equals("SIZE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 32); // NB not present in Java 1.4
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Integer " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Long.class)) {
        if (rvmFieldName.equals("longCache") && rvmFieldType.isArrayType()) {
          Long[] java_lang_Long_longCache = new Long[256];
          // Populate table
          for (int i = -128; i < 128; i++) {
            Long value = (long)i;
            BootImageMap.findOrCreateEntry(value);
            java_lang_Long_longCache[128 + i] = value;
          }
          Statics.setSlotContents(rvmFieldOffset, java_lang_Long_longCache);
          return true;
        } else if (rvmFieldName.equals("MIN_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, -128);
          return true;
        } else if (rvmFieldName.equals("MAX_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 127);
          return true;
        } else if (rvmFieldName.equals("SIZE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 64); // NB not present in Java 1.4
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Long " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.lang.Short.class)) {
        if (rvmFieldName.equals("shortCache") && rvmFieldType.isArrayType()) {
          Short[] java_lang_Short_shortCache = new Short[256];
          // Populate table
          for (short i = -128; i < 128; i++) {
            Short value = i;
            BootImageMap.findOrCreateEntry(value);
            java_lang_Short_shortCache[128 + i] = value;
          }
          Statics.setSlotContents(rvmFieldOffset, java_lang_Short_shortCache);
          return true;
        } else if (rvmFieldName.equals("MIN_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, -128);
          return true;
        } else if (rvmFieldName.equals("MAX_CACHE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 127);
          return true;
        } else if (rvmFieldName.equals("SIZE") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 16); // NB not present in Java 1.4
          return true;
        } else {
          throw new Error("Unknown field in java.lang.Short " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.util.HashMap.class)) {
        if (rvmFieldName.equals("DEFAULT_CAPACITY") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 11);
          return true;
        } else if (rvmFieldName.equals("DEFAULT_LOAD_FACTOR") && rvmFieldType.isFloatType()) {
          Statics.setSlotContents(rvmFieldOffset, Float.floatToIntBits(0.75f));
          return true;
        } else {
          throw new Error("Unknown field in java.util.HashMap " + rvmFieldName + " " + rvmFieldType);
        }
      } else if (jdkType.equals(java.util.AbstractMap.class)) {
        if (rvmFieldName.equals("KEYS") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 0);
          return true;
        } else if (rvmFieldName.equals("VALUES") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 1);
          return true;
        } else if (rvmFieldName.equals("ENTRIES") && rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, 2);
          return true;
        } else {
          throw new Error("Unknown field in java.util.AbstractMap " + rvmFieldName + " " + rvmFieldType);
        }
      } else {
        return false;
      }
    } else if (BootImageWriter.classLibrary() == "openjdk") {
      if (jdkType.equals(java.lang.Class.class) && rvmFieldName.equals("EMPTY_ANNOTATIONS_ARRAY")) {
        Annotation[] emptyAnnotationsArray = new Annotation[0];
        Statics.setSlotContents(rvmFieldOffset, emptyAnnotationsArray);
        return true;
      } else if (rvmFieldName.equals("EMPTY_ANNOTATION_ARRAY") && (jdkType.equals(java.lang.reflect.Field.class) ||
          jdkType.equals(java.lang.reflect.Method.class) || jdkType.equals(java.lang.reflect.Constructor.class))) {
        Annotation[] emptyAnnotationsArray = new Annotation[0];
        Statics.setSlotContents(rvmFieldOffset, emptyAnnotationsArray);
        return true;
      } else if (rvmFieldName.equals("LANGUAGE_MODIFIERS") && jdkType.equals(java.lang.reflect.Constructor.class)) {
        int constructorModifiers = PRIVATE | PROTECTED | PUBLIC;;
        Statics.setSlotContents(rvmFieldOffset, constructorModifiers);
        return true;
      } else if (rvmFieldName.equals("LANGUAGE_MODIFIERS") && jdkType.equals(java.lang.reflect.Method.class)) {
        int methodModifiers = PRIVATE | PROTECTED | PUBLIC | SYNCHRONIZED | NATIVE | ABSTRACT | FINAL | STATIC;
        Statics.setSlotContents(rvmFieldOffset, methodModifiers);
        return true;
      }
      if (rvmFieldName.equals("JDK_PACKAGE_PREFIX") && rvmFieldType == TypeReference.JavaLangString) {
        String packagePrefix = "sun.net.www.protocol";
        Statics.setSlotContents(rvmFieldOffset, packagePrefix);
        return true;
      } else if (rvmFieldName.equals("extendedProviderLock") && rvmFieldType == TypeReference.JavaLangObject) {
        Object lock = new Object();
        Statics.setSlotContents(rvmFieldOffset, lock);
        return true;
      } else {
        System.out.println("Unknow field in " + rvmFieldName + " " + rvmFieldType + " " + rvmFieldOffset);
        return false;
      }
    } else {
      throw new Error("Unknown class library: \"" + BootImageWriter.classLibrary() + "\"");
    }
  }

  static void copyInstanceFieldValue(Object jdkObject, Class<?> jdkType,
      RVMClass rvmScalarType, boolean allocOnly, RVMField rvmField,
      TypeReference rvmFieldType, Address rvmFieldAddress, String rvmFieldName,
      Field jdkFieldAcc, boolean untracedField) throws IllegalAccessException,
      Error {

    if (jdkObject instanceof java.util.HashMap && "table".equals(rvmFieldName)) {
      if (VM.VerifyAssertions) VM._assert("openjdk".equals(BootImageWriter.classLibrary()));
      OpenJDKDifferences.rebuildHashMapForOpenJDK6(jdkObject, jdkType, rvmFieldAddress,
          rvmFieldName);
    }

    boolean valueCopied = FieldValues.setInstanceFieldViaJDKMapping(jdkObject, rvmScalarType, allocOnly,
        rvmField, rvmFieldType, rvmFieldAddress, rvmFieldName, jdkFieldAcc,
        untracedField);
    if (valueCopied) {
      return;
    }

    // Field not found via reflection, search hand-crafted list
    if (VM.VerifyAssertions) VM._assert(jdkFieldAcc == null);
    valueCopied = copyKnownValueForInstanceField(jdkObject, rvmFieldName, rvmFieldType, rvmFieldAddress);
    if (valueCopied) {
      return;
    }

    // Field not found at all, set default value
    FieldValues.setLanguageDefaultValueForInstanceField(jdkType, rvmField,
        rvmFieldType, rvmFieldAddress, rvmFieldName, untracedField);
  }

  private static void setLanguageDefaultValueForInstanceField(Class<?> jdkType,
      RVMField rvmField, TypeReference rvmFieldType, Address rvmFieldAddress,
      String rvmFieldName, boolean untracedField) throws Error {
    // Field wasn't a known Classpath field so write null
    if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push(rvmFieldType.toString(),
        jdkType.getName(), rvmFieldName);
    if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceFieldNotInHostJdk();
    if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
    if (rvmFieldType.isPrimitiveType()) {
      switch (rvmField.getType().getMemoryBytes()) {
      case 1: BootImageWriter.bootImage().setByte(rvmFieldAddress, 0);          break;
      case 2: BootImageWriter.bootImage().setHalfWord(rvmFieldAddress, 0);      break;
      case 4: BootImageWriter.bootImage().setFullWord(rvmFieldAddress, 0);      break;
      case 8: BootImageWriter.bootImage().setDoubleWord(rvmFieldAddress, 0L);   break;
      default:fail("unexpected field type: " + rvmFieldType); break;
      }
    } else {
      BootImageTypes.logMissingField(jdkType, rvmField, BootImageWriter.INSTANCE_FIELD);
      BootImageWriter.bootImage().setNullAddressWord(rvmFieldAddress, !untracedField, !untracedField, false);
    }
  }

  /**
   *
   * @param jdkObject object to write
   * @param rvmScalarType RVM class loader version of type
   * @param allocOnly allocate the object only?
   * @param rvmField RVM Class loader version of field
   * @param rvmFieldType type of RVM Class loader version of field
   * @param rvmFieldAddress address of the concrete field instance (i.e. the location
   *  in the boot image where the value will be placed)
   * @param rvmFieldName name of RVM Class loader version of field
   * @param jdkFieldAcc the JDK reflection accessor for the field, possibly {@code null}
   * @param untracedField {@code true} if the field is untraced
   * @return {@code true} if a field value was copied (which implies that nothing more needs
   *  to be done), {@code false} otherwise
   * @throws IllegalAccessException
   * @throws Error
   *
   * @see Untraced
   */
  private static boolean setInstanceFieldViaJDKMapping(Object jdkObject,
      RVMClass rvmScalarType, boolean allocOnly, RVMField rvmField,
      TypeReference rvmFieldType, Address rvmFieldAddress, String rvmFieldName,
      Field jdkFieldAcc, boolean untracedField) throws IllegalAccessException,
      Error {
    boolean valueCopied = true;
    if (jdkFieldAcc == null) {
      return !valueCopied;
    }
    if (rvmFieldType.isPrimitiveType()) {
      // field is logical or numeric type
      if (rvmFieldType.isBooleanType()) {
        BootImageWriter.bootImage().setByte(rvmFieldAddress,
            jdkFieldAcc.getBoolean(jdkObject) ? 1 : 0);
      } else if (rvmFieldType.isByteType()) {
        BootImageWriter.bootImage().setByte(rvmFieldAddress,
            jdkFieldAcc.getByte(jdkObject));
      } else if (rvmFieldType.isCharType()) {
        BootImageWriter.bootImage().setHalfWord(rvmFieldAddress,
            jdkFieldAcc.getChar(jdkObject));
      } else if (rvmFieldType.isShortType()) {
        BootImageWriter.bootImage().setHalfWord(rvmFieldAddress,
            jdkFieldAcc.getShort(jdkObject));
      } else if (rvmFieldType.isIntType()) {
        try {
          BootImageWriter.bootImage().setFullWord(rvmFieldAddress, jdkFieldAcc.getInt(jdkObject));
        } catch (IllegalArgumentException ex) {
          // TODO: Harmony - clean this up
          if (jdkObject instanceof java.util.WeakHashMap && rvmFieldName.equals("loadFactor")) {
            // the field load factor field in Sun/Classpath is a float but
            // in Harmony it has been "optimized" to an int
            BootImageWriter.bootImage().setFullWord(rvmFieldAddress, 7500);
          } else if (jdkObject instanceof java.lang.ref.ReferenceQueue && rvmFieldName.equals("head")) {
            // Conflicting types between Harmony and Sun
            BootImageWriter.bootImage().setFullWord(rvmFieldAddress, 0);
          } else {
            System.out.println("type " + rvmScalarType + ", field " + rvmField);
            throw ex;
          }
        }
      } else if (rvmFieldType.isLongType()) {
        BootImageWriter.bootImage().setDoubleWord(rvmFieldAddress,
            jdkFieldAcc.getLong(jdkObject));
      } else if (rvmFieldType.isFloatType()) {
        float f = jdkFieldAcc.getFloat(jdkObject);
        BootImageWriter.bootImage().setFullWord(rvmFieldAddress,
            Float.floatToIntBits(f));
      } else if (rvmFieldType.isDoubleType()) {
        double d = jdkFieldAcc.getDouble(jdkObject);
        BootImageWriter.bootImage().setDoubleWord(rvmFieldAddress,
            Double.doubleToLongBits(d));
      } else if (rvmFieldType.equals(TypeReference.Address) ||
          rvmFieldType.equals(TypeReference.Word) ||
          rvmFieldType.equals(TypeReference.Extent) ||
          rvmFieldType.equals(TypeReference.Offset)) {
        Object o = jdkFieldAcc.get(jdkObject);
        String msg = " instance field " + rvmField.toString();
        boolean warn = rvmFieldType.equals(TypeReference.Address);
        BootImageWriter.bootImage().setAddressWord(rvmFieldAddress, BootImageWriter.getWordValue(o, msg, warn), false, false);
      } else {
        fail("unexpected primitive field type: " + rvmFieldType);
      }
    } else {
      // field is reference type
      Object value = jdkFieldAcc.get(jdkObject);
      if (!allocOnly) {
        Class<?> jdkClass = jdkFieldAcc.getDeclaringClass();
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) {
          String typeName = (value == null) ? "(unknown: value was null)" :
            value.getClass().getName();
          BootImageWriter.traceContext().push(typeName,
            jdkClass.getName(),
            jdkFieldAcc.getName());
        }
        BootImageWriter.copyReferenceFieldToBootImage(rvmFieldAddress, value, jdkObject,
            !untracedField, !(untracedField || rvmField.isFinal()), rvmFieldName, rvmFieldType);
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) {
          BootImageWriter.traceContext().pop();
        }
      }
    }
    return valueCopied;
  }

  static void copyStaticFieldValue(HashSet<String> invalidEntrys,
      RVMType rvmType, Class<?> jdkType, int staticFieldIndex, RVMField rvmField,
      TypeReference rvmFieldType, Offset rvmFieldOffset, String rvmFieldName) throws Error, IllegalAccessException {
    boolean copiedValue = setStaticFieldFromEquivalentField(rvmType, jdkType, rvmFieldOffset, rvmFieldName);
    if (copiedValue) {
      return;
    }

    if (jdkType == null) {
      nullifyFieldWithUnknownType(invalidEntrys, rvmField, rvmFieldType,
          rvmFieldOffset, rvmFieldName);
      return;
    }

    copiedValue = setStaticFieldViaJDKMapping(invalidEntrys, jdkType, staticFieldIndex,
        rvmField, rvmFieldType, rvmFieldOffset, rvmFieldName);
    if (copiedValue) {
      return;
    }

    copiedValue = copyKnownValueForStaticField(jdkType, rvmFieldName, rvmFieldType, rvmFieldOffset);
    if (copiedValue) {
      return;
    }

    nullifyFieldWithKnownType(invalidEntrys, jdkType, rvmField, rvmFieldType,
        rvmFieldOffset, rvmFieldName);
  }

  private static boolean setStaticFieldViaJDKMapping(HashSet<String> invalidEntrys,
      Class<?> jdkType, int staticFieldIndex, RVMField rvmField,
      TypeReference rvmFieldType, Offset rvmFieldOffset, String rvmFieldName)
      throws IllegalAccessException, Error {
    Field jdkFieldAcc = BootImageTypes.getJdkFieldAccessor(jdkType, staticFieldIndex, BootImageWriter.STATIC_FIELD);
    if (jdkFieldAcc == null) {
      return false;
    }

    if (! Modifier.isStatic(jdkFieldAcc.getModifiers())) {
      System.out.println("Modifier is not static " + jdkType.getName() + " " + rvmFieldName);
      if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push(rvmFieldType.toString(),
                                          jdkType.getName(), rvmFieldName);
      if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceFieldNotStaticInHostJdk();
      if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
      Statics.setSlotContents(rvmFieldOffset, 0);
      BootImageTypes.logStaticFieldNotStaticInHostJDK(jdkType, rvmField);
      if (!VM.runningTool)
        BootImageWriter.bootImage().countNulledReference();
      invalidEntrys.add(jdkType.getName());
      return true;
    }

    if (!BootImageWriter.equalTypes(jdkFieldAcc.getType().getName(), rvmFieldType)) {
      System.out.println("Not same field between RVM and jdk " + jdkType.getName() + " " + rvmFieldName);
      if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().push(rvmFieldType.toString(),
                                          jdkType.getName(), rvmFieldName);
      if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().traceFieldDifferentTypeInHostJdk();
      if (BootImageWriter.verbosity().isAtLeast(DETAILED)) BootImageWriter.traceContext().pop();
      BootImageTypes.logStaticFieldHasDifferentTypeInHostJDK(jdkType, rvmField);
      Statics.setSlotContents(rvmFieldOffset, 0);
      if (!VM.runningTool)
        BootImageWriter.bootImage().countNulledReference();
      invalidEntrys.add(jdkType.getName());
      return true;
    }

    if (BootImageWriter.verbosity().isAtLeast(DETAILED))
      say("    populating jtoc slot ", String.valueOf(Statics.offsetAsSlot(rvmFieldOffset)),
          " with ", rvmField.toString());
    if (rvmFieldType.isPrimitiveType()) {
      // field is logical or numeric type
      if (rvmFieldType.isBooleanType()) {
        Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getBoolean(null) ? 1 : 0);
      } else if (rvmFieldType.isByteType()) {
        Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getByte(null));
      } else if (rvmFieldType.isCharType()) {
        Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getChar(null));
      } else if (rvmFieldType.isShortType()) {
        Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getShort(null));
      } else if (rvmFieldType.isIntType()) {
          Statics.setSlotContents(rvmFieldOffset, jdkFieldAcc.getInt(null));
      } else if (rvmFieldType.isLongType()) {
        // note: Endian issues handled in setSlotContents.
        Statics.setSlotContents(rvmFieldOffset,
                                   jdkFieldAcc.getLong(null));
      } else if (rvmFieldType.isFloatType()) {
        float f = jdkFieldAcc.getFloat(null);
        Statics.setSlotContents(rvmFieldOffset,
                                   Float.floatToIntBits(f));
      } else if (rvmFieldType.isDoubleType()) {
        double d = jdkFieldAcc.getDouble(null);
        // note: Endian issues handled in setSlotContents.
        Statics.setSlotContents(rvmFieldOffset,
                                   Double.doubleToLongBits(d));
      } else if (rvmFieldType.equals(TypeReference.Address) ||
                 rvmFieldType.equals(TypeReference.Word) ||
                 rvmFieldType.equals(TypeReference.Extent) ||
                 rvmFieldType.equals(TypeReference.Offset)) {
        Object o = jdkFieldAcc.get(null);
        String msg = " static field " + rvmField.toString();
        boolean warn = rvmFieldType.equals(TypeReference.Address);
        Statics.setSlotContents(rvmFieldOffset, BootImageWriter.getWordValue(o, msg, warn));
      } else {
        fail("unexpected primitive field type: " + rvmFieldType);
      }
    } else {
      // field is reference type
      final Object o = jdkFieldAcc.get(null);
      if (BootImageWriter.verbosity().isAtLeast(ADDRESSES))
        say("setting with " + "  " + jdkType.getName() + " " + rvmField.toString() + " " + rvmFieldOffset + " " + Services.addressAsHexString(Magic.objectAsAddress(o)));
      Statics.setSlotContents(rvmFieldOffset, o);
    }
    return true;
  }

  private static boolean setStaticFieldFromEquivalentField(RVMType rvmType, Class<?> jdkType,
      Offset rvmFieldOffset, String rvmFieldName) throws Error {
    boolean copiedValue = false;
    if (jdkType != null &&
        jdkType.equals(java.util.concurrent.locks.AbstractQueuedSynchronizer.class)) {
      RVMClass c = (RVMClass) rvmType;
      if (rvmFieldName.equals("stateOffset")) {
        Statics.setSlotContents(
          rvmFieldOffset,
          c.findDeclaredField(Atom.findOrCreateAsciiAtom("state")).getOffset().toLong());
        return true;
      } else if (rvmFieldName.equals("headOffset")) {
        Statics.setSlotContents(
          rvmFieldOffset,
          c.findDeclaredField(Atom.findOrCreateAsciiAtom("head")).getOffset().toLong());
        return true;
      } else if (rvmFieldName.equals("tailOffset")) {
        Statics.setSlotContents(
          rvmFieldOffset,
          c.findDeclaredField(Atom.findOrCreateAsciiAtom("tail")).getOffset().toLong());
        return true;
      } else if (rvmFieldName.equals("waitStatusOffset")) {
        try {
        Statics.setSlotContents(
          rvmFieldOffset,
          ((RVMClass)BootImageTypes.getRvmTypeForHostType(Class.forName("java.util.concurrent.locks.AbstractQueuedSynchronizer$Node"))).findDeclaredField(Atom.findOrCreateAsciiAtom("waitStatus")).getOffset().toLong());
        } catch (ClassNotFoundException e) {
          throw new Error(e);
        }
        return true;
      }
    } else if (jdkType != null &&
               jdkType.equals(java.util.concurrent.locks.LockSupport.class)) {
      RVMClass c = (RVMClass) rvmType;
      if (rvmFieldName.equals("parkBlockerOffset")) {
        Statics.setSlotContents(
          rvmFieldOffset,
          ((RVMClass)BootImageTypes.getRvmTypeForHostType(java.lang.Thread.class)).findDeclaredField(Atom.findOrCreateAsciiAtom("parkBlocker")).getOffset().toLong());
        return true;
      }
    }
    return copiedValue;
  }

  private static void nullifyFieldWithUnknownType(HashSet<String> invalidEntrys,
      RVMField rvmField, TypeReference rvmFieldType, Offset rvmFieldOffset,
      String rvmFieldName) {
    // We don't know the type and so can't get an accessor, so nullify
    if (BootImageWriter.verbosity().isAtLeast(DETAILED)) {
      BootImageWriter.traceContext().push(rvmFieldType.toString(),
                        rvmFieldType.toString(), rvmFieldName);
      BootImageWriter.traceContext().traceFieldNotInHostJdk();
      BootImageWriter.traceContext().pop();
    }
    Statics.setSlotContents(rvmFieldOffset, 0);
    BootImageTypes.logMissingFieldWithoutType(rvmField, BootImageWriter.STATIC_FIELD);
    System.out.println("We don't understand jdkType " + rvmFieldType.toString() + " " + rvmFieldName);
    if (!VM.runningTool)
      BootImageWriter.bootImage().countNulledReference();
    invalidEntrys.add(rvmField.getDeclaringClass().toString());
  }

  private static void nullifyFieldWithKnownType(HashSet<String> invalidEntrys,
      Class<?> jdkType, RVMField rvmField, TypeReference rvmFieldType,
      Offset rvmFieldOffset, String rvmFieldName) {
    // we didn't know the field so nullify
    if (BootImageWriter.verbosity().isAtLeast(DETAILED)) {
      BootImageWriter.traceContext().push(rvmFieldType.toString(),
                        jdkType.getName(), rvmFieldName);
      BootImageWriter.traceContext().traceFieldNotInHostJdk();
      BootImageWriter.traceContext().pop();
    }
    BootImageTypes.logMissingField(jdkType, rvmField, BootImageWriter.STATIC_FIELD);
    Statics.setSlotContents(rvmFieldOffset, 0);
    if (!VM.runningTool)
      BootImageWriter.bootImage().countNulledReference();
    invalidEntrys.add(jdkType.getName());
  }

}
