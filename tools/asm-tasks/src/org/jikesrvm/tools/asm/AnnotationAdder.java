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
package org.jikesrvm.tools.asm;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodAdapter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.AnnotatedElement;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jikesrvm.classloader.VM_ClassLoader;
import org.jikesrvm.classloader.VM_BootstrapClassLoader;

import org.vmmagic.pragma.Pure;

/**
 * Add annotations to classes using the ASM framework.
 */
public final class AnnotationAdder {
  /**
   * Elements we're looking to adapt and the annotations we want adding to them
   */
  private static final Map<AnnotatedElement, Set<Class<? extends Annotation>>> thingsToAnnotate =
    new HashMap<AnnotatedElement, Set<Class<? extends Annotation>>>();

  private static String destinationDir;

  /**
   * Add annotation to element
   * @param ann annotation to add
   * @param elem element to add it to
   */
  private static void addToAdapt(Class<? extends Annotation> ann, AnnotatedElement elem) {
    if (elem == null)
      throw new Error("Can't adapt a null element");
    if (ann == null)
      throw new Error("Can't annotate with null");
    Set<Class<? extends Annotation>> set = new HashSet<Class<? extends Annotation>>();
    set.add(ann);
    thingsToAnnotate.put(elem, set);
  }

  /* Class constructor - set up things to adapt */
  static {
    try {
      addToAdapt(Pure.class, Integer.class.getMethod("toString", new Class[]{int.class, int.class}));
      addToAdapt(Pure.class, Integer.class.getMethod("valueOf", new Class[]{int.class}));
    } catch (NoSuchMethodException e) {
      throw new Error(e);
    }
  }

  /**
   * Find the annotations to add to the given method
   * @param className name of class we're adding annotation to
   * @param methodName name of method we're adding annotation to
   * @param methodDesc descriptor of method
   * @return set on annotations to add to method or null if none
   */
  static Set<Class<? extends Annotation>> findAnnotationsForMethod(String className, String methodName, String methodDesc) {
    for (AnnotatedElement elem: thingsToAnnotate.keySet()) {
      if (elem instanceof Method) {
        Method m = (Method)elem;
        if (m.getName().equals(methodName) && Type.getMethodDescriptor(m).equals(methodDesc)) {
          return thingsToAnnotate.get(m);
        }
      }
    }
    return null;
  }

  /**
   * Main entry point
   * @param args args[0] is the classpath to use to read classes, args[1] is the destination directory
   */
  public static void main(final String[] args) {
    Set<Class<?>> processedClasses = new HashSet<Class<?>>();

    VM_ClassLoader.init(args[0]);
    destinationDir = args[1] + "/";

    for(AnnotatedElement elem: thingsToAnnotate.keySet()) {
      Class<?> c = getClassForElement(elem);
      if (!processedClasses.contains(c)) {
        adaptClass(c.getName());
        processedClasses.add(c);
      }
    }
  }

  /**
   * Given an annotated element return the class that declares it
   * @param elem the annotated element
   * @return the declaring class or null if the declaring class is unknown
   */
  private static Class<?> getClassForElement(AnnotatedElement elem) {
    if (elem instanceof Method) {
      return ((Method)elem).getDeclaringClass();
    }
    return null;
  }
  /**
   * Chain together a ClassReader than will read the given class and a
   * ClassWriter to write out a new class with an adapter in the middle to add
   * annotations
   * @param fromName the name of the class we're coming from
   */
  private static void adaptClass(String fromName) {
    System.out.println("Adding annotations to class: " + fromName);

    // gets an input stream to read the bytecode of the class
    String resource = fromName.replace('.', '/') + ".class";
    InputStream is = VM_BootstrapClassLoader.getBootstrapClassLoader().getResourceAsStream(resource);
    byte[] b;

    // adapts the class on the fly
    try {
      ClassReader cr = new ClassReader(is);
      ClassWriter cw = new ClassWriter(0);
      ClassVisitor cv = new AddAnnotationClassAdapter(cw, fromName);
      cr.accept(cv, 0);
      b = cw.toByteArray();
    } catch (Exception e) {
      throw new Error("Couldn't find class " + fromName + " (" + resource + ")", e);
    }

    // store the adapted class on disk
    try {
      File file = new File(destinationDir + resource);
      new File(file.getParent()).mkdirs(); // ensure we have a directory to write to
      FileOutputStream fos = new FileOutputStream(file);
      fos.write(b);
      fos.close();
    } catch (Exception e) {
      throw new Error("Error writing to " + destinationDir + resource + " to disk", e);
    }
  }

  /**
   * Class responsible for processing classes and adding annotations
   */
  private static final class AddAnnotationClassAdapter extends ClassAdapter implements Opcodes {
    /** name of class */
    private final String className;

    /**
     * Constructor
     * @param cv the reader of the class
     * @param name the name of the class being read
     */
    public AddAnnotationClassAdapter(ClassVisitor cv, String name) {
      super(cv);
      this.className = name;
    }

    /**
     * Called when adapting a method. Determine whether we need to add an
     * annotation and if so vary the method visitor result
     * @param access flags
     * @param name of method
     * @param desc descriptor
     * @param signature generic signature
     * @param exceptions exceptions thrown
     * @return regular method visitor or one that will apply annotations
     */
    @Override
    public MethodVisitor visitMethod(final int access, final String name,
        final String desc, final String signature, final String[] exceptions) {
      MethodVisitor mv = cv.visitMethod(access, name, desc, signature, exceptions);
      if (mv != null) {
        Set<Class<? extends Annotation>> annotations = findAnnotationsForMethod(className, name, desc);
        if (annotations != null) {
          System.out.println("Found method: " + name);
          return new AddAnnotationMethodAdapter(mv, annotations);
        }
      }
      return mv;
    }
  }

  /**
   * Class responsible for processing methods and adding annotations
   */
  private static final class AddAnnotationMethodAdapter extends MethodAdapter implements Opcodes {
    /** Annotations to add to method */
    private final Set<Class<? extends Annotation>> toAddAnnotations;
    /** Annotations found on method */
    private final Set<String> presentAnnotations = new HashSet<String>();
    /**
     * Constructor
     * @param mv the reader of the method
     * @param anns annotations to add
     */
    public AddAnnotationMethodAdapter(MethodVisitor mv, Set<Class<? extends Annotation>> anns) {
      super(mv);
      toAddAnnotations = anns;
    }

    /**
     * Visit annotation remembering what we see so that we don't add the same
     * annotation twice
     * @param desc descriptor of annotation
     * @param visible is it runtime visible
     * @return default annotation reader
     */
    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      presentAnnotations.add(desc);
      System.out.println("Found annotation: " + desc);
      return mv.visitAnnotation(desc, visible);
    }

    /**
     * We've finished processing method, check what annotations were found and
     * then add those that weren't
     */
    @Override
    public void visitEnd() {
      outer:
      for (Class<? extends Annotation> toAddAnn : toAddAnnotations) {
        Type toAddAnnType = Type.getType(toAddAnn);
        System.out.println("Annotation: " + toAddAnn);
        for (String presentAnn : presentAnnotations) {
          if (toAddAnnType.equals(Type.getType(presentAnn))) {
            System.out.println("Annotation already present: " + toAddAnn + " " + presentAnn);
            continue outer;
          }
        }
        System.out.println("Adding annotation: " + toAddAnn);
        mv.visitAnnotation("L"+toAddAnnType.getInternalName()+";", true);
      }
      mv.visitEnd();
    }
  }
}
