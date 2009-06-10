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
package test.org.jikesrvm.basic.core.bytecode;

class TestConstants {
  public static void main(String[] args) {
    aconst();
    iconst();
    lconst();
    fconst();
    dconst();
    misc();
  }

  private static void aconst() {
    Object x = null;
    System.out.print("Expected: null Actual: ");
    System.out.println(x); // aconst_null
  }

  private static void iconst() {
    int x;
    x = -1;
    System.out.print("Expected: -1 Actual: ");
    System.out.println(x);  // iconst_m1
    x = 0;
    System.out.print("Expected: 0 Actual: ");
    System.out.println(x);  // iconst_0
    x = 1;
    System.out.print("Expected: 1 Actual: ");
    System.out.println(x);  // iconst_1
    x = 2;
    System.out.print("Expected: 2 Actual: ");
    System.out.println(x);  // iconst_2
    x = 3;
    System.out.print("Expected: 3 Actual: ");
    System.out.println(x);  // iconst_3
    x = 4;
    System.out.print("Expected: 4 Actual: ");
    System.out.println(x);  // iconst_4
    x = 5;
    System.out.print("Expected: 5 Actual: ");
    System.out.println(x);  // iconst_5
  }

  private static void lconst() {
    long x;
    x = 0;
    System.out.print("Expected: 0 Actual: ");
    System.out.println(x);  // lconst_0
    x = 1;
    System.out.print("Expected: 1 Actual: ");
    System.out.println(x);  // lconst_1
  }

  private static void fconst() {
    float x;
    x = 0;
    System.out.print("Expected: 0.0 Actual: ");
    System.out.println(x);  // fconst_0
    x = 1;
    System.out.print("Expected: 1.0 Actual: ");
    System.out.println(x);  // fconst_1
    x = 2;
    System.out.print("Expected: 2.0 Actual: ");
    System.out.println(x);  // fconst_2
  }

  static void dconst() {
    double x;
    x = 0;
    System.out.print("Expected: 0.0 Actual: ");
    System.out.println(x);  // dconst_0
    x = 1;
    System.out.print("Expected: 1.0 Actual: ");
    System.out.println(x);  // dconst_1
  }

  private static void misc() {
    byte x0 = 127;
    System.out.print("Expected: 127 Actual: ");
    System.out.println(x0);           // bipush
    x0 = -127;
    System.out.print("Expected: -127 Actual: ");
    System.out.println(x0);           // bipush

    short x1 = 32767;
    System.out.print("Expected: 32767 Actual: ");
    System.out.println(x1);           // sipush
    x1 = -32767;
    System.out.print("Expected: -32767 Actual: ");
    System.out.println(x1);           // sipush

    int x2 = 0x7fffffff;
    System.out.print("Expected: 2147483647 Actual: ");
    System.out.println(x2);           // ldc
    x2 = 0x80000001;
    System.out.print("Expected: -2147483647 Actual: ");
    System.out.println(x2);           // ldc

    long x4 = 0x7fffffffffffffffL;
    System.out.print("Expected: 9223372036854775807 Actual: ");
    System.out.println(x4);           // ldc2_w
    x4 = 0x8000000000000001L;
    System.out.print("Expected: -9223372036854775807 Actual: ");
    System.out.println(x4);           // ldc2_w

    System.out.print("Expected: X98 Actual: ");
    System.out.println(new TestConstants().s());  // ldc_w

    // Intel FPU constants
    System.out.print("Expected: 3.141592653589793 Actual: ");
    System.out.println(Math.PI);

    final double LG2 = 0.3010299956639811952256464283594894482;
    System.out.print("Expected: 0.3010299956639812 Actual: ");
    System.out.println(LG2);

    final double LN2 = 0.6931471805599453094286904741849753009;
    System.out.print("Expected: 0.6931471805599453 Actual: ");
    System.out.println(LN2);

    final double L2E = 1.4426950408889634073876517827983434472;
    System.out.print("Expected: 1.4426950408889634 Actual: ");
    System.out.println(L2E);

    final double L2T = 3.3219280948873623478083405569094566090;
    System.out.print("Expected: 3.321928094887362 Actual: ");
    System.out.println(L2T);
  }

  public final String x0 = "X0";
  public final String x1 = "X1";
  public final String x2 = "X2";
  public final String x3 = "X3";
  public final String x4 = "X4";
  public final String x5 = "X5";
  public final String x6 = "X6";
  public final String x7 = "X7";
  public final String x8 = "X8";
  public final String x9 = "X9";
  public final String x10 = "X10";
  public final String x11 = "X11";
  public final String x12 = "X12";
  public final String x13 = "X13";
  public final String x14 = "X14";
  public final String x15 = "X15";
  public final String x16 = "X16";
  public final String x17 = "X17";
  public final String x18 = "X18";
  public final String x19 = "X19";
  public final String x20 = "X20";
  public final String x21 = "X21";
  public final String x22 = "X22";
  public final String x23 = "X23";
  public final String x24 = "X24";
  public final String x25 = "X25";
  public final String x26 = "X26";
  public final String x27 = "X27";
  public final String x28 = "X28";
  public final String x29 = "X29";
  public final String x30 = "X30";
  public final String x31 = "X31";
  public final String x32 = "X32";
  public final String x33 = "X33";
  public final String x34 = "X34";
  public final String x35 = "X35";
  public final String x36 = "X36";
  public final String x37 = "X37";
  public final String x38 = "X38";
  public final String x39 = "X39";
  public final String x40 = "X40";
  public final String x41 = "X41";
  public final String x42 = "X42";
  public final String x43 = "X43";
  public final String x44 = "X44";
  public final String x45 = "X45";
  public final String x46 = "X46";
  public final String x47 = "X47";
  public final String x48 = "X48";
  public final String x49 = "X49";
  public final String x50 = "X50";
  public final String x51 = "X51";
  public final String x52 = "X52";
  public final String x53 = "X53";
  public final String x54 = "X54";
  public final String x55 = "X55";
  public final String x56 = "X56";
  public final String x57 = "X57";
  public final String x58 = "X58";
  public final String x59 = "X59";
  public final String x60 = "X60";
  public final String x61 = "X61";
  public final String x62 = "X62";
  public final String x63 = "X63";
  public final String x64 = "X64";
  public final String x65 = "X65";
  public final String x66 = "X66";
  public final String x67 = "X67";
  public final String x68 = "X68";
  public final String x69 = "X69";
  public final String x70 = "X70";
  public final String x71 = "X71";
  public final String x72 = "X72";
  public final String x73 = "X73";
  public final String x74 = "X74";
  public final String x75 = "X75";
  public final String x76 = "X76";
  public final String x77 = "X77";
  public final String x78 = "X78";
  public final String x79 = "X79";
  public final String x80 = "X80";
  public final String x81 = "X81";
  public final String x82 = "X82";
  public final String x83 = "X83";
  public final String x84 = "X84";
  public final String x85 = "X85";
  public final String x86 = "X86";
  public final String x87 = "X87";
  public final String x88 = "X88";
  public final String x89 = "X89";
  public final String x90 = "X90";
  public final String x91 = "X91";
  public final String x92 = "X92";
  public final String x93 = "X93";
  public final String x94 = "X94";
  public final String x95 = "X95";
  public final String x96 = "X96";
  public final String x97 = "X97";
  public final String x98 = "X98";
  public final String x99 = "X99";
  public final String x100 = "X100";
  public final String x101 = "X101";
  public final String x102 = "X102";
  public final String x103 = "X103";
  public final String x104 = "X104";
  public final String x105 = "X105";
  public final String x106 = "X106";
  public final String x107 = "X107";
  public final String x108 = "X108";
  public final String x109 = "X109";
  public final String x110 = "X110";
  public final String x111 = "X111";
  public final String x112 = "X112";
  public final String x113 = "X113";
  public final String x114 = "X114";
  public final String x115 = "X115";
  public final String x116 = "X116";
  public final String x117 = "X117";
  public final String x118 = "X118";
  public final String x119 = "X119";
  public final String x120 = "X120";
  public final String x121 = "X121";
  public final String x122 = "X122";
  public final String x123 = "X123";
  public final String x124 = "X124";
  public final String x125 = "X125";
  public final String x126 = "X126";
  public final String x127 = "X127";
  public final String x128 = "X128";
  public final String x129 = "X129";
  public final String x130 = "X130";
  public final String x131 = "X131";
  public final String x132 = "X132";
  public final String x133 = "X133";
  public final String x134 = "X134";
  public final String x135 = "X135";
  public final String x136 = "X136";
  public final String x137 = "X137";
  public final String x138 = "X138";
  public final String x139 = "X139";
  public final String x140 = "X140";
  public final String x141 = "X141";
  public final String x142 = "X142";
  public final String x143 = "X143";
  public final String x144 = "X144";
  public final String x145 = "X145";
  public final String x146 = "X146";
  public final String x147 = "X147";
  public final String x148 = "X148";
  public final String x149 = "X149";
  public final String x150 = "X150";
  public final String x151 = "X151";
  public final String x152 = "X152";
  public final String x153 = "X153";
  public final String x154 = "X154";
  public final String x155 = "X155";
  public final String x156 = "X156";
  public final String x157 = "X157";
  public final String x158 = "X158";
  public final String x159 = "X159";
  public final String x160 = "X160";
  public final String x161 = "X161";
  public final String x162 = "X162";
  public final String x163 = "X163";
  public final String x164 = "X164";
  public final String x165 = "X165";
  public final String x166 = "X166";
  public final String x167 = "X167";
  public final String x168 = "X168";
  public final String x169 = "X169";
  public final String x170 = "X170";
  public final String x171 = "X171";
  public final String x172 = "X172";
  public final String x173 = "X173";
  public final String x174 = "X174";
  public final String x175 = "X175";
  public final String x176 = "X176";
  public final String x177 = "X177";
  public final String x178 = "X178";
  public final String x179 = "X179";
  public final String x180 = "X180";
  public final String x181 = "X181";
  public final String x182 = "X182";
  public final String x183 = "X183";
  public final String x184 = "X184";
  public final String x185 = "X185";
  public final String x186 = "X186";
  public final String x187 = "X187";
  public final String x188 = "X188";
  public final String x189 = "X189";
  public final String x190 = "X190";
  public final String x191 = "X191";
  public final String x192 = "X192";
  public final String x193 = "X193";
  public final String x194 = "X194";
  public final String x195 = "X195";
  public final String x196 = "X196";
  public final String x197 = "X197";
  public final String x198 = "X198";
  public final String x199 = "X199";
  public final String x200 = "X200";
  public final String x201 = "X201";
  public final String x202 = "X202";
  public final String x203 = "X203";
  public final String x204 = "X204";
  public final String x205 = "X205";
  public final String x206 = "X206";
  public final String x207 = "X207";
  public final String x208 = "X208";
  public final String x209 = "X209";
  public final String x210 = "X210";
  public final String x211 = "X211";
  public final String x212 = "X212";
  public final String x213 = "X213";
  public final String x214 = "X214";
  public final String x215 = "X215";
  public final String x216 = "X216";
  public final String x217 = "X217";
  public final String x218 = "X218";
  public final String x219 = "X219";
  public final String x220 = "X220";
  public final String x221 = "X221";
  public final String x222 = "X222";
  public final String x223 = "X223";
  public final String x224 = "X224";
  public final String x225 = "X225";
  public final String x226 = "X226";
  public final String x227 = "X227";
  public final String x228 = "X228";
  public final String x229 = "X229";
  public final String x230 = "X230";
  public final String x231 = "X231";
  public final String x232 = "X232";
  public final String x233 = "X233";
  public final String x234 = "X234";
  public final String x235 = "X235";
  public final String x236 = "X236";
  public final String x237 = "X237";
  public final String x238 = "X238";
  public final String x239 = "X239";
  public final String x240 = "X240";
  public final String x241 = "X241";
  public final String x242 = "X242";
  public final String x243 = "X243";
  public final String x244 = "X244";
  public final String x245 = "X245";
  public final String x246 = "X246";
  public final String x247 = "X247";
  public final String x248 = "X248";
  public final String x249 = "X249";
  public final String x250 = "X250";
  public final String x251 = "X251";
  public final String x252 = "X252";
  public final String x253 = "X253";
  public final String x254 = "X254";
  public final String x255 = "X255";
  public final String x256 = "X256";

  String s() { return x98; } // ldc_w
}
