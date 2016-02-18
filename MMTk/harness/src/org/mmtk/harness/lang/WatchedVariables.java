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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.mmtk.harness.Harness;

public class WatchedVariables {

  private final Map<String,Set<String>> watchedVars = new HashMap<String,Set<String>>();

  public WatchedVariables() {
    String var = Harness.watchVar.getValue();
    if (var != null) {
      String[] parts = var.split("\\.");
      if (parts.length != 2) {
        throw new Error("option watchVar requires a \"method.var\" format");
      }
      addWatch(parts[0], parts[1]);
    }
  }

  /**
   * Add a watch point on variable {@code var} in method {@code method}
   * @param method
   * @param var
   */
  public void addWatch(String method, String var) {
    Set<String> watched = watchedVars.get(method);
    if (watched == null) {
      watched = new HashSet<String>();
      watchedVars.put(method,watched);
    }
    watched.add(var);
  }

  public boolean isWatched(String method, String var) {
    Set<String> watched = watchedVars.get(method);
    if (watched == null) {
      return false;
    }
    return watched.contains(var);
  }
}
