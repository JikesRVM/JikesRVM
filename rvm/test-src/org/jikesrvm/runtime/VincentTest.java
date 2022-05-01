package org.jikesrvm.runtime;

import static org.junit.Assert.*;
import static org.jikesrvm.junit.runners.VMRequirements.isRunningOnBuiltJikesRVM;

import org.jikesrvm.junit.runners.RequiresBootstrapVM;
import org.jikesrvm.junit.runners.RequiresBuiltJikesRVM;
import org.jikesrvm.junit.runners.VMRequirements;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import org.jikesrvm.runtime.SysCall;


@RunWith(VMRequirements.class)
@Category(RequiresBuiltJikesRVM.class)
public class VincentTest {
    @Test
    public void smokeTest() {
        SysCall.sysCall.ProfileInit();
        System.out.println(SysCall.sysCall.GetSocketNum());   
    }
}