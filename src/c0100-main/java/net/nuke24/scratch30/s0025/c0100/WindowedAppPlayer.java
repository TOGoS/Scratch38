package net.nuke24.scratch30.s0025.c0100;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

interface AABB2D<T> {
	T getX0(); T getY0();
	T getX1(); T getY1();
}

interface DestCanvas2D {
	
}

interface DrawableScene {
	public void drawTo(DestCanvas2D dest);
}


// Based on Apallit from NetworkRTS.
// Applet is deprecated for removal in Java 9, so switching to plain old Panel.
class Apallit extends Panel {
	
}

public class WindowedAppPlayer {
	boolean inited = false;
	boolean visible = false;
	Frame f;
	
	public void init() {
		if( this.inited ) return;
		
		Apallit panel = new Apallit();
		panel.setPreferredSize(new Dimension(512, 384));
		panel.setBackground(Color.CYAN);
		
		f = new Frame("asdasd");
		f.add(panel);
		f.pack();
		f.addWindowListener(new WindowAdapter() {
			public @Override void windowClosing(WindowEvent evt) {
				dispose();
			}
		});
		this.inited = true;
	}
	
	public void dispose() {
		if( !this.inited ) return;
		
		this.f.dispose();
		this.inited = false;
		this.f = null;
	}
	
	public void setVisible(boolean visible) {
		if( this.visible == visible ) return;
		
		if( visible ) {
			this.init();
			f.setVisible(true);
		} else {
			if( f != null ) f.setVisible(false);
		}
		this.visible = visible;
	}
	
	public static void main(String[] args) {
		EventQueue.invokeLater(() -> {
			WindowedAppPlayer player = new WindowedAppPlayer();
			player.setVisible(true);
		});
	}
}
