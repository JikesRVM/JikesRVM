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
import org.vmmagic.unboxed.Extent;

/**
 * A memory option that stores values as a whole number of pages.
 */
public class PagesOption extends Option {
  // values
  protected int defaultValue;
  protected int value;

  /**
   * Create a new pages option.
   *
   * @param set The option set this option belongs to.
   * @param name The space separated name for the option.
   * @param desc The purpose of the option
   * @param defaultPages The default value of the option.
   */
  protected PagesOption(OptionSet set, String name, String desc, int defaultPages) {
    super(set, PAGES_OPTION, name, desc);
    this.value = this.defaultValue = defaultPages;
  }

  /**
   * Read the current value of the option in pages.
   *
   * @return The option value.
   */
  @Uninterruptible
  public int getPages() {
    return this.value;
  }

  /**
   * Read the current value of the option in bytes.
   *
   * @return The option value.
   */
  @Uninterruptible
  public Extent getBytes() {
    return set.pagesToBytes(this.value);
  }

  /**
   * Read the default value of the option in bytes.
   *
   * @return The default value.
   */
  @Uninterruptible
  public Extent getDefaultBytes() {
    return set.pagesToBytes(this.defaultValue);
  }

  /**
   * Read the default value of the option in pages.
   *
   * @return The default value.
   */
  @Uninterruptible
  public int getDefaultPages() {
    return this.defaultValue;
  }

  /**
   * Update the value of the option, echoing the change if logChanges is set.
   * A warning is raised if the value is not a whole multiple of pages, and
   * then the validate method is called to allow subclasses to perform any
   * additional validation.
   *
   * @param value The new value for the option.
   */
  public void setBytes(Extent value) {
    int pages = set.bytesToPages(value);
    warnIf(value.NE(set.pagesToBytes(pages)), "Value rounded up to a whole number of pages");
    setPages(pages);
  }

  /**
   * Update the value of the option, echoing the change if logChanges is set.
   * The validate method is called to allow subclasses to perform any additional
   * validation.
   *
   * @param pages The new value for the option.
   */
  public void setPages(int pages) {
    this.value = pages;
    validate();
    set.logChange(this);
  }

  /**
   * Modify the default value of the option.
   *
   * @param value The new default value for the option.
   */
  public void setDefaultPages(int value) {
    this.value = this.defaultValue = value;
  }
}
