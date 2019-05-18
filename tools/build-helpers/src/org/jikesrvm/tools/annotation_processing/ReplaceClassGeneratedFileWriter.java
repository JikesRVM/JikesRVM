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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;

/**
 * Used by {@link SysCallVisitor} to write the generated source file.
 *
 */
class ReplaceClassGeneratedFileWriter extends AbstractGeneratedFileWriter {

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
  ReplaceClassGeneratedFileWriter(String generatingClass, Messager messager, Filer filer) {
    super(generatingClass, messager, filer);
  }

  @Override
  protected void writeSpecificImports() throws IOException {
    writeImport("org.jikesrvm.util.HashMapRVM");
    writeImport("org.jikesrvm.util.HashSetRVM");
  }

  @Override
  protected void addCommentLineForGeneratedAnnotation() throws IOException {
    writeLine("comments = \"" + "Auto-generated from classes annotated with @ReplaceClass" + "\")");
  }


  public void addMethodImplementationForGetNamesOfClassesWithReplacements(Set<String> classNames) throws IOException {
    StringBuilder firstLineOfMethod = new StringBuilder();
    addOverrideAnnotation();

    firstLineOfMethod.append("public ");

    firstLineOfMethod.append("HashSetRVM<String>");
    firstLineOfMethod.append(" ");
    firstLineOfMethod.append("getNamesOfClassesWithReplacements");
    firstLineOfMethod.append("(");
    firstLineOfMethod.append(") {");

    writeLine(firstLineOfMethod.toString());
    increaseIndentation();

    writeLine("HashSetRVM<String> replacementClassNames = new HashSetRVM<String>();");
    for (String name : classNames) {
      writeLine("replacementClassNames.add(\"" + name + "\");");
    }
    writeLine("return replacementClassNames;");

    decreaseIndentation();
    writeLine("}");

    writeEmptyLine();

  }

  public void addMethodImplementationForGetMapOfTargetClassToSourceClass(Map<String, String> sourceNameToClassName) throws IOException {
    StringBuilder firstLineOfMethod = new StringBuilder();
    addOverrideAnnotation();

    firstLineOfMethod.append("public ");

    firstLineOfMethod.append("HashMapRVM<String, String>");
    firstLineOfMethod.append(" ");
    firstLineOfMethod.append("getMapOfTargetClassToSourceClass");
    firstLineOfMethod.append("(");
    firstLineOfMethod.append(") {");

    writeLine(firstLineOfMethod.toString());
    increaseIndentation();

    writeLine("HashMapRVM<String, String> targetClassToSourceClass = new HashMapRVM<String, String>();");
    for (Entry<String, String> e : sourceNameToClassName.entrySet()) {
      writeLine("targetClassToSourceClass.put(\"" + e.getKey() + "\"" + ", \"" + e.getValue() + "\");");
    }
    writeLine("return targetClassToSourceClass;");

    decreaseIndentation();
    writeLine("}");

    writeEmptyLine();
  }

}
