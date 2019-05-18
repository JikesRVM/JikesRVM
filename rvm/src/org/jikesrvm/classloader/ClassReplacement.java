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
package org.jikesrvm.classloader;

import org.jikesrvm.VM;
import org.jikesrvm.classlibrary.AbstractReplacementClasses;
import org.jikesrvm.util.HashMapRVM;
import org.jikesrvm.util.HashSetRVM;
import org.jikesrvm.util.ImmutableEntryHashSetRVM;

/**
 * Provides methods to implement replacement of methods and fields of classes.
 */
public class ClassReplacement {

  private static final String REPLACEMENT_CLASS_NAME_PREFIX = "org.jikesrvm.classlibrary.openjdk.replacements.";

  private AbstractReplacementClasses replacementClasses;
  private HashSetRVM<String> remainingReplacementClassNames;
  private HashMapRVM<String, String> mapOfTargetClassToSourceClass;

  private final ImmutableEntryHashSetRVM<String> loadedReplacementClasses = new ImmutableEntryHashSetRVM<String>();
  private final ImmutableEntryHashSetRVM<String> classesCheckedForReplacements = new ImmutableEntryHashSetRVM<String>();

  private final BootstrapClassLoader bootstrapCL;

  public ClassReplacement(BootstrapClassLoader bootstrapCL) {
    this.bootstrapCL = bootstrapCL;
    if (VM.BuildForOpenJDK) {
      try {
        this.replacementClasses =       (AbstractReplacementClasses)Class.forName("org.jikesrvm.classlibrary.ReplacementClasses").newInstance();
      } catch (ExceptionInInitializerError e) {
        throw new Error("Throwable during construction of BootstrapClassLoader", e);
      } catch (SecurityException e) {
        throw new Error("Throwable during construction of BootstrapClassLoader", e);
      } catch (IllegalAccessException e) {
        throw new Error("Throwable during construction of BootstrapClassLoader", e);
      } catch (InstantiationException e) {
        throw new Error("Throwable during construction of BootstrapClassLoader", e);
      } catch (ClassNotFoundException e) {
        throw new Error("Throwable during construction of BootstrapClassLoader", e);
      }
      remainingReplacementClassNames = replacementClasses.getNamesOfClassesWithReplacements();
      mapOfTargetClassToSourceClass = replacementClasses.getMapOfTargetClassToSourceClass();
    } else {
      remainingReplacementClassNames = new HashSetRVM<String>(1);
      mapOfTargetClassToSourceClass = new HashMapRVM<String, String>(1);
    }
  }

  public void attemptToLoadReplacementClassIfNeededForVmClass(String className)
      throws NoClassDefFoundError, ClassNotFoundException {
    if (remainingReplacementClassNames.contains(className)) {
      if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: " + className + " has a replacement!");
    } else {
      if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: " + className + " has NO replacement!");
      return;
    }

    Atom classNameAtom = null;
    String replacementClassName = null;
    try {
      // TODO OPENJDK/ICEDTEA Adopt better nomenclature for replacement classes
      classNameAtom = Atom.findOrCreateAsciiAtom(className);
      replacementClassName = toReplacementClassNameAtom(className);
      if (VM.VerifyAssertions) VM._assert(replacementClassName != null);
      // TODO OPENJDK/ICEDTEA needs a method to convert class names to descriptors and vice versa. possibly in atom if it doesn't exist yet
      Atom replacementClassDescriptor = Atom.findOrCreateAsciiAtom("L" + replacementClassName.toString().replace('.', '/') + ";");

      boolean isReplacementClass = className.startsWith(REPLACEMENT_CLASS_NAME_PREFIX);

      if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: " + className + " is a replacement class itself and thus won't be checked for a replacement class: " + isReplacementClass);
      if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: " + className + " is a bootstrap class descriptor? " + replacementClassDescriptor.isBootstrapClassDescriptor());
      if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: " + className + " has been checked for replacement? " + classesCheckedForReplacements.contains(className));
      if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: " + className + " would have replacement class name: " + replacementClassName);
      if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: " + className + " would have replacement class descriptor: " + replacementClassDescriptor);

      boolean alreadyTriedToLoadReplacementClass = classesCheckedForReplacements.contains(className);
      // Check if there has been an attempt to load the replacement JDK class of this name
      if (!isReplacementClass && !alreadyTriedToLoadReplacementClass) {
        classesCheckedForReplacements.add(className);
        if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: Checking for replacement class for " + className + " named " + replacementClassName);

        if (VM.VerifyAssertions) VM._assert(!VM.runningVM);
        // Load, resolve and instantiate the replacement class if it exists.
        if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: About to call loadVMClass for replacement class for " + className + " named " + replacementClassName);
        RVMType loadVMClass = bootstrapCL.loadVMClass(replacementClassName.toString());
        if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: Finished with call loadVMClass for replacement class for " + className + " named " + replacementClassName);
        loadVMClass.resolve();
        loadedReplacementClasses.add(replacementClassName);
        remainingReplacementClassNames.remove(className);
        if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: Replacement class " + replacementClassName + " loaded for " + className);
      } else {
        if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: NOT LOADING replacement class for " + className + " named " + replacementClassName);
        if (VM.TraceClassLoading) VM.sysWriteln("alreadyTriedToLoadReplacementClass: " + alreadyTriedToLoadReplacementClass);
        if (VM.TraceClassLoading) VM.sysWriteln("is replacement class: " + isReplacementClass);
      }
    } catch (NoSuchMethodError e) {
        if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: Failed to load replacement class " + replacementClassName + " for " + classNameAtom);
        if (VM.TraceClassLoading) VM.sysWriteln(e.toString());
        if (VM.TraceClassLoading) e.printStackTrace();
        throw new ClassNotFoundException("Failed to load replacement class " + replacementClassName, e);
    } catch (NoClassDefFoundError e) {
      if (VM.TraceClassLoading) VM.sysWriteln("ClassReplacement_VMClass: Failed to load replacement class " + replacementClassName + " for " + classNameAtom);
      if (VM.TraceClassLoading) VM.sysWriteln(e.toString());
      if (VM.TraceClassLoading) e.printStackTrace();
    }
  }

  public String toReplacementClassNameAtom(String className) {
    return mapOfTargetClassToSourceClass.get(className);
  }

}
