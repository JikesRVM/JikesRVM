#[no_mangle]
pub extern "C" fn test_stack_alignment_0() {
    println!("Entering stack alignment test with no args passed");
    unsafe {
        llvm_asm!("movaps %xmm1, (%esp)" : : : "sp", "%xmm1", "memory");
    }
    println!("Exiting stack alignment test");
}

#[allow(clippy::many_single_char_names)]
#[no_mangle]
pub extern "C" fn test_stack_alignment_5(a: usize, b: usize, c: usize, d: usize, e: usize) -> usize {
    println!("Entering stack alignment test");
    println!("a:{}, b:{}, c:{}, d:{}, e:{}", a, b, c, d, e);
    unsafe {
        llvm_asm!("movaps %xmm1, (%esp)" : : : "sp", "%xmm1", "memory");
    }
    let result = a + b * 2 + c * 3 + d * 4 + e * 5;
    println!("Exiting stack alignment test");
    result
}
