package net.nuke24.scratch38.s0025.c0100;

import junit.framework.TestCase;

public class HelloTest extends TestCase
{
	public void testHello() {
		assertEquals("hello", "hello");
	}
	
	public void testHiThere() {
		assertEquals("Hi there, World!", Hello.hiThere("World"));
	}
}
