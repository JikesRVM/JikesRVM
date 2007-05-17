/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
public class OPT_EscapeTest {
    
    public OPT_EscapeTest(int i) { val = i; }
    
    public static void main(String argv[])
    {
        OPT_EscapeTest et1 = new OPT_EscapeTest(10);
        OPT_EscapeTest list = et1.run(new OPT_EscapeTest(20), new OPT_EscapeTest(30));
    }
    
    OPT_EscapeTest  run(OPT_EscapeTest p1, OPT_EscapeTest p2)
    {
        OPT_EscapeTest head = null, tail = null;

        head = tail = new OPT_EscapeTest(100);
        tail.next = new OPT_EscapeTest(200);
        tail = tail.next;
        
        tail.next = new OPT_EscapeTest(300);
        tail = tail.next;
        
        tail.next = new OPT_EscapeTest(400);
        tail = tail.next;
        
        tail.next = new OPT_EscapeTest(500);
        tail = tail.next;
        
        tail.next = p2;
        p2.next = null;

        p1.next = head;

        return p1;
    }

    int val;
    OPT_EscapeTest next;
}

/*
class OPT_EscapeTest2 {
}
*/
