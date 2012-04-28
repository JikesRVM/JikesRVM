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
package org.jikesrvm.options;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.Extent;
import org.vmmagic.unboxed.Word;
import org.vmutil.options.AddressOption;
import org.vmutil.options.BooleanOption;
import org.vmutil.options.EnumOption;
import org.vmutil.options.FloatOption;
import org.vmutil.options.IntOption;
import org.vmutil.options.MicrosecondsOption;
import org.vmutil.options.Option;
import org.vmutil.options.PagesOption;
import org.vmutil.options.StringOption;

import org.jikesrvm.VM;
import org.jikesrvm.Constants;
import org.jikesrvm.CommandLineArgs;

/**
 * Class to handle command-line arguments and options for GC.
 */
public final class OptionSet extends org.vmutil.options.OptionSet {

  private String prefix;

  public static final OptionSet gc = new OptionSet("-X:gc");

  private OptionSet(String prefix) {
    this.prefix = prefix;
  }

  /**
   * Take a string (most likely a command-line argument) and try to proccess it
   * as an option command.  Return true if the string was understood, false
   * otherwise.
   *
   * @param arg a String to try to process as an option command
   * @return true if successful, false otherwise
   */
  public boolean process(String arg) {

    // First handle the "option commands"
    if (arg.equals("help")) {
       printHelp();
       return true;
    }
    if (arg.equals("printOptions")) {
       printOptions();
       return true;
    }
    if (arg.length() == 0) {
      printHelp();
      return true;
    }

    // Required format of arg is 'name=value'
    // Split into 'name' and 'value' strings
    int split = arg.indexOf('=');
    if (split == -1) {
      VM.sysWriteln("  Illegal option specification!\n  \""+arg+
                  "\" must be specified as a name-value pair in the form of option=value");
      return false;
    }

    String name = arg.substring(0,split);
    String value = arg.substring(split+1);

    Option o = getOption(name);

    if (o == null) return false;

    switch (o.getType()) {
      case Option.BOOLEAN_OPTION:
        if (value.equals("true")) {
          ((BooleanOption)o).setValue(true);
          return true;
        } else if (value.equals("false")) {
          ((BooleanOption)o).setValue(false);
          return true;
        }
        return false;
      case Option.INT_OPTION:
        int ival = CommandLineArgs.primitiveParseInt(value);
        ((IntOption)o).setValue(ival);
        return true;
      case Option.ADDRESS_OPTION:
        ival = CommandLineArgs.primitiveParseInt(value);
        ((AddressOption)o).setValue(ival);
        return true;
      case Option.FLOAT_OPTION:
        float fval = CommandLineArgs.primitiveParseFloat(value);
        ((FloatOption)o).setValue(fval);
        return true;
      case Option.STRING_OPTION:
        ((StringOption)o).setValue(value);
        return true;
      case Option.ENUM_OPTION:
        ((EnumOption)o).setValue(value);
        return true;
      case Option.PAGES_OPTION:
        long pval = CommandLineArgs.parseMemorySize(o.getName(), name, "b", 1, arg, value);
        if (pval < 0) return false;
        ((PagesOption)o).setBytes(Extent.fromIntSignExtend((int)pval));
        return true;
      case Option.MICROSECONDS_OPTION:
        int mval = CommandLineArgs.primitiveParseInt(value);
        ((MicrosecondsOption)o).setMicroseconds(mval);
        return true;
    }

    // None of the above tests matched, so this wasn't an option
    return false;
  }

  /**
   * Print a short description of every option
   */
  public void printHelp() {

    VM.sysWriteln("Commands");
    VM.sysWrite(prefix);VM.sysWriteln("[:help]\t\t\tPrint brief description of arguments");
    VM.sysWrite(prefix);VM.sysWriteln(":printOptions\t\tPrint the current values of options");
    VM.sysWriteln();

    //Begin generated help messages
    VM.sysWrite("Boolean Options (");
    VM.sysWrite(prefix);VM.sysWrite(":<option>=true or ");
    VM.sysWrite(prefix);VM.sysWriteln(":<option>=false)");
    VM.sysWriteln("Option                                 Description");

    Option o = getFirst();
    while (o != null) {
      if (o.getType() == Option.BOOLEAN_OPTION) {
        String key = o.getKey();
        VM.sysWrite(key);
        for (int c = key.length(); c<39;c++) {
          VM.sysWrite(" ");
        }
        VM.sysWriteln(o.getDescription());
      }
      o = o.getNext();
    }

    VM.sysWrite("\nValue Options (");VM.sysWrite(prefix);VM.sysWriteln(":<option>=<value>)");
    VM.sysWriteln("Option                         Type    Description");

    o = getFirst();
    while (o != null) {
      if (o.getType() != Option.BOOLEAN_OPTION &&
          o.getType() != Option.ENUM_OPTION) {
        String key = o.getKey();
        VM.sysWrite(key);
        for (int c = key.length(); c<31;c++) {
          VM.sysWrite(" ");
        }
        switch (o.getType()) {
          case Option.INT_OPTION:          VM.sysWrite("int     "); break;
          case Option.ADDRESS_OPTION:      VM.sysWrite("address "); break;
          case Option.FLOAT_OPTION:        VM.sysWrite("float   "); break;
          case Option.MICROSECONDS_OPTION: VM.sysWrite("usec    "); break;
          case Option.PAGES_OPTION:        VM.sysWrite("bytes   "); break;
          case Option.STRING_OPTION:       VM.sysWrite("string  "); break;
        }
        VM.sysWriteln(o.getDescription());
      }
      o = o.getNext();
    }

    VM.sysWriteln("\nSelection Options (set option to one of an enumeration of possible values)");

    o = getFirst();
    while (o != null) {
      if (o.getType() == Option.ENUM_OPTION) {
        String key = o.getKey();
        VM.sysWrite(key);
        for (int c = key.length(); c<31;c++) {
          VM.sysWrite(" ");
        }
        VM.sysWriteln(o.getDescription());
        VM.sysWrite("    { ");
        boolean first = true;
        for (String val : ((EnumOption)o).getValues()) {
          VM.sysWrite(first ? "" : ", ");
          VM.sysWrite(val);
          first = false;
        }
        VM.sysWriteln(" }");
      }
      o = o.getNext();
    }

    VM.sysExit(VM.EXIT_STATUS_PRINTED_HELP_MESSAGE);
  }

  /**
   * Print out the option values
   */
  public void printOptions() {
    VM.sysWriteln("Current value of GC options");

    Option o = getFirst();
    while (o != null) {
      if (o.getType() == Option.BOOLEAN_OPTION) {
        String key = o.getKey();
        VM.sysWrite("\t");
        VM.sysWrite(key);
        for (int c = key.length(); c<31;c++) {
          VM.sysWrite(" ");
        }
        VM.sysWrite(" = ");
        logValue(o, false);
        VM.sysWriteln();
      }
      o = o.getNext();
    }

    o = getFirst();
    while (o != null) {
      if (o.getType() != Option.BOOLEAN_OPTION &&
          o.getType() != Option.ENUM_OPTION) {
        String key = o.getKey();
        VM.sysWrite("\t");
        VM.sysWrite(key);
        for (int c = key.length(); c<31;c++) {
          VM.sysWrite(" ");
        }
        VM.sysWrite(" = ");
        logValue(o, false);
        VM.sysWriteln();
      }
      o = o.getNext();
    }

    o = getFirst();
    while (o != null) {
      if (o.getType() == Option.ENUM_OPTION) {
        String key = o.getKey();
        VM.sysWrite("\t");
        VM.sysWrite(key);
        for (int c = key.length(); c<31;c++) {
          VM.sysWrite(" ");
        }
        VM.sysWrite(" = ");
        logValue(o, false);
        VM.sysWriteln();
      }
      o = o.getNext();
    }
  }

  @Override
  protected void logValue(Option o, boolean forXml) {
    switch (o.getType()) {
    case Option.BOOLEAN_OPTION:
      VM.sysWrite(((BooleanOption) o).getValue() ? "true" : "false");
      break;
    case Option.INT_OPTION:
      VM.sysWrite(((IntOption) o).getValue());
      break;
    case Option.ADDRESS_OPTION:
      VM.sysWrite(((AddressOption) o).getValue());
      break;
    case Option.FLOAT_OPTION:
      VM.sysWrite(((FloatOption) o).getValue());
      break;
    case Option.MICROSECONDS_OPTION:
      VM.sysWrite(((MicrosecondsOption) o).getMicroseconds());
      VM.sysWrite(" usec");
      break;
    case Option.PAGES_OPTION:
      VM.sysWrite(((PagesOption) o).getBytes());
      VM.sysWrite(" bytes");
      break;
    case Option.STRING_OPTION:
      VM.sysWrite(((StringOption) o).getValue());
      break;
    case Option.ENUM_OPTION:
      VM.sysWrite(((EnumOption) o).getValueString());
      break;
    }
  }

  @Override
  protected void logString(String s) {
    VM.sysWrite(s);
  }

  @Override
  protected void logNewLine() {
    VM.sysWriteln();
  }

  @Override
  protected String computeKey(String name) {
    int space = name.indexOf(' ');
    if (space < 0) return name.toLowerCase();

    String word = name.substring(0, space);
    String key = word.toLowerCase();

    do {
      int old = space+1;
      space = name.indexOf(' ', old);
      if (space < 0) {
        key += name.substring(old);
        return key;
      }
      key += name.substring(old, space);
    } while (true);
  }

  @Override
  protected void warn(Option o, String message) {
    VM.sysWriteln("WARNING: Option '" + o.getKey() + "' : " + message);
  }

  @Override
  protected void fail(Option o, String message) {
    VM.sysFail("ERROR: Option '" + o.getKey() + "' : " + message);
  }

  @Override
  @Uninterruptible
  protected int bytesToPages(Extent bytes) {
    return bytes.plus(Constants.BYTES_IN_PAGE-1).toWord().rshl(Constants.LOG_BYTES_IN_PAGE).toInt();
  }

  @Override
  @Uninterruptible
  protected Extent pagesToBytes(int pages) {
    return Word.fromIntZeroExtend(pages).lsh(Constants.LOG_BYTES_IN_PAGE).toExtent();
  }
}
