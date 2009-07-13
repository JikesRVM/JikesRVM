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
package org.vmutil.options;

import org.vmmagic.pragma.Uninterruptible;
import org.vmmagic.unboxed.*;

/**
 * The abstract base class for all option sets.
 *
 * Concrete instantiations of this class include logic
 *
 * All options within the system should have a unique name. No
 * two options shall have a name that is the same when a case
 * insensitive comparison between the names with spaces removed
 * is performed. Only basic alphanumeric characters and spaces
 * are allowed.
 *
 * The VM is required to provide a one way mapping function that
 * takes the name and creates a VM style name, such as mapping
 * "No Finalizer" to noFinalizer. The VM may not remove any letters
 * when performing this mapping but may remove spaces and change
 * the case of any character.
 */
public abstract class OptionSet {
  private Option head;
  private Option tail;
  private boolean loggingChanges;

  /**
   * Initialize the option set so that options can be created.
   */
  protected OptionSet() {
    head = null;
    tail = null;
    loggingChanges = false;
  }

  /**
   * Register the option to this set, computing its key in the process.
   *
   * @param o The option to register.
   */
  final String register(Option o, String name) {
    if (tail == null) {
      tail = head = o;
    } else {
      tail.setNext(o);
      tail = o;
    }
    return computeKey(name);
  }

  /**
   * Using the VM determined key, look up the corresponding option,
   * or return null if an option can not be found.
   *
   * @param key The (unique) option key.
   * @return The option, or null.
   */
  public final Option getOption(String key) {
    Option o = getFirst();
    while (o != null) {
      if (o.getKey().equals(key)) {
        return o;
      }
      o = o.getNext();
    }
    return null;
  }

  /**
   * Return the first option. This can be used with the getNext method to
   * iterate through the options.
   *
   * @return The first option, or null if no options exist.
   */
  public final Option getFirst() {
    return head;
  }

  /**
   * Log an option change
   * @param o The option that changed
   */
  public void logChange(Option o) {
    if (loggingChanges) {
      logString("Option Update: ");
      log(o);
    }
  }

  /**
   * Log the option value in plain text.
   *
   * @param o The option to log.
   */
  public void log(Option o) {
    logString("Option '");
    logString(o.getKey());
    logString("' = ");
    logValue(o, false);
    logNewLine();
  }

  /**
   * Log the option value in Xml.
   *
   * @param o The option to log.
   */
  public void logXml(Option o) {
    logString("<option name=\"");
    logString(o.getKey());
    logString("\" value=\"");
    logValue(o, true);
    logString("\"/>");
    logNewLine();
  }

  /**
   * Log the option values in Xml.
   */
  public void logXml() {
    logString("<options>");
    logNewLine();

    for(Option o = getFirst(); o != null; o = o.getNext()) {
      logXml(o);
    }

    logString("</options>");
    logNewLine();
  }

  /**
   * Format and log an option value.
   *
   * @param o The option.
   * @param forXml Is this part of xml output?
   */
  protected abstract void logValue(Option o, boolean forXml);

  /**
   * Log a string.
   */
  protected abstract void logString(String s);

  /**
   * Print a new line.
   */
  protected abstract void logNewLine();

  /**
   * Determine the VM specific key for a given option name. Option names are
   * space delimited with capitalised words (e.g. "GC Verbosity Level").
   *
   * @param name The option name.
   * @return The VM specific key.
   */
  protected abstract String computeKey(String name);

  /**
   * A non-fatal error occurred during the setting of an option. This method
   * calls into the VM and shall not cause the system to stop.
   *
   * @param o The responsible option.
   * @param message The message associated with the warning.
   */
  protected abstract void warn(Option o, String message);

  /**
   * A fatal error occurred during the setting of an option. This method
   * calls into the VM and is required to cause the system to stop.
   *
   * @param o The responsible option.
   * @param message The error message associated with the failure.
   */
  protected abstract void fail(Option o, String message);

  /**
   * Convert bytes into pages, rounding up if necessary.
   *
   * @param bytes The number of bytes.
   * @return The corresponding number of pages.
   */
  @Uninterruptible
  protected abstract int bytesToPages(Extent bytes);

  /**
   * Convert from pages into bytes.
   * @param pages the number of pages.
   * @return The corresponding number of bytes.
   */
  @Uninterruptible
  protected abstract Extent pagesToBytes(int pages);
}

