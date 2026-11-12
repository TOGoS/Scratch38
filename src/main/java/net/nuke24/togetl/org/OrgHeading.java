package net.nuke24.togetl.org;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One heading in an Org document.
 */
public final class OrgHeading {
	private final int level;
	private final String title;
	private final int line;
	private String customId;
	private final List<OrgLink> links = new ArrayList<OrgLink>();
	private final List<OrgTimestamp> timestamps = new ArrayList<OrgTimestamp>();
	private final List<OrgCheckbox> checkboxes = new ArrayList<OrgCheckbox>();
	
	public OrgHeading(int level, String title, int line) {
		this.level = level;
		this.title = title;
		this.line = line;
	}
	
	public int getLevel() {
		return level;
	}
	
	public String getTitle() {
		return title;
	}
	
	public int getLine() {
		return line;
	}
	
	public String getCustomId() {
		return customId;
	}
	
	public void setCustomId(String customId) {
		this.customId = customId;
	}
	
	public List<OrgLink> getLinks() {
		return Collections.unmodifiableList(links);
	}
	
	public List<OrgTimestamp> getTimestamps() {
		return Collections.unmodifiableList(timestamps);
	}
	
	public List<OrgCheckbox> getCheckboxes() {
		return Collections.unmodifiableList(checkboxes);
	}
	
	public void addLink(OrgLink link) {
		links.add(link);
	}
	
	public void addTimestamp(OrgTimestamp timestamp) {
		timestamps.add(timestamp);
	}
	
	public void addCheckbox(OrgCheckbox checkbox) {
		checkboxes.add(checkbox);
	}
}
