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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;

/**
 * Process the '@SysCallTemplate' annotation. Generates an implementation class
 * for methods annotated with this, that provides an indirection to a native
 * method annotated with @SysCall.
 */
@SupportedSourceVersion(value = SourceVersion.RELEASE_6)
public final class SysCallProcessor extends AbstractProcessor {

  static final String GEN_IMPL_ANNOTATION = "org.jikesrvm.annotations.GenerateImplementation";
  static final String SYSCALL_TEMPLATE_ANNOTATION = "org.jikesrvm.annotations.SysCallTemplate";

  private Elements elementUtils;

  @Override
  public boolean process(Set<? extends TypeElement> annotations,
      RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()){
      // No cleanup is necessary for this processor, so just return
      return false;
    }

    if (annotations.isEmpty()) {
      return false;
    }

    TypeElement generateImplementationAnnotation = elementUtils
        .getTypeElement(GEN_IMPL_ANNOTATION);
    if (generateImplementationAnnotation == null ||
        !annotations.contains(generateImplementationAnnotation)) {
      return false;
    }

    Set<? extends Element> canidatesForProcessing = roundEnv
        .getElementsAnnotatedWith(generateImplementationAnnotation);
    processCandidates(canidatesForProcessing);

    return true;
  }

  private void processCandidates(Set<? extends Element> canidatesForProcessing) {
    String canonicalName = this.getClass().getCanonicalName();
    Filer filer = processingEnv.getFiler();
    Messager messager = processingEnv.getMessager();

    for (Element element : canidatesForProcessing) {
      if (element.getKind().isClass()) {

        SysCallVisitor visitor = new SysCallVisitor(canonicalName, filer,
            messager, elementUtils);
        element.accept(visitor, null);
      }
    }
  }

  /**
   * Returns an unmodifiable set containing the supported annotation types.
   * The supported annotation types are built in.
   */
  @Override
  public Set<String> getSupportedAnnotationTypes() {
    Set<String> supportedAnnotationTypes = new HashSet<String>();
    supportedAnnotationTypes.add(GEN_IMPL_ANNOTATION);
    supportedAnnotationTypes.add(SYSCALL_TEMPLATE_ANNOTATION);
    return Collections.unmodifiableSet(supportedAnnotationTypes);
  }

  /**
   * Initialize the processor and obtain instances of {@link Messager} and
   * {@link Elements} that are needed for annotation processing.
   */
  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    elementUtils = processingEnv.getElementUtils();
    super.init(processingEnv);
  }

}
