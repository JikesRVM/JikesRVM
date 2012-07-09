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
package org.mmtk.harness.lang;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.util.Iterator;

import org.mmtk.harness.Harness;
import org.mmtk.harness.lang.ast.AST;
import org.mmtk.harness.lang.ast.Alloc;
import org.mmtk.harness.lang.ast.Assert;
import org.mmtk.harness.lang.ast.Assignment;
import org.mmtk.harness.lang.ast.Call;
import org.mmtk.harness.lang.ast.Constant;
import org.mmtk.harness.lang.ast.Expression;
import org.mmtk.harness.lang.ast.IfStatement;
import org.mmtk.harness.lang.ast.LoadField;
import org.mmtk.harness.lang.ast.LoadNamedField;
import org.mmtk.harness.lang.ast.Method;
import org.mmtk.harness.lang.ast.NormalMethod;
import org.mmtk.harness.lang.ast.Operator;
import org.mmtk.harness.lang.ast.PrintStatement;
import org.mmtk.harness.lang.ast.Return;
import org.mmtk.harness.lang.ast.Sequence;
import org.mmtk.harness.lang.ast.Spawn;
import org.mmtk.harness.lang.ast.Statement;
import org.mmtk.harness.lang.ast.StoreField;
import org.mmtk.harness.lang.ast.StoreNamedField;
import org.mmtk.harness.lang.ast.TypeLiteral;
import org.mmtk.harness.lang.ast.Variable;
import org.mmtk.harness.lang.ast.WhileStatement;
import org.mmtk.harness.lang.parser.MethodTable;
import org.mmtk.harness.lang.parser.Parser;
import org.vmmagic.unboxed.harness.ArchitecturalWord;

/**
 * Format an AST back into Harness script-language code
 * <p>
 * Implemented as a visitor over the AST
 */
public class PrettyPrinter extends Visitor {

  private final OutputFormatter fmt;

  /**
   * Create a pretty printer that sends output to an internal buffer
   */
  public PrettyPrinter() {
    this(new OutputFormatter());
  }

  /**
   * @param stream Where to send the output
   */
  public PrettyPrinter(PrintStream stream) {
    this(new OutputFormatter(stream));
  }


  private PrettyPrinter(OutputFormatter outputFormatter) {
    fmt = outputFormatter;
  }

  private static class OutputFormatter {
    private static final int INDENT = 2;
    private int indent = 0;
    private boolean pendingIndent = false;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PrintStream out;

    OutputFormatter() {
      out = new PrintStream(buffer,true);
    }

    OutputFormatter(PrintStream out) {
      this.out = out;
    }

    void newline() {
      out.printf("%n");
      pendingIndent = true;
    }

    void out(String format, Object... args) {
      if (pendingIndent) {
        out.printf(margin());
        pendingIndent = false;
      }
      out.printf(format, args);
    }

    private String margin() {
      String indentStr = "";
      for (int i=0; i < indent; i++) {
        indentStr += " ";
      }
      return indentStr;
    }

    void increaseIndent() { indent += INDENT; }

    void decreaseIndent() { indent -= INDENT; }

    String read() {
      return buffer.toString();
    }
  }


  /**
   * Visit a method definition
   */
  @Override
  public Object visit(NormalMethod method) {
    fmt.out("%s %s(",method.getReturnType(),method.getName());
    boolean first = true;
    for (Declaration decl : method.getParams()) {
      if (first) {
        first = false;
      } else {
        fmt.out(",");
      }
      decl.accept(this);
    }
    fmt.out(") {"); fmt.newline();
    fmt.increaseIndent();
    method.getBody().accept(this);
    fmt.decreaseIndent();
    fmt.out("%s}",fmt.margin()); fmt.newline();
    return null;
  }

  /**
   * Visit a method-call
   */
  @Override
  public Object visit(Call call) {
    fmt.out("%s(",call.getMethod().getName());
    boolean first = true;
    for (Expression param : call.getParams()) {
      if (!first) {
        fmt.out(",");
      } else {
        first = false;
      }
      param.accept(this);
    }
    fmt.out(")");
    return null;
  }

  @Override
  public Object visit(Sequence ass) {
    for (Statement stmt : ass) {
      stmt.accept(this); fmt.newline();
    }
    return null;
  }

  @Override
  public Object visit(Assignment a) {
    fmt.out("%s = ", a.getSymbol().getName());
    a.getRhs().accept(this);
    fmt.out(";");
    return null;
  }

  @Override
  public Object visit(IfStatement conditional) {
    String keyword = "if";
    Iterator<Expression> condIter = conditional.getConds().iterator();

    for (Statement body : conditional.getStmts()) {
      if (condIter.hasNext()) {
        Expression cond = condIter.next();
        fmt.out("%s (", keyword);
        cond.accept(this);
        fmt.out(") {"); fmt.newline();
        keyword = "elif";
      } else {
        fmt.out("else {"); fmt.newline();
      }
      fmt.increaseIndent();
      body.accept(this);
      fmt.decreaseIndent();
      fmt.out("} ");
    }
    return null;
  }

  @Override
  public Object visit(WhileStatement w) {
    fmt.out("while (");
    w.getCond().accept(this);
    fmt.out(") {"); fmt.newline();
    fmt.increaseIndent();
    w.getBody().accept(this);
    fmt.decreaseIndent();
    fmt.out("}");
    return null;
  }

  @Override
  public Object visit(Operator op) {
    fmt.out(" %s ", op.toString());
    return null;
  }

  @Override
  public Object visit(LoadField load) {
    fmt.out("%s.%s[", load.getObjectSymbol().getName(), load.getFieldType().toString());
    load.getIndex().accept(this);
    fmt.out("]");
    return null;
  }

  @Override
  public Object visit(LoadNamedField load) {
    fmt.out("%s.%s", load.getObjectSymbol().getName(), load.getFieldName());
    return null;
  }

  @Override
  public Object visit(Constant c) {
    fmt.out(c.value.toString());
    return null;
  }

  @Override
  public Object visit(Variable var) {
    fmt.out("%s", var.getSymbol().getName());
    return null;
  }

  @Override
  public Object visit(StoreField store) {
    fmt.out("%s.%s[",store.getObjectSymbol().getName(), store.getFieldType().toString());
    store.getIndex().accept(this);
    fmt.out("] := ");
    store.getRhs().accept(this);
    fmt.out(";");
    return null;
  }

  @Override
  public Object visit(StoreNamedField store) {
    fmt.out("%s.%s := ",store.getObjectSymbol().getName(), store.getFieldName());
    store.getRhs().accept(this);
    fmt.out(";");
    return null;
  }

  @Override
  public Object visit(Return ret) {
    fmt.out("return");
    if (ret.hasReturnValue()) {
      fmt.out(" ");
      ret.getRhs().accept(this);
    }
    fmt.out(";");
    return null;
  }

  @Override
  public Object visit(Assert ass) {
    fmt.out("assert(");
    ass.getPredicate().accept(this);
    for (Expression expr : ass.getOutputs()) {
      fmt.out(",");
      expr.accept(this);
    }
    fmt.out(");");
    return null;
  }



  @Override
  public Object visit(Declaration decl) {
    fmt.out("%s %s", decl.getType(), decl.getName());
    return null;
  }

  @Override
  public Object visit(PrintStatement print) {
    fmt.out("print");
    String separator = "(";
    for (Expression expr : print.getArgs()) {
      fmt.out(separator);
      separator = ",";
      expr.accept(this);
    }
    fmt.out(");");
    return null;
  }

  @Override
  public Object visit(Alloc alloc) {
    fmt.out("alloc(");
    final int nArgs = alloc.numArgs();
    for (int i=0; i < nArgs; i++) {
      Expression arg = alloc.getArg(i);
      arg.accept(this);
      if (i < nArgs-1) {
        fmt.out(",");
      } else {
        fmt.out(")");
      }
    }
    return null;
   }

  @Override
  public Object visit(Spawn spawn) {
    fmt.out("spawn");
    String separator = "(";
    for (Expression expr : spawn.getArgs()) {
      fmt.out(separator);
      expr.accept(this);
    }
    fmt.out(");");
    return null;
  }

  @Override
  public Object visit(TypeLiteral type) {
    fmt.out(type.getType().toString());
    return null;
  }

  /*******************************************************************
   * Utility methods
   */

  /**
   * Return the string representation of the most recently formatted AST
   */
  public String read() {
    return fmt.read();
  }

  /**
   * Format an AST and return it as a string
   * @param ast
   * @return a string representation of the given AST
   */
  public static String format(AST ast) {
    PrettyPrinter printer = new PrettyPrinter();
    ast.accept(printer);
    return printer.read();
  }

  /**
   * Format an AST and print it on the given stream
   * @param stream
   * @param ast
   */
  public static void print(PrintStream stream, AST ast) {
    PrettyPrinter printer = new PrettyPrinter(System.out);
    ast.accept(printer);
  }

  /**
   * Format an AST and print it on the given stream
   * @param stream
   * @param ast
   */
  public static void println(PrintStream stream, AST ast) {
    print(stream,ast);
    stream.println();
  }

  /**
   * Print a script (represented by a MethodTable)
   * @param methods
   */
  private static void printMethodTable(MethodTable methods) {
    PrettyPrinter printer = new PrettyPrinter(System.out);
    for (Method m : methods.normalMethods()) {
      m.accept(printer);
    }
  }

  public static void main(String[] args) {
    ArchitecturalWord.init(Harness.bits.getValue());
    try {
      MethodTable methods = new Parser(new BufferedInputStream(new FileInputStream(args[0]))).script();
      PrettyPrinter.printMethodTable(methods);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
