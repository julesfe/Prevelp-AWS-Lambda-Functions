package de.julesfehr.prevelp.setpermissions;

import java.net.URLDecoder;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AccessControlList;
import com.amazonaws.services.s3.model.GroupGrantee;
import com.amazonaws.services.s3.model.Permission;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class SetPermissions implements RequestHandler<SNSEvent, String> {
  
    AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
    
    public String handleRequest(SNSEvent event, Context context) {
        try {
         	
        	// Get the SNS notification record.
        	String record = event.getRecords().get(0).getSNS().getMessage();
            
        	// Read the Json in a Json object to extract the bucket and key
        	JsonElement jsonElement = new JsonParser().parse(record);
        	JsonObject 	jsonObject	= jsonElement.getAsJsonObject();
        	JsonArray	jsonArray 	= jsonObject.get("Records").getAsJsonArray();
        				jsonObject	= jsonArray.get(0).getAsJsonObject().get("s3").getAsJsonObject();
 
            // Extract the bucket from the bucket json object.
        	// Bucket name will have quotes when extracted from the json object whicht will lead to it
        	// not being recognized as a valid bucket name. 
            String bucket = jsonObject.get("bucket").getAsJsonObject().get("name").toString().replace("\"", "");

            // Extract the key from the json obect.
            // Object key may have spaces or unicode non-ASCII characters or quotes..
            String key = jsonObject.get("object").getAsJsonObject().get("key").toString().replace("\"", "");
            // URLDecoder may be redundant because the key should have been decoded in the ConvertImage function.
            key = URLDecoder.decode(key, "UTF-8");
            
            // Set the right ACL (make the file publicly accessible).
            AccessControlList acl = s3Client.getObjectAcl(bucket, key);
          
            // TODO later: add permissions only for the right users (customer should only see his pictures, not all)
            // Example: acl.grantPermission(new EmailAddressGrantee("fehr.jules@gmail.com"), Permission.Read);
            // Should be a variable instead of fixed email.
            acl.grantPermission(GroupGrantee.AllUsers, Permission.Read);
            s3Client.setObjectAcl(bucket, key, acl);
            
            return "ok";
        } catch (Exception e) {
        	e.printStackTrace();
        	throw new RuntimeException();
		}
    }
}