package org.opendatakit.camera;

public class TestDimensions {
	private int outerRecW;
	private int outerRecH;
	private int innerRecW;
	private int innerRecH;
	private int y;
	private int x;
	
	public TestDimensions(int outerRecW, int outerRecH, int innerRecW, int innerRecH, int y, int x) {
		this.outerRecW = outerRecW;
		this.outerRecH = outerRecH;
		this.innerRecW = innerRecW;
		this.innerRecH = innerRecH;
		this.y = y;
		this.x = x;
	}
	
	public int getOuterRecW() {
		return outerRecW;
	}
	
	public int getOuterRecH() {
		return outerRecH;
	}
	
	public int getInnerRecW() {
		return innerRecW;
	}
	
	public int getInnerRecH() {
		return innerRecH;
	}
	
	public int getXoffset() {
		return x;
	}
	
	public int getYoffset() {
		return y;
	}
}
