package com.example.openlayersexperiment.widgetset.client.ui;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.canvas.client.Canvas;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.dom.client.Style.Overflow;
import com.google.gwt.dom.client.Style.Position;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.LoadEvent;
import com.google.gwt.event.dom.client.LoadHandler;
import com.google.gwt.event.dom.client.ScrollEvent;
import com.google.gwt.event.dom.client.ScrollHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.vaadin.terminal.gwt.client.ApplicationConnection;
import com.vaadin.terminal.gwt.client.Paintable;
import com.vaadin.terminal.gwt.client.UIDL;

public class VSonarWidget extends ScrollPanel implements Paintable, ScrollHandler  {

	private HorizontalPanel vert;
	private ApplicationConnection client;
	private String uid;
	private List<Canvas> canvases;
	private List<String> drawn;
	private String[] depths;
	private String[] temps;
	private String[] lowlimits;
	private int tilewidth = 400;
	private int height = 400;
	private Label depthlabel;
	private Label templabel;
	private boolean overlay;
	private Canvas ruler;
	private VerticalPanel labels;

	public VSonarWidget() {
		super();
		this.canvases = new ArrayList<Canvas>();
		
		this.drawn = new ArrayList<String>();
		this.depthlabel = new Label();
		this.depthlabel.setText("Depth: ");
		
		this.templabel = new Label();
		this.templabel.setText("Temp: ");

		vert = new HorizontalPanel();
		vert.setHeight("100%");
		
		labels = new VerticalPanel();
		labels.getElement().getStyle().setPosition(Position.FIXED);
		labels.setStyleName("v-sonarwidget-labels");

		setWidget(vert);
		vert.add(labels);
		labels.add(depthlabel);
		labels.add(templabel);
		getElement().getStyle().setOverflowX(Overflow.AUTO);
		getElement().getStyle().setOverflowY(Overflow.HIDDEN);

		addScrollHandler(this);
		sinkEvents(Event.ONMOUSEMOVE);
	}
	
	private void initialize(int width) {		
		vert.clear();
		vert.setWidth(width+"px");
		vert.add(labels);
		
		depths = new String[width];
		temps = new String[width];
		lowlimits = new String[width];
		this.ruler = Canvas.createIfSupported();
		this.ruler.setCoordinateSpaceHeight(this.height);
		this.ruler.setCoordinateSpaceWidth(1);
		this.ruler.setWidth("1px");
		this.ruler.setHeight(this.height+"px");
		this.ruler.getElement().getStyle().setPosition(Position.FIXED);
		
		Context2d context2d = this.ruler.getContext2d();
		context2d.setFillStyle("blue");
		context2d.fillRect(0, 0, 1, this.height);

		vert.add(ruler);
		for(int loop=0; loop < width; loop+=this.tilewidth) {
			Canvas canvas = Canvas.createIfSupported();
			canvas.setCoordinateSpaceHeight(this.height);
			canvas.setCoordinateSpaceWidth(this.tilewidth);
			canvas.setWidth(this.tilewidth+"px");
			canvas.setHeight(this.height+"px");
			this.canvases.add(canvas);
			vert.add(canvas);
		}
	}
	
	private void getData(int offset) {
		int normalizedoffset = offset-offset%tilewidth;
		
		for(int loop = normalizedoffset; loop < normalizedoffset+getOffsetWidth()+this.tilewidth; loop+= this.tilewidth) {
			if(this.drawn.contains(new Integer(loop).toString())) {
				continue;
			} else {
				this.drawn.add(new Integer(loop).toString());
			}
			client.updateVariable(uid, "windowheight", height, false);
			client.updateVariable(uid, "windowwidth", tilewidth, false);
			client.updateVariable(uid, "currentwindow", loop, true);
		}
	}

	@Override
	public void updateFromUIDL(UIDL uidl, ApplicationConnection client) {
		if (client.updateComponent(this, uidl, true)) {
		    // If client.updateComponent returns true there has been no changes and we
		    // do not need to update anything.
			return;
		}
		this.client = client;
		this.uid = uidl.getId();
		
		this.height = getOffsetHeight();
		
		if(this.drawn.isEmpty()) {
			getData(0);
			return;
		}		

		if(this.canvases.isEmpty() && uidl.hasAttribute("pingcount")) {
			initialize(uidl.getIntAttribute("pingcount"));
		}
		
		if(uidl.hasAttribute("overlay")) {
			this.overlay = uidl.getBooleanAttribute("overlay");
		}

		if(uidl.hasAttribute("offset")) {
			int offset = uidl.getIntAttribute("offset");

			Canvas canvas = this.canvases.get((int)(offset/tilewidth));
			final Context2d context = canvas.getContext2d();
			context.clearRect(0, 0, tilewidth, height);
			
			if(uidl.hasAttribute("lowlimits")) {
				fillArray(uidl.getStringArrayAttribute("lowlimits"), this.lowlimits, offset);			
			}
			
			if(uidl.hasAttribute("depths")) {
				fillArray(uidl.getStringArrayAttribute("depths"), this.depths, offset);			
			}
			
			if(uidl.hasAttribute("temps")) {
				fillArray(uidl.getStringArrayAttribute("temps"), this.temps, offset);				
			}
			
			if(uidl.hasAttribute("pic")) {
				drawBitmap(offset, uidl.getStringAttribute("pic"), context);	
			}	
			
			drawOverlay(offset, context);
		}
	}
	
	private void fillArray(String[] slice, String[] array, int offset) {
		for(int loop=offset; loop < offset+slice.length; loop++) {
			array[loop] = slice[loop-offset];
		}	
	}

	private void drawBitmap(final int offset, final String name, final Context2d context) {
		final Image image = new Image(GWT.getHostPageBaseURL()+name.substring(5));
		RootPanel.get().add(image);
		image.setVisible(false);
		image.addLoadHandler(new LoadHandler() {
			
			@Override
			public void onLoad(LoadEvent event) {
				context.setGlobalAlpha(0);
				new Timer() {
					private int alpha = 0;
					@Override
					public void run() {
						
						if(alpha >= 10) {
							this.cancel();
						}
						
						context.setGlobalAlpha(alpha*0.1);
						context.drawImage(ImageElement.as(image.getElement()), 0, 0);
						drawOverlay(offset, context);
						alpha++;
					}
				}.scheduleRepeating(24);
			}
		});
	}

	private void drawOverlay(int offset, final Context2d context) {
		if(!this.overlay) {
			return;
		}
		
		context.setStrokeStyle("red");
		context.beginPath();
		for(int loop=offset; loop < offset+tilewidth; loop++) {
			float depth = new Float(depths[loop]).floatValue();
			float lowlimit = new Float(lowlimits[loop]).floatValue();
			if(loop-offset==0) {
				context.moveTo(loop-offset, this.height*depth/lowlimit);
			} else {
				context.lineTo(loop-offset, this.height*depth/lowlimit);
			}
		}
		context.stroke();
	}
	
	@Override
	public void onScroll(ScrollEvent event) {
		getData(getHorizontalScrollPosition());		
	}
	
	private void onMouseHover(int coordinate) {
				
		this.ruler.getElement().getStyle().setMarginLeft(coordinate-getHorizontalScrollPosition(), Unit.PX);
		this.labels.getElement().getStyle().setMarginLeft(coordinate-getHorizontalScrollPosition(), Unit.PX);
		
		if(this.depths != null && this.depths.length > coordinate) {
			this.depthlabel.setText("Depth: "+this.depths[coordinate]);
		}
		
		if(this.temps != null && this.temps.length > coordinate) {
			this.templabel.setText("Temp: "+this.temps[coordinate]);
		}
		
	}
	
	
	@Override
	public void onBrowserEvent(Event event) {
		super.onBrowserEvent(event);
		switch(DOM.eventGetType(event)) {
		case Event.ONMOUSEMOVE:
			onMouseHover(event.getClientX()-getAbsoluteLeft()+getHorizontalScrollPosition());
			break;
		}
	}
}
