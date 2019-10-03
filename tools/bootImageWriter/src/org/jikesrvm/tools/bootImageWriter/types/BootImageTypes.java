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
package org.jikesrvm.tools.bootImageWriter.types;

import static org.jikesrvm.tools.bootImageWriter.BootImageWriterMessages.say;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.DETAILED;
import static org.jikesrvm.tools.bootImageWriter.Verbosity.SUMMARY;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.tools.bootImageWriter.BootImageWriter;
import org.jikesrvm.tools.bootImageWriter.OpenJDKMappingError;

/**
 * Manages information about the mapping between host JDK and boot image types.
 */
public abstract class BootImageTypes {

  /**
   * Types to be placed into bootimage, stored as key/value pairs
   * where key is a String like "java.lang.Object" or "[Ljava.lang.Object;"
   * and value is the corresponding RVMType.
   */
  private static final Hashtable<String,RVMType> bootImageTypes =
    new Hashtable<String,RVMType>(5000);

  /**
   * For all the scalar types to be placed into bootimage, keep
   * key/value pairs where key is a Key(jdkType) and value is
   * a FieldInfo.
   */
  private static HashMap<Key,FieldInfo> bootImageTypeFields;

  private static final Set<MissingField> missing = Collections.synchronizedSet(new HashSet<MissingField>());
  private static final Set<DifferingField> differing = Collections.synchronizedSet(new HashSet<DifferingField>());


  public static void record(String typeName, RVMType type) {
    bootImageTypes.put(typeName, type);
  }

  public static int typeCount() {
    return bootImageTypes.size();
  }

  public static Collection<RVMType> allTypes() {
    return bootImageTypes.values();
  }

  /**
   * Obtains RVM type corresponding to host JDK type.
   *
   * @param jdkType JDK type
   * @return RVM type ({@code null} --> type does not appear in list of classes
   *         comprising bootimage)
   */
  public static RVMType getRvmTypeForHostType(Class<?> jdkType) {
    return bootImageTypes.get(jdkType.getName());
  }

  public static HashSet<String> createBootImageTypeFields() {
    int typeCount = typeCount();
    BootImageTypes.bootImageTypeFields = new HashMap<Key,FieldInfo>(typeCount);
    HashSet<String> invalidEntrys = new HashSet<String>();

    // First retrieve the jdk Field table for each class of interest
    for (RVMType rvmType : allTypes()) {
      FieldInfo fieldInfo;
      if (!rvmType.isClassType())
        continue; // arrays and primitives have no static or instance fields

      Class<?> jdkType = BootImageTypes.getJdkType(rvmType);
      if (jdkType == null) {
        if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) say("Rvm->JDK Null " + rvmType.toString());
        continue;  // won't need the field info
      }

      Key key   = new Key(jdkType);
      fieldInfo = BootImageTypes.bootImageTypeFields.get(key);
      if (fieldInfo != null) {
        fieldInfo.rvmType = rvmType;
      } else {
        if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) say("making fieldinfo for " + rvmType);
        fieldInfo = new FieldInfo(jdkType, rvmType);
        BootImageTypes.bootImageTypeFields.put(key, fieldInfo);
        // Now do all the superclasses if they don't already exist
        // Can't add them in next loop as Iterator's don't allow updates to collection
        for (Class<?> cls = jdkType.getSuperclass(); cls != null; cls = cls.getSuperclass()) {
          key = new Key(cls);
          fieldInfo = BootImageTypes.bootImageTypeFields.get(key);
          if (fieldInfo != null) {
            break;
          } else {
            if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) say("making fieldinfo for " + jdkType);
            fieldInfo = new FieldInfo(cls, null);
            BootImageTypes.bootImageTypeFields.put(key, fieldInfo);
          }
        }
      }
    }
    // Now build the one-to-one instance and static field maps
    for (FieldInfo fieldInfo : BootImageTypes.bootImageTypeFields.values()) {
      RVMType rvmType = fieldInfo.rvmType;
      if (rvmType == null) {
        logInformationAboutMissingTypeIfNecessary(fieldInfo);
        continue;
      }
      Class<?> jdkType   = fieldInfo.jdkType;
      if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) say("building static and instance fieldinfo for " + rvmType);

      // First the static fields
      //
      RVMField[] rvmFields = rvmType.getStaticFields();
      fieldInfo.jdkStaticFields = new Field[rvmFields.length];

      for (int j = 0; j < rvmFields.length; j++) {
        String  rvmName = rvmFields[j].getName().toString();
        for (Field f : fieldInfo.jdkFields) {
          if (f.getName().equals(rvmName)) {
            fieldInfo.jdkStaticFields[j] = f;
            f.setAccessible(true);
            break;
          }
        }
      }

      // Now the instance fields
      //
      rvmFields = rvmType.getInstanceFields();
      fieldInfo.jdkInstanceFields = new Field[rvmFields.length];

      for (int j = 0; j < rvmFields.length; j++) {
        String  rvmName = rvmFields[j].getName().toString();
        // We look only in the JDK type that corresponds to the
        // RVMType of the field's declaring class.
        // This is the only way to correctly handle private fields.
        jdkType = BootImageTypes.getJdkType(rvmFields[j].getDeclaringClass());
        if (jdkType == null) continue;
        FieldInfo jdkFieldInfo = BootImageTypes.bootImageTypeFields.get(new Key(jdkType));
        if (jdkFieldInfo == null) continue;
        Field[] jdkFields = jdkFieldInfo.jdkFields;
        for (Field f : jdkFields) {
          if (f.getName().equals(rvmName)) {
            fieldInfo.jdkInstanceFields[j] = f;
            f.setAccessible(true);
            break;
          }
        }
      }
    }
    if ("openjdk".equals(BootImageWriter.classLibrary())) {
      fixUpTypesForHashMapForOpenJDK6();
    }
    return invalidEntrys;
  }

  private static void logInformationAboutMissingTypeIfNecessary(
      FieldInfo fieldInfo) {
    Class<?> jdkType = fieldInfo.jdkType;
    if (knownToBeMissingOnCurrentHostClassLibrary(jdkType)) {
      if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) {
        say("bootImageTypeField entry has no rvmType: " + jdkType + " but that's ok because we know it's missing in the current host class library");
      }
    } else {
      if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) {
        say("bootImageTypeField entry has no rvmType: " + jdkType);
      }
    }
  }


  /**
   * @param jdkType the JDK type to check
   * @return whether we know that the type is missing in our current implementation of the class library
   */
  private static boolean knownToBeMissingOnCurrentHostClassLibrary(Class<?> jdkType) {
    String name = jdkType.getName();
    if ("java.lang.ReflectiveOperationException".equals(name)) {
      // only present from Java 7 on and we're using Java 6
      return true;
    } else if ("java.lang.reflect.Executable".equals(name)) {
      // only present from Java 8 on and we're using Java 6
      return true;
    } else if ("java.uitl.HashMap$Node".equals(name)) {
      // present in Java 8 but not present in Java 6 (the class
      // was renamed in Java 8 because the internal organization of HashMap
      // was changed)
      return true;
    }
    return false;
  }

  /**
   * Obtains accessor via which a field value may be fetched from host JDK
   * address space.
   *
   * @param jdkType class whose field is sought
   * @param index index in FieldInfo of field sought
   * @param isStatic is field from Static field table, indicates which table to consult
   * @return field accessor (null --> host class does not have specified field)
   */
  public static Field getJdkFieldAccessor(Class<?> jdkType, int index, boolean isStatic) {
    FieldInfo fInfo = bootImageTypeFields.get(new Key(jdkType));
    Field     f;
    if (isStatic == BootImageWriter.STATIC_FIELD) {
      f = fInfo.jdkStaticFields[index];
      return f;
    } else {
      f = fInfo.jdkInstanceFields[index];
      return f;
    }
  }

  /**
   * Obtains host JDK type corresponding to target RVM type.
   *
   * @param rvmType RVM type
   * @return JDK type ({@code null} --> type does not exist in host namespace)
   */
  public static Class<?> getJdkType(RVMType rvmType) {
    Throwable x;
    try {
      return Class.forName(rvmType.toString());
    } catch (ExceptionInInitializerError e) {
      throw e;
    } catch (IllegalAccessError e) {
      x = e;
    } catch (UnsatisfiedLinkError e) {
      x = e;
    } catch (NoClassDefFoundError e) {
      x = e;
    } catch (SecurityException e) {
      x = e;
    } catch (ClassNotFoundException e) {
      x = e;
    }
    if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) {
      say(x.toString());
    }
    return null;
  }

  public static void logMissingField(Class<?> jdkType, RVMField rvmField, boolean instanceField) {
    MissingField mf = new MissingField(jdkType, rvmField, instanceField);
    missing.add(mf);

  }

  public static void logStaticFieldNotStaticInHostJDK(Class<?> jdkType,
      RVMField rvmField) {
    DifferingField df = new DifferingField(jdkType, rvmField, FieldDifference.STATIC_IN_CLASS_LIB_BUT_NOT_STATIC_IN_HOST_JDK);
    differing.add(df);
  }

  public static void logStaticFieldHasDifferentTypeInHostJDK(Class<?> jdkType,
      RVMField rvmField) {
    DifferingField df = new DifferingField(jdkType, rvmField, FieldDifference.STATIC_FIELD_HAS_DIFFERENT_TYPE_IN_HOST_JDK);
    differing.add(df);
  }

  public static void printFieldDifferenceReport() {
    VM.sysWriteln();
    VM.sysWriteln("Missing fields in host JDK report:");
    VM.sysWriteln("------------------------------------------------------------------------------------------");
    ArrayList<String> missingFieldStrings = new ArrayList<String>(missing.size());
    for (MissingField mf : missing) {
      missingFieldStrings.add(mf.toString());
    }
    Collections.sort(missingFieldStrings);;
    for (String s : missingFieldStrings) {
      VM.sysWriteln(s);
    }
    VM.sysWriteln();
    VM.sysWriteln("Differing fields in host JDK report:");
    VM.sysWriteln("------------------------------------------------------------------------------------------");
    ArrayList<String> differingFieldStrings = new ArrayList<String>(differing.size());
    for (DifferingField df : differing) {
      differingFieldStrings.add(df.toString());
    }
    Collections.sort(differingFieldStrings);
    for (String s : differingFieldStrings) {
      VM.sysWriteln(s);
    }
  }

  public static void logMissingFieldWithoutType(RVMField rvmField,
      boolean isStatic) {
    MissingField mf = new MissingField(null, rvmField, isStatic);
    missing.add(mf);
  }

  // Code to fix up differences between OpenJDK 6 (or 7) and OpenJDK 8

  private static void fixUpTypesForHashMapForOpenJDK6()
      throws OpenJDKMappingError {
    ArrayList<FieldInfo> fields = new ArrayList<FieldInfo>(2);
    FieldInfo f1 = attemptToFixUpHashMap();
    FieldInfo f2 = attemptToFixUpHashMapArray();
    if (f1 != null && f2 != null) {
      fields.add(f1);
      fields.add(f2);
    } else {
      return;
    }

    // Now build the one-to-one instance and static field maps for the affected fields
    for (FieldInfo fieldInfo : fields) {
      RVMType rvmType = fieldInfo.rvmType;
      if (rvmType == null) {
        logInformationAboutMissingTypeIfNecessary(fieldInfo);
        continue;
      }
      Class<?> jdkType   = fieldInfo.jdkType;
      if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) say("building static and instance fieldinfo for " + rvmType);

      // First the static fields
      //
      RVMField[] rvmFields = rvmType.getStaticFields();
      fieldInfo.jdkStaticFields = new Field[rvmFields.length];

      for (int j = 0; j < rvmFields.length; j++) {
        String  rvmName = rvmFields[j].getName().toString();
        for (Field f : fieldInfo.jdkFields) {
          if (f.getName().equals(rvmName)) {
            fieldInfo.jdkStaticFields[j] = f;
            f.setAccessible(true);
            break;
          }
        }
      }

      // Now the instance fields
      //
      rvmFields = rvmType.getInstanceFields();
      fieldInfo.jdkInstanceFields = new Field[rvmFields.length];

      for (int j = 0; j < rvmFields.length; j++) {
        String  rvmName = rvmFields[j].getName().toString();
        // We look only in the JDK type that was defined previously.
        // That's the only way to correctly handle the mapping between JDK 8 and JDK 6.
        if (jdkType == null) continue;
        FieldInfo jdkFieldInfo = BootImageTypes.bootImageTypeFields.get(new Key(jdkType));
        if (jdkFieldInfo == null) continue;
        Field[] jdkFields = jdkFieldInfo.jdkFields;
        for (Field f : jdkFields) {
          if (f.getName().equals(rvmName)) {
            fieldInfo.jdkInstanceFields[j] = f;
            f.setAccessible(true);
            break;
          }
        }
      }
    }

    Class<?> linkedHashMapEntryClass = null;
    try {
      linkedHashMapEntryClass = Class.forName("java.util.LinkedHashMap$Entry");
    } catch (ClassNotFoundException e) {
      throw new OpenJDKMappingError(e);
    }
    // Now adjust the fields for LinkedHashMap$Entry which extends HashMap$Node (JDK 8) or HashMap$Entry (JDK 6)
    if (linkedHashMapEntryClass == null) {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return;
    }
    Class<?> hashMapNodeClass = null;
    try {
      hashMapNodeClass = Class.forName("java.util.HashMap$Node");
    } catch (ClassNotFoundException e) {
      throw new OpenJDKMappingError(e);
    }
    if (hashMapNodeClass == null) {
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
      return;
    }
    Key linkedHashMapEntry = new Key(linkedHashMapEntryClass);
    FieldInfo fieldInfo = bootImageTypeFields.get(linkedHashMapEntry);
    if (VM.VerifyAssertions) VM._assert(fieldInfo != null);
    for (int i = 0; i < fieldInfo.jdkInstanceFields.length; i++) {
      Field f = fieldInfo.jdkInstanceFields[i];
      if (BootImageWriter.verbosity().isAtLeast(DETAILED)) say("Processing " + f.getName() + " declared by " + f.getDeclaringClass());
      if (f.getDeclaringClass().toString().contains("java.util.HashMap$Entry")) {
        Field realField = null;
        try {
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) say("Trying to get field with name " + f.getName() + " from " + hashMapNodeClass);
          realField = hashMapNodeClass.getDeclaredField(f.getName());
          realField.setAccessible(true);
          if (BootImageWriter.verbosity().isAtLeast(DETAILED)) say("Found real field " + realField + " declared by " + realField.getDeclaringClass());
        } catch (SecurityException e) {
          throw new OpenJDKMappingError(e);
        } catch (NoSuchFieldException e) {
          throw new OpenJDKMappingError(e);
        }
        if (VM.VerifyAssertions) VM._assert(realField != null);
        fieldInfo.jdkInstanceFields[i] = realField;
      } else {
        if (BootImageWriter.verbosity().isAtLeast(DETAILED)) say("No match: " + f.getDeclaringClass().toString());
      }
    }
  }

  private static FieldInfo attemptToFixUpHashMap() {
    final String hashMapClassInOpenJDK6 = "java.util.HashMap$Entry";
    String hashMapClassInOpenJDK8 = "java.util.HashMap$Node";
    return attemptToFixUpClass(hashMapClassInOpenJDK6, hashMapClassInOpenJDK8);
  }

  private static FieldInfo attemptToFixUpHashMapArray() {
    final String hashMapClassInOpenJDK6 = "[Ljava.util.HashMap$Entry;";
    String hashMapClassInOpenJDK8 = "[Ljava.util.HashMap$Node;";
    return attemptToFixUpClass(hashMapClassInOpenJDK6, hashMapClassInOpenJDK8);
  }

  private static FieldInfo attemptToFixUpClass(final String jdk6Class,
      String jdk8Class) {
    RVMType jdk6RvmType = null;
    for (RVMType rvmType : allTypes()) {
      if (jdk6Class.equals(rvmType.toString())) {
        jdk6RvmType = rvmType;
        break;
      }
    }
    if (jdk6RvmType == null) {
      if (BootImageWriter.verbosity().isAtLeast(SUMMARY))say("No need to map " + jdk6Class + ", apparently not running on OpenJDK6");
      // Apparently not OpenJDK 6
      return null;
    }
    Class<?> jdkType = null;
    try {
      jdkType = Class.forName(jdk8Class);
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      if (VM.VerifyAssertions) VM._assert(VM.NOT_REACHED);
    }
    if (jdkType == null) {
      if (BootImageWriter.verbosity().isAtLeast(SUMMARY))say("Did not find JDK type " + jdk8Class + " corresponding to " + jdk6Class);
      return null;
    }
    Key key   = new Key(jdkType);
    if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) say("making fieldinfo for " + jdk6RvmType);
    FieldInfo fieldInfo = new FieldInfo(jdkType, jdk6RvmType);
    FieldInfo result = fieldInfo;
    BootImageTypes.bootImageTypeFields.put(key, fieldInfo);
    record(jdk8Class, jdk6RvmType);

    // Now do all the superclasses if they don't already exist
    // Can't add them in next loop as Iterator's don't allow updates to collection
    for (Class<?> cls = jdkType.getSuperclass(); cls != null; cls = cls.getSuperclass()) {
      key = new Key(cls);
      fieldInfo = BootImageTypes.bootImageTypeFields.get(key);
      if (fieldInfo != null) {
        break;
      } else {
        if (BootImageWriter.verbosity().isAtLeast(SUMMARY)) say("making fieldinfo for " + jdkType);
        fieldInfo = new FieldInfo(cls, null);
        BootImageTypes.bootImageTypeFields.put(key, fieldInfo);
      }
    }
    return result;
  }
}
