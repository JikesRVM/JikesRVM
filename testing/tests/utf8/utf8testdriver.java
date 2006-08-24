/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
/**
 * @author unascribed
 */
class utf8testdriver {

    public static void main(String[] args) {
        
        System.out.println("class utf8test {");
        System.out.println("  static String test[] = { ");
        System.out.print("  \"");
        for (int i = 0; i<='\uffff'; ++i) {
            if (i <= '\377') {
                String t = Integer.toOctalString(i);
                System.out.print("\\"+"000".substring(t.length())+t);
            } else {
                String t = Integer.toHexString(i);
                System.out.print("\\u"+"0000".substring(t.length())+t);
            }
            if (i % 11 == 10) {
                if (i % 121 == 120) {
                    System.out.println("\",");
                    System.out.print("  \"");
                } else {
                    System.out.println("\" +");
                    System.out.print("  \"");
                }
            }
        }
        System.out.println("\"};");

        System.out.println("  public static void main(String[] args) {");
        System.out.println("    for (int i=0; i<='\\uffff'; ++i) {");
        System.out.println("      int j = i / 121;");
        System.out.println("      int c = test[j].charAt(i-(j*121));");
        System.out.println("      if (c != i)");
        System.out.println("        System.out.println(\"fail: 0x\"+Integer.toHexString(c)+\" != 0x\"+Integer.toHexString(i));");
        System.out.println("    }");
        System.out.println("    System.out.println(\"utf8 test completed\");");
        System.out.println("  }");
        System.out.println("}");

    }

}
