package net.nuke24.togetl.dialect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.nuke24.togetl.etl.ImportReport;
import net.nuke24.togetl.etl.SourceDocument;
import net.nuke24.togetl.ident.Identifiers;
import net.nuke24.togetl.org.OrgCheckbox;
import net.nuke24.togetl.org.OrgDirective;
import net.nuke24.togetl.org.OrgDocument;
import net.nuke24.togetl.org.OrgHeading;
import net.nuke24.togetl.org.OrgLink;
import net.nuke24.togetl.org.OrgPlanning;
import net.nuke24.togetl.org.OrgProperty;
import net.nuke24.togetl.org.OrgTable;
import net.nuke24.togetl.org.OrgTableRow;
import net.nuke24.togetl.org.OrgTimestamp;
import net.nuke24.togetl.rdf.BlankNode;
import net.nuke24.togetl.rdf.IRI;
import net.nuke24.togetl.rdf.Literal;
import net.nuke24.togetl.rdf.Location;
import net.nuke24.togetl.rdf.StatementConsumer;
import net.nuke24.togetl.vocab.Vocab;

/**
 * Maps Org headings and heading-local markers into statements.
 */
public final class OrgStructureDialect {
	public static final String NAME = "org-structure";
	
	private static final Pattern ABSOLUTE_URI =
			Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:[^\\s]+$");
			
	public void map(
			OrgDocument document,
			SourceDocument source,
			StatementConsumer out,
			ImportReport report
	) throws IOException {
		emitDocumentMetadata(document, source, out);
		
		String scope = Identifiers.documentHash(source.getDocumentIri());
		List<IRI> headingIris = new ArrayList<IRI>();
		Map<String, IRI> headingIrisByCustomId = new HashMap<String, IRI>();
		for (int i = 0; i < document.getHeadings().size(); i++) {
			OrgHeading heading = document.getHeadings().get(i);
			IRI headingSubject = headingIri(source, i, heading.getCustomId());
			headingIris.add(headingSubject);
			String customId = heading.getCustomId();
			if (customId != null && !customId.isEmpty()) {
				if (headingIrisByCustomId.put(customId, headingSubject) != null) {
					report.conflict("org-heading", "CUSTOM_ID", Location.at(heading.getLine(), 1));
				}
			}
		}
		
		List<IRI> levelStack = new ArrayList<IRI>();
		List<String> titleStack = new ArrayList<String>();
		for (int i = 0; i < document.getHeadings().size(); i++) {
			OrgHeading heading = document.getHeadings().get(i);
			while (levelStack.size() >= heading.getLevel()) {
				levelStack.remove(levelStack.size() - 1);
				titleStack.remove(titleStack.size() - 1);
			}
			
			IRI subject = headingIris.get(i);
			Location headingLocation = Location.at(heading.getLine(), 1);
			DialectSupport.emit(source, out, headingLocation, IRI.of(source.getDocumentIri()),
					Vocab.TOGETL_ORG_HEADING_LINK, subject);
			DialectSupport.emit(source, out, headingLocation, subject, Vocab.RDF_TYPE, Vocab.TOGETL_ORG_HEADING);
			DialectSupport.emit(source, out, headingLocation, subject,
					Vocab.TOGETL_DOCUMENT_LINK, IRI.of(source.getDocumentIri()));
			DialectSupport.emit(source, out, headingLocation, subject,
					Vocab.TOGETL_TITLE, Literal.of(heading.getTitle()));
			DialectSupport.emit(source, out, headingLocation, subject,
					Vocab.TOGETL_OUTLINE_LEVEL,
					Literal.of(Integer.toString(heading.getLevel()), Vocab.XSD_INTEGER));
			DialectSupport.emit(source, out, headingLocation, subject,
					Vocab.TOGETL_OUTLINE_INDEX,
					Literal.of(Integer.toString(i), Vocab.XSD_INTEGER));
					
			if (heading.getCustomId() != null) {
				DialectSupport.emit(source, out, headingLocation, subject,
						Vocab.TOGETL_CUSTOM_ID, Literal.of(heading.getCustomId()));
			}
			if (heading.getTodoKeyword() != null) {
				DialectSupport.emit(source, out, headingLocation, subject,
						Vocab.TOGETL_ORG_TODO_KEYWORD, Literal.of(heading.getTodoKeyword()));
			}
			if (heading.getPriority() != null) {
				DialectSupport.emit(source, out, headingLocation, subject,
						Vocab.TOGETL_ORG_PRIORITY, Literal.of(heading.getPriority()));
			}
			for (String tag : heading.getTags()) {
				DialectSupport.emit(source, out, headingLocation, subject,
						Vocab.TOGETL_TAG, Literal.of(tag));
			}
			emitHeadingDetails(source, out, scope, subject, i, heading);
			
			if (!levelStack.isEmpty()) {
				DialectSupport.emit(source, out, headingLocation, subject,
						Vocab.TOGETL_OUTLINE_PARENT, levelStack.get(levelStack.size() - 1));
			}
			
			List<String> pathTitles = new ArrayList<String>(titleStack);
			pathTitles.add(heading.getTitle());
			DialectSupport.emit(source, out, headingLocation, subject,
					Vocab.TOGETL_OUTLINE_PATH, Literal.of(joinPath(pathTitles)));
					
			emitHeadingMarkers(source, out, scope, subject, i, heading, headingIrisByCustomId);
			
			levelStack.add(subject);
			titleStack.add(heading.getTitle());
		}
		
		emitDirectives(document, source, out, scope, headingIris);
		emitTables(document, source, out, scope, headingIris);
	}
	
	private static void emitDirectives(
			OrgDocument document,
			SourceDocument source,
			StatementConsumer out,
			String scope,
			List<IRI> headingIris
	) throws IOException {
		for (int i = 0; i < document.getDirectives().size(); i++) {
			OrgDirective directive = document.getDirectives().get(i);
			Location location = Location.at(directive.getLine(), directive.getColumn());
			IRI owner = ownerIri(source, headingIris, directive.getHeadingIndex());
			BlankNode node = BlankNode.of("org-directive-" + scope + "-" + i);
			DialectSupport.emit(source, out, location, owner,
					Vocab.TOGETL_ORG_DIRECTIVE_LINK, node);
			DialectSupport.emit(source, out, location, node,
					Vocab.RDF_TYPE, Vocab.TOGETL_ORG_DIRECTIVE);
			DialectSupport.emit(source, out, location, node,
					Vocab.TOGETL_ORG_DIRECTIVE_NAME, Literal.of(directive.getName()));
			DialectSupport.emit(source, out, location, node,
					Vocab.TOGETL_ORG_DIRECTIVE_VALUE, Literal.of(directive.getValue()));
		}
	}
	
	private static void emitTables(
			OrgDocument document,
			SourceDocument source,
			StatementConsumer out,
			String scope,
			List<IRI> headingIris
	) throws IOException {
		for (int i = 0; i < document.getTables().size(); i++) {
			OrgTable table = document.getTables().get(i);
			Location tableLocation = Location.at(table.getLine(), 1);
			IRI owner = ownerIri(source, headingIris, table.getHeadingIndex());
			BlankNode tableNode = BlankNode.of("org-table-" + scope + "-" + i);
			DialectSupport.emit(source, out, tableLocation, owner,
					Vocab.TOGETL_ORG_TABLE_LINK, tableNode);
			DialectSupport.emit(source, out, tableLocation, tableNode,
					Vocab.RDF_TYPE, Vocab.TOGETL_ORG_TABLE);
			if (table.getName() != null && !table.getName().isEmpty()) {
				DialectSupport.emit(source, out, tableLocation, tableNode,
						Vocab.TOGETL_ORG_TABLE_NAME, Literal.of(table.getName()));
			}
			if (table.getCaption() != null && !table.getCaption().isEmpty()) {
				DialectSupport.emit(source, out, tableLocation, tableNode,
						Vocab.TOGETL_ORG_TABLE_CAPTION, Literal.of(table.getCaption()));
			}
			
			for (int j = 0; j < table.getRows().size(); j++) {
				OrgTableRow row = table.getRows().get(j);
				Location rowLocation = Location.at(row.getLine(), 1);
				BlankNode rowNode = BlankNode.of(
						"org-table-row-" + scope + "-" + i + "-" + j);
				DialectSupport.emit(source, out, rowLocation, tableNode,
						Vocab.TOGETL_ORG_TABLE_ROW_LINK, rowNode);
				DialectSupport.emit(source, out, rowLocation, rowNode,
						Vocab.RDF_TYPE, Vocab.TOGETL_ORG_TABLE_ROW);
				DialectSupport.emit(source, out, rowLocation, rowNode,
						Vocab.TOGETL_ORG_TABLE_ROW_INDEX,
						Literal.of(Integer.toString(j), Vocab.XSD_INTEGER));
				if (row.isSeparator()) {
					DialectSupport.emit(source, out, rowLocation, rowNode,
							Vocab.TOGETL_ORG_TABLE_ROW_SEPARATOR,
							Literal.of("true", Vocab.XSD_BOOLEAN));
					continue;
				}
				for (int k = 0; k < row.getCellCount(); k++) {
					BlankNode cellNode = BlankNode.of(
							"org-table-cell-" + scope + "-" + i + "-" + j + "-" + k);
					DialectSupport.emit(source, out, rowLocation, rowNode,
							Vocab.TOGETL_ORG_TABLE_CELL_LINK, cellNode);
					DialectSupport.emit(source, out, rowLocation, cellNode,
							Vocab.RDF_TYPE, Vocab.TOGETL_ORG_TABLE_CELL);
					DialectSupport.emit(source, out, rowLocation, cellNode,
							Vocab.TOGETL_ORG_TABLE_CELL_INDEX,
							Literal.of(Integer.toString(k), Vocab.XSD_INTEGER));
					DialectSupport.emit(source, out, rowLocation, cellNode,
							Vocab.TOGETL_ORG_TABLE_CELL_VALUE,
							Literal.of(row.getCell(k)));
				}
			}
		}
	}
	
	private static IRI ownerIri(
			SourceDocument source,
			List<IRI> headingIris,
			int headingIndex
	) {
		if (headingIndex >= 0 && headingIndex < headingIris.size()) {
			return headingIris.get(headingIndex);
		}
		return IRI.of(source.getDocumentIri());
	}
	
	private static void emitDocumentMetadata(
			OrgDocument document,
			SourceDocument source,
			StatementConsumer out
	) throws IOException {
		IRI doc = IRI.of(source.getDocumentIri());
		IRI documentImport = IRI.of(source.getDocumentImportIri());
		Location none = Location.none();
		DialectSupport.emit(source, out, none, doc, Vocab.RDF_TYPE, Vocab.TOGETL_DOCUMENT);
		DialectSupport.emit(source, out, none, doc, Vocab.TOGETL_SOURCE_URL, Literal.of(source.getSourceIri()));
		DialectSupport.emit(source, out, none, doc, Vocab.TOGETL_SHA256, Literal.of(source.getSha256()));
		DialectSupport.emit(source, out, none, doc, Vocab.TOGETL_BYTE_LENGTH,
				Literal.of(Long.toString(source.getByteLength()), Vocab.XSD_INTEGER));
		DialectSupport.emit(source, out, none, documentImport, Vocab.RDF_TYPE, Vocab.TOGETL_DOCUMENT_IMPORT);
		DialectSupport.emit(source, out, none, documentImport, Vocab.TOGETL_SOURCE_DOCUMENT, doc);
		DialectSupport.emit(source, out, none, documentImport, Vocab.TOGETL_FORMAT, Literal.of(source.getFormat()));
		DialectSupport.emit(source, out, none, documentImport, Vocab.TOGETL_PARSER_VERSION,
				Literal.of(source.getParserVersion()));
		DialectSupport.emit(source, out, none, documentImport, Vocab.TOGETL_HAS_BOM,
				Literal.of(document.hasBom() ? "true" : "false", Vocab.XSD_BOOLEAN));
	}
	
	private static IRI headingIri(SourceDocument source, int headingIndex, String customId) {
		if (customId != null && !customId.isEmpty()) {
			// CUSTOM_ID is the stable, document-independent identity of a heading,
			// so that links such as [[#custom-id]] can resolve to it from anywhere.
			return IRI.of(Identifiers.mint("org-heading", customId));
		}
		return IRI.of(Identifiers.mint("org-heading",
				Identifiers.documentKey(source.getDocumentIri()) + ":" + headingIndex));
	}
	
	private static String joinPath(List<String> titlePath) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < titlePath.size(); i++) {
			if (i > 0) {
				out.append(" / ");
			}
			out.append(titlePath.get(i));
		}
		return out.toString();
	}
	
	private static void emitHeadingDetails(
			SourceDocument source,
			StatementConsumer out,
			String scope,
			IRI headingSubject,
			int headingIndex,
			OrgHeading heading
	) throws IOException {
		for (int i = 0; i < heading.getProperties().size(); i++) {
			OrgProperty property = heading.getProperties().get(i);
			Location location = Location.at(property.getLine(), property.getColumn());
			BlankNode node = BlankNode.of(
					"org-property-" + scope + "-" + headingIndex + "-" + i);
			DialectSupport.emit(source, out, location, headingSubject,
					Vocab.TOGETL_ORG_PROPERTY_LINK, node);
			DialectSupport.emit(source, out, location, node,
					Vocab.RDF_TYPE, Vocab.TOGETL_ORG_PROPERTY);
			DialectSupport.emit(source, out, location, node,
					Vocab.TOGETL_ORG_PROPERTY_NAME, Literal.of(property.getKey()));
			DialectSupport.emit(source, out, location, node,
					Vocab.TOGETL_ORG_PROPERTY_VALUE, Literal.of(property.getValue()));
		}
		
		for (int i = 0; i < heading.getPlanning().size(); i++) {
			OrgPlanning planning = heading.getPlanning().get(i);
			OrgTimestamp timestamp = planning.getTimestamp();
			Location location = Location.at(timestamp.getLine(), timestamp.getColumn());
			BlankNode node = BlankNode.of(
					"org-planning-" + scope + "-" + headingIndex + "-" + i);
			DialectSupport.emit(source, out, location, headingSubject,
					Vocab.TOGETL_ORG_PLANNING_LINK, node);
			DialectSupport.emit(source, out, location, node,
					Vocab.RDF_TYPE, Vocab.TOGETL_ORG_PLANNING);
			DialectSupport.emit(source, out, location, node,
					Vocab.TOGETL_ORG_PLANNING_KEYWORD, Literal.of(planning.getKeyword()));
			DialectSupport.emit(source, out, location, node,
					Vocab.TOGETL_ORG_TIMESTAMP, Literal.of(timestamp.getText()));
			DialectSupport.emit(source, out, location, node,
					Vocab.TOGETL_ORG_TIMESTAMP_ACTIVE,
					Literal.of(timestamp.isActive() ? "true" : "false", Vocab.XSD_BOOLEAN));
		}
	}
	
	private static void emitHeadingMarkers(
			SourceDocument source,
			StatementConsumer out,
			String scope,
			IRI headingSubject,
			int headingIndex,
			OrgHeading heading,
			Map<String, IRI> headingIrisByCustomId
	) throws IOException {
		for (int i = 0; i < heading.getLinks().size(); i++) {
			OrgLink link = heading.getLinks().get(i);
			BlankNode node = BlankNode.of("org-link-" + scope + "-" + headingIndex + "-" + i);
			Location location = Location.at(link.getLine(), link.getColumn());
			DialectSupport.emit(source, out, location, headingSubject, Vocab.TOGETL_ORG_LINK, node);
			DialectSupport.emit(source, out, location, node, Vocab.RDF_TYPE, Vocab.TOGETL_ORG_LINK_NODE);
			emitLinkTarget(source, out, location, node, link.getTarget(), headingIrisByCustomId);
			if (link.getLabel() != null && !link.getLabel().isEmpty()) {
				DialectSupport.emit(source, out, location, node, Vocab.TOGETL_ORG_LINK_LABEL,
						Literal.of(link.getLabel()));
			}
		}
		
		for (OrgTimestamp timestamp : heading.getTimestamps()) {
			DialectSupport.emit(source, out,
					Location.at(timestamp.getLine(), timestamp.getColumn()),
					headingSubject,
					Vocab.TOGETL_ORG_TIMESTAMP,
					Literal.of(timestamp.getText()));
		}
		
		for (OrgCheckbox checkbox : heading.getCheckboxes()) {
			DialectSupport.emit(source, out,
					Location.at(checkbox.getLine(), checkbox.getColumn()),
					headingSubject,
					Vocab.TOGETL_ORG_CHECKBOX_STATE,
					Literal.of(checkbox.getState()));
		}
	}
	
	private static void emitLinkTarget(
			SourceDocument source,
			StatementConsumer out,
			Location location,
			BlankNode node,
			String target,
			Map<String, IRI> headingIrisByCustomId
	) throws IOException {
		String customId = linkCustomId(target);
		if (customId != null) {
			IRI heading = headingIrisByCustomId.get(customId);
			if (heading != null) {
				DialectSupport.emit(source, out, location, node,
						Vocab.TOGETL_ORG_LINK_TARGET, heading);
				return;
			}
		}
		if (isAbsoluteUri(target)) {
			DialectSupport.emit(source, out, location, node,
					Vocab.TOGETL_ORG_LINK_TARGET, IRI.of(target));
		} else {
			DialectSupport.emit(source, out, location, node,
					Vocab.TOGETL_ORG_LINK_TARGET, Literal.of(target));
		}
	}
	
	/**
	 * Extracts the CUSTOM_ID referenced by an internal link, i.e. {@code #id}
	 * or {@code <something>::#id}. A fragment on an external URL such as
	 * {@code https://example.com/#top} is not treated as internal.
	 */
	private static String linkCustomId(String target) {
		int hash = target.lastIndexOf('#');
		if (hash < 0 || hash == target.length() - 1) {
			return null;
		}
		String prefix = target.substring(0, hash);
		if (prefix.isEmpty() || prefix.endsWith("::")) {
			return target.substring(hash + 1);
		}
		return null;
	}
	
	private static boolean isAbsoluteUri(String target) {
		return ABSOLUTE_URI.matcher(target).matches();
	}
}
