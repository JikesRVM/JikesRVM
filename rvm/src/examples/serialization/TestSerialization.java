/*
 * (C) Copyright IBM Corp. 2001
 */

import java.io.Serializable;

class TestSerialization implements Serializable
{
  private static final class Obj implements Serializable { public Obj() { } }
  private boolean z;    // Z
  private byte b;       // B
  private short h;      // S
  private int i;        // I
  private long j;       // J
  private float f;      // F
  private double d;     // D
  private String s;     // Ljava/lang/String;
  private Obj o;        // LTestSerialization$Obj;
  private Object n;     // null
  private boolean[] za; // [Z
  private byte[] ba;    // [B
  private short[] ha;   // [S
  private int[] ia;     // [I
  private long[] ja;    // [J
  private float[] fa;   // [F
  private double[] da;  // [D
  private String[] sa;  // [Ljava/lang/String;
  private Obj[] oa;     // [LTestSerialization$Obj;

  public TestSerialization() {
    z = true;         // Z
    b = (byte)1;      // B
    h = (short)2;     // S
    i = 3;            // I
    j = 4;            // J
    f = 5.0f;         // F
    d = 6.0;          // D
    s = "7";          // Ljava/lang/String;
    o = new Obj();    // LTestSerialization$Obj;
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
    StringBuffer res = new StringBuffer();
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


