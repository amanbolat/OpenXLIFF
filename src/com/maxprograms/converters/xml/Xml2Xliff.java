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
package com.maxprograms.converters.xml;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Vector;
import java.lang.System.Logger.Level;
import java.lang.System.Logger;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.maxprograms.converters.Utils;
import com.maxprograms.segmenter.Segmenter;
import com.maxprograms.xml.Attribute;
import com.maxprograms.xml.CData;
import com.maxprograms.xml.Catalog;
import com.maxprograms.xml.Comment;
import com.maxprograms.xml.Document;
import com.maxprograms.xml.Element;
import com.maxprograms.xml.SAXBuilder;
import com.maxprograms.xml.SilentErrorHandler;
import com.maxprograms.xml.TextNode;
import com.maxprograms.xml.XMLNode;
import com.maxprograms.xml.XMLUtils;
import com.wutka.dtd.DTD;
import com.wutka.dtd.DTDParser;

public class Xml2Xliff {

	private static final Logger LOGGER = System.getLogger(Xml2Xliff.class.getName());

	private static final String STARTG = "%%START%%";
	private static final String ENDG = "%%END%%";

	static final String DOUBLEPRIME = "\u2033";
	static final String MATHLT = "\u2039";
	static final String MATHGT = "\u200B\u203A";
	static final String GAMP = "\u200B\u203A";

	private static String inputFile;
	private static String xliffFile;
	private static String skeletonFile;
	private static String sourceLanguage;
	private static String srcEncoding;
	private static FileInputStream input;
	private static FileOutputStream output;
	private static FileOutputStream skeleton;
	private static int segId;
	private static int tagId;
	private static List<String> segments;
	private static Hashtable<String, String> startsSegment;
	private static Hashtable<String, Vector<String>> translatableAttributes;
	private static Hashtable<String, String> inline;
	private static Hashtable<String, String> ctypes;
	private static Hashtable<String, String> keepFormating;
	private static boolean segByElement;
	private static Segmenter segmenter;
	private static String catalog;
	private static String rootElement;
	private static Hashtable<String, String> entities;
	private static String entitiesMap;
	private static Element root;
	private static String text;
	private static Stack<String> stack;
	private static String translatable = "";
	private static boolean inDesign = false;
	private static Hashtable<String, String> ignore;
	private static boolean generic;
	private static boolean resx;
	private static String startText;
	private static String endText;
	private static boolean dita_based;
	private static String targetLanguage;
	private static boolean inCData;
	private static boolean translateComments;

	private Xml2Xliff() {
		// do not instantiate this class
		// use run method instead
	}

	public static Vector<String> run(Hashtable<String, String> params) {
		Vector<String> result = new Vector<String>();
		segId = 1;
		stack = new Stack<String>();

		inputFile = params.get("source");
		xliffFile = params.get("xliff");
		skeletonFile = params.get("skeleton");
		sourceLanguage = params.get("srcLang");
		targetLanguage = params.get("tgtLang");
		srcEncoding = params.get("srcEncoding");
		catalog = params.get("catalog");
		String elementSegmentation = params.get("paragraph");
		String initSegmenter = params.get("srxFile");
		String isInDesign = params.get("InDesign");
		if (isInDesign != null) {
			inDesign = true;
		} else {
			inDesign = false;
		}
		String isResx = params.get("resx");
		if (isResx != null) {
			resx = true;
		} else {
			resx = false;
		}
		String dita = params.get("dita_based");
		if (dita != null) {
			dita_based = dita.equalsIgnoreCase("yes");
		}

		String isGeneric = params.get("generic");
		if (isGeneric != null && isGeneric.equals("yes")) {
			generic = true;
		} else {
			generic = false;
		}

		String comments = params.get("translateComments");
		if (comments != null) {
			translateComments = comments.equalsIgnoreCase("yes");
		}

		try {
			boolean autoConfiguration = false;
			String iniFile = getIniFile(inputFile);
			File f = new File(iniFile);
			if (!f.exists()) {
				if (!generic) {
					MessageFormat mf = new MessageFormat(
							"Configuration file ''{0}'' not found. \\n\\nWrite a new configuration file for the XML Converter or set file type to ''XML (Generic)''.");
					throw new IOException(mf.format(new Object[] { f.getName() }));
				}
				AutoConfiguration.run(inputFile, f.getAbsolutePath(), catalog);
				autoConfiguration = true;
			}

			if (elementSegmentation == null) {
				segByElement = false;
			} else {
				if (elementSegmentation.equals("yes")) {
					segByElement = true;
				} else {
					segByElement = false;
				}
			}

			if (segByElement == false) {
				segmenter = new Segmenter(initSegmenter, sourceLanguage, catalog);
			}

			String detected = getEncoding(inputFile);
			if (!srcEncoding.equals(detected)) {
				srcEncoding = detected;
			}

			input = new FileInputStream(inputFile);
			skeleton = new FileOutputStream(skeletonFile);
			output = new FileOutputStream(xliffFile);
			writeHeader();

			int size = input.available();
			byte[] array = new byte[size];
			if (size != input.read(array)) {
				result.add("1");
				result.add("Error reading from input file.");
				return result;
			}
			String file = new String(array, srcEncoding);
			// remove xml declaration and doctype
			int begin = file.indexOf("<" + rootElement);
			if (begin != -1) {
				if (file.charAt(0) == '<') {
					writeSkeleton(file.substring(0, begin));
				} else {
					writeSkeleton(file.substring(1, begin));
				}
			}

			buildTables(iniFile);

			if (autoConfiguration) {
				Files.delete(Paths.get(f.toURI()));
			}

			buildList();

			processList();

			skeleton.close();
			writeString("</body>\n");
			writeString("</file>\n");
			writeString("</xliff>");
			input.close();
			output.close();

			result.add("0");

		} catch (IOException | SAXException | ParserConfigurationException e) {
			LOGGER.log(Level.ERROR, "Error converting XML file", e);
			result.add("1");
			result.add(e.getMessage());
		}

		return result;
	}

	public static String getIniFile(String fileName) throws SAXException, IOException, ParserConfigurationException {
		File folder = new File(System.getProperty("user.dir"), "xmlfilter");
		SAXBuilder builder = new SAXBuilder();
		Catalog cat = new Catalog(catalog);
		builder.setEntityResolver(cat);
		builder.setValidating(false);
		builder.setErrorHandler(new SilentErrorHandler());
		/*
		 * set expandEntities to false if automatic inclusion of referenced documents is
		 * not desired. It is enabled by default, but may change later upon request.
		 *
		 */
		// builder.expandEntities(false);
		Document doc = builder.build(fileName);
		entities = new Hashtable<String, String>();

		Hashtable<String, String> map = doc.getEntities();

		entitiesMap = "";
		if (map != null) {
			Enumeration<String> en = entities.keys();
			while (en.hasMoreElements()) {
				String key = en.nextElement();
				entitiesMap = entitiesMap + "      <prop prop-type=\"" + key.substring(1, key.length() - 1) + "\">"
						+ cleanEntity(entities.get(key)) + "</prop>\n";
			}
		}

		// Add predefined standard entities
		entities.put("&gt;", ">");
		entities.put("&lt;", "<");
		entities.put("&amp;", "&");
		entities.put("&apos;", "'");
		entities.put("&quot;", "\"");

		root = doc.getRootElement();
		rootElement = root.getName();
		if (dita_based && rootElement.equals("svg")) {
			dita_based = false;
		}

		// check for ResX before anything else
		// this requires a fixed ini name
		if (root.getName().equals("root")) {
			List<Element> dataElements = root.getChildren("data");
			if (dataElements.size() > 0) {
				boolean isResx = false;
				for (int i = 0; i < dataElements.size(); i++) {
					Element g = (dataElements.get(i)).getChild("translate");
					if (g != null) {
						isResx = true;
						break;
					}
				}
				if (isResx) {
					return new File(folder, "config_resx.xml").getAbsolutePath();
				}
			}
		}
		String pub = doc.getPublicId();
		String sys = doc.getSystemId();
		if (sys != null) {
			// remove path from systemId
			if (sys.indexOf('/') != -1 && sys.lastIndexOf('/') < sys.length()) {
				sys = sys.substring(sys.lastIndexOf('/') + 1);
			}
			if (sys.indexOf('\\') != -1 && sys.lastIndexOf('/') < sys.length()) {
				sys = sys.substring(sys.lastIndexOf('\\') + 1);
			}
		}

		Document d = builder.build(catalog);
		Element r = d.getRootElement();
		List<Element> dtds = r.getChildren("dtd");
		Iterator<Element> i = dtds.iterator();
		while (i.hasNext()) {
			Element dtd = i.next();
			if (pub != null && dtd.getAttributeValue("publicId", "").equals(pub)) {
				String s = getRootElement(dtd.getText());
				if (s != null) {
					return new File(folder, "config_" + s + ".xml").getAbsolutePath();
				}
			}
			if (sys != null && dtd.getAttributeValue("systemId", "").equals(sys)) {
				String s = getRootElement(dtd.getText());
				if (s != null) {
					return new File(folder, "config_" + s + ".xml").getAbsolutePath();
				}
				if (dtd.getAttributeValue("systemId", "").endsWith(sys)) {
					String st = getRootElement(dtd.getText());
					if (st != null) {
						return new File(folder, "config_" + st + ".xml").getAbsolutePath();
					}
				}
			}
		}

		if (rootElement.indexOf(':') != -1) {
			return new File(folder, "config_" + rootElement.substring(0, rootElement.indexOf(':')) + ".xml")
					.getAbsolutePath();
		}

		File f = new File(folder, "config_" + rootElement + ".xml");
		if (!f.exists() && dita_based) {
			File base = new File(folder, "config_dita.xml");
			Document dd = builder.build(base);
			List<Element> list = dd.getRootElement().getChildren();
			Iterator<Element> it = list.iterator();
			while (it.hasNext()) {
				if (rootElement.equals(it.next().getText().trim())) {
					return base.getAbsolutePath();
				}
			}
			String cls = root.getAttributeValue("class", "");
			String[] parts = cls.split("\\s");
			for (int h = 0; h < parts.length; h++) {
				String part = parts[h];
				if (part.indexOf('/') == -1) {
					continue;
				}
				String code = part.substring(part.indexOf('/') + 1).trim();
				it = list.iterator();
				while (it.hasNext()) {
					if (code.equals(it.next().getText().trim())) {
						return base.getAbsolutePath();
					}
				}
			}
		}
		return new File(folder, "config_" + rootElement + ".xml").getAbsolutePath();
	}

	private static String cleanEntity(String s) {
		int control = s.indexOf('&');
		while (control != -1) {
			int sc = s.indexOf(';', control);
			if (sc == -1) {
				// no semicolon, it's not an entity
				s = s.substring(0, control) + "&amp;" + s.substring(control + 1);
			} else {
				// may be an entity
				String candidate = s.substring(control, sc) + ";";
				if (!candidate.equals("&amp;")) {
					String entity = entities.get(candidate);
					if (entity != null) {
						s = s.substring(0, control) + entity + s.substring(sc + 1);
					} else if (candidate.startsWith("&#x")) {
						// it's a character in hexadecimal format
						int c = Integer.parseInt(candidate.substring(3, candidate.length() - 1), 16);
						s = s.substring(0, control) + (char) c + s.substring(sc + 1);
					} else if (candidate.startsWith("&#")) {
						// it's a character
						int c = Integer.parseInt(candidate.substring(2, candidate.length() - 1));
						s = s.substring(0, control) + (char) c + s.substring(sc + 1);
					} else {
						s = s.substring(0, control) + "&amp;" + s.substring(control + 1);
					}
				}
			}
			if (control < s.length()) {
				control++;
			}
			control = s.indexOf('&', control);
		}

		control = s.indexOf('<');
		while (control != -1) {
			s = s.substring(0, control) + "&lt;" + s.substring(control + 1);
			if (control < s.length()) {
				control++;
			}
			control = s.indexOf('<', control);
		}

		control = s.indexOf('>');
		while (control != -1) {
			s = s.substring(0, control) + "&gt;" + s.substring(control + 1);
			if (control < s.length()) {
				control++;
			}
			control = s.indexOf('>', control);
		}
		return s;
	}

	private static String getRootElement(String file) {
		String result = null;
		File dtd = new File(file);
		try {
			DTDParser parser = new DTDParser(dtd);
			DTD d = parser.parse(true);
			if (d != null && d.rootElement != null) {
				result = d.rootElement.getName();
			}
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Error getting root element from DTD", e);
		}
		return result;
	}

	private static void writeHeader() throws IOException {
		String tgtLang = "";
		if (targetLanguage != null) {
			tgtLang = "\" target-language=\"" + targetLanguage;
		}

		String format = "xml";
		if (inDesign) {
			format = "x-inx";
		} else if (resx) {
			format = "resx";
		}
		writeString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		writeString("<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\" "
				+ "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
				+ "xsi:schemaLocation=\"urn:oasis:names:tc:xliff:document:1.2 xliff-core-1.2-transitional.xsd\">\n");
		writeString("<?encoding " + srcEncoding + "?>\n");

		writeString("<file original=\"" + cleanString(inputFile) + "\" source-language=\"" + sourceLanguage + tgtLang
				+ "\" datatype=\"" + format + "\">\n");
		writeString("<header>\n");
		writeString("   <skl>\n");
		writeString("      <external-file href=\"" + Utils.cleanString(skeletonFile) + "\"/>\n");
		writeString("   </skl>\n");
		if (!entitiesMap.equals("")) {
			writeString("   <prop-group name=\"entities\">\n" + entitiesMap + "   </prop-group>\n");
		}
		writeString("</header>\n");
		writeString("<body>\n");
	}

	private static void processList() throws IOException, SAXException, ParserConfigurationException {
		for (int i = 0; i < segments.size(); i++) {
			String txt = segments.get(i);

			if (txt.startsWith("" + '\u007F' + "" + '\u007F')) {
				// send directly to skeleton
				writeSkeleton(txt.substring(2));
				continue;
			}
			if (txt.startsWith("" + '\u0081')) {
				inCData = true;
				txt = txt.substring(1);
			} else {
				inCData = false;
			}
			if (inDesign && !txt.trim().equals("")) {
				if (txt.startsWith("c_") && !txt.substring(2).trim().equals("")) {
					writeSkeleton("c_");
					txt = txt.substring(2);
					txt = txt.replaceAll("~sep~", "_");
				} else {
					writeSkeleton(txt);
					continue;
				}
			}
			tagId = 0;
			if (dita_based) {
				txt = prepareG(txt);
			}
			txt = addTags(txt);
			if (segByElement == true) {
				writeSegment(txt);
			} else {
				String[] segs = segmenter.segment(txt);
				for (int h = 0; h < segs.length; h++) {
					String seg = segs[h];
					while (seg.startsWith("" + '\u2029')) {
						writeSkeleton("" + '\u2029');
						seg = seg.substring(1);
					}
					writeSegment(seg);
				}
			}
		}
	}

	private static String prepareG(String txt) {
		int start = txt.indexOf(STARTG);
		if (start == -1) {
			return txt;
		}
		String result = txt.substring(0, start);
		while (start != -1) {
			txt = txt.substring(start + STARTG.length());
			start = txt.indexOf(STARTG);
			String element = txt.substring(0, start);
			result = result + makeMrk(element);
			txt = txt.substring(start + STARTG.length());
			int end = txt.indexOf(ENDG);
			String content = txt.substring(0, end);
			result = result + content + "</mrk>";
			end = txt.indexOf(ENDG, end + 1);
			txt = txt.substring(end + ENDG.length());
			start = txt.indexOf(STARTG);
			if (start != -1) {
				result = result + txt.substring(0, start);
			}
		}
		return result + txt;
	}

	private static void writeSegment(String tagged) throws IOException, SAXException, ParserConfigurationException {
		String restype = "";
		if (!containsText(tagged)) {
			String untagged = removeTags(tagged);
			writeSkeleton(untagged);
			return;
		}
		if (inCData) {
			restype = " restype=\"x-cdata\"";
		}
		String seg = "   <trans-unit id=\"" + segId + "\" xml:space=\"preserve\" approved=\"no\" " + restype + ">\n"
				+ "      <source xml:lang=\"" + sourceLanguage + "\">" + tagged + "</source>\n   </trans-unit>\n";

		String clean = tidy(seg);
		String dirt = startText + "%%%" + segId++ + "%%%\n" + endText;
		writeString(clean);
		writeSkeleton(dirt);
	}

	private static String removeTags(String tagged) throws IOException, SAXException, ParserConfigurationException {
		String source = "<skeleton>" + tagged + "</skeleton>";
		SAXBuilder b = new SAXBuilder();
		Document d = null;
		try {
			d = b.build(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
		} catch (SAXException sax) {
			LOGGER.log(Level.ERROR, "Broken segment: " + source);
			throw sax;
		}
		Element r = d.getRootElement();
		return extractText(r);
	}

	private static String extractText(Element element) throws SAXException {
		if (element.getName().equals("ph")) {
			return Xliff2Xml.fixEntities(element);
		}
		if (dita_based && element.getName().equals("g")) {
			return cleanMrk(element);
		}

		String result = "";
		List<XMLNode> content = element.getContent();
		Iterator<XMLNode> i = content.iterator();
		while (i.hasNext()) {
			XMLNode n = i.next();
			switch (n.getNodeType()) {
			case XMLNode.ELEMENT_NODE:
				Element e = (Element) n;
				if (e.getName().equals("ph")) {
					String ph = extractText(e);
					result = result + ph;
				} else if (e.getName().equals("mrk")) {
					String mrk = cleanMrk(e);
					result = result + mrk;
				} else {
					throw new SAXException("broken tagged text");
				}
				break;
			case XMLNode.TEXT_NODE:
				if (inCData) {
					result = result + ((TextNode) n).getText();
				} else {
					result = result + addEntities(((TextNode) n).getText());
				}
				break;
			}
		}
		return result;
	}

	private static String cleanMrk(Element element) throws SAXException {
		String ts = element.getAttributeValue("ts", "");
		if (ts.isEmpty()) {
			throw new SAXException("Broken <mrk> element.");
		}
		ts = restoreChars(ts).trim();
		String name = "";
		for (int i = 1; i < ts.length(); i++) {
			if (Character.isSpaceChar(ts.charAt(i))) {
				break;
			}
			name = name + ts.charAt(i);
		}
		String content = "";
		List<XMLNode> nodes = element.getContent();
		Iterator<XMLNode> it = nodes.iterator();
		while (it.hasNext()) {
			XMLNode n = it.next();
			switch (n.getNodeType()) {
			case XMLNode.ELEMENT_NODE:
				Element e = (Element) n;
				String ph = extractText(e);
				content = content + ph;
				break;
			case XMLNode.TEXT_NODE:
				content = content + XMLUtils.cleanText(((TextNode) n).getText());
				break;
			}
		}
		return ts + content + "</" + name + ">"; // TODO recurse content
	}

	private static String restoreChars(String value) {
		value = value.replaceAll(Xml2Xliff.MATHLT, "<");
		value = value.replaceAll(Xml2Xliff.MATHGT, ">");
		value = value.replaceAll(Xml2Xliff.DOUBLEPRIME, "\"");
		value = value.replaceAll(Xml2Xliff.GAMP, "&");
		return value;
	}

	private static String addEntities(String string) {
		string = string.replaceAll("&lt;", "<");
		string = string.replaceAll("&gt;", ">");
		string = string.replaceAll("&quot;", "\"");
		string = string.replaceAll("&amp;", "&");
		return string;
	}

	private static String tidy(String seg) throws SAXException, IOException, ParserConfigurationException {
		startText = "";
		endText = "";
		SAXBuilder b = new SAXBuilder();
		Document d = b.build(new ByteArrayInputStream(seg.getBytes(StandardCharsets.UTF_8)));
		Element r = d.getRootElement();
		Element s = r.getChild("source");
		if (s.getChildren().size() == 0) {
			return seg;
		}
		List<XMLNode> start = new ArrayList<>();
		List<XMLNode> end = new ArrayList<>();
		List<XMLNode> txt = new ArrayList<>();

		List<XMLNode> content = s.getContent();

		Vector<XMLNode> startTags = new Vector<>();
		Vector<XMLNode> endTags = new Vector<>();

		for (int i = 0; i < content.size(); i++) {
			XMLNode n = content.get(i);
			if (n.getNodeType() == XMLNode.TEXT_NODE && !n.toString().trim().equals("")) {
				break;
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				if (!e.getName().equals("ph")) {
					break;
				}
				startTags.add(e);
			}
			start.add(n);
		}

		if (startTags.size() == 0) {
			start.clear();
		}
		for (int i = content.size() - 1; i >= 0; i--) {
			XMLNode n = content.get(i);
			if (n.getNodeType() == XMLNode.TEXT_NODE && !n.toString().trim().equals("")) {
				break;
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e = (Element) n;
				if (!e.getName().equals("ph")) {
					break;
				}
				endTags.add(0, e);
			}
			end.add(0, n);
		}
		if (endTags.size() == 0) {
			end.clear();
		}

		int trimmed = 0;

		if (startTags.size() > 0 && endTags.size() > 0) {
			for (int i = 0; i < startTags.size() && i < endTags.size(); i++) {
				Element f = (Element) startTags.get(i);
				Element l = (Element) endTags.get(endTags.size() - 1 - i);
				if ((l.getText().startsWith("</") && l.getText().endsWith(">"))
						&& (!(f.getText().startsWith("</") || f.getText().endsWith("/>")))) {
					String endTag = l.getText().substring(2);
					endTag = endTag.substring(0, endTag.length() - 1);
					if (f.getText().startsWith("<" + endTag)) {
						// matched
						trimmed++;
					}
				}
			}
		}

		if (trimmed > 0) {
			List<XMLNode> start2 = new ArrayList<>();
			List<XMLNode> end2 = new ArrayList<>();

			int count = 0;
			for (int h = 0; h < start.size(); h++) {
				XMLNode n = start.get(h);
				start2.add(n);
				if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
					count++;
					if (count == trimmed) {
						break;
					}
				}
			}
			start = start2;
			count = 0;
			for (int h = end.size() - 1; h >= 0; h--) {
				XMLNode n = end.get(h);
				end2.add(0, n);
				if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
					count++;
					if (count == trimmed) {
						break;
					}
				}
			}
			end = end2;
		} else {
			if (startTags.size() == 1 && s.getChildren().size() == 1) {
				// send initial tag to skeleton, keep end spaces
				end.clear();
			} else if (s.getChildren().size() == 1 && endTags.size() == 1) {
				// set ending tag to skeleton, keep initial spaces
				start.clear();
			} else {
				start.clear();
				end.clear();
			}
		}

		for (int i = start.size(); i < content.size() - end.size(); i++) {
			txt.add(content.get(i));
		}

		for (int i = 0; i < start.size(); i++) {
			XMLNode n = start.get(i);
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				startText += ((TextNode) n).getText();
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				startText += ((Element) n).getText();
			}
		}
		for (int i = 0; i < end.size(); i++) {
			XMLNode n = end.get(i);
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				endText += ((TextNode) n).getText();
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				endText += ((Element) n).getText();
			}
		}
		s.setContent(txt);
		List<Element> children = s.getChildren("ph");
		for (int id = 0; id < children.size(); id++) {
			Element child = children.get(id);
			child.setAttribute("id", "" + id);
		}

		return r.toString();
	}

	private static boolean containsText(String tagged) {
		int start = tagged.indexOf("<mrk ");
		int end = tagged.indexOf("</mrk>");
		if (dita_based) {
			while (start != -1 && end != -1) {
				tagged = tagged.substring(0, start) + tagged.substring(end + 6);
				start = tagged.indexOf("<mrk ");
				if (start != -1) {
					end = tagged.indexOf("</mrk>", start + 5);
				} else {
					end = -1;
				}
			}
		}
		start = tagged.indexOf("<ph");
		end = tagged.indexOf("</ph>");

		while (start != -1 && end != -1) {
			tagged = tagged.substring(0, start) + tagged.substring(end + 5);
			start = tagged.indexOf("<ph");
			if (start != -1) {
				end = tagged.indexOf("</ph>", start + 4);
			} else {
				end = -1;
			}
		}

		tagged = tagged.trim();
		if (tagged.length() == 0) {
			return false;
		}
		for (int i = 0; i < tagged.length(); i++) {
			int c = tagged.charAt(i);
			if (" \u00A0\r\n\f\t\u2028\u2029,.;\":<>¿?¡!()[]{}=+/*\u00AB\u00BB\u201C\u201D\u201E\uFF00"
					.indexOf(c) == -1) {
				return true;
			}
		}
		return false;
	}

	private static String normalize(String string) {
		string = string.replace('\n', ' ');
		string = string.replace('\t', ' ');
		string = string.replace('\r', ' ');
		string = string.replace('\f', ' ');
		String rs = "";
		int length = string.length();
		for (int i = 0; i < length; i++) {
			char ch = string.charAt(i);
			if (ch != ' ') {
				rs = rs + ch;
			} else {
				rs = rs + ch;
				while (i < (length - 1) && string.charAt(i + 1) == ' ') {
					i++;
				}
			}
		}
		return rs;
	}

	private static String addTags(String src) {
		String result = "";
		int start = src.indexOf('<');
		int end = src.indexOf('>');

		while (start != -1) {
			if (start > 0) {
				result = result + cleanString(src.substring(0, start));
				src = src.substring(start);
				start = src.indexOf('<');
				end = src.indexOf('>');
			}
			String element = src.substring(start, end + 1);
			src = src.substring(end + 1);
			if (dita_based) {
				if (!(element.startsWith("<mrk ") || element.equals("</mrk>"))) {
					result = result + tag(element);
				} else {
					result = result + element;
				}
			} else {
				result = result + tag(element);
			}
			start = src.indexOf('<');
			end = src.indexOf('>');
		}
		result = result + cleanString(src);
		return result;
	}

	private static String makeMrk(String element) {
		return "<mrk mtype=\"protected\" mid=\"" + tagId++ + "\" ts=\"" + clean(element) + "\">";
	}

	private static String clean(String string) {
		string = string.replaceAll("<", MATHLT);
		string = string.replaceAll(">", MATHGT);
		string = string.replaceAll("\"", DOUBLEPRIME);
		return replaceAmp(string);
	}

	private static String replaceAmp(String value) {
		String result = "";
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (c == '&') {
				result = result + GAMP;
			} else {
				result = result + c;
			}
		}
		return result;
	}

	private static String tag(String element) {
		String result = "";
		String type = getType(element);
		if (translatableAttributes.containsKey(type)) {
			result = extractAttributes(type, element);
		} else {
			String ctype = "";
			if (ctypes.containsKey(type)) {
				ctype = " ctype=\"" + ctypes.get(type) + "\"";
			}
			result = "<ph id=\"" + tagId++ + "\"" + ctype + ">" + cleanString(element) + "</ph>";
		}
		return result;
	}

	private static String cleanString(String s) {
		int control = s.indexOf('&');
		while (control != -1) {
			int sc = s.indexOf(";", control);
			if (sc == -1) {
				// no semicolon, it's not an entity
				s = s.substring(0, control) + "&amp;" + s.substring(control + 1);
			} else {
				// may be an entity
				String candidate = s.substring(control, sc) + ";";
				if (validEntitiy(candidate)) {
					if (!candidate.equals("&amp;") && !candidate.equals("&quot;")) {
						String entity = entities.get(candidate);
						if (entity != null) {
							s = s.substring(0, control) + entity + s.substring(sc + 1);
						} else {
							s = s.substring(0, control) + "%%%ph id=\"" + tagId++ + "\"%%%&amp;"
									+ candidate.substring(1) + "%%%/ph%%%" + s.substring(sc + 1);
						}
					} else {
						// it is an "&amp;"
						s = s.substring(0, control) + "&amp;" + s.substring(control + 1);
					}
				} else {
					// treat as an "&amp;"
					s = s.substring(0, control) + "&amp;" + s.substring(control + 1);
				}
			}
			if (control < s.length()) {
				control++;
			}
			control = s.indexOf('&', control);
		}

		control = s.indexOf('<');
		while (control != -1) {
			s = s.substring(0, control) + "&lt;" + s.substring(control + 1);
			if (control < s.length()) {
				control++;
			}
			control = s.indexOf('<', control);
		}

		control = s.indexOf('>');
		while (control != -1) {
			s = s.substring(0, control) + "&gt;" + s.substring(control + 1);
			if (control < s.length()) {
				control++;
			}
			control = s.indexOf('>', control);
		}
		s = s.replaceAll("%%%/ph%%%", "</ph>");
		s = s.replaceAll("%%%ph", "<ph");
		s = s.replaceAll("\"%%%&amp;", "\">&amp;");
		return XMLUtils.validChars(s);
	}

	private static boolean validEntitiy(String candidate) {
		if (candidate.length() < 3) {
			return false;
		}
		char nameStart = candidate.charAt(1);
		// ":" | [A-Z] | "_" | [a-z] | [#xC0-#xD6] | [#xD8-#xF6] | [#xF8-#x2FF] |
		// [#x370-#x37D] | [#x37F-#x1FFF] | [#x200C-#x200D] | [#x2070-#x218F] |
		// [#x2C00-#x2FEF] | [#x3001-#xD7FF] | [#xF900-#xFDCF] | [#xFDF0-#xFFFD] |
		// [#x10000-#xEFFFF]
		if (nameStart == ':' || (nameStart >= 'A' && nameStart <= 'Z') || (nameStart >= 'a' && nameStart <= 'z')
				|| (nameStart >= '\u00C0' && nameStart <= '\u00D6') || (nameStart >= '\u00D8' && nameStart <= '\u00F6')
				|| (nameStart >= '\u00F8' && nameStart <= '\u02FF') || (nameStart >= '\u0370' && nameStart <= '\u037D')
				|| (nameStart >= '\u037F' && nameStart <= '\u1FFF') || (nameStart >= '\u200C' && nameStart <= '\u200D')
				|| (nameStart >= '\u2017' && nameStart <= '\u218F') || (nameStart >= '\u2C00' && nameStart <= '\u2FEF')
				|| (nameStart >= '\u3001' && nameStart <= '\uD7FF') || (nameStart >= '\uF900' && nameStart <= '\uFDCF')
				|| (nameStart >= '\uFDF0' && nameStart <= '\uFFFD')) // not considered [#x10000-#xEFFFF]
		{
			// its OK
		} else {
			return false;
		}
		for (int i = 2; i < candidate.length() - 1; i++) {
			// valid = nameStart | "-" | "." | [0-9] | #xB7 | [#x0300-#x036F] |
			// [#x203F-#x2040]
			char nameChar = candidate.charAt(i);
			if (nameChar == ':' || (nameChar >= 'A' && nameChar <= 'Z') || (nameChar >= 'a' && nameChar <= 'z')
					|| (nameChar >= '\u00C0' && nameChar <= '\u00D6') || (nameChar >= '\u00D8' && nameChar <= '\u00F6')
					|| (nameChar >= '\u00F8' && nameChar <= '\u02FF') || (nameChar >= '\u0370' && nameChar <= '\u037D')
					|| (nameChar >= '\u037F' && nameChar <= '\u1FFF') || (nameChar >= '\u200C' && nameChar <= '\u200D')
					|| (nameChar >= '\u2017' && nameChar <= '\u218F') || (nameChar >= '\u2C00' && nameChar <= '\u2FEF')
					|| (nameChar >= '\u3001' && nameChar <= '\uD7FF') || (nameChar >= '\uF900' && nameChar <= '\uFDCF')
					|| (nameChar >= '\uFDF0' && nameChar <= '\uFFFD') || nameChar == '-'
					|| (nameChar >= '0' && nameChar <= '9') || (nameChar >= '\u0300' && nameChar <= '\u036F')
					|| (nameChar >= '\u203F' && nameChar <= '\u2040')) {
				// its OK
			} else {
				return false;
			}
		}
		return true;
	}

	private static void writeSkeleton(String string) throws IOException {
		skeleton.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private static void writeString(String string) throws IOException {
		output.write(string.getBytes(StandardCharsets.UTF_8));
	}

	private static String extractAttributes(String type, String element) {

		String ctype = "";
		if (ctypes.containsKey(type)) {
			ctype = " ctype=\"" + ctypes.get(type) + "\"";
		}
		String result = "<ph id=\"" + tagId++ + "\"" + ctype + ">";
		element = cleanString(element);

		Vector<String> v = translatableAttributes.get(type);

		StringTokenizer tokenizer = new StringTokenizer(element, "&= \t\n\r\f/", true);

		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			if (!v.contains(token)) {
				result = result + token;
			} else {
				result = result + token;
				String s = tokenizer.nextToken();
				while (s.equals("=") || s.equals(" ")) {
					result = result + s;
					s = tokenizer.nextToken();
				}
				// s contains the first word of the attribute
				if (((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))
						&& s.length() > 1) {
					// the value is one word and it is quoted
					result = result + s.substring(0, 1) + "</ph>" + s.substring(1, s.length() - 1) + "<ph id=\""
							+ tagId++ + "\">" + s.substring(s.length() - 1);
				} else {
					if (s.startsWith("\"") || s.startsWith("'")) {
						// attribute value is quoted, but it has more than one
						// word
						String quote = s.substring(0, 1);
						result = result + s.substring(0, 1) + "</ph>" + s.substring(1);
						s = tokenizer.nextToken();
						do {
							result = result + s;
							if (tokenizer.hasMoreElements()) {
								s = tokenizer.nextToken();
							}
						} while (s.indexOf(quote) == -1);
						String left = s.substring(0, s.indexOf(quote));
						String right = s.substring(s.indexOf(quote));
						result = result + left + "<ph id=\"" + tagId++ + "\">" + right;
					} else {
						// attribute is not quoted, it can only be one word
						result = result + "</ph>" + s + "<ph id=\"" + tagId++ + "\">";
					}
				}
			}
		}
		result = result + "</ph>";
		return result;
	}

	private static void buildTables(String iniFile) throws SAXException, IOException, ParserConfigurationException {
		SAXBuilder builder = new SAXBuilder();
		builder.setEntityResolver(new Catalog(catalog));
		Document doc = builder.build(iniFile);
		Element rt = doc.getRootElement();
		List<Element> tags = rt.getChildren("tag");

		startsSegment = new Hashtable<>();
		translatableAttributes = new Hashtable<>();
		ignore = new Hashtable<>();
		ctypes = new Hashtable<>();
		keepFormating = new Hashtable<>();
		inline = new Hashtable<>();

		Iterator<Element> i = tags.iterator();
		while (i.hasNext()) {
			Element t = i.next();
			if (t.getAttributeValue("hard-break", "inline").equals("yes")
					|| t.getAttributeValue("hard-break", "inline").equals("segment")) {
				startsSegment.put(t.getText(), "yes");
			} else if (t.getAttributeValue("hard-break", "inline").equals("no")
					|| t.getAttributeValue("hard-break", "inline").equals("inline")) {
				inline.put(t.getText(), "yes");
			} else {
				ignore.put(t.getText(), "yes");
			}
			if (t.getAttributeValue("keep-format", "no").equals("yes")) {
				keepFormating.put(t.getText(), "yes");
			}
			String attributes = t.getAttributeValue("attributes", "");
			if (!attributes.equals("")) {
				StringTokenizer tokenizer = new StringTokenizer(attributes, ";");
				int count = tokenizer.countTokens();
				Vector<String> v = new Vector<>(count);
				for (int j = 0; j < count; j++) {
					v.add(tokenizer.nextToken());
				}
				translatableAttributes.put(t.getText(), v);
			}
			String ctype = t.getAttributeValue("ctype", "");
			if (!ctype.equals("")) {
				ctypes.put(t.getText(), ctype);
			}
		}
	}

	private static void buildList() throws SAXException, IOException {
		segments = new ArrayList<>();
		text = "";
		parseNode(root);
		segments.add(text);
	}

	private static void parseNode(XMLNode n) throws SAXException, IOException {
		switch (n.getNodeType()) {
		case XMLNode.ATTRIBUTE_NODE:
			throw new SAXException("Parsed undeclared attribute node." + n);
		case XMLNode.CDATA_SECTION_NODE:
			String name = stack.peek();
			if (startsSegment.containsKey(name)) {
				segments.add(text);
				segments.add("" + '\u007F' + '\u007F' + "<![CDATA[");
				CData data = (CData) n;
				segments.add("" + '\u0081' + data.getData());
				segments.add("" + '\u007F' + '\u007F' + "]]>");
			} else {
				segments.add(text);
				segments.add("" + '\u007F' + '\u007F' + n.toString());
			}
			translatable = "";
			text = "";
			break;
		case XMLNode.COMMENT_NODE:
			segments.add(text);
			if (translateComments) {
				segments.add("" + '\u007F' + '\u007F' + "<!--");
				segments.add(((Comment) n).getText());
				segments.add("" + '\u007F' + '\u007F' + "-->");
			} else {
				segments.add("" + '\u007F' + '\u007F' + n.toString());
			}
			translatable = "";
			text = "";
			break;
		case XMLNode.ELEMENT_NODE:
			Element e = (Element) n;
			if (dita_based && !isKnownElement(e.getName())) {
				configureElement(e);
			}
			if (dita_based && e.getAttributeValue("translate", "yes").equals("no")) {

				if (startsSegment.containsKey(e.getName())) {
					// treat as element to ignore, send to skeleton
					segments.add(text);
					if (e.getAttributeValue("removeTranslate", "no").equals("yes")) {
						e.removeAttribute("translate");
					}
					segments.add("" + '\u007F' + "" + '\u007F' + e.toString());
					text = "";
					translatable = "";
					stack = null;
					stack = new Stack<>();
					return;
				}

				removeComments(e);

				text = text + STARTG;
				text = text + "<" + e.getName();
				List<Attribute> attributes = e.getAttributes();
				for (int i = 0; i < attributes.size(); i++) {
					Attribute a = attributes.get(i);
					text = text + " " + a.getName() + "=\"" + cleanAttribute(a.getValue()) + "\"";
				}
				text = text + ">" + STARTG;
				List<XMLNode> content = e.getContent();
				for (int i = 0; i < content.size(); i++) {
					XMLNode node = content.get(i);
					if (node.getNodeType() == XMLNode.TEXT_NODE) {
						TextNode tn = (TextNode) node;
						text = text + cleanString(tn.getText());
					} else {
						Element el = (Element) node;
						text = text + el.toString();
					}
				}
				text = text + ENDG + "</" + e.getName() + ">" + ENDG;

				return;
			}
			if (dita_based && e.getAttributeValue("fluentaIgnore", "no").equals("yes")) {
				e.removeAttribute("fluentaIgnore");
				segments.add(text);
				segments.add("" + '\u007F' + "" + '\u007F' + e.toString());
				text = "";
				translatable = "";
				stack = null;
				stack = new Stack<>();
				return;
			}
			if (startsSegment.containsKey(e.getName())) {
				segments.add(text);
				text = "";
				translatable = "";
				stack = null;
				stack = new Stack<>();
				stack.push(e.getName());
				if (!keepFormating.containsKey(e.getName())
						&& !e.getAttributeValue("xml:space", "default").equals("preserve")) {
					normalizeElement(e);
				}
			}
			if (ignore.containsKey(e.getName())) {
				segments.add(text);
				segments.add("" + '\u007F' + "" + '\u007F' + e.toString());
				text = "";
				translatable = "";
				stack = null;
				stack = new Stack<>();
				return;
			}
			if (stack.size() == 0 && e.getChildren().size() == 0 && !translatableAttributes.containsKey(e.getName())) {
				if (inline.containsKey(e.getName()) && !e.getText().equals("")) {
					if (text.startsWith('\u007F' + "" + '\u007F')) {
						segments.add(text);
						text = "";
						translatable = "";
						stack = null;
						stack = new Stack<>();
					}
					stack.push(e.getName());
					if (!keepFormating.containsKey(e.getName())
							&& !e.getAttributeValue("xml:space", "default").equals("preserve")) {
						normalizeElement(e);
					}
				} else {
					segments.add(text);
					text = "";
					translatable = "";
					segments.add('\u007F' + "" + '\u007F' + e.toString());
					break;
				}
			}
			if (stack.size() > 0 && !startsSegment.containsKey(e.getName())) {
				stack.push(e.getName());
			}
			List<Attribute> attributes = e.getAttributes();
			text = text + "<" + e.getName();
			if (attributes.size() > 0) {
				for (int i = 0; i < attributes.size(); i++) {
					Attribute a = attributes.get(i);
					text = text + " " + a.getName() + "=\"" + cleanAttribute(a.getValue()) + "\"";
				}
			}
			List<XMLNode> content = e.getContent();
			if (content.size() == 0) {
				if (text.equals("")) {
					text = "" + '\u007F' + '\u007F' + "/>";
				} else {
					text = text + "/>";
				}
			} else {
				if (!inline.containsKey(e.getName())) {
					if (!text.equals("")) {
						segments.add(text + ">");
						text = "";
					} else {
						segments.add("" + '\u007F' + '\u007F' + ">");
					}
					translatable = "";
				} else {
					if (!text.equals("")) {
						text = text + ">";
					} else {
						segments.add("" + '\u007F' + '\u007F' + ">");
						translatable = "";
					}
				}
				for (int i = 0; i < content.size(); i++) {
					parseNode(content.get(i));
				}
				if (startsSegment.containsKey(e.getName())) {
					segments.add(text);
					text = "";
					translatable = "";
				}
				if (!text.equals("")) {
					text = text + "</" + e.getName() + ">";
				} else {
					segments.add("" + '\u007F' + '\u007F' + "</" + e.getName() + ">");
				}
			}
			if (stack.size() > 0) {
				stack.pop();
			}

			break;
		case XMLNode.PROCESSING_INSTRUCTION_NODE:
			if (inDesign && !translatable.trim().equals("")) {
				text = text + n.toString();
			} else {
				segments.add(text);
				segments.add("" + '\u007F' + '\u007F' + n.toString());
				text = "";
				translatable = "";
			}
			break;
		case XMLNode.TEXT_NODE:
			String value = ((TextNode) n).getText();
			//
			// Don't enable replacement of "&". Replacement of "<" and ">" is needed because
			// otherwise tag
			// handling will fail (it searches for initial "<" and closing ">"
			//
			// value = value.replaceAll("&","&amp;");
			value = value.replaceAll("<", "&lt;");
			value = value.replaceAll(">", "&gt;");

			text = text + value;
			if (value.trim().length() > 0) {
				translatable = translatable + value;
			}
			if (value.trim().length() > 0 && text.startsWith("" + '\u007F' + '\u007F')) {
				for (int j = 0; j < stack.size(); j++) {
					if (startsSegment.containsKey(stack.get(j))) {
						text = text.substring(2);
						break;
					}
				}
			}

			break;
		}
	}

	private static void configureElement(Element e) {
		String cls = e.getAttributeValue("class", "");
		String[] parts = cls.split("\\s");
		for (int h = 0; h < parts.length; h++) {
			String part = parts[h];
			if (part.indexOf('/') == -1) {
				continue;
			}
			String ancestor = part.substring(part.indexOf('/') + 1).trim();
			if (isKnownElement(ancestor)) {
				if (startsSegment.containsKey(ancestor)) {
					startsSegment.put(e.getName(), startsSegment.get(ancestor));
				}
				if (inline.contains(ancestor)) {
					inline.put(e.getName(), inline.get(ancestor));
				}
				if (ignore.containsKey(ancestor)) {
					ignore.put(e.getName(), ignore.get(ancestor));
				}
				if (keepFormating.containsKey(ancestor)) {
					keepFormating.put(e.getName(), keepFormating.get(ancestor));
				}
				if (translatableAttributes.containsKey(ancestor)) {
					translatableAttributes.put(e.getName(), translatableAttributes.get(ancestor));
				}
				if (ctypes.containsKey(ancestor)) {
					ctypes.put(e.getName(), ctypes.get(ancestor));
				}
				return;
			}
		}
		LOGGER.log(Level.WARNING, "Unknown element: " + e.getName());
	}

	private static boolean isKnownElement(String name) {
		if (startsSegment.containsKey(name)) {
			return true;
		}
		if (inline.containsKey(name)) {
			return true;
		}
		if (ignore.containsKey(name)) {
			return true;
		}
		return false;
	}

	private static void removeComments(Element e) {
		List<XMLNode> content = new ArrayList<>();
		List<XMLNode> list = e.getContent();
		Iterator<XMLNode> it = list.iterator();
		while (it.hasNext()) {
			XMLNode n = it.next();
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				content.add(n);
			}
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				content.add(n);
			}
		}
		e.setContent(content);
	}

	private static void normalizeElement(Element e) {
		List<XMLNode> l = e.getContent();
		Iterator<XMLNode> i = l.iterator();
		List<XMLNode> normal = new Vector<>();
		while (i.hasNext()) {
			XMLNode n = i.next();
			if (n.getNodeType() == XMLNode.TEXT_NODE) {
				String value = ((TextNode) n).getText();
				value = normalize(value);
				((TextNode) n).setText(value);
			}
			if (n.getNodeType() == XMLNode.ELEMENT_NODE) {
				Element e1 = (Element) n;
				if (!keepFormating.containsKey(e1.getName())
						&& !e1.getAttributeValue("xml:space", "default").equals("preserve")) {
					normalizeElement((Element) n);
				}
			}
			normal.add(n);
		}
		e.setContent(normal);
	}

	/**
	 * @param value
	 * @return
	 */
	private static String cleanAttribute(String value) {
		if (stack.size() > 1 && !text.startsWith("" + '\u007F' + '\u007F')) {
			// this is an inline element and will be placed in <ph>
			value = value.replaceAll("&", "###AMP###");
		} else {
			value = value.replaceAll("&", "&amp;");
		}
		value = value.replaceAll(">", "&gt;");
		value = value.replaceAll("<", "&lt;");
		value = value.replaceAll("\"", "&quot;");
		return value;
	}

	private static String getType(String string) {

		String result = "";
		if (string.startsWith("<![CDATA[")) {

			return "![CDATA[";
		}
		if (string.startsWith("<!--")) {

			return "!--";
		}
		if (string.startsWith("<?")) {

			return "?";
		}

		if (!string.equals("")) {
			// skip initial "<"
			string = string.substring(1);
		}

		for (int i = 0; i < string.length(); i++) {
			char c = string.charAt(i);
			if (c == ' ' || c == '\n' || c == '\f' || c == '\t' || c == '\r' || c == '>') {
				break;
			}
			result = result + c;
		}
		if (result.endsWith("/") && result.length() > 1) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	public static String getEncoding(String fileName) throws IOException {
		// check if there is a BOM (byte order mark)
		// at the start of the document
		byte[] array = new byte[2];
		try (FileInputStream inputStream = new FileInputStream(fileName)) {
			if (inputStream.read(array) == -1) {
				throw new IOException("Premature end of file");
			}
		}
		byte[] lt = "<".getBytes();
		byte[] feff = { -1, -2 };
		byte[] fffe = { -2, -1 };
		if (array[0] != lt[0]) {
			// there is a BOM, now check the order
			if (array[0] == fffe[0] && array[1] == fffe[1]) {
				return StandardCharsets.UTF_16BE.name();
			}
			if (array[0] == feff[0] && array[1] == feff[1]) {
				return StandardCharsets.UTF_16LE.name();
			}
		}
		// check declared encoding
		// return UTF-8 as default
		String result = StandardCharsets.UTF_8.name();
		String line = "";
		try (FileReader in = new FileReader(fileName)) {
			BufferedReader buffer = new BufferedReader(in);
			line = buffer.readLine();
		}
		if (line.startsWith("<?")) {
			line = line.substring(2, line.indexOf("?>"));
			line = line.replaceAll("\'", "\"");
			StringTokenizer tokenizer = new StringTokenizer(line);
			while (tokenizer.hasMoreTokens()) {
				String token = tokenizer.nextToken();
				if (token.startsWith("encoding")) {
					result = token.substring(token.indexOf('\"') + 1, token.lastIndexOf('\"'));
				}
			}
		}
		return result;
	}

}
