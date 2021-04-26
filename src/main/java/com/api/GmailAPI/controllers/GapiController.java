package com.api.GmailAPI.controllers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets.Details;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;

import io.restassured.path.json.JsonPath;
//import io.restassured.path.json.JsonPath;

@RestController
public class GapiController {
	private static final String APPLICATION_NAME = "AS App";
	private static HttpTransport httpTransport;
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static com.google.api.services.gmail.Gmail client;

	public static String userId = "<Your Gmail UserID>";
	public static String query = "is:All";

	GoogleClientSecrets clientSecrets;
	GoogleAuthorizationCodeFlow flow;
	Credential credential;

	@Value("${gmail.client.clientId}")
	private String clientId;

	@Value("${gmail.client.clientSecret}")
	private String clientSecret;

	@Value("${gmail.client.redirectUri}")
	private String redirectUri;

	private String authorize() throws Exception {
		AuthorizationCodeRequestUrl authorizationUrl;
		if (flow == null) {
			Details web = new Details();
			web.setClientId(clientId);
			web.setClientSecret(clientSecret);
			clientSecrets = new GoogleClientSecrets().setWeb(web);
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
			flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets,
					Collections.singleton(GmailScopes.GMAIL_READONLY)).build();
		}
		authorizationUrl = flow.newAuthorizationUrl().setRedirectUri(redirectUri);

		// System.out.println("gmail authorizationUrl -" + authorizationUrl);
		return authorizationUrl.build();
	}

	@RequestMapping(value = "/login/gmail", method = RequestMethod.GET)
	// public RedirectView googleConnectionStatus(HttpServletRequest request,
	// @PathVariable String ghfgu) throws Exception {
	public RedirectView googleConnectionStatus(HttpServletRequest request) throws Exception {
		return new RedirectView(authorize());
	}

	@RequestMapping(value = "login/gmailCallback", method = RequestMethod.GET, params = "code")
	public ResponseEntity<String> oauth2Callback(@RequestParam String code) {

		List<String> subjects = new ArrayList<String>();
		JSONObject jsonObject = new JSONObject();

		try {
			TokenResponse response = flow.newTokenRequest(code).setRedirectUri(redirectUri).execute();
			credential = flow.createAndStoreCredential(response, "userID");

			client = new com.google.api.services.gmail.Gmail.Builder(httpTransport, JSON_FACTORY, credential)
					.setApplicationName(APPLICATION_NAME).build();

			ListMessagesResponse responseMessages = client.users().messages().list(userId).setQ(query).execute();
			List<Message> messages = new ArrayList<Message>();


			for (Message msg : responseMessages.getMessages()) {

				messages.add(msg);
				if (responseMessages.getNextPageToken() != null) {
					Message message = client.users().messages().get(userId, msg.getId()).execute();
					JsonPath jp = new JsonPath(message.toString());
					subjects.add(jp.getString("payload.headers.find { it.name == 'Subject' }.value"));
				}
				else
					break;
			}

			for(int index=0;index<10;index=index+1) {
				jsonObject.put("subject" + index , subjects.get(index));			
			}
		}

		catch (Exception e) {

			System.out.println("exception cached");
			//e.printStackTrace();
		}

		return new ResponseEntity<String>(jsonObject.toString(), HttpStatus.OK);
	}

	@RequestMapping("/error")
	public String handleError(HttpServletRequest request) {
		Integer statusCode = (Integer) request.getAttribute("javax.servlet.error.status_code");
		Exception exception = (Exception) request.getAttribute("javax.servlet.error.exception");
		return String.format(
				"<html><body><h2>Error Page</h2><div>Status code: <b>%s</b></div>"
						+ "<div>Exception Message: <b>%s</b></div><body></html>",
						statusCode, exception == null ? "N/A" : exception.getMessage());
	}

}
