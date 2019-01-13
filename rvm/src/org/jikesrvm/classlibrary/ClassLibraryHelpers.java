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
package org.jikesrvm.classlibrary;

import static org.jikesrvm.classloader.ClassLoaderConstants.*;
import org.jikesrvm.VM;
import org.jikesrvm.classloader.Atom;
import org.jikesrvm.classloader.MemberReference;
import org.jikesrvm.classloader.RVMClass;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.RVMMethod;
import org.jikesrvm.classloader.RVMType;
import org.jikesrvm.classloader.TypeReference;
import org.jikesrvm.runtime.Reflection;
import org.jikesrvm.runtime.RuntimeEntrypoints;

// TODO decide where to put this code before merging the OpenJDK branch
public class ClassLibraryHelpers {

  /**
   * Field name for the field in {@link Class} that contains the {@link RVMType}
   * for the class.
   * <p>
   * For now, we use the same name for this field as in GNU Classpath in order to
   * reuse the code for setup (e.g. in the boot image writer when copying Class objects).
   */
  private static final String RVM_TYPE_FIELD_NAME_FOR_JAVA_LANG_CLASS = "type";
  private static final String PROTECTIOND_DOMAIN_TYPE_FIELD_NAME_FOR_JAVA_LANG_CLASSS = "pd";

  public static RVMField rvmThreadField;
  public static RVMField rvmTypeField;
  public static RVMField protectionDomainField;

  /**
   * Allocates an object of the given class and runs the no-arg constructor
   * (even if that constructor is private).
   *
   * @param clazz
   *          clazz to be instantiated
   * @return an object of the given class
   */
  @SuppressWarnings("unchecked")
  public static <T> T allocateObjectForClassAndRunNoArgConstructor(
      Class<T> clazz) {
    RVMClass rvmClass = JikesRVMSupport.getTypeForClass(clazz).asClass();
    RVMMethod[] constructors = rvmClass.getConstructorMethods();
    RVMMethod noArgConst = null;
    for (RVMMethod constructor : constructors) {
      if (constructor.getParameterTypes().length == 0) {
        noArgConst = constructor;
        break;
      }
    }
    if (VM.VerifyAssertions)
      VM._assert(noArgConst != null, "didn't find any no-arg constructor");
    T systemThreadGroup = (T) RuntimeEntrypoints.resolvedNewScalar(rvmClass);
    Reflection.invoke(noArgConst, null, systemThreadGroup, null, true);
    return systemThreadGroup;
  }

  public static RVMField[] modifyDeclaredFields(RVMField[] declaredFields, TypeReference typeRef) {
    if (typeRef == TypeReference.findOrCreate(java.lang.Thread.class)) {
      RVMField rvmThreadField = createField(typeRef, "Lorg/jikesrvm/scheduler/RVMThread;", "rvmThread");
      RVMField[] newDeclaredFields = new RVMField[declaredFields.length + 1];
      System.arraycopy(declaredFields, 0, newDeclaredFields, 0, declaredFields.length);
      newDeclaredFields[newDeclaredFields.length - 1] = rvmThreadField;
      ClassLibraryHelpers.rvmThreadField = rvmThreadField;
      if (VM.TraceClassLoading) VM.sysWriteln("Added rvmThread field to java.lang.Thread");
      return newDeclaredFields;
    } else if (typeRef == TypeReference.findOrCreate(java.lang.Class.class)) {
      // TODO this really should be final, but that would mean we'd have to add a constructor, too
      RVMField rvmTypeField = createField(typeRef, "Lorg/jikesrvm/classloader/RVMType;", RVM_TYPE_FIELD_NAME_FOR_JAVA_LANG_CLASS);
      RVMField pdField = createField(typeRef, "Ljava/security/ProtectionDomain;", PROTECTIOND_DOMAIN_TYPE_FIELD_NAME_FOR_JAVA_LANG_CLASSS);
      RVMField[] newDeclaredFields = new RVMField[declaredFields.length + 2];
      System.arraycopy(declaredFields, 0, newDeclaredFields, 0, declaredFields.length);
      newDeclaredFields[newDeclaredFields.length - 2] = rvmTypeField;
      newDeclaredFields[newDeclaredFields.length - 1] = pdField;
      ClassLibraryHelpers.rvmTypeField = rvmTypeField;
      ClassLibraryHelpers.protectionDomainField = pdField;
      if (VM.TraceClassLoading) VM.sysWriteln("Added " + RVM_TYPE_FIELD_NAME_FOR_JAVA_LANG_CLASS + " field to java.lang.Class");
      if (VM.TraceClassLoading) VM.sysWriteln("Added " + PROTECTIOND_DOMAIN_TYPE_FIELD_NAME_FOR_JAVA_LANG_CLASSS + " field to java.lang.Class");
      return newDeclaredFields;
    }

    return declaredFields;
  }

  private static RVMField createField(TypeReference typeRef,
      String descriptorString, String fieldNameString) {
    short modifiers = ACC_SYNTHETIC | ACC_PRIVATE;
    Atom fieldName = Atom.findOrCreateUnicodeAtom(fieldNameString);
    Atom fieldDescriptor = Atom.findOrCreateUnicodeAtom(descriptorString);
    MemberReference memRef = MemberReference.findOrCreate(typeRef, fieldName, fieldDescriptor);
    RVMField newField = RVMField.createSyntheticFieldForReplacementClass(typeRef, modifiers, fieldName, null, memRef);
    return newField;
  }

}
