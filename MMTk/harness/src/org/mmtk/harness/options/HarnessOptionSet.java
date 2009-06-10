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
package org.mmtk.harness.options;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;
import org.vmutil.options.AddressOption;
import org.vmutil.options.BooleanOption;
import org.vmutil.options.EnumOption;
import org.vmutil.options.FloatOption;
import org.vmutil.options.IntOption;
import org.vmutil.options.MicrosecondsOption;
import org.vmutil.options.Option;
import org.vmutil.options.PagesOption;
import org.vmutil.options.StringOption;

/**
 * Class to handle command-line arguments and options for GC.
 */
public final class HarnessOptionSet extends org.vmutil.options.OptionSet {

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
      System.err.println("  Illegal option specification!\n  \""+arg+
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
        try {
          int ival = Integer.parseInt(value);
          ((IntOption)o).setValue(ival);
          return true;
        } catch (NumberFormatException nfe) {}
        return false;
      case Option.ADDRESS_OPTION:
        try {
          int ival = Integer.parseInt(value);
          ((AddressOption)o).setValue(ival);
          return true;
        } catch (NumberFormatException nfe) {}
        return false;
      case Option.FLOAT_OPTION:
        try {
          float fval = Float.parseFloat(value);
          ((FloatOption)o).setValue(fval);
          return true;
        } catch (NumberFormatException nfe) {}
        return false;
      case Option.STRING_OPTION:
        ((StringOption)o).setValue(value);
        return true;
      case Option.ENUM_OPTION:
        ((EnumOption)o).setValue(value);
        return true;
      case Option.PAGES_OPTION:
        try {
          char last = value.charAt(value.length() - 1);
          int factor = 1;
          switch (last) {
            case 'g': case 'G': factor *= 1024;
            case 'm': case 'M': factor *= 1024;
            case 'k': case 'K': factor *= 1024;
              value = value.substring(0, value.length() - 1);
          }
          int ival = Integer.parseInt(value);
          ((PagesOption)o).setBytes(Extent.fromIntZeroExtend(ival * factor));
          return true;
        } catch (NumberFormatException nfe) {
        } catch (IndexOutOfBoundsException nfe) {}
        return false;
      case Option.MICROSECONDS_OPTION:
        try {
          int ival = Integer.parseInt(value);
          ((MicrosecondsOption)o).setMicroseconds(ival);
          return true;
        } catch (NumberFormatException nfe) {}
        return false;
    }

    // None of the above tests matched, so this wasn't an option
    return false;
  }

  /**
   * Print a short description of every option
   */
  public void printHelp() {

    System.err.println("Commands");
    System.err.println("help\t\t\tPrint brief description of arguments");
    System.err.println("printOptions\t\tPrint the current values of options");
    System.err.println();

    //Begin generated help messages
    System.err.print("Boolean Options (");
    System.err.print("<option>=true or ");
    System.err.println("<option>=false)");
    System.err.println("Option                                 Description");

    Option o = getFirst();
    while (o != null) {
      if (o.getType() == Option.BOOLEAN_OPTION) {
        String key = o.getKey();
        System.err.print(key);
        for (int c = key.length(); c<39;c++) {
          System.err.print(" ");
        }
        System.err.println(o.getDescription());
      }
      o = o.getNext();
    }

    System.err.print("\nValue Options (");System.err.println("<option>=<value>)");
    System.err.println("Option                         Type    Description");

    o = getFirst();
    while (o != null) {
      if (o.getType() != Option.BOOLEAN_OPTION &&
          o.getType() != Option.ENUM_OPTION) {
        String key = o.getKey();
        System.err.print(key);
        for (int c = key.length(); c<31;c++) {
          System.err.print(" ");
        }
        switch (o.getType()) {
          case Option.INT_OPTION:          System.err.print("int     "); break;
          case Option.ADDRESS_OPTION:      System.err.print("address "); break;
          case Option.FLOAT_OPTION:        System.err.print("float   "); break;
          case Option.MICROSECONDS_OPTION: System.err.print("usec    "); break;
          case Option.PAGES_OPTION:        System.err.print("bytes   "); break;
          case Option.STRING_OPTION:       System.err.print("string  "); break;
        }
        System.err.println(o.getDescription());
      }
      o = o.getNext();
    }

    System.err.println("\nSelection Options (set option to one of an enumeration of possible values)");

    o = getFirst();
    while (o != null) {
      if (o.getType() == Option.ENUM_OPTION) {
        String key = o.getKey();
        System.err.print(key);
        for (int c = key.length(); c<31;c++) {
          System.err.print(" ");
        }
        System.err.println(o.getDescription());
        System.err.print("    { ");
        boolean first = true;
        for (String val : ((EnumOption)o).getValues()) {
          System.err.print(first ? "" : ", ");
          System.err.print(val);
          first = false;
        }
        System.err.println(" }");
      }
      o = o.getNext();
    }

    System.exit(-1);
  }

  /**
   * Print out the option values
   */
  public void printOptions() {
    System.err.println("Current value of GC options");

    Option o = getFirst();
    while (o != null) {
      if (o.getType() == Option.BOOLEAN_OPTION) {
        String key = o.getKey();
        System.err.print("\t");
        System.err.print(key);
        for (int c = key.length(); c<31;c++) {
          System.err.print(" ");
        }
        System.err.print(" = ");
        logValue(o, false);
        System.err.println();
      }
      o = o.getNext();
    }

    o = getFirst();
    while (o != null) {
      if (o.getType() != Option.BOOLEAN_OPTION &&
          o.getType() != Option.ENUM_OPTION) {
        String key = o.getKey();
        System.err.print("\t");
        System.err.print(key);
        for (int c = key.length(); c<31;c++) {
          System.err.print(" ");
        }
        System.err.print(" = ");
        logValue(o, false);
        System.err.println();
      }
      o = o.getNext();
    }

    o = getFirst();
    while (o != null) {
      if (o.getType() == Option.ENUM_OPTION) {
        String key = o.getKey();
        System.err.print("\t");
        System.err.print(key);
        for (int c = key.length(); c<31;c++) {
          System.err.print(" ");
        }
        System.err.print(" = ");
        logValue(o, false);
        System.err.println();
      }
      o = o.getNext();
    }
  }

  /**
   * Format and log an option value.
   *
   * @param o The option.
   * @param forXml Is this part of xml output?
   */
  protected void logValue(Option o, boolean forXml) {
    switch (o.getType()) {
    case Option.BOOLEAN_OPTION:
      System.err.print(((BooleanOption) o).getValue() ? "true" : "false");
      break;
    case Option.INT_OPTION:
      System.err.print(((IntOption) o).getValue());
      break;
    case Option.ADDRESS_OPTION:
      System.err.print(((AddressOption) o).getValue());
      break;
    case Option.FLOAT_OPTION:
      System.err.print(((FloatOption) o).getValue());
      break;
    case Option.MICROSECONDS_OPTION:
      System.err.print(((MicrosecondsOption) o).getMicroseconds());
      System.err.print(" usec");
      break;
    case Option.PAGES_OPTION:
      System.err.print(((PagesOption) o).getBytes());
      System.err.print(" bytes");
      break;
    case Option.STRING_OPTION:
      System.err.print(((StringOption) o).getValue());
      break;
    case Option.ENUM_OPTION:
      System.err.print(((EnumOption) o).getValueString());
      break;
    }
  }

  /**
   * Log a string.
   */
  protected void logString(String s) {
    System.err.print(s);
  }

  /**
   * Print a new line.
   */
  protected void logNewLine() {
    System.err.println();
  }

  /**
   * Determine the VM specific key for a given option name. Option names are
   * space delimited with capitalised words (e.g. "GC Verbosity Level").
   *
   * @param name The option name.
   * @return The VM specific key.
   */
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

  /**
   * A non-fatal error occurred during the setting of an option. This method
   * calls into the VM and shall not cause the system to stop.
   *
   * @param o The responsible option.
   * @param message The message associated with the warning.
   */
  protected void warn(Option o, String message) {
    System.err.println("WARNING: Option '" + o.getKey() + "' : " + message);
  }

  /**
   * A fatal error occurred during the setting of an option. This method
   * calls into the VM and is required to cause the system to stop.
   *
   * @param o The responsible option.
   * @param message The error message associated with the failure.
   */
  protected void fail(Option o, String message) {
    throw new RuntimeException("Option '" + o.getKey() + "' : " + message);
  }

  /**
   * Convert bytes into pages, rounding up if necessary.
   *
   * @param bytes The number of bytes.
   * @return The corresponding number of pages.
   */
  @Uninterruptible
  protected int bytesToPages(Extent bytes) {
    return bytes.plus(SimulatedMemory.BYTES_IN_PAGE-1).toWord().rshl(SimulatedMemory.LOG_BYTES_IN_PAGE).toInt();
  }

  /**
   * Convert from pages into bytes.
   * @param pages the number of pages.
   * @return The corresponding number of bytes.
   */
  @Uninterruptible
  protected Extent pagesToBytes(int pages) {
    return Word.fromIntZeroExtend(pages).lsh(SimulatedMemory.LOG_BYTES_IN_PAGE).toExtent();
  }
}
