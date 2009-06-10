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
public class EscapeTest {

    public EscapeTest(int i) { val = i; }

    public static void main(String[] argv) {
        EscapeTest et1 = new EscapeTest(10);
        EscapeTest list = et1.run(new EscapeTest(20), new EscapeTest(30));
    }

    EscapeTest  run(EscapeTest p1, EscapeTest p2) {
        EscapeTest head = null, tail = null;

        head = tail = new EscapeTest(100);
        tail.next = new EscapeTest(200);
        tail = tail.next;

        tail.next = new EscapeTest(300);
        tail = tail.next;

        tail.next = new EscapeTest(400);
        tail = tail.next;

        tail.next = new EscapeTest(500);
        tail = tail.next;

        tail.next = p2;
        p2.next = null;

        p1.next = head;

        return p1;
    }

    int val;
    EscapeTest next;
}

/*
class EscapeTest2 {
}
*/
