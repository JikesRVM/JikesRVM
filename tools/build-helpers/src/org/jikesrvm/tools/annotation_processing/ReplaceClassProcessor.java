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
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;

/**
 * Processes the {@link org.vmmagic.pragma.ReplaceClass @ReplaceClass}
 * annotation.
 */
@SupportedSourceVersion(value = SourceVersion.RELEASE_6)
public class ReplaceClassProcessor extends AbstractProcessor {

  static final String REPLACE_CLASS_ANNOTATION =
      "org.vmmagic.pragma.ReplaceClass";

  static final String REPLACE_MEMBER_ANNOTATION =
      "org.vmmagic.pragma.ReplaceMember";

  static final boolean DEBUG_PROCESSOR = false;

  private Set<String> classNames = new TreeSet<String>();
  private Map<String, String> sourceNameToClassName = new TreeMap<String, String>();

  private ProcessingEnvironment processingEnv;
  private Messager messager;

  private TypeElement replaceClassAnnotation;

  private ReplaceClassGeneratedFileWriter fileWriter;

  /**
   * @return an unmodifiable set containing the supported annotation types. The
   *         supported annotation types are built in.
   */
  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return Collections.singleton(REPLACE_CLASS_ANNOTATION);
  }

  @Override
  public boolean process(Set<? extends TypeElement> types,
      RoundEnvironment roundEnv) {
    if (roundEnv.processingOver()) {
      if (DEBUG_PROCESSOR) {
        messager.printMessage(Kind.NOTE, "Processing is over, found the following class names: " + Arrays.toString(classNames.toArray()));
      }

      // No cleanup is necessary for this processor, so just return
      return false;
    }

    if (types.isEmpty()) {
      if (DEBUG_PROCESSOR) {
        messager.printMessage(Kind.NOTE, "No types found to process for @ReplaceClass, returning early");
      }
      return false;
    }

    if (replaceClassAnnotation == null || !types.contains(
        replaceClassAnnotation)) {
      if (DEBUG_PROCESSOR) {
        messager.printMessage(Kind.NOTE, "@ReplaceClass annotation not available so no processing can start");
      }
      return false;
    }

    Set<? extends Element> canidatesForProcessing = roundEnv
        .getElementsAnnotatedWith(replaceClassAnnotation);
    processCandidates(canidatesForProcessing);
    generateClassFile();

    if (DEBUG_PROCESSOR) {
      messager.printMessage(Kind.NOTE, "Finished processing all candidates");
    }


    return true;
  }

  public void generateClassFile() {
    String packageName = "org.jikesrvm.classlibrary";
    String unqualifiedName = "ReplacementClasses";
    String fullyQualifiedName = packageName + "." + unqualifiedName;
    TypeElement sourceElement = processingEnv.getElementUtils().getTypeElement("org.jikesrvm.classlibrary.AbstractReplacementClasses");
    fileWriter.startSourceFileForGeneratedImplementation(fullyQualifiedName, sourceElement);
    try {
      fileWriter.addPackageDeclaration(packageName);
      fileWriter.addImports();
      fileWriter.addClassAnnotations(sourceElement);
      fileWriter.addClassName(sourceElement, unqualifiedName);
      fileWriter.addMethodImplementationForGetNamesOfClassesWithReplacements(classNames);
      fileWriter.addMethodImplementationForGetMapOfTargetClassToSourceClass(sourceNameToClassName);
      fileWriter.finishSourceFileForGeneratedImplementation();

    } catch (IOException e) {
      messager.printMessage(Kind.ERROR,
          "Error during writing of source file.");
      messager.printMessage(Kind.ERROR, e.getMessage());

      fileWriter.giveUp();
    }
  }

  private void processCandidates(
      Set<? extends Element> canidatesForProcessing) {
    if (canidatesForProcessing.isEmpty()) {
      messager.printMessage(Kind.NOTE, "No candidates");
    }

    for (Element e : canidatesForProcessing) {
      processType(e);
    }
  }

  private void processType(Element e) {
    if (e.getKind().isClass()) {
      if (DEBUG_PROCESSOR) {
        messager.printMessage(Kind.NOTE, "Processing class " + e.toString());
      }
      ReplaceClassVisitor visitor = new ReplaceClassVisitor(messager);
      e.accept(visitor, null);
      String targetClassName = visitor.getTargetClassName();
      classNames.add(targetClassName);
      String sourceName = visitor.getNameOfClassAccordingToSourceFile();
      sourceNameToClassName.put(targetClassName, sourceName);
    }
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    this.processingEnv = processingEnv;
    super.init(processingEnv);
    this.messager = processingEnv.getMessager();
    if (DEBUG_PROCESSOR) {
      messager.printMessage(Kind.NOTE, "Initializing processor for @ReplaceClass");
    }
    this.fileWriter = new ReplaceClassGeneratedFileWriter(this.getClass().getCanonicalName(), messager, processingEnv.getFiler());
    replaceClassAnnotation = processingEnv.getElementUtils().getTypeElement(
        REPLACE_CLASS_ANNOTATION);
  }

}
