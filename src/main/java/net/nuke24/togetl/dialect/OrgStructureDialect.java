package net.nuke24.togetl.dialect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.nuke24.togetl.etl.ImportReport;
import net.nuke24.togetl.etl.SourceDocument;
import net.nuke24.togetl.ident.Identifiers;
import net.nuke24.togetl.org.OrgCheckbox;
import net.nuke24.togetl.org.OrgDocument;
import net.nuke24.togetl.org.OrgHeading;
import net.nuke24.togetl.org.OrgLink;
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
	
	public void map(
			OrgDocument document,
			SourceDocument source,
			StatementConsumer out,
			ImportReport report
	) throws IOException {
		emitDocumentMetadata(document, source, out);
		
		List<IRI> levelStack = new ArrayList<IRI>();
		List<String> titleStack = new ArrayList<String>();
		for (int i = 0; i < document.getHeadings().size(); i++) {
			OrgHeading heading = document.getHeadings().get(i);
			while (levelStack.size() >= heading.getLevel()) {
				levelStack.remove(levelStack.size() - 1);
				titleStack.remove(titleStack.size() - 1);
			}
			
			IRI subject = headingIri(source, i, heading.getCustomId());
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
			
			if (!levelStack.isEmpty()) {
				DialectSupport.emit(source, out, headingLocation, subject,
						Vocab.TOGETL_OUTLINE_PARENT, levelStack.get(levelStack.size() - 1));
			}
			
			List<String> pathTitles = new ArrayList<String>(titleStack);
			pathTitles.add(heading.getTitle());
			DialectSupport.emit(source, out, headingLocation, subject,
					Vocab.TOGETL_OUTLINE_PATH, Literal.of(joinPath(pathTitles)));
					
			emitHeadingMarkers(source, out, subject, i, heading);
			
			levelStack.add(subject);
			titleStack.add(heading.getTitle());
		}
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
		String key = Identifiers.documentKey(source.getDocumentIri()) + ":";
		if (customId != null && !customId.isEmpty()) {
			key += customId;
		} else {
			key += headingIndex;
		}
		return IRI.of(Identifiers.mint("org-heading", key));
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
	
	private static void emitHeadingMarkers(
			SourceDocument source,
			StatementConsumer out,
			IRI headingSubject,
			int headingIndex,
			OrgHeading heading
	) throws IOException {
		for (int i = 0; i < heading.getLinks().size(); i++) {
			OrgLink link = heading.getLinks().get(i);
			BlankNode node = BlankNode.of("org-link-" + headingIndex + "-" + i);
			Location location = Location.at(link.getLine(), link.getColumn());
			DialectSupport.emit(source, out, location, headingSubject, Vocab.TOGETL_ORG_LINK, node);
			DialectSupport.emit(source, out, location, node, Vocab.TOGETL_ORG_LINK_TARGET,
					Literal.of(link.getTarget()));
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
}
