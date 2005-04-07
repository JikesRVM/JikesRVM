/* 
 * (C) Copyright ANU. 2004
 */
package org.mmtk.vm;
 
import org.vmmagic.unboxed.Extent;

import org.mmtk.utility.options.*;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_CommandLineArgs;

/**
 * Class to handle command-line arguments and options for GC.
 *
 * $Id$ 
 *
 * @author Daniel Frampton 
 *
 * @version $Revision$
 * @date $Date$
 */
public final class Options {

  /**
   * Map a name into a key in the VM's format
   *
   * @param name the space delimited name. 
   * @return the vm specific key.
   */
  public static String getKey(String name) {
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
   * Take a string (most likely a command-line argument) and try to proccess it
   * as an option command.  Return true if the string was understood, false 
   * otherwise.
   *
   * @param arg a String to try to process as an option command
   * @return true if successful, false otherwise
   */
  public static boolean process(String arg) {

    // First handle the "option commands"
    if (arg.equals("help")) {
       printHelp();
       return true;
    }
    if (arg.equals("printOptions")) {
       printOptions();
       return true;
    }
    if (arg.equals("")) {
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

    Option o = Option.getOption(name); 

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
        int ival = VM_CommandLineArgs.primitiveParseInt(value);
        ((IntOption)o).setValue(ival);
        return true;
      case Option.FLOAT_OPTION:
        float fval = VM_CommandLineArgs.primitiveParseFloat(value);
        ((FloatOption)o).setValue(fval);
        return true;
      case Option.LONG_OPTION:
      case Option.DOUBLE_OPTION:
        // Not supported yet
        // XXX DF: Implement when required.
        return false;
      case Option.STRING_OPTION:
        ((StringOption)o).setValue(value);
        return true;
      case Option.ENUM_OPTION:
        ((EnumOption)o).setValue(value);
        return true;
      case Option.PAGES_OPTION:
        long pval = VM_CommandLineArgs.parseMemorySize(
          o.getName(),
          ":gc:" + o.getKey() + "=",
          "b",
          1,
          ":gc:" + o.getKey() + "=" + value,
          value);
        if (pval < 0) return false;
        ((PagesOption)o).setBytes(Extent.fromInt((int)pval));
        return true;
      case Option.MICROSECONDS_OPTION:
        int mval = VM_CommandLineArgs.primitiveParseInt(value);
        mval = mval; 
        ((MicrosecondsOption)o).setMicroseconds(mval);
        return true;
    }

    // None of the above tests matched, so this wasn't an option
    return false;
  }

  /**
   * Failure during option processing. This must never return.
   *
   * @param o The option that was being set.
   * @param message The error message.
   */
  public static void fail(Option o, String message) {
      VM.sysFail("ERROR: Option '" + o.getKey() + "' : " + 
                 message);
  }

  /**
   * Warning during option processing.
   *
   * @param o The option that was being set.
   * @param message The warning message.
   */
  public static void warn(Option o, String message) {
      VM.sysWriteln("WARNING: Option '" + o.getKey() + "' : " + 
                    message);
  }

  /**
   * Print a short description of every option
   */
  public static void printHelp() {

    VM.sysWriteln("Commands");
    VM.sysWriteln("-X:gc[:help]\t\t\tPrint brief description of GC arguments");
    VM.sysWriteln("-X:gc:printOptions\t\tPrint the current values of GC options");
    VM.sysWriteln();

    //Begin generated help messages
    VM.sysWriteln("Boolean Options (-X:gc:<option>=true or -X:gc:<option>=false)");
    VM.sysWriteln("Option                                 Description");

    Option o = Option.getFirst();
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

    VM.sysWriteln("\nValue Options (-X:gc:<option>=<value>)");
    VM.sysWriteln("Option                         Type    Description");
    
    o = Option.getFirst();
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
          case Option.FLOAT_OPTION:        VM.sysWrite("float   "); break;
          case Option.LONG_OPTION:         VM.sysWrite("long    "); break;
          case Option.MICROSECONDS_OPTION: VM.sysWrite("usec    "); break;
          case Option.PAGES_OPTION:        VM.sysWrite("bytes   "); break;
          case Option.STRING_OPTION:       VM.sysWrite("string  "); break;
        }
        VM.sysWriteln(o.getDescription()); 
      }
      o = o.getNext();
    }

    VM.sysWriteln("\nSelection Options (set option to one of an enumeration of possible values)");

    o = Option.getFirst();
    while (o != null) {
      if (o.getType() == Option.ENUM_OPTION) { 
        VM.sysWrite("\t\t");
        VM.sysWriteln(o.getDescription());
        String key = o.getKey();
        VM.sysWrite(key);
        for (int c = key.length(); c<31;c++) {
          VM.sysWrite(" ");
        }
        String[] vals = ((EnumOption)o).getValues();
        for(int j=0; j<vals.length; j++) {
          VM.sysWrite(vals[j]);
          VM.sysWrite(" ");
        }
        VM.sysWriteln();
      }
      o = o.getNext();
    }

    VM.sysExit(VM.EXIT_STATUS_PRINTED_HELP_MESSAGE);
  }    

  /**
   * Print out the option values
   */
  public static void printOptions() {
    VM.sysWriteln("Current value of GC options");

    Option o = Option.getFirst();
    while (o != null) {
      if (o.getType() == Option.BOOLEAN_OPTION) {
        String key = o.getKey();
        VM.sysWrite("\t");
        VM.sysWrite(key);
        for (int c = key.length(); c<31;c++) {
          VM.sysWrite(" ");
        }
        VM.sysWrite(" = ");
        VM.sysWriteln(((BooleanOption)o).getValue());
      }
      o = o.getNext();
    }

    o = Option.getFirst();
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
        switch (o.getType()) {
          case Option.INT_OPTION:
            VM.sysWriteln(((IntOption)o).getValue()); 
            break;
          case Option.FLOAT_OPTION:
            VM.sysWriteln(((FloatOption)o).getValue()); 
            break;
          case Option.LONG_OPTION:
            VM.sysWriteln(((LongOption)o).getValue()); 
            break;
          case Option.MICROSECONDS_OPTION:
            VM.sysWrite(((MicrosecondsOption)o).getMicroseconds()); 
            VM.sysWriteln(" usec");
            break;
          case Option.PAGES_OPTION:
            VM.sysWrite(((PagesOption)o).getBytes());
            VM.sysWriteln(" bytes");
            break;
          case Option.STRING_OPTION:
            VM.sysWriteln(((StringOption)o).getValue()); 
            break;
        }
      }
      o = o.getNext();
    }

    o = Option.getFirst();
    while (o != null) {
      if (o.getType() == Option.ENUM_OPTION) {
        String key = o.getKey();
        VM.sysWrite("\t");
        VM.sysWrite(key);
        for (int c = key.length(); c<31;c++) {
          VM.sysWrite(" ");
        }
        VM.sysWrite(" = ");
        VM.sysWriteln(((EnumOption)o).getValueString());
      }
      o = o.getNext();
    }
  }
}
