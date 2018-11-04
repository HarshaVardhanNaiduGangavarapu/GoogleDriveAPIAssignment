package com.gdapi.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

/***
 * 
 * @author Harsha Vardhan Naidu Gangavarapu
 *
 */
@Path("/gdapi")
public class GoogleDriveUtil {
	// Application Name
	private static final String APPLICATION_NAME = "HarshaGoogleDriveAPI";
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	static ClassLoader classLoader = new GoogleDriveUtil().getClass().getClassLoader();
	// Directory to store user credentials for this application.
	private static final java.io.File CREDENTIALS_FOLDER //
			= new java.io.File(classLoader.getResource("GoogleDriveCredentials/").getFile());
	private static final String CLIENT_SECRET_FILE_NAME = "client_secret.json";
	// Global instance of the scopes required by this quick start.
	private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE);
	private static final String UPLOAD_FILE_DIRECTORY = System.getProperty("user.home") + "/uploadedFiles/";

	@POST
	@Path("/list")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response getFilesListInFolder(String folderNameJson) {
		try {
			JSONObject json = new JSONObject(folderNameJson);
			String folderName = json.getString("folderName");
			// Get All folders List to validate for presence of User Provided Folder Name.
			List<File> fileList = getFileList();
			if (fileList == null || fileList.size() == 0) {
				return Response.status(200).entity("Folder Not Present : " + folderName).build();
			} else {
				File folder = null;
				for (File file : fileList) {
					if (file.getName().equals(folderName)) {
						folder = file;
						break;
					}
				}
				// If Folder not present.
				if (folder == null) {
					return Response.status(200).entity("Folder Not Present : " + folderName).build();
				}
				String query = "'" + folder.getId() + "' in parents";
				// Get Drive Config details
				Drive service = getDriveDetails();
				// Get All files present in the Folder.
				FileList result = service.files().list().setQ(query).setFields("nextPageToken, files(mimeType,name)")
						.execute();
				return Response.status(200).entity(result.getFiles().toString()).build();
			}
		} catch (JSONException e) {
			return Response.status(200)
					.entity("Provide Data in proper JSON format : {" + "folderName" + ":" + "{value}" + "}").build();
		} catch (Exception e) {
			return Response.status(200).entity("Error in getting files : " + e).build();
		}
	}

	@POST
	@Path("/download")
	@Produces(MediaType.TEXT_PLAIN)
	public Response downloadFile(String data) {
		try {
			JSONObject json = new JSONObject(data);
			String folderName = json.getString("folderName");
			String fileName = json.getString("fileName");
			File fileToDownload = null;
			// Validate Files present in Folder to download user provided file name.
			List<File> fileList = getFilesInGivenFolder(folderName);
			if (null == fileList) {
				return Response.status(200).entity("Folder : " + folderName + " is not Present.").build();
			}
			for (File file : fileList) {
				if (file.getName().equals(fileName)) {
					fileToDownload = file;
					break;
				}
			}
			// If file not present in given folder
			if (fileToDownload == null) {
				return Response.status(200).entity("File : " + fileName + " is not Present in " + folderName).build();
			}
			// Get Drive details
			Drive service = getDriveDetails();
			// get file to download Response
			HttpResponse resp = service.files().get(fileToDownload.getId()).executeMedia();
			ResponseBuilder response = Response.ok(resp.getContent());
			response.header("Content-Disposition", "attachment; filename=\"" + fileToDownload.getName() + ".txt\"");
			return response.build();
		} catch (Exception e) {
			return Response.status(200).entity("Error in downloading File : " + e).build();
		}
	}

	@POST
	@Path("/upload")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	public Response uploadFile(@FormDataParam("file") InputStream uploadedInputStream,
			@FormDataParam("file") FormDataContentDisposition fileDetail,
			@FormDataParam("folderName") String folderName) {
		try {
			// Get Folders present in Drive
			List<File> fileList = getFileList();
			// validate for the presence of User Provided folder.
			File folder = null;
			if (fileList == null || fileList.size() == 0) {
				return Response.status(200).entity("Folder Not Present : " + folderName).build();
			} else {
				for (File file : fileList) {
					if (file.getName().equals(folderName)) {
						folder = file;
						break;
					}
				}
				// if folder not present
				if (folder == null) {
					return Response.status(200).entity("Folder Not Present : " + folderName).build();
				}
			}
			// check if all form parameters are provided
			if (uploadedInputStream == null || fileDetail == null || folderName == null) {
				return Response.status(400).entity("Invalid form data").build();
			}
			// create our destination folder, if it not exists
			createFolderIfNotExists(UPLOAD_FILE_DIRECTORY);
			String uploadedFileLocation = UPLOAD_FILE_DIRECTORY + fileDetail.getFileName();
			// save file locally
			saveToFile(uploadedInputStream, uploadedFileLocation);
			Drive service = getDriveDetails();
			// Set file Meta data
			File fileMetadata = new File();
			fileMetadata.setName(fileDetail.getFileName());
			fileMetadata.setParents(Collections.singletonList(folder.getId()));
			// Set File Path
			java.io.File filePath = new java.io.File(uploadedFileLocation);
			FileContent mediaContent = new FileContent("text/plain", filePath);
			// upload file to drive
			File file = service.files().create(fileMetadata, mediaContent).setFields("id,parents,name").execute();
			// delete file
			deleteFile(uploadedFileLocation);
			return Response.status(200).entity(file.getName() + " saved to Folder : " + folder.getName()).build();
		} catch (Exception e) {
			return Response.status(200).entity("Error in Uploading File : " + e).build();
		}
	}

	// delete locally saved file
	private void deleteFile(String uploadedFileLocation) {
		java.io.File delFile = new java.io.File(uploadedFileLocation);
		if (delFile.exists()) {
			delFile.delete();
		}

	}

	// save file locally
	private void saveToFile(InputStream inStream, String target) {
		OutputStream out = null;
		int read = 0;
		try {
			byte[] bytes = new byte[1024];
			out = new FileOutputStream(new java.io.File(target));
			while ((read = inStream.read(bytes)) != -1) {
				out.write(bytes, 0, read);
			}
			out.flush();
			out.close();
		} catch (IOException e) {
			System.out.println("Error in Saving File Locally : " + e);
		}
	}

	// create folder if not exist
	private void createFolderIfNotExists(String dirName) throws SecurityException {
		try {
			java.io.File directory = new java.io.File(dirName);
			if (!directory.exists()) {
				directory.mkdir();
			}
		} catch (SecurityException e) {
			System.out.println("Error in Creating Directory : " + e);
		}
	}

	/**
	 * This method provides Google Drive Credentials Details.
	 */
	private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) {
		try {
			java.io.File clientSecretFilePath = new java.io.File(CREDENTIALS_FOLDER, CLIENT_SECRET_FILE_NAME);
			if (!clientSecretFilePath.exists()) {
				throw new FileNotFoundException("Please copy " + CLIENT_SECRET_FILE_NAME + " to folder: "
						+ CREDENTIALS_FOLDER.getAbsolutePath());
			}
			// Load client secrets.
			InputStream in = new FileInputStream(clientSecretFilePath);
			GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
			// Build flow and trigger user authorization request.
			GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
					clientSecrets, SCOPES).setDataStoreFactory(new FileDataStoreFactory(CREDENTIALS_FOLDER))
							.setAccessType("offline").build();
			return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
		} catch (IOException e) {
			System.out.println("Error in getting Drive Credentials : " + e);
			return null;
		}
	}

	// get file list present in root folder.
	public List<File> getFileList() {
		try {
			Drive service = getDriveDetails();
			// get the file names and IDs from root of Drive
			FileList fileList = service.files().list().setFields("nextPageToken, files(id, name)").execute();
			List<File> files = fileList.getFiles();
			return files;
		} catch (Exception e) {
			System.out.println("Error in getting Files : " + e);
			return null;
		}
	}

	// get google drive details
	private Drive getDriveDetails() {
		try {
			// Create HttpTransport
			NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			// Read client_secret.json file & create Credential object.
			Credential credential = getCredentials(HTTP_TRANSPORT);
			// Create Google Drive Service.
			Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential) //
					.setApplicationName(APPLICATION_NAME).build();
			return service;
		} catch (Exception e) {
			System.out.println("Error in creating Drive Details : " + e);
			return null;
		}
	}

	// get files and folders present in given foldername
	private List<File> getFilesInGivenFolder(String folderName) {
		try {
			final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			Credential credential = getCredentials(HTTP_TRANSPORT);
			List<File> fileList = getFileList();
			if (fileList == null || fileList.size() == 0) {
				return null;
			} else {
				File folder = null;
				for (File file : fileList) {
					if (file.getName().equals(folderName)) {
						folder = file;
						break;
					}
				}
				if (folder == null) {
					return null;
				}
				String query = "'" + folder.getId() + "' in parents";
				Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
						.setApplicationName(APPLICATION_NAME).build();
				FileList fileListList = service.files().list().setQ(query)
						.setFields("nextPageToken, files(mimeType,name,id)").execute();
				return fileListList.getFiles();
			}
		} catch (Exception e) {
			System.out.println("Error in getting file in the given folder : " + e);
		}
		return null;
	}

	@GET
	@Path("/test")
	@Produces(MediaType.TEXT_PLAIN)
	public String test() {
		return "hello";
	}
}