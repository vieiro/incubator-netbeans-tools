/*
 * Copyright 2018 antonio.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package wikimedia.html.conversion;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.mylyn.wikitext.mediawiki.MediaWikiLanguage;
import org.eclipse.mylyn.wikitext.parser.HtmlParser;
import org.eclipse.mylyn.wikitext.parser.MarkupParser;
import org.xml.sax.InputSource;

/**
 *
 * @author Antonio Vieiro <vieiro@apache.org>
 */
public class HTMLConverter {

    private static final String APACHE_LICENSE_HEADER = ""
            + "\n"
            + "    Licensed to the Apache Software Foundation (ASF) under one\n"
            + "    or more contributor license agreements.  See the NOTICE file\n"
            + "    distributed with this work for additional information\n"
            + "    regarding copyright ownership.  The ASF licenses this file\n"
            + "    to you under the Apache License, Version 2.0 (the\n"
            + "    \"License\"); you may not use this file except in compliance\n"
            + "    with the License.  You may obtain a copy of the License at\n"
            + "\n"
            + "      http://www.apache.org/licenses/LICENSE-2.0\n"
            + "\n"
            + "    Unless required by applicable law or agreed to in writing,\n"
            + "    software distributed under the License is distributed on an\n"
            + "    \"AS IS\" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY\n"
            + "    KIND, either express or implied.  See the License for the\n"
            + "    specific language governing permissions and limitations\n"
            + "    under the License.\n\n";

    private static final Logger LOG = Logger.getLogger(Converter.class.getName());
    private static final FileFilter HTML_FILES = (file) -> {
        return file.isDirectory() || (file.isFile() && file.getName().endsWith(".html"));
    };

    private static String asciidocHeader = null;

    private static String getAsciidocHeader() {
        if (asciidocHeader == null) {
            StringBuilder sb = new StringBuilder();
            String[] lines = APACHE_LICENSE_HEADER.split("\n");
            for (String line : lines) {
                sb.append("// ").append(line).append("\n");
            }
            sb.append("//\n\n");
            asciidocHeader = sb.toString();
        }
        return asciidocHeader;
    }
    private static ThreadLocal<SimpleDateFormat> sdf = null;

    private static final synchronized SimpleDateFormat getSimpleDateFormat() {
        if (sdf == null) {
            SimpleDateFormat d = new SimpleDateFormat("yyyy-MM-dd");
            sdf = new ThreadLocal<>();
            sdf.set(d);
        }
        return sdf.get();
    }

    private static void convertHTMLToAsciidoc(File html_file, File asciidoc) throws Exception {
        // Convert *.wikimedia to asciidoc
        try (BufferedWriter output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(asciidoc), "utf-8"), 16 * 1024);
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(html_file), "utf-8"))) {
            output.write(getAsciidocHeader());
            String title = html_file.getName().replace(".html", "");
            output.write("= " + title + "\n");
            output.write(":jbake-type: page\n");
            output.write(":jbake-tags: oldsite, needsreview\n");
            output.write(":jbake-status: published\n");
            output.write(":keywords: Apache NetBeans  " + title + "\n");
            output.write(":description: Apache NetBeans  " + title + "\n");
            output.write(":toc: left\n");
            output.write(":toc-title:\n");
            output.write("\n");
            CustomAsciiDocDocumentBuilder asciidocBuilder = new CustomAsciiDocDocumentBuilder(1, output);
            asciidocBuilder.setImagesRootDirectory(new File("/home/antonio/WORK/REPOSITORIES/incubator-netbeans-website-cleanup/src/content/"));
            asciidocBuilder.setOutputDirectory(asciidoc.getParentFile());
            InputSource source = new InputSource(reader);
            HtmlParser.instanceWithHtmlCleanupRules().parse(source, asciidocBuilder);
            output.write("\n");
            output.write("NOTE: This document was automatically converted to the AsciiDoc format on " + getSimpleDateFormat().format(new Date()) + ", and needs to be reviewed.\n");
        }
    }

    private static void convert(File src, File dest) throws Exception {
        LOG.log(Level.INFO, "Converting from {0} to {1}", new Object[]{src.getAbsolutePath(), dest.getAbsolutePath()});
        File[] html_files = src.listFiles(HTML_FILES);
        if (html_files == null || html_files.length == 0) {
            LOG.log(Level.WARNING, "No *.html files found in {0}", src.getAbsolutePath());
        } else {
            int fileCount = 0;
            for (File html_file : html_files) {
                if (html_file.isDirectory()) {
                    File newDest = new File(dest, html_file.getName());
                    newDest.mkdirs();
                    convert(html_file, newDest);
                } else {
                    File asciidoc = new File(dest, html_file.getName().replaceAll("\\.html", ".asciidoc"));
                    convertHTMLToAsciidoc(html_file, asciidoc);
                    fileCount++;
                }
            }
            LOG.log(Level.INFO, "Converted {0} files.", fileCount);
        }
    }

    private static final void checkDirectory(File dir) throws Exception {
        String error = null;
        if (!dir.exists()) {
            error = dir.getAbsolutePath() + " does not exist.";
        }
        if (error == null && !dir.isDirectory()) {
            error = dir.getAbsolutePath() + " is not a directory.";
        }
        if (error == null && !dir.canRead()) {
            error = dir.getAbsolutePath() + " is not readable.";
        }
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }

    private static void usage() {
        System.err.println("Use: java " + HTMLConverter.class.getName() + " inputDirectory outputDirectory");
        System.err.println("  Converts HTML to asciidoc.");
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            usage();
        }
        File src = new File(args[0]);
        checkDirectory(src);

        File dest = new File(args[1]);
        if (!dest.exists()) {
            if (!dest.mkdirs()) {
                throw new IllegalStateException("Cannot create directory " + dest.getAbsolutePath());
            }
        }
        checkDirectory(dest);
        if (!dest.canWrite()) {
            throw new IllegalStateException("Cannot write to " + dest.getAbsolutePath());
        }

        convert(src, dest);

    }

}
