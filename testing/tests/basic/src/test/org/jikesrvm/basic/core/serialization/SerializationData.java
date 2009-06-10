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
package test.org.jikesrvm.basic.core.serialization;

import java.io.Serializable;

class SerializationData implements Serializable {
  private static final class Obj implements Serializable {
    private static final long serialVersionUID = 42L;

    public Obj() {}

    public String toString() {
      return "SerializationData$Obj(42L)";
    }
  }

  private boolean z;    // Z
  private byte b;       // B
  private short h;      // S
  private int i;        // I
  private long j;       // J
  private float f;      // F
  private double d;     // D
  private String s;     // Ljava/lang/String;
  private Obj o;        // LSerializationData$Obj;
  private Object n;     // null
  private boolean[] za; // [Z
  private byte[] ba;    // [B
  private short[] ha;   // [S
  private int[] ia;     // [I
  private long[] ja;    // [J
  private float[] fa;   // [F
  private double[] da;  // [D
  private String[] sa;  // [Ljava/lang/String;
  private Obj[] oa;     // [LSerializationData$Obj;

  public SerializationData() {
    z = true;         // Z
    b = (byte) 1;      // B
    h = (short) 2;     // S
    i = 3;            // I
    j = 4;            // J
    f = 5.0f;         // F
    d = 6.0;          // D
    s = "7";          // Ljava/lang/String;
    o = new Obj();    // LSerializationData$Obj;
    n = null;         // null
    za = new boolean[1]; // [Z
    za[0] = z;
    ba = new byte[1];    // [B
    ba[0] = b;
    ha = new short[1];   // [S
    ha[0] = h;
    ia = new int[1];     // [I
    ia[0] = i;
    ja = new long[1];    // [J
    ja[0] = j;
    fa = new float[1];   // [F
    fa[0] = f;
    da = new double[1];  // [D
    da[0] = d;
    sa = new String[1];  // [Ljava/lang/String;
    sa[0] = s;
    oa = new Obj[1];     // [LTestSerialization$Obj;
    oa[0] = o;
  }

  void jitter() {
    z = false;         // Z
    b = (byte) 16;      // B
    h = (short) -3;     // S
    i = 43;            // I
    j = 42;            // J
    f = 52.3f;         // F
    d = 16.2222;       // D
    s = "88";         // Ljava/lang/String;
    o = new Obj();    // LSerializationData$Obj;
    n = null;         // null
    za = new boolean[1]; // [Z
    za[0] = z;
    ba = new byte[1];    // [B
    ba[0] = b;
    ha = new short[1];   // [S
    ha[0] = h;
    ia = new int[1];     // [I
    ia[0] = i;
    ja = new long[1];    // [J
    ja[0] = j;
    fa = new float[1];   // [F
    fa[0] = f;
    da = new double[1];  // [D
    da[0] = d;
    sa = new String[1];  // [Ljava/lang/String;
    sa[0] = s;
    oa = new Obj[1];     // [LTestSerialization$Obj;
    oa[0] = o;
  }

  public String toString() {
    StringBuilder res = new StringBuilder();
    res.append("Z:").append(z).append("\n");
    res.append("B:").append(b).append("\n");
    res.append("S:").append(h).append("\n");
    res.append("I:").append(i).append("\n");
    res.append("J:").append(j).append("\n");
    res.append("F:").append(f).append("\n");
    res.append("D:").append(d).append("\n");
    res.append("Ljava/lang/String;:").append(s).append("\n");
    res.append("Ljava/lang/Object;:").append(o).append("\n");
    res.append("null:").append(n).append("\n");
    res.append("[Z:").append(za[0]).append("\n");
    res.append("[B:").append(ba[0]).append("\n");
    res.append("[S:").append(ha[0]).append("\n");
    res.append("[I:").append(ia[0]).append("\n");
    res.append("[J:").append(ja[0]).append("\n");
    res.append("[F:").append(fa[0]).append("\n");
    res.append("[D:").append(da[0]).append("\n");
    res.append("[Ljava/lang/String;:").append(sa[0]).append("\n");
    res.append("[Ljava/lang/Object;:").append(oa[0]).append("\n");
    return res.toString();
  }
}


