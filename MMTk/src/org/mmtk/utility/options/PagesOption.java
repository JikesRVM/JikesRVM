/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 */
package org.mmtk.utility.options;

import org.mmtk.utility.Log;
import org.mmtk.utility.Conversions;

import org.vmmagic.pragma.UninterruptiblePragma;
import org.vmmagic.unboxed.Extent;

/**
 * A memory option that stores values as a whole number of pages.
 *
 * $Id$
 *
 * @author Daniel Frampton
 * @version $Revision$
 * @date $Date$
 */
public class PagesOption extends Option {
  // values
  protected int defaultValue;
  protected int value;

  /**
   * Create a new pages option.
   *
   * @param name The space separated name for the option.
   * @param desc The purpose of the option
   * @param defaultPages The default value of the option.
   */
  protected PagesOption(String name, String desc, int defaultPages) {
    super(PAGES_OPTION, name, desc);
    this.value = this.defaultValue = defaultPages;
  }

  /**
   * Read the current value of the option in pages.
   *
   * @return The option value.
   */
  public int getPages() throws UninterruptiblePragma {
    return this.value;
  }

  /**
   * Read the current value of the option in bytes.
   *
   * @return The option value.
   */
  public Extent getBytes() throws UninterruptiblePragma {
    return Conversions.pagesToBytes(this.value);
  }

  /**
   * Read the default value of the option in bytes.
   *
   * @return The default value.
   */
  public Extent getDefaultBytes() throws UninterruptiblePragma {
    return Conversions.pagesToBytes(this.defaultValue);
  }

  /**
   * Read the default value of the option in pages.
   *
   * @return The default value.
   */
  public int getDefaultPages() throws UninterruptiblePragma {
    return this.defaultValue;
  }

  /**
   * Update the value of the option, echoing the change if the echoOptions
   * option is set. A warning is raised if the value is not a whole multiple
   * of pages, and then the validate method is called to allow subclasses to
   * perform any additional validation.
   *
   * @param value The new value for the option.
   */
  public void setBytes(Extent value) {
    Extent oldValue = getBytes();
    if (Options.echoOptions.getValue()) {
      Log.write("Option '");
      Log.write(this.getKey());
      Log.write("' set ");
      Log.write(oldValue);
      Log.write(" -> ");
      Log.writeln(value);
    }
    int pages = Conversions.bytesToPagesUp(value);
    warnIf(value.NE(Conversions.pagesToBytes(pages)),
           "Value rounded up to a whole number of pages");
    this.value = Conversions.bytesToPagesUp(value);
    validate();
  }
}
