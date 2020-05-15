package io.jenkins.plugins;

import com.google.common.base.Strings;
import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.*;
import hudson.model.AbstractProject;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.Secret;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.http.entity.StringEntity;
import org.kohsuke.stapler.DataBoundConstructor;


public class PreflightBuilder extends Builder implements SimpleBuildStep {
    private static final String API_TOKEN_URL = "https://auth.preflight.com/connect/token";
    private static final String API_RUN_URL="https://api.preflight.com/v1/Tests";
    private static final String DISPLAY_NAME = "Run PreFlight Test";
    
    private final String clientId;
    private final Secret clientSecret;
    private final String testId;
    private final String groupId;
    private final String environmentId;
    private final String platforms;
    private final String sizes;
    private final boolean captureScreenshots;
    private final boolean waitResults;
    PrintStream logger;

    public String getClientId() {
        return clientId;
    }
    public Secret getClientSecret() {
        return clientSecret;
    }
    public String getTestId() {
        return testId;
    }
    public String getGroupId() {
        return groupId;
    }
    public String getEnvironmentId() {
        return environmentId;
    }
    public String getPlatforms() {
        return platforms;
    }
    public String getSizes() {
        return sizes;
    }
    public boolean getCaptureScreenshots() {
        return captureScreenshots;
    }
    public boolean getWaitResults() {
        return waitResults;
    }
    
    @DataBoundConstructor
    public PreflightBuilder(String clientId, String clientSecret, String testId, String groupId, String environmentId, String platforms, String sizes, boolean captureScreenshots, boolean waitResults) {
        this.clientId = clientId;
        this.clientSecret = Secret.fromString(clientSecret);
        this.testId = testId;
        this.groupId = groupId;
        this.environmentId = environmentId;
        this.platforms = platforms;
        this.sizes = sizes;
        this.captureScreenshots = captureScreenshots;
        this.waitResults = waitResults;
    }
    
    @Override
    public void perform(Run<?, ?> build, @Nonnull FilePath workspace, @Nonnull Launcher launcher, TaskListener listener) throws InterruptedException, IOException {
      logger = listener.getLogger();

      logger.println(DISPLAY_NAME);
      logger.println("Client Id: " + clientId);
      logger.println("Test Id: " + testId);
      logger.println("Group Id: " + groupId);
      logger.println("Environment Id: " + environmentId);
      logger.println("Platforms: " + platforms);
      logger.println("Sizes: " + sizes);
      logger.println("Capture Screenshots: " + captureScreenshots);
      logger.println("Wait Results: " + waitResults);

      try {
        boolean result = Execute();
        if (!result) {
          build.setResult(Result.FAILURE);
        }
      } catch (Exception e) {
        logger.println("Exception:" + e.toString());
        build.setResult(Result.FAILURE);
      }
    }
    
    public boolean Execute() throws Exception {
        String token = GetToken();
        if (Strings.isNullOrEmpty(token) ) {
            return false;
        }
        String testRunId = RunTest(token);
        if (Strings.isNullOrEmpty(testRunId) ) {
            return false;
        }
        if (waitResults) {
            boolean wait = true;
            while(wait){
                String status = CheckStatus(token, testRunId);
                
                if ("Waiting".equalsIgnoreCase(status)) {
                    logger.println("Waiting for all the test(s) to be completed.");
                    TimeUnit.SECONDS.sleep(5);
                }
                else if ("Failed".equalsIgnoreCase(status)) {
                    logger.println("Test(s) failed.");
                    return false;
                }
                else {
                    logger.println(" All test(s) successfully completed.");
                    return true;
                }
            }
        }
        return true;
    }
    
    private String GetToken() throws Exception {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()){
            final RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(60 * 1000)
                    .setConnectionRequestTimeout(60 * 1000)
                    .setSocketTimeout(60 * 1000)
                    .build();

            final HttpPost request = new HttpPost(API_TOKEN_URL);
            request.setConfig(config);
            request.setHeader("Content-Type", "application/x-www-form-urlencoded");
      
            List <NameValuePair> nvps = new ArrayList <>();
            nvps.add(new BasicNameValuePair("client_id", clientId));
            nvps.add(new BasicNameValuePair("client_secret", clientSecret.getPlainText()));
            nvps.add(new BasicNameValuePair("grant_type", "client_credentials"));
            nvps.add(new BasicNameValuePair("scope", "tests_run"));
            request.setEntity(new UrlEncodedFormEntity(nvps, Consts.UTF_8));
            
            logger.println("Executing Get Token :" + request.getRequestLine());
            
            String responseBody = httpclient.execute(request, HandleResponse());
            logger.println("Get Token is successfull");
            
            JSONObject result = JSONObject.fromObject(responseBody);
            return result.get("access_token").toString();
        } 
    }
    
    private String RunTest(String accessToken) throws Exception {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()){   
            JSONObject json = new JSONObject();
            
            String requestPath = "";
            if (!testId.isEmpty()) {
                requestPath = "/"+ testId +"/Run";
            }
            else if (!groupId.isEmpty()) {
                requestPath = "/Run";
                json.put("groupId", groupId);
            }
            
            if (!environmentId.isEmpty()) {
                json.put("environmentId", environmentId);
            }
            if (!platforms.isEmpty()) {
                List<String> platformBrowserList = Arrays.asList(platforms.split(","));
                String jsonString = "[";
                StringBuffer bufferPlatform = new StringBuffer();
                for (String platformBrowser : platformBrowserList) {
                    bufferPlatform.append(GetPlatformAndBrowser(platformBrowser) + ",");
                }
                String appendedPlatforms = bufferPlatform.toString();
                jsonString += RemoveLastChar(appendedPlatforms) + "]";
                json.put("platforms", JSONArray.fromObject(jsonString));
            }
            if (!sizes.isEmpty()) {
                List<String> sizeList = Arrays.asList(sizes.split(","));
                String jsonString = "[";
                StringBuffer bufferSizes = new StringBuffer();
                for (String size : sizeList) {
                    bufferSizes.append(GetSizes(size) + ",");
                }
                String appendedSizes = bufferSizes.toString();
                jsonString += RemoveLastChar(appendedSizes) + "]";
                json.put("sizes", JSONArray.fromObject(jsonString));
            }
            json.put("captureScreenshots", captureScreenshots);
            
            final RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(60 * 1000)
                .setConnectionRequestTimeout(60 * 1000)
                .setSocketTimeout(60 * 1000)
                .build();

            final HttpPost request = new HttpPost(API_RUN_URL + requestPath);
            request.setConfig(config);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Authorization", "Bearer " + accessToken); 
            
            StringEntity body = new StringEntity(json.toString());
            request.setEntity((HttpEntity) body);
            
            logger.println("Executing Run Test :" + request.getRequestLine());
            logger.println("Request :" + json.toString());
            
            String responseBody = httpclient.execute(request, HandleResponse());
            logger.println("Response Run Test :" + responseBody);
            
            JSONObject result = JSONObject.fromObject(responseBody);
            return result.get("testRunId").toString();
        } 
    }
    
    private String GetPlatformAndBrowser(String platformBrowser) throws Exception{
        String jsonItem="{'platform':";
        String platform = platformBrowser.split("-")[0];
        switch (platform.trim().toLowerCase()) {
            case "win":
                jsonItem += "'windows'";
                break;
            default:
                throw new Exception("Invalid platform.");
        }
        jsonItem +=",'browser':";
        String browser = platformBrowser.split("-")[1];
        switch (browser.trim().toLowerCase()) {
            case "ie":
                jsonItem += "'internetexplorer'";
                break;
            case "edge":
                jsonItem += "'edge'";
                break;
            case "firefox":
                jsonItem += "'firefox'";
                break;
            case "chrome":
                jsonItem += "'chrome'";
                break;
            default:
                throw new Exception("Invalid browser.");
        }
        jsonItem +="}";
        return jsonItem; 
    }
    
    private String GetSizes(String sizes){
        String jsonItem="{";
        
        String width = sizes.split("[xX]")[0];
        jsonItem += "'width':" + width;
        
        jsonItem +=",";

        String height = sizes.split("[xX]")[1];
        jsonItem += "'height':" + height;
        
        jsonItem +="}";
        
        return jsonItem; 
    }
    
    private static String RemoveLastChar(String str) {
        return str.substring(0, str.length() - 1);
    }
    
    private String CheckStatus(String accessToken, String testRunId) throws Exception {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()){
            final RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(60 * 1000)
                    .setConnectionRequestTimeout(60 * 1000)
                    .setSocketTimeout(60 * 1000)
                    .build();

            final HttpGet request = new HttpGet(API_RUN_URL + "/Run/" + testRunId);
            request.setConfig(config);
            request.setHeader("Content-Type", "application/json");
            request.setHeader("Authorization", "Bearer " + accessToken);    
            
            logger.println("Executing Check Status :" + request.getRequestLine());
            
            String responseBody = httpclient.execute(request, HandleResponse());
            logger.println("Response Check Status :" + responseBody);
            
            JSONObject result = JSONObject.fromObject(responseBody);
            JSONArray results = result.getJSONArray("results");
            
            String status = "Success";
            
            for (int i=0; i<results.size(); i++) {
                JSONObject resultItem = results.getJSONObject(i);
                status = resultItem.getString("status");
                if ("Failed".equalsIgnoreCase(status)) {
                    break;
                }
                else if ("Queued".equalsIgnoreCase(status) || "Running".equalsIgnoreCase(status)){
                    status = "Waiting";
                    break;
                }
            }
            return status;
        } 
    }
       
    private ResponseHandler<String> HandleResponse(){
        ResponseHandler<String> responseHandler = response -> {
            int status = response.getStatusLine().getStatusCode();
            if (status >= 200 && status < 300) {
                return EntityUtils.toString(response.getEntity());
            } else {
                throw new ClientProtocolException("Unexpected response status: " + status);
            }
        };
        return responseHandler;
    }

    @Override
    public DescriptorImpl getDescriptor() {
      return (DescriptorImpl)super.getDescriptor();
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        public DescriptorImpl() {
          load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
          return true;
        }

        @Override
        public String getDisplayName() {
          return DISPLAY_NAME;
        }
    }
}
