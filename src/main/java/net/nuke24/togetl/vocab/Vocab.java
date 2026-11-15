package net.nuke24.togetl.vocab;

import net.nuke24.togetl.rdf.IRI;

/**
 * IRIs for project, TEF, and RDF vocabulary terms used by importers.
 *
 * <p>TOGETL terms and TEF metadata terms live under tag URIs in the project
 * namespace, while instance and derived data IDs use a more specific tag URI
 * namespace for each data domain.</p>
 */
public final class Vocab {
	public static final String TOGETL_NS = "tag:nuke24.net,2026-11:togetl:";
	public static final String TEF_NS = "tag:nuke24.net,2026-11:togetl:tef:";
	public static final String GENERAL_NS = "tag:nuke24.net,2026-11:general:";
	public static final String PARTS3D_NS = "tag:nuke24.net,2026-11:3dparts:";
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
	public static final IRI TOGETL_DOCUMENT_IMPORT = togetl("DocumentImport");
	public static final IRI TOGETL_PARSE_ISSUE = togetl("ParseIssue");
	public static final IRI TOGETL_FILE = togetl("File");
	public static final IRI TOGETL_NAMESPACE = togetl("Namespace");
	public static final IRI TOGETL_NARRATION = togetl("Narration");
	public static final IRI PARTS3D_PART = parts3d("Part");
	public static final IRI PARTS3D_PART_CATEGORY = parts3d("PartCategory");
	public static final IRI PARTS3D_PART_SET = parts3d("PartSet");
	public static final IRI GENERAL_CATEGORY = general("Category");
	public static final IRI GENERAL_STANDARD = general("Standard");
	public static final IRI GENERAL_PROJECT_BASE = general("ProjectBase");
	public static final IRI GENERAL_FORMAT_CLASS = general("Format");
	public static final IRI TEF_ENTRY = tef("Entry");
	public static final IRI TEF_HEADER = tef("Header");
	public static final IRI TEF_COMMENT = tef("Comment");
	public static final IRI TEF_DIRECTIVE = tef("Directive");
	
	// Document metadata.
	public static final IRI TOGETL_SOURCE_DOCUMENT = togetl("sourceDocument");
	public static final IRI TOGETL_SOURCE_URI = togetl("sourceUri");
	public static final IRI TOGETL_FORMAT = togetl("format");
	public static final IRI TOGETL_PARSER_VERSION = togetl("parserVersion");
	public static final IRI TOGETL_HAS_BOM = togetl("hasBom");
	public static final IRI TOGETL_DOCUMENT_LINK = togetl("document");
	public static final IRI TOGETL_PARSE_ISSUE_LINK = togetl("parseIssue");
	public static final IRI GENERAL_SHA256 = general("sha256");
	public static final IRI GENERAL_BYTE_LENGTH = general("byteLength");

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
	// TODO: Figure out how these TEF_ terms are used
	// and whether they could be moved to general
	// or given more specific names (like TEF_ENTRY_KEY).
	// If moved, update in tef-dialect.tef, which is the canonical
	// source of term information.
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
	public static final IRI GENERAL_FILENAME = general("filename");
	
	// Domain predicates.
	public static final IRI GENERAL_NAME = general("name");
	public static final IRI GENERAL_TITLE = general("title");
	public static final IRI GENERAL_SHORT_DESCRIPTION = general("shortDescription");
	public static final IRI GENERAL_TAG = general("tag");
	public static final IRI GENERAL_KEYWORD = general("keyword");
	public static final IRI GENERAL_IS_MEMBER_OF = general("isMemberOf");
	public static final IRI GENERAL_MEMBER = general("member");
	public static final IRI GENERAL_SEE_ALSO = general("seeAlso");
	public static final IRI GENERAL_OBSOLETED_BY = general("obsoletedBy");
	public static final IRI GENERAL_FILE_LINK = general("file");
	public static final IRI GENERAL_IMAGE = general("image");
	public static final IRI GENERAL_PHOTO = general("photo");
	public static final IRI GENERAL_DOCUMENTED_BY = general("isDocumentedBy");
	public static final IRI GENERAL_DEMO = general("demo");
	public static final IRI GENERAL_MIRROR = general("mirror");
	public static final IRI GENERAL_ALIAS = general("alias");
	public static final IRI GENERAL_CREATED = general("created");
	public static final IRI GENERAL_SOURCE = general("source");
	public static final IRI GENERAL_PROJECT_HOMEPAGE = general("projectHomepage");
	public static final IRI GENERAL_SUBFORMAT_OF = general("subformatOf");
	public static final IRI GENERAL_CONTENT_TYPE = togetl("contentType");
	public static final IRI PARTS3D_OPENSCAD_RENDERING = parts3d("openscadRendering");
	public static final IRI PARTS3D_OPENSCAD_DESIGN = parts3d("openscadDesign");
	public static final IRI PARTS3D_STL_FILE = parts3d("stlFile");
	public static final IRI PARTS3D_CSG_FILE = parts3d("csgFile");
	
	private Vocab() { }
	
	public static IRI togetl(String localName) {
		return IRI.of(TOGETL_NS + localName);
	}
	
	public static IRI general(String localName) {
		return IRI.of(GENERAL_NS + localName);
	}
	
	public static IRI parts3d(String localName) {
		return IRI.of(PARTS3D_NS + localName);
	}
	
	public static IRI tef(String localName) {
		return IRI.of(TEF_NS + localName);
	}
}
