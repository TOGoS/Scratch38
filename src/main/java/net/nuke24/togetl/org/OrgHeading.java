package net.nuke24.togetl.org;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One heading in an Org document.
 */
public final class OrgHeading {
	private final int level;
	private String title;
	private final int line;
	private String todoKeyword;
	private String priority;
	private String customId;
	private final List<String> tags = new ArrayList<String>();
	private final List<OrgProperty> properties = new ArrayList<OrgProperty>();
	private final List<OrgPlanning> planning = new ArrayList<OrgPlanning>();
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
	
	/** Title text after any TODO keyword, priority cookie, and tags. */
	public void setTitle(String title) {
		this.title = title;
	}
	
	public int getLine() {
		return line;
	}
	
	/** The leading TODO-style keyword, or null when the heading has none. */
	public String getTodoKeyword() {
		return todoKeyword;
	}
	
	public void setTodoKeyword(String todoKeyword) {
		this.todoKeyword = todoKeyword;
	}
	
	/** The {@code [#A]} priority cookie value, or null when absent. */
	public String getPriority() {
		return priority;
	}
	
	public void setPriority(String priority) {
		this.priority = priority;
	}
	
	public String getCustomId() {
		return customId;
	}
	
	public void setCustomId(String customId) {
		this.customId = customId;
	}
	
	public List<String> getTags() {
		return Collections.unmodifiableList(tags);
	}
	
	public void addTag(String tag) {
		tags.add(tag);
	}
	
	public List<OrgProperty> getProperties() {
		return Collections.unmodifiableList(properties);
	}
	
	public String getProperty(String key) {
		for (OrgProperty property : properties) {
			if (property.getKey().equals(key)) {
				return property.getValue();
			}
		}
		return null;
	}
	
	public void addProperty(OrgProperty property) {
		properties.add(property);
	}
	
	public List<OrgPlanning> getPlanning() {
		return Collections.unmodifiableList(planning);
	}
	
	public void addPlanning(OrgPlanning entry) {
		planning.add(entry);
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
