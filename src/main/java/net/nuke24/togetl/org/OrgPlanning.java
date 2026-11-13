package net.nuke24.togetl.org;

/**
 * A planning keyword and timestamp, such as {@code SCHEDULED: <...>}.
 */
public final class OrgPlanning {
	private final String keyword;
	private final OrgTimestamp timestamp;
	
	public OrgPlanning(String keyword, OrgTimestamp timestamp) {
		this.keyword = keyword;
		this.timestamp = timestamp;
	}
	
	public String getKeyword() {
		return keyword;
	}
	
	public OrgTimestamp getTimestamp() {
		return timestamp;
	}
}
