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

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.SimpleElementVisitor6;
import javax.tools.Diagnostic.Kind;

/**
 * A visitor used by {@link ReplaceClassProcessor} to process methods annotated with
 * {@code @ReplaceClass}. The visitor emits warnings and errors when the semantics of
 * {@code ReplaceClass} are violated.
 */
class ReplaceClassVisitor extends SimpleElementVisitor6<Void, Void> {

  private final Messager messager;
  private String targetClassName;
  private String nameOfClassAccordingToSourceFile;

  /**
   * Create a SysCallVisitor that will process <code>@SysCallTemplate</code>
   * annotations.
   * @param messager
   *            {@link Messager} provided by {@link ProcessingEnvironment}
   */
  ReplaceClassVisitor(Messager messager) {
    this.messager = messager;
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

    readTargetClassName(e);

    return null;
  }

  private void readTargetClassName(TypeElement e) {
    AnnotationMirror genImplAnnotation = getMatchingAnnotationsFromElement(
        e, ReplaceClassProcessor.REPLACE_CLASS_ANNOTATION);
    targetClassName = null;

    if (genImplAnnotation != null) {
      targetClassName = getAnnotationElementValue("className()",
          genImplAnnotation);
      nameOfClassAccordingToSourceFile = e.toString();

      if (isEmptyOrNull(targetClassName)) {
        messager.printMessage(
            Kind.ERROR,
            "Class " + e.toString() +
                " did not provide a name for the replaced class in its annotation.");
      } else {
        if (ReplaceClassProcessor.DEBUG_PROCESSOR) {
          messager.printMessage(
              Kind.NOTE,
              "Class " + e.toString() +
                  " is targeting class " + targetClassName);
        }
      }
    }
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

  public String getTargetClassName() {
    return targetClassName;
  }

  public String getNameOfClassAccordingToSourceFile() {
    return nameOfClassAccordingToSourceFile;
  }


}
