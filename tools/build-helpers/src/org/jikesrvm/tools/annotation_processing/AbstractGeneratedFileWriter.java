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

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

abstract class AbstractGeneratedFileWriter {

  protected Filer filer;
  protected JavaFileObject fileObject;
  protected Writer writer;
  protected String generatingClass;
  protected Messager messager;
  protected int currentIndentationLevel;

  protected AbstractGeneratedFileWriter(String generatingClass, Messager messager, Filer filer) {
    this.generatingClass = generatingClass;
    this.messager = messager;
    this.filer = filer;
  }

  protected void giveUp() {
    messager.printMessage(Kind.ERROR, "Could not generate class, giving up");
    if (fileObject != null) {
      messager.printMessage(Kind.NOTE,
          "Attempting to delete fileObject ...");
      boolean success = fileObject.delete();
      String result = success ? "suceeded" : "failed";
      messager.printMessage(Kind.NOTE, "Deletion " + result);
    }
  }

  protected void writeLine(String lineContent) throws IOException {
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

  protected void writeEmptyLine() throws IOException {
    writeNewLine();
  }

  private void writeNewLine() throws IOException {
    writer.write("\n");
  }

  protected void increaseIndentation() {
    currentIndentationLevel++;
  }

  protected void decreaseIndentation() {
    currentIndentationLevel--;
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

  protected JavaFileObject getSourceFile(String nameOfGeneratedClass,
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

  protected Writer getWriter(JavaFileObject sourceFile) {
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
   * Imports cannot be read out via the annotation processing API, so fully
   * qualified names are used almost exclusively. This means that no imports
   * are necessary. <br>
   * <br>
   * By default, only the {@link Generated} annotation is imported.
   *
   * @throws IOException
   */
  void addImports() throws IOException {
    writeImport("javax.annotation.Generated");
    writeSpecificImports();
    writeEmptyLine();
  }

  /**
   * Writes imports that are specific to the type of file that's generated.
   *
   * @throws IOException
   */
  protected abstract void writeSpecificImports() throws IOException;

  /**
   * Adds the class name and start the declaration.
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

  /**
   * Adds all annotations for the class.
   * <p>
   * The default implementation only adds the generated annotation
   *
   * @param sourceClass
   *            the class that the implementation is being generated for.
   * @throws IOException
   */
  void addClassAnnotations(TypeElement sourceClass) throws IOException {
    addGeneratedAnnotation();
  }

  protected void addGeneratedAnnotation() throws IOException {
    writeLine("@Generated(");
    writeLine("value = \"" + generatingClass + "\",");
    addCommentLineForGeneratedAnnotation();
  }

  protected abstract void addCommentLineForGeneratedAnnotation() throws IOException;

  protected void writeImport(String importString) throws IOException {
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
  protected void addPackageDeclaration(String packageName) throws IOException {
    writePackageDeclaration(packageName);
    writeEmptyLine();
  }

  private void writePackageDeclaration(String packageName) throws IOException {
    String packageDeclaration = "package " + packageName + ";";
    writeLine(packageDeclaration);
  }

  protected void addOverrideAnnotation() throws IOException {
    writeLine("@java.lang.Override");
  }

  /**
   * Finish the source file for the generated implementation.
   *
   * @throws IOException
   */
  protected void finishSourceFileForGeneratedImplementation()
      throws IOException {
        writer.write("}");
        writer.close();
      }

}
