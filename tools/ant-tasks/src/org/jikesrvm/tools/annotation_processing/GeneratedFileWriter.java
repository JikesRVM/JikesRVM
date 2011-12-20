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
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

/**
 * Used by {@link SysCallVisitor} to write the generated source file.
 *
 */
class GeneratedFileWriter {

  private Filer filer;

  private JavaFileObject fileObject;

  private Writer writer;

  private String generatingClass;

  private Messager messager;

  private int currentIndentationLevel;

  /**
   *
   * @param generatingClass
   *            the name of the class that generates the implementation, e.g.
   *            "SysCallProcessor"
   * @param messager
   *            {@link Messager} provided by {@link ProcessingEnvironment}
   * @param filer
   *            {@link Filer} provided by {@link ProcessingEnvironment}
   */
  GeneratedFileWriter(String generatingClass, Messager messager, Filer filer) {
    this.generatingClass = generatingClass;
    this.messager = messager;
    this.filer = filer;
  }

  /**
   * Start a source file for a new implementation class.
   *
   * @param nameOfGeneratedClass
   *            the name of the class that is being generated
   * @param sourceClass
   *            the class that the implementation is being generated for. This
   *            will be used as originating element in
   *            {@link Filer#createSourceFile(CharSequence, Element...)}
   */
  void startSourceFileForGeneratedImplementation(String nameOfGeneratedClass,
      TypeElement sourceClass) {
    messager.printMessage(Kind.NOTE, "Creating class " + nameOfGeneratedClass);

    fileObject = getSourceFile(nameOfGeneratedClass, sourceClass);
    if (fileObject == null) {
      giveUp();
      return;
    }

    writer = getWriter(fileObject);
    if (writer == null) {
      giveUp();
      return;
    }

    currentIndentationLevel = 0;
  }

  private JavaFileObject getSourceFile(String nameOfGeneratedClass,
      TypeElement typeElement) {
    JavaFileObject sourceFile = null;
    try {
      sourceFile = filer.createSourceFile(nameOfGeneratedClass,
          typeElement);
    } catch (IOException e) {
      messager.printMessage(Kind.ERROR, "Error creating source file " +
          nameOfGeneratedClass);
      messager.printMessage(Kind.ERROR, e.getMessage());
    }
    return sourceFile;
  }

  void giveUp() {
    messager.printMessage(Kind.ERROR, "Could not generate class, giving up");
    if (fileObject != null) {
      messager.printMessage(Kind.NOTE,
          "Attempting to delete fileObject ...");
      boolean success = fileObject.delete();
      String result = success ? "suceeded" : "failed";
      messager.printMessage(Kind.NOTE, "Deletion " + result);
    }
  }

  private Writer getWriter(JavaFileObject sourceFile) {
    Writer writer = null;
    try {
      writer = sourceFile.openWriter();
    } catch (IOException e) {
      messager.printMessage(Kind.ERROR,
          "Error opening writer for generated class.");
      messager.printMessage(Kind.ERROR, e.getMessage());
    }
    return writer;
  }

  /**
   * Add the class name and start the declaration.
   *
   * @param sourceClass
   *            the class that the implementation is being generated for.
   * @param generatedClassName
   *            the name of the class that is being generated
   * @throws IOException
   */
  void addClassName(TypeElement sourceClass, String generatedClassName)
      throws IOException {
    String superClassName = sourceClass.getQualifiedName().toString();
    String classDeclaration = "public final class " + generatedClassName +
        " extends " + superClassName + " {";
    writeLine(classDeclaration);
    writeEmptyLine();
    increaseIndentation();
  }

  private void writeLine(String lineContent) throws IOException {
    startLine();
    writeLinePartString(lineContent);
    finishLine();
  }

  private void startLine() throws IOException {
    for (int i = 0; i < currentIndentationLevel; i++) {
      writeIndentation();
    }
  }

  private void writeIndentation() throws IOException {
    writer.write("  ");
  }

  private void writeLinePartString(String linePart) throws IOException {
    writer.write(linePart);
  }

  private void finishLine() throws IOException {
    writer.write("\n");
  }

  private void writeEmptyLine() throws IOException {
    writeNewLine();
  }

  private void writeNewLine() throws IOException {
    writer.write("\n");
  }

  private void increaseIndentation() {
    currentIndentationLevel++;
  }

  /**
   * Generate an implementation for a method. The implementation will call a
   * private native stub that will also be generated.
   *
   * @param method
   * @throws IOException
   */
  void processMethod(ExecutableElement method) throws IOException {
    generateMethodImplementation(method);
    generatePrivateNativeStub(method);
  }

  private void generateMethodImplementation(ExecutableElement method)
      throws IOException {

    List<? extends VariableElement> parameters = method.getParameters();

    StringBuilder firstLineOfMethod = new StringBuilder();
    preserveNecessaryAnnotations(method,
        SysCallProcessor.SYSCALL_TEMPLATE_ANNOTATION);
    addOverrideAnnotation();

    firstLineOfMethod.append(getModifierString(method));

    TypeMirror returnType = method.getReturnType();
    firstLineOfMethod.append(returnType.toString());
    firstLineOfMethod.append(" ");
    firstLineOfMethod.append(getMethodName(method));
    firstLineOfMethod.append("(");
    firstLineOfMethod.append(getParameterString(method.getParameters()));
    firstLineOfMethod.append(") {");

    writeLine(firstLineOfMethod.toString());
    increaseIndentation();

    StringBuilder methodBody = new StringBuilder();

    boolean needsReturn = !returnType.getKind().equals(TypeKind.VOID);
    if (needsReturn) {
      methodBody.append("return ");
    }

    methodBody.append(getMethodName(method) +
        "(BootRecord.the_boot_record.");
    methodBody.append(getMethodName(method) + "IP");

    generateParameterList(parameters, methodBody, false);

    methodBody.append(");");
    writeLine(methodBody.toString());

    decreaseIndentation();
    writeLine("}");

    writeEmptyLine();
  }

  private String getModifierString(ExecutableElement e) throws IOException {
    Set<Modifier> modifiers = checkModifiers(e);
    StringBuilder modifierList = new StringBuilder();
    for (Modifier m : modifiers) {
      if (m.equals(Modifier.ABSTRACT)) {
        continue;
      }

      modifierList.append(m.toString());
      modifierList.append(" ");
    }

    return modifierList.toString();
  }

  private Set<Modifier> checkModifiers(ExecutableElement e) {
    Set<Modifier> modifiers = e.getModifiers();
    Name methodName = e.getSimpleName();
    if (modifiers.contains(Modifier.NATIVE)) {
      messager.printMessage(Kind.ERROR,
          "Cannot implement @SysCallTemplate on a native method: " +
              methodName);
    }
    if (modifiers.contains(Modifier.STATIC)) {
      messager.printMessage(Kind.ERROR,
          "Cannot implement @SysCallTemplate on a static method: " +
              methodName);
    }
    if (!modifiers.contains(Modifier.ABSTRACT)) {
      messager.printMessage(Kind.ERROR,
          "Can only implement @SysCallTemplate on an abstract method: " +
              methodName);
    }
    return modifiers;
  }

  private String getMethodName(ExecutableElement e) {
    return e.getSimpleName().toString();
  }

  private String getParameterString(List<? extends VariableElement> parameters)
      throws IOException {
    StringBuilder parameterString = new StringBuilder();

    Iterator<? extends VariableElement> parametersIt = parameters
        .iterator();
    VariableElement parameter;
    while (parametersIt.hasNext()) {
      parameter = parametersIt.next();
      Set<Modifier> modifiers = parameter.getModifiers();
      for (Modifier m : modifiers) {
        parameterString.append(m.toString());
        parameterString.append(" ");
      }
      parameterString.append(parameter.asType().toString());
      parameterString.append(" ");
      parameterString.append(parameter.getSimpleName().toString());
      if (parametersIt.hasNext()) {
        parameterString.append(", ");
      }
    }
    return parameterString.toString();
  }

  /**
   * Generate a parameter list.
   *
   * @param parameters
   *            the parameters
   * @param stringBuilder
   *            a builder the list will be appended to
   * @param methodDeclaration
   *            <code>true</code> true if the list is used in a method
   *            declaration, <code>false</code> if it is used in a method call
   */
  private void generateParameterList(
      List<? extends VariableElement> parameters,
      StringBuilder stringBuilder, boolean methodDeclaration) {
    Iterator<? extends VariableElement> parametersIt;
    VariableElement parameter;
    parametersIt = parameters.iterator();

    if (!parameters.isEmpty()) {
      stringBuilder.append(", ");
    }

    while (parametersIt.hasNext()) {
      parameter = parametersIt.next();

      if (methodDeclaration) {
        Set<Modifier> modifiers = parameter.getModifiers();
        for (Modifier m : modifiers) {
          stringBuilder.append(m.toString());
          stringBuilder.append(" ");
        }
        stringBuilder.append(parameter.asType().toString());
        stringBuilder.append(" ");
      }

      stringBuilder.append(parameter.getSimpleName().toString());
      if (parametersIt.hasNext()) {
        stringBuilder.append(", ");
      }
    }
  }

  private void decreaseIndentation() {
    currentIndentationLevel--;
  }

  private void generatePrivateNativeStub(ExecutableElement method)
      throws IOException {
    writeLine("@org.vmmagic.pragma.SysCallNative");

    StringBuilder stubDeclaration = new StringBuilder();
    stubDeclaration.append("private static native ");

    TypeMirror returnType = method.getReturnType();
    stubDeclaration.append(returnType);

    stubDeclaration.append(" ");
    stubDeclaration.append(getMethodName(method));
    stubDeclaration.append("(org.vmmagic.unboxed.Address nativeIP");

    List<? extends VariableElement> parameters = method.getParameters();
    generateParameterList(parameters, stubDeclaration, true);

    stubDeclaration.append(")");
    stubDeclaration.append(";");

    writeLine(stubDeclaration.toString());
    writeEmptyLine();
  }

  /**
   * Add all annotations for the class.
   *
   * @param sourceClass
   *            the class that the implementation is being generated for.
   * @throws IOException
   */
  void addClassAnnotations(TypeElement sourceClass) throws IOException {
    preserveNecessaryAnnotations(sourceClass,
        SysCallProcessor.GEN_IMPL_ANNOTATION,
        "javax.annotation.Generated");
    addGeneratedAnnotation(sourceClass);
  }

  private void addGeneratedAnnotation(TypeElement typeElement)
      throws IOException {
    String baseClass = typeElement.getQualifiedName().toString();

    writeLine("@Generated(");
    writeLine("value = \"" + generatingClass + "\",");
    writeLine("comments = \"" + "Auto-generated from " + baseClass + "\")");
  }

  /**
   * Imports cannot be read out via the annotation processing API, so fully
   * qualified names are used almost exclusively. This means that no imports
   * are necessary. <br>
   * <br>
   * The only exception is an import for the {@link Generated} annotation.
   *
   * @throws IOException
   */
  void addImports() throws IOException {
    writeImport("javax.annotation.Generated");
    writeEmptyLine();
  }

  private void writeImport(String importString) throws IOException {
    String importLine = "import " + importString + ";";
    writeLine(importLine);
  }

  /**
   * Add the package declaration.
   *
   * @param packageName
   *            name of the package
   * @throws IOException
   */
  void addPackageDeclaration(String packageName) throws IOException {
    writePackageDeclaration(packageName);
    writeEmptyLine();
  }

  private void writePackageDeclaration(String packageName) throws IOException {
    String packageDeclaration = "package " + packageName + ";";
    writeLine(packageDeclaration);
  }

  private void preserveNecessaryAnnotations(Element element,
      String... throwAway) throws IOException {
    Set<String> annotationsToThrowAway = new HashSet<String>(
        Arrays.asList(throwAway));

    for (AnnotationMirror annMirror : element.getAnnotationMirrors()) {
      String annotationName = annMirror.getAnnotationType().toString();
      if (!annotationsToThrowAway.contains(annotationName)) {
        String annotation = annMirror.toString();
        writeLine(annotation);
      }
    }
  }

  private void addOverrideAnnotation() throws IOException {
    writeLine("@java.lang.Override");
  }

  /**
   * Finish the source file for the generated implementation.
   *
   * @throws IOException
   */
  void finishSourceFileForGeneratedImplementation() throws IOException {
    writer.write("}");
    writer.close();
  }

}
