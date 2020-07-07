package dave.jiraclient;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class JiraClientStandalone {

    private static final String UN = "gallinda";
    private static final String PW = "NrThW18446";
    private static final String JIRA_API = "https://helpdesk2.arhs-developments.com/rest/api/2/issue/";
    private static final String JIRA_REQ_LINK = "Requirements";
    private static final String JIRA_WBS_LINK = "WBS";
    private static final String JIRA_KEY = "OPTEDENOT-";
    private static final String REQ_BASE_URL = "https://intranet2.arhs-developments.com/display/OPTEDAPPS/";
    private static final String WBS_BASE_URL = "https://intranet2.arhs-developments.com/display/OPTEDAPPS/WBS#WBS-";
    private static final Integer JIRA_FIRST_ISSUE = 558;
    private static final Integer JIRA_LAST_ISSUE = 707;

    public static void main(String[] args) throws IOException {
        String auth = UN + ":" + PW;
        byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.UTF_8));
        String authHeader = "Basic " + new String(encodedAuth);

        for (int i = JIRA_FIRST_ISSUE; i <= JIRA_LAST_ISSUE; i++) {
            String issueKey = JIRA_KEY + i;
            String issueSummary = retrieveSummary(issueKey, authHeader);

            // reqs
            removeLink(issueKey, JIRA_REQ_LINK, authHeader);
            if (issueSummary.startsWith("UC")) {
                String issueUcKeyAndTitleForReqs = StringUtils.replace(issueSummary, "  ", " ");
                issueUcKeyAndTitleForReqs = StringUtils.replace(issueUcKeyAndTitleForReqs, " ", "+");
                addLink(issueKey, REQ_BASE_URL + issueUcKeyAndTitleForReqs, JIRA_REQ_LINK, authHeader);
            }

            // wbs
            removeLink(issueKey, JIRA_WBS_LINK, authHeader);
            if (issueSummary.startsWith("UC") || issueSummary.startsWith("AUC") || issueSummary.startsWith("TT") || issueSummary.startsWith("PT")) {
                String issueUcKey = StringUtils.substringBefore(issueSummary, " ");
                addLink(issueKey, WBS_BASE_URL + issueUcKey, JIRA_WBS_LINK, authHeader);
            }
        }
    }

    private static HttpURLConnection prepareConnection(String url, String authHeader, String method) throws IOException {
        URL urlCon = new URL(url);
        HttpURLConnection con = (HttpURLConnection) urlCon.openConnection();
        con.setRequestProperty("Authorization", authHeader);
        con.setRequestMethod(method);
        return con;
    }

    private static String retrieveSummary(String issue, String authHeader) throws IOException {
        HttpURLConnection con = prepareConnection(JIRA_API + issue, authHeader, "GET");
        String outputString = retrieveHttpResponse(con, null);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode outputJson = mapper.readTree(outputString);
        return outputJson.get("fields").get("summary").getValueAsText();
    }

    private static void removeLink(String issue, String linkTitle, String authHeader) throws IOException {
        HttpURLConnection retrieveCon = prepareConnection(JIRA_API + issue + "/remotelink", authHeader, "GET");
        String outputString = retrieveHttpResponse(retrieveCon, null);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode outputJson = mapper.readTree(outputString);

        String linkId = null;
        for (int i = 0; i < outputJson.size(); i++) {
            JsonNode currLink = outputJson.get(i);
            if (linkTitle.equals(currLink.get("object").get("title").getValueAsText())) {
                linkId = currLink.get("id").getValueAsText();
                break;
            }
        }
        if (linkId == null) {
            System.out.println("Link " + linkTitle + " does not exist for issue " + issue + ".");
            return;
        }

        HttpURLConnection deleteCon = prepareConnection(JIRA_API + issue + "/remotelink/" + linkId, authHeader, "DELETE");
        try {
            deleteCon.getResponseCode();
        } finally {
            deleteCon.disconnect();
        }
    }

    private static void addLink(String issue, String linkUrl, String linkTitle, String authHeader) throws IOException {
        HttpURLConnection con = prepareConnection(JIRA_API + issue + "/remotelink", authHeader, "POST");
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        con.setDoOutput(true);

        String inputString = "{\n" +
                "    \"object\": {\n" +
                "        \"url\":\"" + linkUrl + "\",\n" +
                "        \"title\":\"" + linkTitle + "\"\n" +
                "    }\n" +
                "}";
        retrieveHttpResponse(con, inputString);
    }

    private static String retrieveHttpResponse(HttpURLConnection con, String inputStr) throws IOException {
        StringBuilder response = new StringBuilder();
        try {
            if (inputStr != null) {
                OutputStream os = con.getOutputStream();
                byte[] input = inputStr.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        } finally {
            con.disconnect();
        }
        return response.toString();
    }

}
