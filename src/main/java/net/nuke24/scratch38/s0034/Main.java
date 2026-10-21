package net.nuke24.scratch38.s0034;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;

class StringUtil {
        private StringUtil() { throw new RuntimeException("Don't instantiate me, bro!"); }

        public static CharSequence leftPad(CharSequence template, CharSequence content) {
                if( content.length() == 0 ) {
                        return template;
                } else if( content.length() == template.length() ) {
                        return content;
                } else if( content.length() > template.length() ) {
                        return content.subSequence((content.length() - template.length()), content.length());
                } else {
                        // Hmm: toString() + toString() could be inefficient if template or content is hundreds of megabytes!
                        // Revisit if/when anybody ever uses it for such cases.
                        return template.subSequence(0, template.length()-content.length()).toString() + content.toString();
                }
        }
}

public class Main {
	public static void main(String[] args) {
		CsvReader<CsvRecord> reader = CsvReader.builder().ofCsvRecord(System.in);
		List<List<String>> data = reader.stream().map(rec -> rec.getFields()).toList();
		List<Integer> columnWidths = data.stream().<List<Integer>>reduce(
			Collections.<Integer>emptyList(),
			(List<Integer> acc, List<String> row) -> {
				List<Integer> nuAcc = new ArrayList<>();
				for( int i=0; i<row.size() || i < acc.size(); ++i ) {
					nuAcc.add(Math.max(
						row.size() > i ? row.get(i).length() : 0,
						acc.size() > i ? acc.get(i) : 0
					));
				}
				return nuAcc;
			},
			(List<Integer> lacc, List<Integer> racc) -> {
				List<Integer> nuAcc = new ArrayList<>();
				for( int i=0; i<lacc.size() || i < racc.size(); ++i ) {
					nuAcc.add(Math.max(
						lacc.size() > i ? lacc.get(i) : 0,
						racc.size() > i ? racc.get(i) : 0
					));
				}
				return nuAcc;
			}
		);
		List<String> templates = columnWidths.stream().map(" "::repeat).toList();
		
		for( List<String> row : data ) {
			System.out.print("| ");
			for( int i=0; i<templates.size(); ++i ) {
				if( i > 0 ) System.out.print(" | ");
				System.out.print(StringUtil.leftPad(templates.get(i), row.size() > i ? row.get(i) : ""));
			}
			System.out.print(" |");
			System.out.println();
		}
	}
}
