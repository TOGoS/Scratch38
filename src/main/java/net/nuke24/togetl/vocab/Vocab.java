package net.nuke24.togetl.vocab;

import net.nuke24.togetl.rdf.IRI;

/**
 * IRIs for project, TEF, and RDF vocabulary terms used by importers.
 *
 * <p>Vocabulary terms are HTTP IRIs so they can eventually be documented at
 * that address. Only instance resources use tag URIs.</p>
 */
public final class Vocab {
	public static final String TOGETL_NS = "http://ns.nuke24.net/TOGETL/";
	public static final String TEF_NS = "http://ns.nuke24.net/TOGETL/TEF/";
	public static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
	public static final String RDFS_NS = "http://www.w3.org/2000/01/rdf-schema#";
	public static final String XSD_NS = "http://www.w3.org/2001/XMLSchema#";
	public static final String DC_NS = "http://purl.org/dc/elements/1.1/";
	public static final String DCTERMS_NS = "http://purl.org/dc/terms/";
	public static final String CC_NS = "http://ns.nuke24.net/ContentCouch/";
	
	public static final IRI RDF_TYPE = IRI.of(RDF_NS + "type");
	public static final IRI RDF_PROPERTY = IRI.of(RDF_NS + "Property");
	public static final IRI RDFS_CLASS = IRI.of(RDFS_NS + "Class");
	public static final IRI RDFS_DATATYPE = IRI.of(RDFS_NS + "Datatype");
	public static final IRI RDFS_COMMENT = IRI.of(RDFS_NS + "comment");
	public static final IRI RDFS_LABEL = IRI.of(RDFS_NS + "label");
	public static final IRI XSD_INTEGER = IRI.of(XSD_NS + "integer");
	public static final IRI XSD_BOOLEAN = IRI.of(XSD_NS + "boolean");
	public static final IRI XSD_STRING = IRI.of(XSD_NS + "string");
	
	// Classes.
	public static final IRI TOGETL_DOCUMENT = togetl("Document");
	public static final IRI TOGETL_PARSE_ISSUE = togetl("ParseIssue");
	public static final IRI TOGETL_FILE = togetl("File");
	public static final IRI TOGETL_PART = togetl("Part");
	public static final IRI TOGETL_PART_CATEGORY = togetl("PartCategory");
	public static final IRI TOGETL_PART_SET = togetl("PartSet");
	public static final IRI TOGETL_CATEGORY = togetl("Category");
	public static final IRI TOGETL_STANDARD = togetl("Standard");
	public static final IRI TOGETL_NARRATION = togetl("Narration");
	public static final IRI TOGETL_NAMESPACE = togetl("Namespace");
	public static final IRI TOGETL_PROJECT_BASE = togetl("ProjectBase");
	public static final IRI TOGETL_FORMAT_CLASS = togetl("Format");
	public static final IRI TEF_ENTRY = tef("Entry");
	public static final IRI TEF_HEADER = tef("Header");
	public static final IRI TEF_COMMENT = tef("Comment");
	public static final IRI TEF_DIRECTIVE = tef("Directive");
	
	// Document metadata.
	public static final IRI TOGETL_SOURCE_URL = togetl("sourceUrl");
	public static final IRI TOGETL_SHA256 = togetl("sha256");
	public static final IRI TOGETL_BYTE_LENGTH = togetl("byteLength");
	public static final IRI TOGETL_FORMAT = togetl("format");
	public static final IRI TOGETL_PARSER_VERSION = togetl("parserVersion");
	public static final IRI TOGETL_HAS_BOM = togetl("hasBom");
	public static final IRI TOGETL_DOCUMENT_LINK = togetl("document");
	public static final IRI TOGETL_PARSE_ISSUE_LINK = togetl("parseIssue");
	
	// Parse issues.
	public static final IRI TOGETL_SEVERITY = togetl("severity");
	public static final IRI TOGETL_CODE = togetl("code");
	public static final IRI TOGETL_MESSAGE = togetl("message");
	public static final IRI TOGETL_RAW = togetl("raw");
	
	// TEF entry structure.
	public static final IRI TEF_ENTRY_TYPE = tef("entryType");
	public static final IRI TEF_ENTRY_VALUE = tef("entryValue");
	public static final IRI TEF_ENTRY_INDEX = tef("entryIndex");
	// `tef:content` is [usually] free-form descriptive text for the entry's subject,
	// not a second resource or a structured value that should be dereferenced.
	public static final IRI TEF_CONTENT = tef("content");
	public static final IRI TEF_HEADER_LINK = tef("header");
	public static final IRI TEF_COMMENT_LINK = tef("comment");
	public static final IRI TEF_DIRECTIVE_LINK = tef("directive");
	public static final IRI TEF_KEY = tef("key");
	public static final IRI TEF_VALUE = tef("value");
	public static final IRI TEF_INDEX = tef("index");
	public static final IRI TEF_TEXT = tef("text");
	public static final IRI TEF_NAME = tef("name");
	public static final IRI TEF_RAW = tef("raw");
	
	// Unmapped reporting.
	public static final IRI TOGETL_UNMAPPED_ENTRY = togetl("unmappedEntry");
	public static final IRI TOGETL_UNMAPPED_FIELD = togetl("unmappedField");
	public static final IRI TOGETL_AS_WRITTEN = togetl("asWritten");
	public static final IRI TOGETL_FILENAME = togetl("filename");
	
	// Domain predicates.
	public static final IRI TOGETL_NAME = togetl("name");
	public static final IRI TOGETL_TITLE = togetl("title");
	public static final IRI TOGETL_SHORT_DESCRIPTION = togetl("shortDescription");
	public static final IRI TOGETL_TAG = togetl("tag");
	public static final IRI TOGETL_KEYWORD = togetl("keyword");
	public static final IRI TOGETL_MEMBER_OF = togetl("memberOf");
	public static final IRI TOGETL_MEMBER = togetl("member");
	public static final IRI TOGETL_SEE_ALSO = togetl("seeAlso");
	public static final IRI TOGETL_OBSOLETED_BY = togetl("obsoletedBy");
	public static final IRI TOGETL_STL_FILE = togetl("stlFile");
	public static final IRI TOGETL_CSG_FILE = togetl("csgFile");
	public static final IRI TOGETL_FILE_LINK = togetl("file");
	public static final IRI TOGETL_IMAGE_REF = togetl("imageRef");
	public static final IRI TOGETL_PHOTO_REF = togetl("photoRef");
	public static final IRI TOGETL_DOC_REF = togetl("docRef");
	public static final IRI TOGETL_DEMO_REF = togetl("demoRef");
	public static final IRI TOGETL_OPENSCAD_RENDERING_REF = togetl("openscadRenderingRef");
	public static final IRI TOGETL_OPENSCAD_RENDERING = togetl("openscadRendering");
	public static final IRI TOGETL_OPENSCAD_DESIGN_REF = togetl("openscadDesignRef");
	public static final IRI TOGETL_MIRROR = togetl("mirror");
	public static final IRI TOGETL_ALIAS = togetl("alias");
	public static final IRI TOGETL_CREATED = togetl("created");
	public static final IRI TOGETL_SOURCE = togetl("source");
	public static final IRI TOGETL_PROJECT_HOMEPAGE_URL = togetl("projectHomepageUrl");
	public static final IRI TOGETL_SUBFORMAT_OF = togetl("subformatOf");
	public static final IRI TOGETL_CONTENT_TYPE = togetl("contentType");
	
	private Vocab() {
	}
	
	public static IRI togetl(String localName) {
		return IRI.of(TOGETL_NS + localName);
	}
	
	public static IRI tef(String localName) {
		return IRI.of(TEF_NS + localName);
	}
}
