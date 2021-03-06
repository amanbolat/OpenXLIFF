/*******************************************************************************
 * Copyright (c) 2003, 2018 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/

package com.maxprograms.converters.po;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.util.Vector;
import java.lang.System.Logger.Level;
import java.lang.System.Logger;

import com.maxprograms.converters.Utils;

public class Po2Xliff {

	private static FileOutputStream output;
	private static FileOutputStream skeleton;

	private static String source;
	private static String target;
	private static String comment;
	private static String context;
	private static String reference;
	private static String flags;
	private static boolean fuzzy;
	private static boolean cformat;

	private static String sourceLanguage;
	private static int segId;
	private static int domainId;
	private static int contextId = 1;
	private static int refId = 1;
	private static String newContext;
	private static Vector<String> pluralTargets;
	private static int plurals;
	private static String plural_source;

	private Po2Xliff() {
		// do not instantiate this class
		// use run method instead
	}

	public static Vector<String> run(Hashtable<String, String> params) {
		Vector<String> result = new Vector<>();

		String inputFile = params.get("source");
		String xliffFile = params.get("xliff");
		String skeletonFile = params.get("skeleton");
		sourceLanguage = params.get("srcLang");
		String targetLanguage = params.get("tgtLang");
		String srcEncoding = params.get("srcEncoding");
		String tgtLang = "";
		if (targetLanguage != null) {
			tgtLang = "\" target-language=\"" + targetLanguage;
		}

		source = "";
		plural_source = "";
		pluralTargets = new Vector<>();
		target = "";
		comment = "";
		context = "";
		reference = "";
		flags = "";
		newContext = "";
		fuzzy = false;
		boolean inDomain = false;
		cformat = false;

		try {
			try (FileInputStream stream = new FileInputStream(inputFile)) {
				try (InputStreamReader input = new InputStreamReader(stream, srcEncoding)) {
					BufferedReader buffer = new BufferedReader(input);

					output = new FileOutputStream(xliffFile);

					writeString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
					writeString("<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
							+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
							+ "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");
					writeString("<?encoding " + srcEncoding + "?>\n");

					writeString("<file original=\"" + inputFile + "\" source-language=\"" + sourceLanguage + tgtLang
							+ "\" datatype=\"po\">\n");
					writeString("<header>\n");
					writeString("   <skl>\n");
					writeString("      <external-file href=\"" + Utils.cleanString(skeletonFile) + "\"/>\n");
					writeString("   </skl>\n");
					writeString("</header>\n");
					writeString("<body>\n");

					skeleton = new FileOutputStream(skeletonFile);

					String line;
					while ((line = buffer.readLine()) != null) {
						line = line + "\n";

						if (line.trim().length() == 0) {
							// no text in this line
							// segment separator
							writeSkeleton(line);
						} else {
							if (line.startsWith("#:")) {
								// it is a reference
								if (reference.equals("")) {
									reference = line.substring(2);
								} else {
									reference = reference + " " + line.substring(2);
								}
							}
							if (line.startsWith("# ")) {
								// translator comment
								comment = comment + line.substring(2);
							}
							if (line.trim().equals("#")) {
								comment = comment + "\n";
							}
							if (line.startsWith("#.")) {
								// automatic comment
								context = context + line.substring(2);
							}
							if (line.startsWith("#,")) {
								flags = line.substring(2);
								// check for fuzzy
								if (flags.indexOf("fuzzy") != -1) {
									fuzzy = true;
								}
								// Only c-format is parsed. Tags from other
								// formats, like php-format or python-format,
								// are left as part of the text
								if (flags.indexOf("c-format") != -1 && flags.indexOf("no-c-format") == -1) {
									cformat = true;
								}
							}
							if (line.startsWith("#~")) {
								// commented entry
								writeSkeleton(line);
							}
							if (line.startsWith("msgctxt")) {
								// get context text
								line = line.substring(5);
								newContext = line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
								line = buffer.readLine();
								while (line.startsWith("\"")) {
									newContext = newContext + "\n"
											+ line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
									line = buffer.readLine();
								}
								continue;
							}
							if (line.startsWith("msgid")) {
								if (line.startsWith("msgid_plural")) {
									// get plural source
									line = line.substring(12);
									plural_source = line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
									line = buffer.readLine();
									while (line.startsWith("\"")) {
										plural_source = plural_source + "\n"
												+ line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
										line = buffer.readLine();
									}
								} else {
									// get source text
									line = line.substring(5);
									source = line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
									line = buffer.readLine();
									while (line.trim().startsWith("\"")) {
										source = source + "\n"
												+ line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
										line = buffer.readLine();
									}
								}
								continue;
							}
							if (line.startsWith("msgstr")) {
								if (line.startsWith("msgstr[")) {
									while (line.startsWith("msgstr[")) {
										// get all plural targets
										line = line.substring(line.indexOf(']') + 1);
										String pluralTarget = line.substring(line.indexOf('\"') + 1,
												line.lastIndexOf('\"'));
										line = buffer.readLine();
										while (line != null && line.trim().startsWith("\"")) {
											pluralTarget = pluralTarget + "\n"
													+ line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
											line = buffer.readLine();
											if (line == null) {
												line = "";
											}
										}
										if (line == null) {
											line = "";
										}
										pluralTargets.add(pluralTarget);
									}
									writeSegment();
								} else {
									// get the target
									line = line.substring(6);
									target = line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
									line = buffer.readLine();
									while (line != null && line.trim().startsWith("\"")) {
										target = target + "\n"
												+ line.substring(line.indexOf('\"') + 1, line.lastIndexOf('\"'));
										line = buffer.readLine();
										if (line == null) {
											line = "";
										}
										if (line.trim().startsWith("\"Plural-Forms:")) {
											parsePlural(line);
										}
									}
									if (plural_source.equals("")) {
										writeSegment();
									}
								}
								continue;
							}
							if (line.startsWith("domain")) {
								if (inDomain) {
									writeString("   </group>\n");
								}
								inDomain = true;
								writeString(
										"   <group id=\"##" + domainId++ + "\" restype=\"x-gettext-domain\" resname=\""
												+ line.substring(6).trim() + "\">\n");
								writeSkeleton(line);
							}
						}
					}

					skeleton.close();

					if (inDomain) {
						writeString("   </group>\n");
					}
					writeString("</body>\n");
					writeString("</file>\n");
					writeString("</xliff>");
					output.close();
				}
			}
			result.add(0, "0"); // success
		} catch (IOException e) {
			Logger logger = System.getLogger(Po2Xliff.class.getName());
			logger.log(Level.ERROR, "Error converting PO file", e);
			result.add(0, "1");
			result.add(1, e.getMessage());
		}

		return result;
	}

	private static void parsePlural(String line) {
		String string = line.substring(line.indexOf("nplurals") + 8).trim();
		String number = string.substring(string.indexOf('=') + 1, string.indexOf(';'));
		plurals = Integer.parseInt(number);
	}

	private static void writeString(String string) throws IOException {
		output.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private static void writeSkeleton(String string) throws IOException {
		skeleton.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private static void writeSegment() throws IOException {
		if (!plural_source.equals("")) {
			writeString("   <group restype=\"x-gettext-plurals\" id=\"" + segId + "\">\n");
			if (!context.equals("")) {
				writeString("      <context-group name=\"x-po-entry-header#" + contextId++
						+ "\" purpose=\"information\">\n" + "         <context context-type=\"x-po-autocomment\">"
						+ Utils.cleanString(context) + "</context>\n" + "      </context-group>\n");
			}
			if (!reference.equals("")) {
				parseReference(reference);
			}
			if (!newContext.equals("")) {
				writeString("      <context-group name=\"x-po-msgctxt#" + contextId++ + "\" purpose=\"information\">\n"
						+ "         <context context-type=\"x-msgctxt\">" + Utils.cleanString(newContext)
						+ "</context>\n" + "      </context-group>\n");
			}
			if (!flags.equals("")) {
				writeString("      <prop-group>\n" + "         <prop prop-type=\"x-po-flags\">"
						+ Utils.cleanString(flags).trim() + "</prop>\n" + "      </prop-group>\n");
			}
			// write singular first
			if (!pluralTargets.isEmpty()) {
				target = pluralTargets.get(0);
			} else {
				target = "";
			}
			String approved = "no";
			if (!fuzzy && target.trim().length() > 0) {
				approved = "yes";
			}
			writeString("      <trans-unit id=\"" + segId + " [0]\" xml:space=\"preserve\" approved=\"" + approved
					+ "\">\n");
			if (cformat) {
				writeString("         <source xml:lang=\"" + sourceLanguage + "\">"
						+ parseString(Utils.cleanString(source)) + "</source>\n");
				if (target.length() > 0 || approved.equals("yes")) {
					writeString("         <target>" + parseString(Utils.cleanString(target)) + "</target>\n");
				}
			} else {
				writeString("         <source xml:lang=\"" + sourceLanguage + "\">" + Utils.cleanString(source)
						+ "</source>\n");
				if (target.length() > 0 || approved.equals("yes")) {
					writeString("         <target>" + Utils.cleanString(target) + "</target>\n");
				}
			}
			if (!comment.equals("")) {
				writeString("         <note from=\"po-file\">" + Utils.cleanString(comment) + "</note>\n");
			}
			writeString("         <note from=\"po-file\" annotates=\"source\">" + "Singular form" + "</note>\n");
			writeString("      </trans-unit>\n");
			// write plurals
			for (int i = 1; i < plurals; i++) {
				if (pluralTargets.size() > i) {
					target = pluralTargets.get(i);
				} else {
					target = "";
				}
				if (!fuzzy && target.trim().length() > 0) {
					approved = "yes";
				}
				writeString("      <trans-unit id=\"" + segId + " [" + i + "]\" xml:space=\"preserve\" approved=\""
						+ approved + "\">\n");
				if (cformat) {
					writeString("         <source xml:lang=\"" + sourceLanguage + "\">"
							+ parseString(Utils.cleanString(plural_source)) + "</source>\n");
					if (target.length() > 0 || approved.equals("yes")) {
						writeString("         <target>" + parseString(Utils.cleanString(target)) + "</target>\n");
					}
				} else {
					writeString("         <source xml:lang=\"" + sourceLanguage + "\">"
							+ Utils.cleanString(plural_source) + "</source>\n");
					if (target.length() > 0 || approved.equals("yes")) {
						writeString("         <target>" + Utils.cleanString(target) + "</target>\n");
					}
				}
				writeString("         <note from=\"po-file\" annotates=\"source\">" + "Plural form: [" + i + "]"
						+ "</note>\n");
				writeString("      </trans-unit>\n");
			}
			writeString("   </group>\n");
		} else {
			// only singular
			String approved = "no";
			if (!fuzzy && target.trim().length() > 0) {
				approved = "yes";
			}
			String restype = "";
			if (source.trim().equals("")) {
				restype = " restype=\"x-gettext-domain-header\" ";
			}
			writeString("   <trans-unit id=\"" + segId + "\" xml:space=\"preserve\" approved=\"" + approved + "\""
					+ restype + ">\n");
			if (cformat) {
				writeString("      <source xml:lang=\"" + sourceLanguage + "\">"
						+ parseString(Utils.cleanString(source)) + "</source>\n");
				if (target.length() > 0 || approved.equals("yes")) {
					writeString("      <target>" + parseString(Utils.cleanString(target)) + "</target>\n");
				}
			} else {
				if (source.trim().equals("")) {
					source = target;
				}
				writeString("      <source xml:lang=\"" + sourceLanguage + "\">" + Utils.cleanString(source)
						+ "</source>\n");
				if (target.length() > 0 || approved.equals("yes")) {
					writeString("      <target>" + Utils.cleanString(target) + "</target>\n");
				}
			}
			if (!comment.equals("")) {
				writeString("      <note from=\"po-file\">" + Utils.cleanString(comment) + "</note>\n");
			}
			if (!context.equals("")) {
				writeString("      <context-group name=\"x-po-entry-header#" + contextId++
						+ "\" purpose=\"information\">\n" + "         <context context-type=\"x-po-autocomment\">"
						+ Utils.cleanString(context) + "</context>\n" + "      </context-group>\n");
			}
			if (!reference.equals("")) {
				parseReference(reference);
			}
			if (!newContext.equals("")) {
				writeString("      <context-group name=\"x-po-msgctxt#" + contextId++ + "\" purpose=\"information\">\n"
						+ "         <context context-type=\"x-msgctxt\">" + Utils.cleanString(newContext)
						+ "</context>\n" + "      </context-group>\n");
			}
			if (!flags.equals("")) {
				writeString("      <prop-group>\n" + "         <prop prop-type=\"x-po-flags\">"
						+ Utils.cleanString(flags).trim() + "</prop>\n" + "      </prop-group>\n");
			}
			writeString("   </trans-unit>\n");
		}

		writeSkeleton("%%%" + segId++ + "%%%\n");

		source = "";
		plural_source = "";
		pluralTargets.removeAllElements();
		target = "";
		comment = "";
		context = "";
		reference = "";
		flags = "";
		newContext = "";
		fuzzy = false;
		cformat = false;
	}

	private static String parseString(String string) {
		// Valid c format especifications must end
		// with one of diouxXfeEgGcs

		int id = 1;
		int index = string.indexOf('%');
		if (index == -1) {
			return string;
		}
		if (string.charAt(index + 1) == '%') {
			index = string.indexOf('%', index + 2);
		}
		String result = "";
		while (index != -1) {
			result = result + string.substring(0, index) + "<ph ctype=\"x-c-param\" id=\"" + id++ + "\">";
			int i = index;
			char c = string.charAt(i++);
			while (i < string.length() && "diouxXfeEgGcs".indexOf(c) == -1) {
				result = result + c;
				c = string.charAt(i++);
			}
			result = result + c + "</ph>";
			string = string.substring(i);
			index = string.indexOf('%');
			if (index != -1 && index < string.length() && string.charAt(index + 1) == '%') {
				index = string.indexOf('%', index + 2);
			}
		}
		result = result + string;
		return result;
	}

	private static void parseReference(String ref) throws IOException {
		if (ref.trim().equals("")) {
			return;
		}
		if (ref.indexOf(':') != -1) {
			// regular reference
			StringTokenizer tokenizer = new StringTokenizer(ref.trim());
			while (tokenizer.hasMoreTokens()) {
				writeString("      <context-group name=\"x-po-reference#" + refId++ + "\" purpose=\"location\">\n");
				String token = tokenizer.nextToken();
				if (token.indexOf(':') != -1) {
					writeString("         <context context-type=\"sourcefile\">"
							+ token.substring(0, token.indexOf(':')) + "</context>\n");
					writeString("         <context context-type=\"linenumber\">"
							+ token.substring(token.indexOf(':') + 1) + "</context>\n");
				}
				writeString("      </context-group>\n");
			}
		} else {
			// strange thing, may be from .properties file
			writeString("      <context-group name=\"x-po-reference#" + refId++ + "\" purpose=\"x-unknown\">\n");
			writeString(
					"         <context context-type=\"x-unknown\">" + Utils.cleanString(ref).trim() + "</context>\n");
			writeString("      </context-group>\n");
		}
	}

}
