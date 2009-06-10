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
package org.jikesrvm.tools.template;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Generates output files given a template.  Run from command line.
 *
 * Command line format:
 *    java GenerateFromTemplate [-debug] <input> <output> {<var>=<value>}
 *
 *    NOTE: the command line variable feature is VERY powerful, but also
 *          VERY dangerous.  Make sure command line variable names don't
 *          overlap with ANY text that might be found in the template except
 *          actual substitution points!
 *
 * Supported commands:
 *    [a] means 0 or 1 occurences of a
 *    {a} means 0 or more occurences of a
 *
 *    $$$$ INCLUDE <filename>
 *      Inserts the contents of a specified file at the current point in
 *      the template.  NOTE: variable bindings propagate into the included
 *      file, which may result in some unexpected substitutions.
 *
 *    $$$$ FOREACH <var> <filename> [<field> <bgn>[..<end>]] ... $$$$ END
 *      Loops over a file with a fixed record structure (description below).
 *      If both <bgn> and <end> are specified, the loop is over records
 *      with <field> value between <bgn> and <end> (inclusive) (it is
 *      assumed that there is only one record with <field> value <bgn> or
 *      <field> value <end>; <bgn> must precede <end>).  If only <bgn> is
 *      specified, the loop is over all records with <field> value equal
 *      to <bgn>.
 *
 *    $$$$ LOOP <var> { <value> }                            ... $$$$ END
 *      Loops over a given list of values.
 *      Numeric ranges (<num>..<num>) can also be used.
 *      <var>.INDEX can be used to count iterations (0-indexed)
 *
 *    $$$$ COUNT <var> { <value> }                           ... $$$$ END
 *      Defines the variable to contain the number of elements in a given
 *      list of values.
 *      Numeric ranges (<num>..<num>) can also be used.
 *
 *    $$$$ LET <var> <expr>                                  ... $$$$ END
 *      Defines the variable to have a given value until the matching END
 *      (think functional).
 *      NOTE: a variable can't be reassigned inside the block.
 *      NOTE: operator precedence is NOT the same as in Java:
 *            '|' and '^' have the same precedence as '+' and '-'
 *            '&' has the same precedence as '*', '/' and '-'
 *            '~' has the same precedence as unary '-'
 *
 *      The expression has the following grammar:
 *         LetExpr ::= StrExpr
 *         StrExpr ::= StrExpr '`' String               // concatenation
 *                   | String
 *         String  ::= @UPPER(StrExpr)                  // to upper case
 *                   | @LOWER(StrExpr)                  // to lower case
 *                   | @PAD(StrExpr, Expr, "str")       // pad to given length
 *                                                      //   with given string
 *                   | @SUBST(StrExpr, "str", "str")    // substitute chars
 *                                                      //   from string 1
 *                                                      //   with chars from
 *                                                      //   string 2
 *                   | @IF(Cond, StrExpr, StrExpr)      // conditional eval.
 *                   | "str"
 *                   | str
 *                   | Expr                             // arith. expression
 *         Cond    ::= Expr '<' Expr                    // less
 *                   | Expr '<=' Expr                   // less or equal
 *                   | Expr '>' Expr                    // greater
 *                   | Expr '>=' Expr                   // greater or equal
 *                   | Expr '==' Expr                   // equal
 *                   | Expr '!=' Expr                   // not equal
 *         Expr    ::= Expr + Term                      // addition
 *                   | Expr - Term                      // subtraction
 *                   | Expr | Term                      // bitwise OR
 *                   | Expr ^ Term                      // bitwise XOR
 *                   | Term
 *         Term    ::= Term * Factor                    // multiplication
 *                   | Term / Factor                    // (integer) division
 *                   | Term % Factor                    // (integer) remainder
 *                   | Term & Factor                    // bitwise AND
 *                   | Factor
 *         Factor  ::= @LENGTH(StrExpr)                 // string length
 *                   | (Expr)                           // precedence
 *                   | - Factor                         // negation
 *                   | ~ Factor                         // bitwise NOT
 *                   | int
 *
 *    $$$$ SPLIT "<value>" "<sep>" { <var> }                 ... $$$$ END
 *      Splits a given value into tokens using given separators, and places
 *      each token into the corresponding variable.  If there are fewer
 *      variables than tokens, the extra tokens are ignored.  If there are more
 *      variables than tokens, the extra variables get an empty value ("").
 *
 *    $$$$ JOIN <var> "<sep>" { <value> }                    ... $$$$ END
 *      Joins a given list of values using a given separator into the variable.
 *
 *    $$$$ EVAL                                              ... $$$$ END
 *      Evaluates the result of template generation inside the block as
 *      a template.
 *
 *    $$$$ IF <cond>            ... [ $$$$ ELSE            ... ] $$$$ END
 *      Conditionally generates enclosed templates.
 *      <cond> is one of the following:
 *        <value> == <value>                     String equality
 *        <value> != <value>                     String inequality
 *        <value> >  <value>                     Alphabetically greater
 *        <value> >= <value>                     Alphabetically greater or equal
 *        <value> <  <value>                     Alphabetically less
 *        <value> <= <value>                     Alphabetically less or equal
 *        <value> =~ <value>                     Contains as substring
 *        <value> IN { <value> }                 List membership
 *        <value> eq <value>                     Numeric equality (integers)
 *        <value> ne <value>                     Numeric inequality (integers)
 *        <value> gt <value>                     Numerically greater
 *        <value> ge <value>                     Numerically greater or equal
 *        <value> lt <value>                     Numerically less
 *        <value> le <value>                     Numerically less or equal
 *
 * The FOREACH file has the following format:
 *    <record structure>\n
 *    { <record>\n }
 *
 *    where <record structure> is
 *        { { <field name> }\n }
 *    terminated by a blank line
 *
 * Long lines can be extended by placing a backslash ("\") as the last
 * character of a line.  The next line is appended to the end of the
 * current line.
 * NOTE: Lines are appended AS IS: leading spaces become part of the line.
 * if you wish the line to be broken use a double backslash ("\\")
 *
 * Lines starting with "#" are considered comments and skipped.
 *
 * On substitution, <FOREACH var>.<field name> is substituted by a value
 * of the field named <field name> in the current record of the FOREACH file.
 * A special field INDEX will contain the current record number.
 */
public class GenerateFromTemplate {

  static boolean DEBUG = false;

  static String inDir;

  static final String templateMarker = "$$$$";
  static final String commentMarker = "#";

  static final String indexField = "INDEX";

  /**
   * Enumeration of commands that can be generated
   */
  enum Command {
    END,
    FOREACH,
    LOOP,
    IF,
    ELSE,
    SPLIT,
    JOIN,
    EVAL,
    LET,
    COUNT,
    INCLUDE
  }

  static final int MAX_DATA_SIZE = 65536;

  LineNumberReader in;
  PrintWriter out;

  String[] vars;
  String[] vals;

  String params;

  /**
   * Main.
   *
   * @params args arguments from command line
   */
  public static void main(String[] args) throws IOException {
    if (args.length < 2) {
      System.out.println("Usage: java GenerateFromTemplate [-debug] <input> <output> {<var>=<value>}");
      return;
    }
    int argc = args.length;
    if (args[0].equals("-debug")) {
      DEBUG = true;
      argc--;
      System.arraycopy(args, 1, args, 0, argc);
    }

    // When driven from ant (on AIX), there's a problem keeping tokens that
    // are separated by blanks from being split into separate tokens when
    // when the java command is forked. Each token should contain an "=".
    // Verify this and reassemble them if they don't.
    int limit = argc;
    argc = 2;
    for (int i = 2; i < limit; i++) {
      if (args[i].indexOf("=") < 0)
        args[ argc-1 ] = args[ argc-1 ] + " " + args[ i ];
      else {
        args[ argc++ ] = args[ i ];
      }
    }

    FileInputStream inStream = null;
    FileOutputStream outStream = null;
    if (DEBUG) System.out.println("in:"+args[0]+"\nout:"+args[1]);
    try {
      if (args[0].indexOf(File.separator) != -1)
          inDir = args[0].substring(0, args[0].lastIndexOf(File.separator)+1);
      else
          inDir = "";
      if (DEBUG) System.out.println("inDir="+inDir);
      inStream = new FileInputStream(args[0]);
      outStream = new FileOutputStream(args[1]);

      String[] vars = new String[argc-2];
      String[] vals = new String[argc-2];
      for (int i = 2; i < argc; i++) {
         String arg = args[i];
         int pos = arg.indexOf("=");
         vars[i-2] = arg.substring(0, pos);
         vals[i-2] = arg.substring(pos+1);
         if (DEBUG) System.out.println(vars[i-2]+" = "+vals[i-2]);
      }

      GenerateFromTemplate gft = new GenerateFromTemplate(inStream, outStream);
      gft.setSubst(vars, vals);
      gft.generateOutputFromTemplate();
    // } catch (IOException e) {
    //   System.out.println("An error occurred: "+e);
    } finally {
      try {
        inStream.close();
        outStream.close();
      } catch (Exception e) {}
    }
  }

  GenerateFromTemplate(InputStream inStream, OutputStream outStream) {
    // setup i/o
    in = new LineNumberReader(new InputStreamReader(inStream));
    out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outStream)));
  }

  void setSubst(String[] vr, String[] vl) {
    vars = vr;
    vals = vl;
  }

  String readLine() throws IOException {
    String inLine = in.readLine();
    if (inLine != null)
      for (int i = 0; i < vars.length; i++)
        inLine = substitute(inLine, vars[i], vals[i]);
    return inLine;
  }

  void generateOutputFromTemplate() throws IOException {
    try {
      String inLine;
      // loop over strings
      for (inLine = readLine(); inLine != null; inLine = readLine()) {
        if (DEBUG) System.out.println("from input:"+inLine);
        if (!isTemplateLine(inLine)) {
          if (DEBUG) System.out.println("not template line, continuing...");
          out.print(inLine+"\n");
          continue;
        }
        Vector<Object> region = buildTemplateRegion(inLine);
        processTemplateRegion(region);
      }
    } finally {
      out.flush();
    }
  }

  boolean isTemplateLine(String line) {
    StringTokenizer st = new StringTokenizer(line, " \t");
    return st.hasMoreTokens() && st.nextToken().equals(templateMarker);
  }

  Vector<Object> buildTemplateRegion(String inLine) throws IOException {
    Vector<Object> region = new Vector<Object>();
    region.addElement(inLine);
    Command command = getTemplateCommand(inLine);
    if (DEBUG) System.out.println("template command #"+command);
    switch (command) {
       case FOREACH:
       case LOOP:
       case SPLIT:
       case JOIN:
       case EVAL:
       case LET:
       case COUNT:   buildLoopRegion(region); break;
       case IF:      buildCondRegion(region); break;
       case INCLUDE: buildIncludeRegion(region); break;
    }
    return region;
  }

  void buildLoopRegion(Vector<Object> region) throws IOException {
    for (;;) {
      String inLine = readLine();
      if (inLine == null) throw new IOException("Unexpected end of file");
      if (isTemplateLine(inLine)) {
        Command command = getTemplateCommand(inLine);
        if (command == Command.END) break;
        region.addElement(buildTemplateRegion(inLine));
      } else {
         if (DEBUG) System.out.println("adding line to region :"+inLine);
         region.addElement(inLine);
      }
    }
  }

  void buildCondRegion(Vector<Object> region) throws IOException {
    Vector<Object> intern = new Vector<Object>();
    for (;;) {
      String inLine = readLine();
      if (inLine == null) throw new IOException("Unexpected end of file");
      if (isTemplateLine(inLine)) {
        Command command = getTemplateCommand(inLine);
        if (command == Command.END) {
          region.addElement(intern);
          break;
        } else if (command == Command.ELSE) {
          region.addElement(intern);
          intern = new Vector<Object>();
        } else {
          intern.addElement(buildTemplateRegion(inLine));
        }
      } else {
         if (DEBUG) System.out.println("adding line to region :"+inLine);
         intern.addElement(inLine);
      }
    }
  }

  void buildIncludeRegion(Vector<Object> region) throws IOException {
    QuotedStringTokenizer pst = new QuotedStringTokenizer(params);
    if (!pst.hasMoreTokens())
       throw new IOException("Missing filename in INCLUDE");
    String file_name = pst.nextToken();
    LineNumberReader old_in = in;
    try {
        in = new LineNumberReader(new FileReader(file_name));
    } catch (java.io.FileNotFoundException e) {
        in = new LineNumberReader(new FileReader(inDir + file_name));
    }
    String inLine;
    // loop over strings in the file
    for (inLine = readLine(); inLine != null; inLine = readLine()) {
      if (isTemplateLine(inLine)) {
        region.addElement(buildTemplateRegion(inLine));
      } else {
         if (DEBUG) System.out.println("adding line to region :"+inLine);
         region.addElement(inLine);
      }
    }
    in = old_in;
  }

  Command getTemplateCommand(String line) throws IOException {
    int startMatch = line.indexOf(templateMarker) + templateMarker.length() + 1;
    int endMatch = line.indexOf(' ', startMatch);
    if (endMatch < 0) {
      endMatch = line.length();
    }
    if (DEBUG) System.out.println("getting template command :"+line.substring(startMatch));
    Command i;
    try {
      i = Enum.valueOf(Command.class, line.substring(startMatch, endMatch));
    } catch (IllegalArgumentException e) {
      IOException newE = new IOException("Invalid command");
      newE.initCause(e);
      throw newE;
    }
    params = line.substring(endMatch);
    if (DEBUG) System.out.println("command is " + i + ". params ="+params);
    return i;
  }

  void processTemplateRegion(Vector<Object> region) throws IOException {
    String inLine = (String)region.elementAt(0);
    Command command = getTemplateCommand(inLine);
    switch (command) {
      case FOREACH: processForeachRegion(region); break;
      case LOOP:    processLoopRegion(region); break;
      case COUNT:   processCountRegion(region); break;
      case SPLIT:   processSplitRegion(region); break;
      case JOIN:    processJoinRegion(region); break;
      case LET:     processLetRegion(region); break;
      case EVAL:    processEvalRegion(region); break;
      case IF:      processCondRegion(region); break;
      case INCLUDE: processIncludeRegion(region); break;
    }
  }

  private String getNextLine(BufferedReader data) throws IOException {
    String line = data.readLine();
    if (line == null || line.length() == 0)
      return line;
    while (line.startsWith(commentMarker)) {
      line = data.readLine();
    }
    while (line.endsWith("\\")) {
      if (line.endsWith("\\\\")) {
        line = line.substring(0, line.length()-2) + "\n" + data.readLine();
      } else {
        line = line.substring(0, line.length()-1) + data.readLine();
      }
    }
    return line;
  }

  void processForeachRegion(Vector region) throws IOException {
    // get var name and data file name
    QuotedStringTokenizer pst = new QuotedStringTokenizer(params);
    if (!pst.hasMoreTokens())
       throw new IOException("Missing variable in FOREACH");
    String var_name = pst.nextToken();
    if (!pst.hasMoreTokens())
       throw new IOException("Missing filename in FOREACH");
    String file_name = pst.nextToken();
    String select = null;
    String start = null;
    String end = null;
    boolean inRange = false;
    if (pst.hasMoreTokens()) {
       select = pst.nextToken();
       if (!pst.hasMoreTokens())
          throw new IOException("Missing field value in FOREACH");
       String fval = pst.nextToken();
       int dotdot = fval.indexOf("..");
       if (dotdot != -1 && dotdot == fval.lastIndexOf("..")) {
          start = fval.substring(0, dotdot);
          end = fval.substring(dotdot+2);
       } else {
          start = fval;
       }
    }

    if (DEBUG) System.out.println("doing foreach with varname "+var_name+
                                  " on data file :"+file_name);
    if (DEBUG && select != null) {
       System.out.print("   selecting records with "+select);
       if (end == null) System.out.println(" equal to \""+start+"\"");
       else System.out.println(" between \""+start+"\" and \""+end+"\"");
    }

    // open data file
    BufferedReader data;
    try {
        data = new BufferedReader(new FileReader(file_name));
    } catch (java.io.FileNotFoundException e) {
        data = new BufferedReader(new FileReader(inDir + file_name));
    }

    // read field information
    Vector<String> fields_v = new Vector<String>();
    Vector<Integer> fpl_v = new Vector<Integer>();
    for (String inLine = getNextLine(data);
         (inLine != null && inLine.length() != 0);
         inLine = getNextLine(data)) {
      StringTokenizer st = new StringTokenizer(inLine);
      fpl_v.addElement(st.countTokens());
      while (st.hasMoreTokens()) {
        String tok = st.nextToken();
        if (DEBUG) System.out.println("read field "+fields_v.size()+" :"+tok);
        fields_v.addElement(tok);
      }
    }
    fields_v.addElement(indexField);
    // convert to arrays for faster access
    int[] fieldsPerLine = new int[fpl_v.size()];
    for (int i = 0; i < fieldsPerLine.length; i++)
       fieldsPerLine[i] = fpl_v.elementAt(i);
    String[] fields = fields_v.toArray(new String[0]);

    // Count through data file.
  dataFileLoop:
    for (int curField = 0; ; curField++) {
      // Read in all fields
      int i = 0;
      String[] fieldData = new String[fields.length];
      for (int aFieldsPerLine : fieldsPerLine) {
        String line = getNextLine(data);
        if (line == null) break dataFileLoop;
        if (aFieldsPerLine == 1) {
          if (DEBUG) System.out.println("read field " + fields[i] + " :" + line);
          fieldData[i++] = line;
        } else {
          if (DEBUG) System.out.println("reading " + aFieldsPerLine + " fields");
          StringTokenizer st = new StringTokenizer(line);
          try {
            for (int k = 0; k < aFieldsPerLine; k++) {
              String tok = st.nextToken();
              if (DEBUG) System.out.println("read field " + fields[i] + ": " + tok);
              fieldData[i++] = tok;
            }
          } catch (NoSuchElementException x) {
            throw new IOException("Missing field " + fields[i]);
          }
        }
      }
      if (fieldsPerLine.length != 1) getNextLine(data); // skip empty line.
      fieldData[i++] = Integer.toString(curField);

      if (select != null) {
        for (int j = 0; j < fields.length; j++) {
          if (DEBUG) System.out.println("checking if select is field "+fields[j]);
          if (select.equals(fields[j])) {
            String value = fieldData[j];
            if (value.equals(start)) inRange = true;
            else if (end == null) inRange = false;
            else if (value.equals(end)) end = null;

            if (DEBUG) System.out.println("record in range; including");
            break;
          }
        }

        if (!inRange) break;
      }

      // Count through each line in region.
      for (int j = 1; j < region.size(); j++) {
        try {
          String currentLine = (String) region.elementAt(j);
          String result = substitute(currentLine, var_name, fields, fieldData);
          out.print(result+"\n");
        } catch (ClassCastException e) {
          @SuppressWarnings("unchecked") // Suppress complaints that we are casting to an erased type
          Vector<Object> oldRegion = (Vector<Object>)region.elementAt(j);
          Vector<Object> newRegion = substituteInRegion(oldRegion, var_name, fields,
                                                fieldData);
          processTemplateRegion(newRegion);
        }
      } // for j
    } // for curField
    data.close();
  }

  void processLoopRegion(Vector region) throws IOException {
    // get var name and data values
    if (DEBUG) System.out.println("params=\""+params+"\"");
    QuotedStringTokenizer pst = new QuotedStringTokenizer(params);
    if (!pst.hasMoreTokens())
       throw new IOException("Missing var name in LOOP");
    String var_name = pst.nextToken();
    Vector<String> valvec = new Vector<String>();
    while (pst.hasMoreTokens()) {
       String v_i = pst.nextToken();
       int dotdot = v_i.indexOf("..");
       if (dotdot != -1 && dotdot == v_i.lastIndexOf("..")) {
          int start = Integer.parseInt(v_i.substring(0, dotdot));
          int end = Integer.parseInt(v_i.substring(dotdot+2));
          for (int j = start; j <= end; j++)
             valvec.addElement(Integer.toString(j));
       } else
          valvec.addElement(v_i);
    }
    String[] values = new String[valvec.size()];
    for (int i = 0; i < values.length; i++)
       values[i] = valvec.elementAt(i);

    if (DEBUG) System.out.println("doing loop with varname "+var_name+
                                  " on values :"+
                                  params.substring(var_name.length()+1));

    // Loop through data values.
    for (int curValue = 0; curValue < values.length; curValue++) {
      // Count through each line in region.
      for (int j = 1; j < region.size(); j++) {
        try {
          String currentLine = (String) region.elementAt(j);
          // String result = substitute(currentLine, var_name, values[curValue]);
          String result = substitute(currentLine,
                                     var_name+"."+indexField,
                                     Integer.toString(curValue));
          result = substitute(result, var_name, values[curValue]);
          out.print(result+"\n");
        } catch (ClassCastException e) {
          @SuppressWarnings("unchecked") // Suppress complaints that we are casting to an erased type
          Vector<Object> oldRegion = (Vector<Object>) region.elementAt(j);
          // Vector newRegion = substituteInRegion(oldRegion, var_name,
          //                                       values[curValue]);
          Vector<Object> newRegion = substituteInRegion(oldRegion,
                                                var_name+"."+indexField,
                                                Integer.toString(curValue));
          newRegion = substituteInRegion(newRegion, var_name, values[curValue]);
          processTemplateRegion(newRegion);
        }
      } // for j
    } // for currentValue
  }

  void processCountRegion(Vector region) throws IOException {
    // get var name and data values
    if (DEBUG) System.out.println("params=\""+params+"\"");
    QuotedStringTokenizer pst = new QuotedStringTokenizer(params);
    if (!pst.hasMoreTokens())
       throw new IOException("Missing var name in COUNT");
    String var_name = pst.nextToken();
    int count = 0;
    while (pst.hasMoreTokens()) {
       String v_i = pst.nextToken();
       int dotdot = v_i.indexOf("..");
       if (dotdot != -1 && dotdot == v_i.lastIndexOf("..")) {
          int start = Integer.parseInt(v_i.substring(0, dotdot));
          int end = Integer.parseInt(v_i.substring(dotdot+2));
          for (int j = start; j <= end; j++)
             count++;
       } else
          count++;
    }
    String value = Integer.toString(count);

    if (DEBUG) System.out.println("doing count with varname "+var_name+
                                  " on values :"+
                                  params.substring(var_name.length()+1)+
                                  "; count="+value);

    // Count through each line in region.
    for (int j = 1; j < region.size(); j++) {
      try {
        String currentLine = (String) region.elementAt(j);
        String result = substitute(currentLine, var_name, value);
        out.print(result+"\n");
      } catch (ClassCastException e) {
        @SuppressWarnings("unchecked") // Suppress complaints that we are casting to an erased type
        Vector<Object> oldRegion = (Vector<Object>) region.elementAt(j);
        Vector<Object> newRegion = substituteInRegion(oldRegion, var_name, value);
        processTemplateRegion(newRegion);
      }
    } // for j
  }

  private String parseArg(StreamTokenizer st) throws IOException {
     if (st.nextToken() != '(') throw new IOException("Missing '('");
     String ret = evalStrExpr(st);
     if (st.nextToken() != ')') throw new IOException("Missing ')'");
     return ret;
  }

  private String evalUpper(StreamTokenizer st) throws IOException {
     return parseArg(st).toUpperCase();
  }

  private String evalLower(StreamTokenizer st) throws IOException {
     return parseArg(st).toLowerCase();
  }

  private String evalPad(StreamTokenizer st) throws IOException {
     if (st.nextToken() != '(') throw new IOException("Missing '('");
     StringBuilder val = new StringBuilder(evalStrExpr(st));
     int unpaddedSize = val.length();
     if (st.nextToken() != ',') throw new IOException("Missing ','");
     int len = evalExpr(st);
     if (st.nextToken() != ',') throw new IOException("Missing ','");
     if (st.nextToken() != '"') throw new IOException("Invalid string");
     String pad = st.sval;
     if (st.nextToken() != ')') throw new IOException("Missing ')'");
     while (val.length() < len)
        val.append(pad);
     val.setLength(Math.max(len, unpaddedSize));
     return val.toString();
  }

  private String evalSubst(StreamTokenizer st) throws IOException {
     if (st.nextToken() != '(') throw new IOException("Missing '('");
     StringBuilder val = new StringBuilder(evalStrExpr(st));
     if (st.nextToken() != ',') throw new IOException("Missing ','");
     if (st.nextToken() != '"') throw new IOException("Invalid string");
     String oldc = st.sval;
     if (st.nextToken() != ',') throw new IOException("Missing ','");
     if (st.nextToken() != '"') throw new IOException("Invalid string");
     String newc = st.sval;
     if (st.nextToken() != ')') throw new IOException("Missing ')'");
     for (int i = 0; i < val.length(); i++) {
        int l = oldc.indexOf(val.charAt(i));
        if (l != -1) val.setCharAt(i, newc.charAt(l));
     }
     return val.toString();
  }

  private boolean evalCond(StreamTokenizer st) throws IOException {
     int val = evalExpr(st);
     int token = st.nextToken();
     switch (token) {
        case '>': if (st.nextToken() == '=')
                     return val >= evalExpr(st);
                  else {
                     st.pushBack();
                     return val > evalExpr(st);
                  }
        case '<': if (st.nextToken() == '=')
                     return val <= evalExpr(st);
                  else {
                     st.pushBack();
                     return val < evalExpr(st);
                  }
        case '=': if (st.nextToken() != '=')
                     throw new IOException("Invalid token");
                  return val == evalExpr(st);
        case '!': if (st.nextToken() != '=')
                     throw new IOException("Invalid token");
                  return val != evalExpr(st);
        default:  throw new IOException("Invalid token");
     }
  }

  private String evalIf(StreamTokenizer st) throws IOException {
     if (st.nextToken() != '(') throw new IOException("Missing '('");
     boolean cond = evalCond(st);
     if (st.nextToken() != ',') throw new IOException("Missing ','");
     String valtrue = evalStrExpr(st);
     if (st.nextToken() != ',') throw new IOException("Missing ','");
     String valfalse = evalStrExpr(st);
     if (st.nextToken() != ')') throw new IOException("Missing ')'");
     if (cond)
        return valtrue;
     return valfalse;
  }

  private int evalLength(StreamTokenizer st) throws IOException {
     return parseArg(st).length();
  }

  private int evalFactor(StreamTokenizer st) throws IOException {
     int tok = st.nextToken();
     switch (tok) {
        case StreamTokenizer.TT_NUMBER: return (int) st.nval;
        case '-':          return -evalFactor(st);
        case '~':          return ~evalFactor(st);
        case '(':          int val = evalExpr(st);
                           if (st.nextToken() != ')')
                              throw new IOException("Mismatched parentheses");
                           return val;
        case StreamTokenizer.TT_WORD:   if (st.sval.equals("@LENGTH"))
                              return evalLength(st);
                           else
                              throw new IOException("Invalid token: "+tok);
        default:           throw new IOException("Invalid token: "+tok);
     }
  }

  private int evalTerm(StreamTokenizer st) throws IOException {
     int val = evalFactor(st);
     int token = st.nextToken();
     while (token == '*' || token == '/' || token == '%' || token == '&') {
        int t = evalFactor(st);
        switch (token) {
           case '*': val *= t; break;
           case '/': val /= t; break;
           case '%': val %= t; break;
           case '&': val &= t; break;
           default: throw new IOException("Invalid token");
        }
        token = st.nextToken();
     }
     st.pushBack();
     return val;
  }

  private int evalExpr(StreamTokenizer st) throws IOException {
     int val = evalTerm(st);
     int token = st.nextToken();
     while (token == '+' || token == '-' || token == '|' || token == '^') {
        int t = evalTerm(st);
        switch (token) {
           case '+': val += t; break;
           case '-': val -= t; break;
           case '|': val |= t; break;
           case '^': val ^= t; break;
           default: throw new IOException("Invalid token");
        }
        token = st.nextToken();
     }
     st.pushBack();
     return val;
  }

  private String evalString(StreamTokenizer st) throws IOException {
     int token = st.nextToken();
     switch (token) {
        case StreamTokenizer.TT_WORD:
           if      (st.sval.equals("@UPPER"))  return evalUpper(st);
           else if (st.sval.equals("@LOWER"))  return evalLower(st);
           else if (st.sval.equals("@PAD"))    return evalPad(st);
           else if (st.sval.equals("@SUBST"))  return evalSubst(st);
           else if (st.sval.equals("@IF"))     return evalIf(st);
           else if (st.sval.equals("@LENGTH")) break;             // in Factor
           else                                return st.sval;
        case '"': return st.sval;
        default:  break;
     }
     st.pushBack();
     return Integer.toString(evalExpr(st));
  }

  private String evalStrExpr(StreamTokenizer st) throws IOException {
     StringBuilder val = new StringBuilder(evalString(st));
     int token = st.nextToken();
     while (token == '`') {
        val.append(evalString(st));
        token = st.nextToken();
     }
     st.pushBack();
     return val.toString();
  }

  private String evalLet(StreamTokenizer st) throws IOException {
     String val = evalStrExpr(st);
     if (st.nextToken() != StreamTokenizer.TT_EOF) throw new IOException("Extra input: '"+st.ttype+"'");
     return val;
  }

  void processLetRegion(Vector region) throws IOException {
    // get var name and data values
    if (DEBUG) System.out.println("params=\""+params+"\"");
    StringReader sr = new StringReader(params);
    StreamTokenizer pst = new StreamTokenizer(sr);
//    pst.resetSyntax(); pst.parseNumbers(); pst.whitespaceChars(0, ' ');
//    pst.wordChars('a', 'z'); pst.wordChars('A', 'Z');
//    pst.wordChars(128 + 32, 255);
//    pst.quoteChar('"'); pst.quoteChar('\'');
    pst.ordinaryChar('-'); pst.ordinaryChar('/'); pst.ordinaryChar('*');
    pst.wordChars('@','@'); pst.wordChars('_', '_');
    int tok = pst.nextToken();
    if (tok != StreamTokenizer.TT_WORD)
       throw new IOException("Missing var name in LET");
    String var_name = pst.sval;
    if (pst.nextToken() == StreamTokenizer.TT_EOF)
       throw new IOException("Missing value in LET");
    pst.pushBack();
    String value = evalLet(pst);

    if (DEBUG) System.out.println("doing let with varname "+var_name+
                                  " and value=\""+value+"\"");

    // Execute region assigning data value.
    // Count through each line in region.
    for (int j = 1; j < region.size(); j++) {
      try {
        String currentLine = (String) region.elementAt(j);
        String result = substitute(currentLine, var_name, value);
        out.print(result+"\n");
      } catch (ClassCastException e) {
        @SuppressWarnings("unchecked") // Suppress complaints that we are casting to an erased type
        Vector<Object> oldRegion = (Vector) region.elementAt(j);
        Vector<Object> newRegion = substituteInRegion(oldRegion, var_name, value);
        processTemplateRegion(newRegion);
      }
    } // for j
  }

  void processJoinRegion(Vector<Object> region) throws IOException {
    // get var name and data values
    if (DEBUG) System.out.println("params=\""+params+"\"");
    QuotedStringTokenizer pst = new QuotedStringTokenizer(params);
    if (!pst.hasMoreTokens())
       throw new IOException("Missing var name in JOIN");
    String var_name = pst.nextToken();
    if (!pst.hasMoreTokens())
       throw new IOException("Missing separators in JOIN");
    String sep = pst.nextToken();
    int numValues = pst.countTokens();
    String value = "";
    if (pst.hasMoreTokens()) {
       value = pst.nextToken();
       for (int i = 1; i < numValues; i++)
          value += sep + pst.nextToken();
    }

    if (DEBUG) System.out.println("doing join with varname "+var_name+
                                  " and value=\""+value+"\"");

    // Execute region assigning data value.
    // Count through each line in region.
    for (int j = 1; j < region.size(); j++) {
      try {
        String currentLine = (String) region.elementAt(j);
        String result = substitute(currentLine, var_name, value);
        out.print(result+"\n");
      } catch (ClassCastException e) {
        @SuppressWarnings("unchecked") // Suppress complaints that we are casting to an erased type
        Vector<Object> oldRegion = (Vector<Object>) region.elementAt(j);
        Vector<Object> newRegion = substituteInRegion(oldRegion, var_name, value);
        processTemplateRegion(newRegion);
      }
    } // for j
  }

  void processSplitRegion(Vector<Object> region) throws IOException {
    // get data value and var names
    if (DEBUG) System.out.println("params=\""+params+"\"");
    QuotedStringTokenizer pst = new QuotedStringTokenizer(params);
    if (!pst.hasMoreTokens())
       throw new IOException("Missing value in SPLIT");
    String value = pst.nextToken();
    if (!pst.hasMoreTokens())
       throw new IOException("Missing separators in SPLIT");
    String sep = pst.nextToken();
    if (!pst.hasMoreTokens())
       throw new IOException("Missing variables in SPLIT");
    int numVars = pst.countTokens();
    String[] var_names = new String[numVars];
    for (int i = 0; i < numVars; i++)
       var_names[i] = pst.nextToken();
    StringTokenizer vst = new StringTokenizer(value, sep);
    String[] values = new String[numVars];
    for (int i = 0; i < numVars; i++)
       if (vst.hasMoreTokens())
          values[i] = vst.nextToken();
       else
          values[i] = "";

    if (DEBUG) System.out.println("doing split with value \""+value+
                                  "\" to vars :"+
                                  params.substring(value.length()+3));

    // Count through each line in region.
    for (int j = 1; j < region.size(); j++) {
      try {
        String result = (String) region.elementAt(j);
        // Loop through vars.
        for (int curVar = 0; curVar < var_names.length; curVar++)
          result = substitute(result, var_names[curVar], values[curVar]);
        out.print(result+"\n");
      } catch (ClassCastException e) {
        Vector<Object> newRegion = (Vector<Object>) region.elementAt(j);
        // Loop through vars.
        for (int curVar = 0; curVar < var_names.length; curVar++)
          newRegion = substituteInRegion(newRegion, var_names[curVar],
                                         values[curVar]);
        processTemplateRegion(newRegion);
      }
    } // for j
  }

  void processEvalRegion(Vector<Object> region) throws IOException {
    if (DEBUG) System.out.println("doing eval");

    PrintWriter old_out = out;
    StringWriter sw = new StringWriter();
    out = new PrintWriter(sw);

    // Count through each line in region.
    for (int j = 1; j < region.size(); j++) {
      try {
        String currentLine = (String) region.elementAt(j);
        out.print(currentLine+"\n");
      } catch (ClassCastException e) {
        @SuppressWarnings("unchecked") // Suppress complaints that we are casting to an erased type
        Vector<Object> tmpRegion = (Vector<Object>) region.elementAt(j);
        processTemplateRegion(tmpRegion);
      }
    } // for j

    out = old_out;

    if (DEBUG) System.out.println("doing eval: evaluating\n"+sw);

    LineNumberReader old_in = in;
    in = new LineNumberReader(new StringReader(sw.toString()));

    String inLine;
    // loop over strings in the newly created region
    for (inLine = readLine(); inLine != null; inLine = readLine()) {
      if (DEBUG) System.out.println("from input:"+inLine);
      if (!isTemplateLine(inLine)) {
        if (DEBUG) System.out.println("not template line, continuing...");
        out.print(inLine+"\n");
        continue;
      }
      Vector<Object> newRegion = buildTemplateRegion(inLine);
      processTemplateRegion(newRegion);
    }

    in = old_in;
  }

  boolean evaluateConditional(String arg, String op, String[] value) {
    boolean retval = false;
    op = op.intern();
    if      (op == "==") retval = arg.equals(value[0]);
    else if (op == "!=") retval = !arg.equals(value[0]);
    else if (op == "<")  retval = arg.compareTo(value[0]) <  0;
    else if (op == "<=") retval = arg.compareTo(value[0]) <= 0;
    else if (op == ">")  retval = arg.compareTo(value[0]) >  0;
    else if (op == ">=") retval = arg.compareTo(value[0]) >= 0;
    else if (op == "=~") retval = arg.indexOf(value[0]) >= 0;
    else if (op == "IN") {
      for (int i = 0; i < value.length && !retval; i++)
        if (arg.equals(value[i]))
          retval = true;
    } else if (op == "eq")
       retval = Integer.parseInt(arg) == Integer.parseInt(value[0]);
    else if (op == "ne")
       retval = Integer.parseInt(arg) != Integer.parseInt(value[0]);
    else if (op == "lt")
       retval = Integer.parseInt(arg) <  Integer.parseInt(value[0]);
    else if (op == "le")
       retval = Integer.parseInt(arg) <= Integer.parseInt(value[0]);
    else if (op == "gt")
       retval = Integer.parseInt(arg) >  Integer.parseInt(value[0]);
    else if (op == "ge")
       retval = Integer.parseInt(arg) >= Integer.parseInt(value[0]);
    return retval;
  }

  void processCondRegion(Vector region) throws IOException {
    // get var name, operation and data value
    QuotedStringTokenizer pst = new QuotedStringTokenizer(params);
    if (!pst.hasMoreTokens())
       throw new IOException("Missing argument in IF");
    String arg = pst.nextToken();
    if (!pst.hasMoreTokens())
       throw new IOException("Missing operation in IF");
    String op = pst.nextToken();
    String[] value = new String[pst.countTokens()];
    for (int i = 0; i < value.length; i++)
       value[i] = pst.nextToken();

    if (DEBUG) {
      if (value.length > 0)
        System.out.println("doing conditional "+arg+" "+op+" "+value[0]);
      else
        System.out.println("doing conditional "+arg+" "+op+" <NO ARGUMENT TOKEN FOUND>");
    }

    // Evaluate conditional.
    Vector newRegion = (Vector) region.elementAt(1);
    if (!evaluateConditional(arg, op, value)) {
      if (region.size() > 2)
        newRegion = (Vector) region.elementAt(2);
      else
        newRegion = new Vector();
      if (DEBUG) System.out.println("condition is false");
    } else {
      if (DEBUG) System.out.println("condition is true");
    }

    // Count through each line in region.
    for (int j = 0; j < newRegion.size(); j++) {
      try {
        String currentLine = (String) newRegion.elementAt(j);
        out.print(currentLine+"\n");
      } catch (ClassCastException e) {
        @SuppressWarnings("unchecked") // Suppress complaints that we are casting to an erased type
        Vector<Object> tmpRegion = (Vector<Object>) newRegion.elementAt(j);
        processTemplateRegion(tmpRegion);
      }
    } // for j
  }

  void processIncludeRegion(Vector<Object> region) throws IOException {
    // Count through each line in region.
    for (int j = 1; j < region.size(); j++) {
      try {
        String result = (String) region.elementAt(j);
        out.print(result+"\n");
      } catch (ClassCastException e) {
        @SuppressWarnings("unchecked") // Suppress complaints that we are casting to an erased type
        Vector<Object> newRegion = (Vector<Object>)region.elementAt(j);
        processTemplateRegion(newRegion);
      }
    } // for j
  }

  Vector<Object> substituteInRegion(Vector<Object> region, String var,
                                    String[] fields, String[] fieldData)
                                    throws IOException {
    Vector<Object> newRegion = new Vector<Object>(region.size());
    for (int i = 0; i < region.size(); i++) {
      Object el = region.elementAt(i);
      try {
        String s = (String) el;
        String r = substitute(s, var, fields, fieldData);
        newRegion.addElement(r);
      } catch (ClassCastException e) {
        @SuppressWarnings("unchecked") // Suppress complaints that we are casting to an erased type
        Vector<Object> s = (Vector<Object>)el;
        Vector<Object> r = substituteInRegion(s, var, fields, fieldData);
        newRegion.addElement(r);
      }
    }
    return newRegion;
  }

  Vector<Object> substituteInRegion(Vector<Object> region, String var, String value)
  throws IOException {
    Vector<Object> newRegion = new Vector<Object>(region.size());
    for (int i = 0; i < region.size(); i++) {
      Object el = region.elementAt(i);
      try {
        String s = (String) el;
        String r = substitute(s, var, value);
        newRegion.addElement((Object)r);
      } catch (ClassCastException e) {
        @SuppressWarnings("unchecked") // Suppress complaints that we are casting to an erased type
        Vector<Object> s = (Vector<Object>)el;
        Vector<Object> r = substituteInRegion(s, var, value);
        newRegion.addElement(r);
      }
    }
    return newRegion;
  }

  String substitute(String input, String var,
                    String[] fields, String[] fieldData) throws IOException {
    StringBuilder out = new StringBuilder();
    int varlen = var.length();
    int oidx = 0;
    for (;;) {
      if (DEBUG) System.out.println("checking for occurrence of "+var+
                                    " in :"+input.substring(oidx));
      int idx = input.indexOf(var, oidx);
      if (idx == -1) break;

      // Write stuff inbetween last variable and current variable
      out.append(input.substring(oidx, idx));
      idx += varlen;
      if (input.charAt(idx) != '.') throw new IOException("no field");
      idx++;

      // Find which field this is
      int idx_save = idx;
      for (int i = 0; i < fields.length; i++) {
        String fld = fields[i];
        int flen = fld.length();
        if (DEBUG) System.out.println("checking if it is field "+fld);
        if (input.regionMatches(idx, fld, 0, flen)) {
          String value = fieldData[i];
          if (DEBUG) System.out.println("field matches. outputting data :"+
                                        value);
          out.append(value);
          idx += flen;
          break;
        }
      }
      if (idx == idx_save) throw new IOException("unknown field");
      oidx = idx;
    }
    if (DEBUG) System.out.println("no more variables left on this line");
    out.append(input.substring(oidx));

    return out.toString();
  }

  String substitute(String input, String var, String value) throws IOException {
    StringBuilder out = new StringBuilder();
    int varlen = var.length();
    int oidx = 0;
    for (;;) {
      int idx = input.indexOf(var, oidx);
      if (idx == -1) break;
      out.append(input.substring(oidx, idx));
      idx += varlen;
      out.append(value);
      oidx = idx;
    }
    out.append(input.substring(oidx));

    return out.toString();
  }
}

