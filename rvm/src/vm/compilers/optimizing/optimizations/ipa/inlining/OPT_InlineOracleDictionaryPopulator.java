/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

import  java.io.*;

/**
 * This interface identifies objects that know how to populate an
 * OPT_InlineOracleDictionary from an input stream.
 *
 * @author Stephen Fink
 */
interface OPT_InlineOracleDictionaryPopulator {

  /**
   * Populate the inline oracle dictionary from an input stream
   * @param in the stream
   */
  void populateInlineOracleDictionary (LineNumberReader in);
}



