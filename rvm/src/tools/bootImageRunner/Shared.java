package com.ibm.JikesRVM.GenerateInterfaceDeclarations;

import  java.io.PrintStream;

/** A class for shared code among GenerateInterfaceDeclarations et al. */

class Shared {
  static PrintStream out;
  static String outFileName;

  static void p(String s) {
    out.print(s);
  }
  static void pln(String s) {
    out.println(s);
  }
  static void pln() {
    out.println();
  }
  
  static void ep(String s) {
    System.err.print(s);
  }

  static void epln(String s) {
    System.err.println(s);
  }

  static void epln() {
    System.err.println();
  }

  static void reportTrouble(String msg) {
    epln("GenerateInterfaceDeclarations: While we were creating InterfaceDeclarations.h, there was a problem.");
    epln(msg);
    ep("The build system will delete the output file");
    if (outFileName != null) {
      ep(" ");
      ep(outFileName);
    }
    epln();
    
    System.exit(1);
  }


}
