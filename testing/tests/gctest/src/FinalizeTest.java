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
import org.vmmagic.pragma.NonMoving;

/**
 * Test finalizers in various spaces.
 */
public class FinalizeTest {

  /** This class will be allocated into the non-moving space */
  @NonMoving
  static class FinalizeNM {
    @Override
    public void finalize() throws Throwable {
    }
  }

  /** This class will be allocated into the default space, eg nursery */
  static class Finalize {
    @Override
    public void finalize() throws Throwable {
    }
  }

  /** This class will be allocated into the large object space */
  static class FinalizeLarge {
    // 16 x 8 bytes = 128 bytes per row
    // 64 rows = 8192 bytes
    // + 8 byte header = 8200 bytes, which should occupy 3 pages in the LOS.
    long a01, b01, c01, d01, e01, f01, g01, h01, i01, j01, k01, l01, m01, n01, o01, p01;
    long a02, b02, c02, d02, e02, f02, g02, h02, i02, j02, k02, l02, m02, n02, o02, p02;
    long a03, b03, c03, d03, e03, f03, g03, h03, i03, j03, k03, l03, m03, n03, o03, p03;
    long a04, b04, c04, d04, e04, f04, g04, h04, i04, j04, k04, l04, m04, n04, o04, p04;
    long a05, b05, c05, d05, e05, f05, g05, h05, i05, j05, k05, l05, m05, n05, o05, p05;
    long a06, b06, c06, d06, e06, f06, g06, h06, i06, j06, k06, l06, m06, n06, o06, p06;
    long a07, b07, c07, d07, e07, f07, g07, h07, i07, j07, k07, l07, m07, n07, o07, p07;
    long a08, b08, c08, d08, e08, f08, g08, h08, i08, j08, k08, l08, m08, n08, o08, p08;
    long a09, b09, c09, d09, e09, f09, g09, h09, i09, j09, k09, l09, m09, n09, o09, p09;
    long a10, b10, c10, d10, e10, f10, g10, h10, i10, j10, k10, l10, m10, n10, o10, p10;
    long a11, b11, c11, d11, e11, f11, g11, h11, i11, j11, k11, l11, m11, n11, o11, p11;
    long a12, b12, c12, d12, e12, f12, g12, h12, i12, j12, k12, l12, m12, n12, o12, p12;
    long a13, b13, c13, d13, e13, f13, g13, h13, i13, j13, k13, l13, m13, n13, o13, p13;
    long a14, b14, c14, d14, e14, f14, g14, h14, i14, j14, k14, l14, m14, n14, o14, p14;
    long a15, b15, c15, d15, e15, f15, g15, h15, i15, j15, k15, l15, m15, n15, o15, p15;
    long a16, b16, c16, d16, e16, f16, g16, h16, i16, j16, k16, l16, m16, n16, o16, p16;
    long a17, b17, c17, d17, e17, f17, g17, h17, i17, j17, k17, l17, m17, n17, o17, p17;
    long a18, b18, c18, d18, e18, f18, g18, h18, i18, j18, k18, l18, m18, n18, o18, p18;
    long a19, b19, c19, d19, e19, f19, g19, h19, i19, j19, k19, l19, m19, n19, o19, p19;
    long a20, b20, c20, d20, e20, f20, g20, h20, i20, j20, k20, l20, m20, n20, o20, p20;
    long a21, b21, c21, d21, e21, f21, g21, h21, i21, j21, k21, l21, m21, n21, o21, p21;
    long a22, b22, c22, d22, e22, f22, g22, h22, i22, j22, k22, l22, m22, n22, o22, p22;
    long a23, b23, c23, d23, e23, f23, g23, h23, i23, j23, k23, l23, m23, n23, o23, p23;
    long a24, b24, c24, d24, e24, f24, g24, h24, i24, j24, k24, l24, m24, n24, o24, p24;
    long a25, b25, c25, d25, e25, f25, g25, h25, i25, j25, k25, l25, m25, n25, o25, p25;
    long a26, b26, c26, d26, e26, f26, g26, h26, i26, j26, k26, l26, m26, n26, o26, p26;
    long a27, b27, c27, d27, e27, f27, g27, h27, i27, j27, k27, l27, m27, n27, o27, p27;
    long a28, b28, c28, d28, e28, f28, g28, h28, i28, j28, k28, l28, m28, n28, o28, p28;
    long a29, b29, c29, d29, e29, f29, g29, h29, i29, j29, k29, l29, m29, n29, o29, p29;
    long a30, b30, c30, d30, e30, f30, g30, h30, i30, j30, k30, l30, m30, n30, o30, p30;
    long a31, b31, c31, d31, e31, f31, g31, h31, i31, j31, k31, l31, m31, n31, o31, p31;
    long a32, b32, c32, d32, e32, f32, g32, h32, i32, j32, k32, l32, m32, n32, o32, p32;
    long a33, b33, c33, d33, e33, f33, g33, h33, i33, j33, k33, l33, m33, n33, o33, p33;
    long a34, b34, c34, d34, e34, f34, g34, h34, i34, j34, k34, l34, m34, n34, o34, p34;
    long a35, b35, c35, d35, e35, f35, g35, h35, i35, j35, k35, l35, m35, n35, o35, p35;
    long a36, b36, c36, d36, e36, f36, g36, h36, i36, j36, k36, l36, m36, n36, o36, p36;
    long a37, b37, c37, d37, e37, f37, g37, h37, i37, j37, k37, l37, m37, n37, o37, p37;
    long a38, b38, c38, d38, e38, f38, g38, h38, i38, j38, k38, l38, m38, n38, o38, p38;
    long a39, b39, c39, d39, e39, f39, g39, h39, i39, j39, k39, l39, m39, n39, o39, p39;
    long a40, b40, c40, d40, e40, f40, g40, h40, i40, j40, k40, l40, m40, n40, o40, p40;
    long a41, b41, c41, d41, e41, f41, g41, h41, i41, j41, k41, l41, m41, n41, o41, p41;
    long a42, b42, c42, d42, e42, f42, g42, h42, i42, j42, k42, l42, m42, n42, o42, p42;
    long a43, b43, c43, d43, e43, f43, g43, h43, i43, j43, k43, l43, m43, n43, o43, p43;
    long a44, b44, c44, d44, e44, f44, g44, h44, i44, j44, k44, l44, m44, n44, o44, p44;
    long a45, b45, c45, d45, e45, f45, g45, h45, i45, j45, k45, l45, m45, n45, o45, p45;
    long a46, b46, c46, d46, e46, f46, g46, h46, i46, j46, k46, l46, m46, n46, o46, p46;
    long a47, b47, c47, d47, e47, f47, g47, h47, i47, j47, k47, l47, m47, n47, o47, p47;
    long a48, b48, c48, d48, e48, f48, g48, h48, i48, j48, k48, l48, m48, n48, o48, p48;
    long a49, b49, c49, d49, e49, f49, g49, h49, i49, j49, k49, l49, m49, n49, o49, p49;
    long a50, b50, c50, d50, e50, f50, g50, h50, i50, j50, k50, l50, m50, n50, o50, p50;
    long a51, b51, c51, d51, e51, f51, g51, h51, i51, j51, k51, l51, m51, n51, o51, p51;
    long a52, b52, c52, d52, e52, f52, g52, h52, i52, j52, k52, l52, m52, n52, o52, p52;
    long a53, b53, c53, d53, e53, f53, g53, h53, i53, j53, k53, l53, m53, n53, o53, p53;
    long a54, b54, c54, d54, e54, f54, g54, h54, i54, j54, k54, l54, m54, n54, o54, p54;
    long a55, b55, c55, d55, e55, f55, g55, h55, i55, j55, k55, l55, m55, n55, o55, p55;
    long a56, b56, c56, d56, e56, f56, g56, h56, i56, j56, k56, l56, m56, n56, o56, p56;
    long a57, b57, c57, d57, e57, f57, g57, h57, i57, j57, k57, l57, m57, n57, o57, p57;
    long a58, b58, c58, d58, e58, f58, g58, h58, i58, j58, k58, l58, m58, n58, o58, p58;
    long a59, b59, c59, d59, e59, f59, g59, h59, i59, j59, k59, l59, m59, n59, o59, p59;
    long a60, b60, c60, d60, e60, f60, g60, h60, i60, j60, k60, l60, m60, n60, o60, p60;
    long a61, b61, c61, d61, e61, f61, g61, h61, i61, j61, k61, l61, m61, n61, o61, p61;
    long a62, b62, c62, d62, e62, f62, g62, h62, i62, j62, k62, l62, m62, n62, o62, p62;
    long a63, b63, c63, d63, e63, f63, g63, h63, i63, j63, k63, l63, m63, n63, o63, p63;
    long a64, b64, c64, d64, e64, f64, g64, h64, i64, j64, k64, l64, m64, n64, o64, p64;

    @Override
    public void finalize() throws Throwable {
    }
  }

  public static void main(String[] args) {
    // These objects will live (in their various spaces) until the end of the test
    // and be processed as potentially finalizable at the end of each GC
    Object liveM = new Finalize();
    Object liveNM = new FinalizeNM();
    Object liveLarge = new FinalizeLarge();

    // These objects will be finalized in various spaces
    Object toDieMature = new Finalize();
    new FinalizeNM();
    new Finalize();
    new FinalizeLarge();
    int sum = toDieMature.hashCode();

    // This should be enough allocation to force toDieMatureMature into
    // the mature space, and to process all finalizers in a nursery collection
    for (int i=0; i < 1E5; i++) {
      @SuppressWarnings("unused")
      Object o = new byte[512];
    }
    System.gc();
    toDieMature = null;
    for (int i=0; i < 1E5; i++) {
      @SuppressWarnings("unused")
      Object o = new byte[512];
    }

    sum += liveNM.hashCode()+liveM.hashCode()+liveLarge.hashCode();
    System.out.println(sum);
    System.out.println("SUCCESS");
  }


}
