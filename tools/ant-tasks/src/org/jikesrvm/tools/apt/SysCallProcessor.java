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
package org.jikesrvm.tools.apt;

import com.sun.mirror.apt.AnnotationProcessor;
import com.sun.mirror.apt.AnnotationProcessorEnvironment;
import com.sun.mirror.apt.Filer;
import com.sun.mirror.apt.Messager;
import com.sun.mirror.declaration.AnnotationMirror;
import com.sun.mirror.declaration.AnnotationTypeElementDeclaration;
import com.sun.mirror.declaration.AnnotationValue;
import com.sun.mirror.declaration.ClassDeclaration;
import com.sun.mirror.declaration.Declaration;
import com.sun.mirror.declaration.MethodDeclaration;
import com.sun.mirror.declaration.Modifier;
import com.sun.mirror.declaration.ParameterDeclaration;
import com.sun.mirror.declaration.TypeDeclaration;
import com.sun.mirror.type.AnnotationType;
import com.sun.mirror.type.TypeMirror;
import com.sun.mirror.util.DeclarationVisitors;
import com.sun.mirror.util.SimpleDeclarationVisitor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;

/**
 * Process the '@SysCallTemplate' annotation.  Generates an implementation
 * class for methods annotated with this, that provides an indirection to
 * a native method annotated with @SysCall.
 */
public class SysCallProcessor implements AnnotationProcessor {

  public static final String GEN_IMPL_ANNOTATION =
    "org.jikesrvm.apt.annotations.GenerateImplementation";
  public static final String SYSCALL_TEMPLATE_ANNOTATION =
    "org.jikesrvm.apt.annotations.SysCallTemplate";

  private final AnnotationProcessorEnvironment env;

  public SysCallProcessor(AnnotationProcessorEnvironment env) {
      this.env = env;
  }

  /**
   * @see AnnotationProcessor#process()
   */
  public void process() {
    for (TypeDeclaration typeDecl : env.getSpecifiedTypeDeclarations())
      typeDecl.accept(DeclarationVisitors.getSourceOrderDeclarationScanner(
          /* pre-processing */   new SysCallVisitor(),
          /* post-processing */  DeclarationVisitors.NO_OP));
  }

  /**
   * Visit a class that has SysCall annotations
 */
  private class SysCallVisitor extends SimpleDeclarationVisitor {
    private PrintWriter out;
    private final Messager mess = env.getMessager();
    private final Filer filer = env.getFiler();

    SysCallVisitor() {}

    /**
     * Process a class declaration.  We want to produce a new java class,
     * so process all the declarations in the class explicitly from here.
     *
     * A class that uses the SysCallTemplate annotation should itself
     * be annotated with @GenerateImplementation, and it is from this
     * annotation that we get the name of the generated class.
     */
    @Override
    public void visitClassDeclaration(ClassDeclaration classDecl) {
      /*
       * Get the GeneratedImplementation annotation and find out
       * what class we should be generating
       */
      String generatedClass = derivedClassName(classDecl);

      /*
       * Break the generated class name into package and class names
       */
      int lastDot = generatedClass.lastIndexOf(".");
      String generatedPackage;
      String generatedClassName;

      if (lastDot > 0) {
        generatedPackage = generatedClass.substring(0,lastDot);
        generatedClassName = generatedClass.substring(lastDot+1);
      } else {
        generatedPackage = classDecl.getPackage().toString();
        generatedClassName = generatedClass;
      }


      /*
       * Create the output file
       */
      try {
        out = filer.createSourceFile(generatedClass);
      } catch (IOException e) {
        mess.printError("Error creating source file "+generatedClass);
        mess.printError(e.getMessage());
      }

      mess.printNotice("Creating "+generatedClass);

      out.println(asComment("Auto-generated from "+classDecl.getQualifiedName()));
      printDocComment(classDecl.getPackage());
      out.println("package "+generatedPackage+";");

      /* Need a decent way to pass imports to the generated file */
      out.println();
      out.println("import org.vmmagic.pragma.*;");
      out.println("import org.vmmagic.unboxed.*;");
      out.println();


      /*
       * Preserve other annotations
       */
      for (AnnotationMirror ann : classDecl.getAnnotationMirrors()) {
        AnnotationType type = ann.getAnnotationType();
        if (!type.toString().equals(GEN_IMPL_ANNOTATION))
          out.println("  "+ann);
      }

      out.println("public final class "+generatedClassName+" extends "+classDecl.getQualifiedName()+" {");
      for (MethodDeclaration m : classDecl.getMethods())
        if (getAnnotation(m,SYSCALL_TEMPLATE_ANNOTATION) != null)
          doMethodDeclaration(m);
      out.println("}");
    }

    private String derivedClassName(ClassDeclaration d) {
      String generatedClass;
      AnnotationMirror ann = getAnnotation(d,GEN_IMPL_ANNOTATION);
      if (ann != null) {
        generatedClass = getAnnotationElementValue("value()", ann);
      } else {
        generatedClass = d.getQualifiedName()+"Impl";
      }
      return generatedClass;
    }

    /**
     * Process a method declaration, annotated with a @SysCallTemplate annotation.
     *
     * Generate the public instance method that dispatches to the native method,
     * and the native method that is the SysCall itself.
     *
     * @param methodDecl
     */
    public void doMethodDeclaration(MethodDeclaration methodDecl) {
      String methodName = methodDecl.getSimpleName();
      printDocComment(methodDecl);

      /*
       * Presevre annotations that we don't process
       */
      for (AnnotationMirror ann : methodDecl.getAnnotationMirrors()) {
        AnnotationType type = ann.getAnnotationType();
        if (!type.toString().equals(SYSCALL_TEMPLATE_ANNOTATION))
          out.println("  "+ann);
      }
      /*
       * Create the concrete method body
       */
      out.print("  ");
      boolean isAbstract = false;
      for (Modifier m : methodDecl.getModifiers()) {
        switch(m) {
        case NATIVE:
          mess.printError("Cannot implement @SysCallTemplate on a native method");
          break;
        case STATIC:
          mess.printError("Cannot implement @SysCallTemplate on a static method");
          break;
        case ABSTRACT:
          isAbstract = true;
          break;
        default:
          out.print(m+" ");
        }
      }
      if (!isAbstract) {
        mess.printError("Can only implement @SysCallTemplate on an abstract method");
      }
      final TypeMirror returnType = methodDecl.getReturnType();
      boolean isVoid = returnType.equals(env.getTypeUtils().getVoidType());

      out.print(returnType+" "+methodName);

      /*
       * Formal parameters
       */
      out.print("(");
      Iterator<ParameterDeclaration> params = methodDecl.getParameters().iterator();
      while (params.hasNext()) {
        ParameterDeclaration p = params.next();
        out.print(p);
        if (params.hasNext())
          out.print(",");
      }
      out.println(") {");

      /*
       * Method body
       */
      out.print("    ");
      if (!isVoid)
        out.print("return ");
      out.print(methodName+"(BootRecord.the_boot_record.");
      out.print(methodName+"IP");
      if (!methodDecl.getParameters().isEmpty()) {
        params = methodDecl.getParameters().iterator();
        while (params.hasNext()) {
          out.print(", ");
          ParameterDeclaration p = params.next();
          out.print(p.getSimpleName());
       }
      }
      out.println(");");
      out.println("  }");

      /*
       * Generate the private native stub
       */
      out.println();
      out.println("  @SysCallNative");
      out.print("  private static native "+returnType+" "+methodName+"(Address nativeIP");
      params = methodDecl.getParameters().iterator();
      while (params.hasNext()) {
        out.print(", ");
        ParameterDeclaration p = params.next();
        out.print(p.getType()+" "+p.getSimpleName());
      }
      out.println(");");
      out.println();
      out.println();
    }

    private void printDocComment(Declaration d) {
      if (d.getDocComment() != null)
        out.println(asJavadoc(d.getDocComment()));
    }
  }

  private static String asJavadoc(String content) {
    return asComment(content,true);
  }

  private static String asComment(String content) {
    return asComment(content,false);
  }

  private static String asComment(String content, boolean javadoc) {
    String result = javadoc ? "/**\n" : "/*\n";
    for (String line : content.split("\n"))
      result += " * "+line+"\n";
    result += " */";
    return result;
  }

  /* utility methods for dealing with type mirrors */

  /**
   * Return an annotation from a declaration, given its fq name
   *
   * @param d The declaration to look at
   * @param annotationName Name of the annotation
   */
  private static AnnotationMirror getAnnotation(Declaration d, String annotationName) {
    for (AnnotationMirror ann : d.getAnnotationMirrors()) {
      AnnotationType type = ann.getAnnotationType();
      if (type.toString().equals(annotationName)) {
        return ann;
      }
    }
    return null;
  }

  /**
   * Get the value of an element of an annotation
   *
   * @param elementName
   * @param ann
   * @return
   */
  private static String getAnnotationElementValue(String elementName, AnnotationMirror ann) {
    Map<AnnotationTypeElementDeclaration,AnnotationValue> values =
      ann.getElementValues();
    for (AnnotationTypeElementDeclaration element : values.keySet()) {
      if (element.toString().equals(elementName)) {
        AnnotationValue val = values.get(element);
        return (String)val.getValue();
      }
    }
    return null;
  }

}
