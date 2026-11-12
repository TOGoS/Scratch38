package net.nuke24.togetl.togetltsv;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TOGETLTSV alias table.
 *
 * <p>Aliases ending with ':', '#', or '/' act as namespace prefixes.
 * Aliases may be defined in terms of earlier aliases, so callers must
 * expand a target before storing it.</p>
 */
public final class AliasTable {
    private final LinkedHashMap<String, String> aliases = new LinkedHashMap<String, String>();

    public static AliasTable withDefaults() {
        AliasTable table = new AliasTable();
        table.define("rdf:", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        table.define("rdfs:", "http://www.w3.org/2000/01/rdf-schema#");
        table.define("xsd:", "http://www.w3.org/2001/XMLSchema#");
        table.define("togetl:", "http://ns.nuke24.net/TOGETL/");
        table.define("tef:", "http://ns.nuke24.net/TOGETL/TEF/");
        table.define("cc:", "http://ns.nuke24.net/ContentCouch/");
        table.define("dc:", "http://purl.org/dc/elements/1.1/");
        table.define("dcterms:", "http://purl.org/dc/terms/");
        return table;
    }

    public void clear() {
        aliases.clear();
    }

    public void define(String alias, String expandedTarget) {
        if (alias == null || alias.isEmpty()) {
            throw new IllegalArgumentException("Alias name must not be empty");
        }
        if (expandedTarget == null || expandedTarget.isEmpty()) {
            throw new IllegalArgumentException("Alias target must not be empty");
        }
        checkNoWhitespace(alias, "alias");
        checkNoWhitespace(expandedTarget, "alias target");
        aliases.put(alias, expandedTarget);
    }

    public Map<String, String> getAliases() {
        return Collections.unmodifiableMap(aliases);
    }

    public String expand(String token) {
        String bestAlias = null;
        for (String alias : aliases.keySet()) {
            if (token.equals(alias)) {
                return aliases.get(alias);
            }
            if (isNamespaceAlias(alias) && token.startsWith(alias)) {
                if (bestAlias == null || alias.length() > bestAlias.length()) {
                    bestAlias = alias;
                }
            }
        }
        if (bestAlias == null) {
            return token;
        }
        return aliases.get(bestAlias) + token.substring(bestAlias.length());
    }

    public String compress(String iri) {
        String bestAlias = null;
        String bestTarget = null;
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String alias = entry.getKey();
            String target = entry.getValue();
            if (iri.equals(target)) {
                return alias;
            }
            if (isNamespaceAlias(alias) && iri.startsWith(target)) {
                if (bestTarget == null || target.length() > bestTarget.length()) {
                    bestAlias = alias;
                    bestTarget = target;
                }
            }
        }
        if (bestAlias == null) {
            return iri;
        }
        return bestAlias + iri.substring(bestTarget.length());
    }

    private static boolean isNamespaceAlias(String alias) {
        char last = alias.charAt(alias.length() - 1);
        return last == ':' || last == '#' || last == '/';
    }

    private static void checkNoWhitespace(String value, String what) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                throw new IllegalArgumentException("Alias " + what + " must not contain whitespace: " + value);
            }
        }
    }
}
