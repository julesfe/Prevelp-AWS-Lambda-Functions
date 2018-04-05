package de.julesfehr.prevelp.convertimage;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.event.S3EventNotification.S3EventNotificationRecord;
import com.amazonaws.services.s3.model.ObjectMetadata;

public class ConvertImage implements RequestHandler<S3Event, String> {
    private final String JPG_TYPE = (String) "jpg";
    private final String JPG_MIME = (String) "image/jpeg";
    private final String PNG_TYPE = (String) "png";
    private final String PNG_MIME = (String) "image/png";
  
    AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();

    public String handleRequest(S3Event s3event, Context context) {
        try {
         	// Get the S3 notification record.
            S3EventNotificationRecord record = s3event.getRecords().get(0);
            
            // Extract the source bucket from the record.
            String srcBucket = record.getS3().getBucket().getName();
            // Object key may have spaces or unicode non-ASCII characters.
            String srcKey = record.getS3().getObject().getKey().replace('+', ' ');
            srcKey = URLDecoder.decode(srcKey, "UTF-8");


            // Specify bucket for the converted image.
            String dstBucket = "prevelp-image-converted";
            // Key is the name of the file.
            String dstKey = "converted-" + srcKey;

            // Sanity check: validate that source and destination are different buckets.
            if (srcBucket.equals(dstBucket)) {
                context.getLogger().log("Destination bucket must not match source bucket.");
                return "";
            }

            // Infer the image type.
            Matcher matcher = Pattern.compile(".*\\.([^\\.]*)").matcher(srcKey);
            if (!matcher.matches()) {
            	context.getLogger().log("Unable to infer image type for key " + srcKey);
                return "";
            }
            String imageType = matcher.group(1);
            if (!(JPG_TYPE.equals(imageType)) && !(PNG_TYPE.equals(imageType))) {
            	context.getLogger().log("Skipping non-image " + srcKey);
                return "";
            }

            // Download the image from S3 into a stream.
        	InputStream objectData = s3Client.getObject(srcBucket, srcKey).getObjectContent();
            
            // Read the source image.
        	BufferedImage srcImage = ImageIO.read(objectData);
            
        	// Reduce the image quality.
            reduceQuality(srcImage, imageType, srcKey);
           
            // Add Watermark.
            addTextWatermark("test", srcImage);

            // Re-encode image to target format.
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            ImageIO.write(srcImage, imageType, os);
            InputStream is = new ByteArrayInputStream(os.toByteArray());
            
            // Set Content-Length and Content-Type.
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(os.size());
            if (JPG_TYPE.equals(imageType)) {
                meta.setContentType(JPG_MIME);
            }
            if (PNG_TYPE.equals(imageType)) {
                meta.setContentType(PNG_MIME);
            }
            
            // Uploading to S3 destination bucket.
            context.getLogger().log("Writing to: " + dstBucket + "/" + dstKey);
            s3Client.putObject(dstBucket, dstKey, is, meta);
            context.getLogger().log("Successfully converted " + srcBucket + "/" + srcKey + " and uploaded to " + dstBucket + "/" + dstKey);
            return "Ok";
        } catch (Exception e) {
        	e.printStackTrace();
        	throw new RuntimeException();
        }
    }
    
    /**
     * Embeds a textual watermark over a source image to produce
     * a watermarked one.
     * @param text The text to be embedded as watermark.
     * @param srcImage The source image file.
     */
    static BufferedImage addTextWatermark(String text, BufferedImage srcImage) {
        Graphics2D g2d = (Graphics2D) srcImage.getGraphics();
    
		// Initializes necessary graphic properties.
		AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);
		g2d.setComposite(alphaChannel);
		g2d.setColor(Color.BLUE);
		g2d.setFont(new Font("Arial", Font.BOLD, 64));
		FontMetrics fontMetrics = g2d.getFontMetrics();
		Rectangle2D rect = fontMetrics.getStringBounds(text, g2d);
    
		// Calculates the coordinates where the String is painted.
		int centerX = (srcImage.getWidth() - (int) rect.getWidth()) / 2;
		int centerY = srcImage.getHeight() / 2;
    
		// Paints the textual watermark.
		g2d.drawString(text, centerX, centerY);
    
		g2d.dispose();
		return srcImage;
    }
    
    static BufferedImage reduceQuality(BufferedImage srcImage, String imageType, String srcKey) throws FileNotFoundException, IOException {
    	ImageWriter imageWriter = ImageIO.getImageWritersByFormatName(imageType).next();
    	ImageWriteParam writeParam = imageWriter.getDefaultWriteParam();
    	
    	// This call is needed in order to explicitly set the compression level.
    	writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    	
    	// 1.0f is max quality, min compression; 0.0f is min qual, max comp.
    	writeParam.setCompressionQuality(0.1f);
    	
    	ByteArrayOutputStream os = new ByteArrayOutputStream();
		ImageOutputStream ios = ImageIO.createImageOutputStream(os);
    	imageWriter.setOutput(ios);
		imageWriter.write(null, new IIOImage(srcImage, null, null), writeParam);
    	imageWriter.dispose();
    	InputStream is = new ByteArrayInputStream(os.toByteArray());
    	srcImage = ImageIO.read(is);
    	return srcImage;
    }
}
