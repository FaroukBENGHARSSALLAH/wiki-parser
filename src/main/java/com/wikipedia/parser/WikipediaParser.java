package com.wikipedia.parser;

import java.io.StringReader;
import java.io.StringWriter;

import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;

import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import com.wikipedia.model.Article;

import net.java.textilej.parser.MarkupParser;
import net.java.textilej.parser.builder.HtmlDocumentBuilder;
import net.java.textilej.parser.markup.mediawiki.MediaWikiDialect;



public class WikipediaParser {

	 @SuppressWarnings("deprecation")
	public static void main(String[] args){

		           // just download 'winutils.exe' in attached 'winutils' folder
		          //  and create a folder 'C:' like this 'C:/Hadoop/bin' and put it under 
		    System.setProperty("hadoop.home.dir", "C:\\Hadoop\\");
		    
		        // Spark configuration, setting application name and master node "local" i.e. embedded mode
	        SparkConf lSparkConfiguration = new SparkConf().setMaster("local[2]").setAppName("Wikipedia Parser");
	        
	           // Java Spark context as it used in Java environment
	        JavaSparkContext lJavaSparkContext = new JavaSparkContext(lSparkConfiguration);
	        
	           // Spark SQL context for SQL APIs
	        SQLContext lSQLContext = new SQLContext(lJavaSparkContext);
	                
	           // schema used to parse the XML file 
	        StructType wikipediaXMLschema = new StructType(new StructField[] {
			        	    new StructField("id", DataTypes.IntegerType, true, Metadata.empty()),
			        	    new StructField("title", DataTypes.StringType, true, Metadata.empty()),
			        	    new StructField("revision", DataTypes.createStructType(new StructField[] {
			        	    		   new StructField("text", DataTypes.StringType, true, Metadata.empty()),         
			        	    }), true, Metadata.empty())
	        	  });
	         
	            // loading the XML file in a Spark data frame 
	        DataFrame dataFrame = lSQLContext.read()
	        		                         .format("com.databricks.spark.xml")
	        		                         .option("rowTag", "page")
	        		                         .schema(wikipediaXMLschema)
	        		                         .load("simplewiki-20161020-pages-articles.xml");
	        
	           // using an article table for SQL operations purpose
	        dataFrame.registerTempTable("article");
	        
	        
	              // cleaning and updating data frame 
	        @SuppressWarnings("serial")
			DataFrame cleanedDataFrame = lSQLContext.createDataFrame(
	        		      lSQLContext.sql("select id, title, revision.text as body from article where id is not null")
	        		                 .javaRDD()
	        		                 .map(new Function<Row, Article>(){
									        	  public Article call(Row row){
								    		        try{
										      		           Article article = new Article();
										      		           article.setId(row.getInt(0));
										      		           article.setTitle(row.getString(1));
										      		                 // formatting artcicle's body in HTML format 
										      		           article.setBody(markupBody(row.getString(2)));
										      		           return article;
										    		                 }
								    		        catch(Exception e){}
								    		                         // save the row with a negative ID, to be deleted from table 
								    		              Double id = (Math.random()*100 + Math.random()*100 + Math.random()*100 + Math.random()*100) * -1;
								    		              return new Article(id.intValue(), "NULL VALUE", "NULL VALUE");
								    	                    } 
									        	     }
      		                               ), Article.class
	        		                  );
	 
	        
	           // generate a single CSV file
	        cleanedDataFrame.coalesce(1)
	                         .write()
	                         .format("com.databricks.spark.csv")
	                         .option("header", "true")
	                         .save("E:/Downloads/24 season 1/newcarsm.csv");
	         
	         String databseURL = "jdbc:mysql:''//:3306/''?sessionVariables=sql_mode='NO_ENGINE_SUBSTITUTION'&jdbcCompliantTruncation=false&user=''&password=''";
	        	   
	           // inserting articles in the database  
	         cleanedDataFrame.insertIntoJDBC(databseURL, "article", false);
	    }
	 
	
	           // format body in HTML format
	private static String markupBody(String body) {
		                        try {
										StringWriter writer = new StringWriter();
								        HtmlDocumentBuilder builder = new HtmlDocumentBuilder(writer);
								        builder.setEmitAsDocument(false);
								        MarkupParser parser = new MarkupParser(new MediaWikiDialect());
								        parser.setBuilder(builder);
								        parser.parse(body);
								        final String formatted_body = writer.toString();
								        final StringBuilder cleaned = new StringBuilder();
								        HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
								                public void handleText(char[] data, int pos) {
								                            cleaned.append(new String(data)).append(' ');
								                          }
								                 };
									    new ParserDelegator().parse(new StringReader(formatted_body), callback, false);
									    
									            // updating articles references links
						                return formatted_body.replace("href=\"/wiki/", "href=\"#/article/");
		                            }
		                        catch(Exception e){}
		                        return null;
	             }

      }