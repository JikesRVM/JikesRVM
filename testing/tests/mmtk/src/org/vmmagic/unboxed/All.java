package org.vmmagic.unboxed;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;


@RunWith(Suite.class)
@SuiteClasses({
    WordTest.class,
    AddressTestBasic.class,
    AddressTest.class,
    ObjectReferenceTest.class})
public class All {

}
