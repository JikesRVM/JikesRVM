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
package org.jikesrvm.tools.annotation_processing;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.tools.Diagnostic.Kind;

/**
 * A visitor used by {@link SysCallProcessor} to process methods annotated with
 * <code>@SysCallTemplate</code>. The enclosing class of the methods must be
 * annotated with <code>@GenerateImplementation</code>.
 */
class SysCallVisitor extends SimpleElementVisitor6<Void, Void> {

  private static final String STANDARD_SUFFIX = "Impl";

  private final Messager messager;

  private GeneratedFileWriter generatedFileWriter;
  private Elements elementUtils;

  /**
   * Create a SysCallVisitor that will process <code>@SysCallTemplate</code>
   * annotations.
   *
   * @param generatingClass
   *            the name of the class that generates the implementation, e.g.
   *            "SysCallProcessor"
   * @param filer
   *            {@link Filer} provided by {@link ProcessingEnvironment}
   * @param messager
   *            {@link Messager} provided by {@link ProcessingEnvironment}
   * @param elementUtils
   *            {@link Elements} provided by {@link ProcessingEnvironment}
   */
  SysCallVisitor(String generatingClass, Filer filer, Messager messager,
      Elements elementUtils) {
    this.messager = messager;
    this.elementUtils = elementUtils;
    this.generatedFileWriter = new GeneratedFileWriter(generatingClass, messager, filer);
  };

  /**
   * Visit a {@link TypeElement} of {@link ElementKind#CLASS} and generate an
   * implementation for it. The implementation will subclass the visited
   * class.<br>
   * <br>
   * If an {@link ExecutableElement} with another {@link ElementKind} than
   * {@link ElementKind#CLASS} is visited, the method returns immediately.
   */
  @Override
  public Void visitType(TypeElement e, Void p) {
    if (!e.getKind().isClass()) {
      return null;
    }

    String nameOfGeneratedClass = getNameOfGeneratedClass(e);

    String packageName = getPackageName(nameOfGeneratedClass, e);
    String className = getClassName(nameOfGeneratedClass, e);

    try {
      generatedFileWriter.startSourceFileForGeneratedImplementation(nameOfGeneratedClass, e);
      generatedFileWriter.addPackageDeclaration(packageName);
      generatedFileWriter.addImports();
      generatedFileWriter.addClassAnnotations(e);
      generatedFileWriter.addClassName(e, className);

      List<ExecutableElement> methods = ElementFilter.methodsIn(e
          .getEnclosedElements());
      for (ExecutableElement method : methods) {
        method.accept(this, null);
      }

      generatedFileWriter.finishSourceFileForGeneratedImplementation();

    } catch (IOException ioException) {
      messager.printMessage(Kind.ERROR,
          "Error during writing of source file.");
      messager.printMessage(Kind.ERROR, ioException.getMessage());

      generatedFileWriter.giveUp();
    }

    return null;
  }

  private String getNameOfGeneratedClass(TypeElement e) {
    AnnotationMirror genImplAnnotation = getMatchingAnnotationsFromElement(
        e, SysCallProcessor.GEN_IMPL_ANNOTATION);
    String generatedClassName;

    if (genImplAnnotation != null) {
      generatedClassName = getAnnotationElementValue("value()",
          genImplAnnotation);

      if (isEmptyOrNull(generatedClassName)) {
        generatedClassName = getStandardNameForGeneratedClass(e);
        messager.printMessage(
            Kind.WARNING,
            "Class " + e.toString() +
                " did not provide a name for the generated class in its annotation. The following name will be used: " +
                generatedClassName);
      }
    } else {
      generatedClassName = getStandardNameForGeneratedClass(e);
      messager.printMessage(Kind.WARNING,
          "Could not find @GenerateImplementation annotation for Class " +
              e.toString() + " . The following name will be used: " +
              generatedClassName);

    }

    return generatedClassName;
  }

  private AnnotationMirror getMatchingAnnotationsFromElement(Element element,
      String fullyQualifiedAnnotationName) {
    List<? extends AnnotationMirror> annotationsOnElement = element
        .getAnnotationMirrors();
    for (AnnotationMirror annotationMirror : annotationsOnElement) {
      DeclaredType declType = annotationMirror.getAnnotationType();
      String fullyQualifiedName = declType.toString();
      if (fullyQualifiedName.equals(fullyQualifiedAnnotationName)) {
        return annotationMirror;
      }
    }

    return null;
  }

  private String getAnnotationElementValue(String elementValue,
      AnnotationMirror am) {
    Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = am
        .getElementValues();
    for (Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elementValues
        .entrySet()) {
      ExecutableElement element = entry.getKey();
      if (element.toString().equals(elementValue)) {
        AnnotationValue annotationValue = entry.getValue();
        return (String) annotationValue.getValue();
      }
    }

    return null;
  }

  private boolean isEmptyOrNull(String generatedClassName) {
    return generatedClassName == null ||
        generatedClassName.trim().isEmpty();
  }

  private String getStandardNameForGeneratedClass(TypeElement e) {
    return e.getQualifiedName().toString() + STANDARD_SUFFIX;
  }

  private String getPackageName(String className, TypeElement classElement) {
    int lastDot = className.lastIndexOf(".");

    if (lastDot > 0) {
      return className.substring(0, lastDot);
    }

    return elementUtils.getPackageOf(classElement).getQualifiedName()
        .toString();
  }

  private String getClassName(String className, TypeElement classElement) {
    int lastDot = className.lastIndexOf(".");

    if (lastDot > 0) {
      return className.substring(lastDot + 1);
    }

    return className;
  }

  /**
   * Visit an {@link ExecutableElement} of {@link ElementKind#METHOD} that is
   * annotated with <code>@SysCallTemplate</code> and generate the public
   * instance method that dispatches to the native method, and the native
   * method that is the SysCall itself.<br>
   * <br>
   * If an {@link ExecutableElement} with another {@link ElementKind} than
   * {@link ElementKind#METHOD} is visited, <code>@SysCallTemplate</code>
   * cannot be present, so the method returns immediately.
   */
  @Override
  public Void visitExecutable(ExecutableElement e, Void p) {
    if (!e.getKind().equals(ElementKind.METHOD)) {
      return null;
    }

    AnnotationMirror sysCallAnnotation = getMatchingAnnotationsFromElement(
        e, SysCallProcessor.SYSCALL_TEMPLATE_ANNOTATION);
    if (sysCallAnnotation == null) {
      return null;
    }

    try {
      generatedFileWriter.processMethod(e);
    } catch (IOException ioException) {
      Name methodName = e.getSimpleName();
      messager.printMessage(Kind.ERROR,
          "Error during processing of method: " + methodName);
      messager.printMessage(Kind.ERROR, ioException.getMessage());
    }
    return null;
  }

}
