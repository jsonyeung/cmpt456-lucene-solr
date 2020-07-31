package org.apache.lucene.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Date;

import org.apache.lucene.benchmark.byTask.feeds.DemoHTMLParser;
import org.apache.lucene.benchmark.byTask.feeds.DocData;
import org.apache.lucene.benchmark.byTask.feeds.TrecContentSource;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class HtmlIndexFiles {
  private HtmlIndexFiles() {}

  /* Index all text files under a directory. */
  public static void main(String[] args) {
    String indexPath = "index";
    String docsPath = null;
    boolean create = true;
    String usage = "java org.apache.lucene.demo.HtmlIndexFiles"
                 + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
                 + "This indexes HTML documents in DOCS_PATH, creating a Lucene index"
                 + "in INDEX_PATH that can be searched with SearchFiles";

    for (int i=0; i<args.length; i++) {
      if ("-index".equals(args[i])) { indexPath = args[i+1]; i++; } 
      else if ("-docs".equals(args[i])) { docsPath = args[i+1]; i++; } 
      else if ("-update".equals(args[i])) { create = false; }
    }

    if (docsPath == null) {
      System.err.println("Usage: " + usage);
      System.exit(1);
    }

    final Path docDir = Paths.get(docsPath);
    if (!Files.isReadable(docDir)) {
      System.out.println("Document directory '" + docDir.toAbsolutePath() + "' does not exist or is not readable, please check the path");
      System.exit(1);
    }
    
    Date start = new Date();
    try {
      System.out.println("Indexing to directory '" + indexPath + "'...");

      Directory dir = FSDirectory.open(Paths.get(indexPath));
      Analyzer analyzer = new StandardAnalyzer(); // new CMPT456Analyzer();
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

      if (create) { iwc.setOpenMode(OpenMode.CREATE); } 
      else { iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);}

      // initiate indexing for documents
      IndexWriter writer = new IndexWriter(dir, iwc);
      indexDocs(writer, docDir);
      writer.close();

      Date end = new Date();
      System.out.println(end.getTime() - start.getTime() + " total milliseconds");

    } catch (IOException e) {
      System.out.println(" caught a " + e.getClass() +
       "\n with message: " + e.getMessage());
    }
  }

  /* Index a collection of documents */
  static void indexDocs(final IndexWriter writer, Path path) throws IOException {
    if (Files.isDirectory(path)) {
      Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          // index each indiviual doc
          try { indexDoc(writer, file, attrs.lastModifiedTime().toMillis());
          } catch (IOException ignore) {}
          return FileVisitResult.CONTINUE;
        }
      });

    } else {
      indexDoc(writer, path, Files.getLastModifiedTime(path).toMillis());
    }
  }

  /* Index a single document */
  static void indexDoc(IndexWriter writer, Path file, long lastModified) throws IOException {
    try (InputStream stream = Files.newInputStream(file)) {
      DemoHTMLParser htmlParser = new DemoHTMLParser();
      
      // parse html document
      DocData htmlRes = htmlParser.parse(
        new DocData(), "",
        new Date(lastModified), 
        new InputStreamReader(stream),
        new TrecContentSource()
      );

      // make a new, empty document
      Document doc = new Document();
      doc.add(new StringField("path", file.toString(), Field.Store.YES));
      doc.add(new LongPoint("modified", lastModified));

      // insert parsed html data as content
      doc.add(new TextField("contents", new StringReader(
        htmlRes.getTitle() + '\n' + htmlRes.getBody()
      )));

      if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
        // new index, so we just add the document (no old document can be there):
        System.out.println("adding " + file);
        writer.addDocument(doc);
        
      } else {
        // existing index (an old copy of this document may have been indexed) so 
        // we use updateDocument instead to replace the old one matching the exact 
        // path, if present:
        System.out.println("updating " + file);
        writer.updateDocument(new Term("path", file.toString()), doc);
      }
    }
  }
}