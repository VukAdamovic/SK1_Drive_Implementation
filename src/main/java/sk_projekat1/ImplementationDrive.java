package sk_projekat1;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import org.apache.commons.io.FilenameUtils;
import sk_projekat1.enums.TypeFilter;
import sk_projekat1.enums.TypeSort;
import sk_projekat1.exceptions.CustomException;

import java.io.*;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class ImplementationDrive implements Storage {

    final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

    public Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
            .setApplicationName(APPLICATION_NAME)
            .build();

    /**
     * Application name.
     */
    private static final String APPLICATION_NAME = "Google Drive API Java Quickstart";
    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    /**
     * Directory to store authorization tokens for this application.
     */
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES =
            Arrays.asList(DriveScopes.DRIVE, DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_METADATA, DriveScopes.DRIVE_SCRIPTS);
        private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    //private static final String CREDENTIALS_FILE_PATH = "/andrej.json";

    public ImplementationDrive() throws GeneralSecurityException, IOException {
    }

    /**
     * Creates an authorized Credential object.
     *
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
            throws IOException {
        // Load client secrets.
        InputStream in = ImplementationDrive.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        // Returns an authorized Credential object.
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user1");
    }

    static {
        try {
            StorageManager.registerStorage(new ImplementationDrive());
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    /*--------------------------------------------------------------------------------------*/

    @Override
    public boolean setPath(String storageId) {
        List<File> driveList = getFilesByName("", "", service);
        boolean operation;
        boolean check = false;

        for (File file : driveList) {
            if (file.getId().equals(storageId)) {
                check = true;
                break;
            }
        }
        if (!check) {
            return false;
        } else {
            File storage;
            try {
                storage = service.files().get(storageId).setFields("id,name,parents,mimeType,size").execute();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            try {
                List<String> configAtributes = new ArrayList<>();
                java.io.File configFile = new java.io.File("D:/googleDriveFiles", storage.getName() + "_CONFIGURATION.txt");
                //java.io.File configFile = new java.io.File("/Users/adjenadic/googleDriveFiles", storage.getName() + "_CONFIGURATION.txt");
                Scanner myReader = new Scanner(configFile);

                while (myReader.hasNextLine()) {
                    String line = myReader.nextLine();
                    String[] value = line.split(":");
                    configAtributes.add(value[1]);
                }

                StorageArguments.name = configAtributes.get(0);
                StorageArguments.driveStorage_Id = storageId;
                StorageArguments.totalSpace = Integer.parseInt(configAtributes.get(1));
                StorageArguments.restrictedExtensions = Collections.singletonList(configAtributes.get(2));
                StorageArguments.maxFilesInStorage = Integer.parseInt(configAtributes.get(3));
                StorageArguments.usedSpace = getUsedSpaceInStorage("");
                StorageArguments.fileNumberInStorage = searchFilesInFolders(".", "", "", "", "", "").size();
                operation = true;
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        return operation;
    }

    @Override
    public boolean createStorage(String storageName, String storagePath, int storageSize, String storageRestrictedExtensions, int maxFilesInStorage) {
        //inicijalizacija
        StorageArguments.name = storageName;
        StorageArguments.path = storagePath;
        StorageArguments.totalSpace = storageSize;
        StorageArguments.usedSpace = 0;
        StorageArguments.restrictedExtensions = new ArrayList<>();
        String[] resExe = storageRestrictedExtensions.split(",");
        StorageArguments.restrictedExtensions.addAll(Arrays.asList(resExe));
        StorageArguments.maxFilesInStorage = maxFilesInStorage;

        // Kreiranje storage
        File storageMetaData = new File();
        storageMetaData.setName(storageName);
        storageMetaData.setMimeType("application/vnd.google-apps.folder");

        try {
            File storageFile = service.files()
                    .create(storageMetaData).
                    setFields("id,name,parents,mimeType,size").
                    execute();

            StorageArguments.driveStorage_Id = storageFile.getId();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Kreiranje lokalnog file
        java.io.File localFile = new java.io.File("D:/googleDriveFiles", storageName + "_Configuration.txt");
        //java.io.File localFile = new java.io.File("/Users/adjenadic/googleDriveFiles", storageName + "_Configuration.txt");
        try {
            FileWriter fileWriter = new FileWriter(localFile);
            fileWriter.write("Storage name:" + storageName + "\n");
            fileWriter.write("Storage size in bytes:" + storageSize + "\n");
            fileWriter.write("Storage restricted extensions:" + storageRestrictedExtensions + "\n");
            fileWriter.write("Storage max file size number:" + maxFilesInStorage);
            fileWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Kreiranje config file
        File readMeMetaData = new File();
        readMeMetaData.setName(storageName + "_Configuration.txt");
        readMeMetaData.setParents(Collections.singletonList(StorageArguments.driveStorage_Id));

        FileContent readMeContent = new FileContent("text/txt", localFile);
        try {
            service.files()
                    .create(readMeMetaData, readMeContent)
                    .setFields("id,name,parents,mimeType,size")
                    .execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    @Override
    public boolean createDefaultStorage() {
        //inicijalizacija
        StorageArguments.name = "DefaultStorage" + StorageArguments.counter;
        StorageArguments.path = "";
        StorageArguments.totalSpace = 250;
        StorageArguments.usedSpace = 0;
        StorageArguments.restrictedExtensions = new ArrayList<>();
        StorageArguments.maxFilesInStorage = 15;


        // Kreiranje storage
        File storageMetaData = new File();
        storageMetaData.setName(StorageArguments.name);
        storageMetaData.setMimeType("application/vnd.google-apps.folder");

        try {
            File storageFile = service.files()
                    .create(storageMetaData).
                    setFields("id,name,parents,mimeType,size").
                    execute();
            StorageArguments.driveStorage_Id = storageFile.getId();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Kreiranje lokalnog file
        java.io.File localFile = new java.io.File("D:/googleDriveFiles", StorageArguments.name + "_CONFIGURATION.txt");
        //java.io.File localFile = new java.io.File("/Users/adjenadic/googleDriveFiles", StorageArguments.name + "_CONFIGURATION.txt");
        try {
            FileWriter fileWriter = new FileWriter(localFile);
            fileWriter.write("Storage name:" + StorageArguments.name + "\n");
            fileWriter.write("Storage size in bytes:" + StorageArguments.totalSpace + "\n");
            fileWriter.write("Storage restricted extensions:" + StorageArguments.restrictedExtensions + "\n");
            fileWriter.write("Storage max file size number:" + StorageArguments.maxFilesInStorage);
            fileWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Kreiranje config file na drajvu
        File readMeMetaData = new File();
        readMeMetaData.setName(StorageArguments.name + "_CONFIGURATION.txt");
        readMeMetaData.setParents(Collections.singletonList(StorageArguments.driveStorage_Id));

        FileContent readMeContent = new FileContent("text/text", localFile);
        try {
            service.files()
                    .create(readMeMetaData, readMeContent)
                    .setFields("id,name,parents,mimeType,size")
                    .execute();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    @Override
    public boolean createFolder(String folderName, String folderPath) {

        if (folderPath.equals(".")) {
            folderPath = "";
        }

        //Provera da li postoji folder za datim imenom na zadatoj putanji
        String name = "name='" + folderName + "'";
        String nameAndMimeiType = name + " and mimeType='" + "application/vnd.google-apps.folder" + "'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("", nameAndMimeiType, service);

        String parentID = getFileId(folderPath, "", service);// pitaj
        for (File file : files) {
            if (file.getParents().contains(parentID) && file.getName().equals(folderName)) {
                String absoPath = StorageArguments.name + "/" + folderPath + "/" + folderName;
                throw new CustomException("Action FAILED \t Folder: " + absoPath + " already exists");
            }
        }

        //Kreiranje foldera
        File fileMetadata = new File();
        fileMetadata.setName(folderName);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setParents(Collections.singletonList(parentID));

        try {
            service.files().create(fileMetadata).setFields("id,name,parents,mimeType,size").execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    @Override
    public boolean createFile(String fileName, String filePath) {

        if (filePath.equals(".")) {
            filePath = "";
        }

        //Provera ekstenzije fajla
        if (StorageArguments.restrictedExtensions.contains(FilenameUtils.getExtension(fileName))) {
            throw new CustomException("Action FAILED \t Storage unsupported extensions:" + StorageArguments.restrictedExtensions);
        }
        //Provera prekoracenja maksimalne kolicine fajlova u  skladistu
        if (StorageArguments.fileNumberInStorage + 1 > StorageArguments.maxFilesInStorage) {
            throw new CustomException("Action FAILED \t Storage limit max files:" + StorageArguments.maxFilesInStorage);
        }

        //Provera da li postoji fajl za datim imenom na zadatoj putanji
        String name = "name='" + fileName + "'";
        String nameAndMimeiType = name + " and mimeType!='" + "application/vnd.google-apps.folder" + "'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("", nameAndMimeiType, service);

        String parentID = getFileId(filePath, "application/vnd.google-apps.folder", service);//
        for (File value : files) {
            if (value.getParents().contains(parentID) && value.getName().equals(fileName)) {
                String absolutePath = StorageArguments.name + "/" + filePath + "/" + fileName;
                throw new CustomException("Action FAILED \t File: " + absolutePath + " already exists");
            }
        }

        //Kreiranje
        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(Collections.singletonList(parentID));
        java.io.File localFile = new java.io.File("D:/googleDriveFiles/testSizeStorage.txt"); //zbog testiranja
   //     java.io.File localFile = new java.io.File("/Users/adjenadic/googleDriveFiles/testSizeStorage.txt");

        FileContent fileContent = new FileContent("txt/txt", localFile);

        try {
            File file = service.files().create(fileMetadata, fileContent).setFields("id,name,parents,mimeType,size").execute();

            //Provera prekoracenje velicine skladista
            if (StorageArguments.usedSpace + file.getSize() > StorageArguments.totalSpace) {
                service.files().delete(file.getId()).execute();
                throw new CustomException("Action FAILED \t Storage byte size:" + StorageArguments.totalSpace);
            }

            //Storage.content.add(file.getAbsolutePath());
            StorageArguments.fileNumberInStorage += 1;
            StorageArguments.usedSpace = (int) (StorageArguments.usedSpace + file.getSize());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    @Override
    public boolean moveFile(String oldFilePath, String newFilePath) {
        if (oldFilePath.equals(".")) {
            throw new CustomException("Action FAILED \t Storage can not be moved");
        }
        if (newFilePath.equals(".")) {
            newFilePath = "";
        }

        //Proverava da li je dobra putanja, ako jeste uzima id poslednjeg parenta
        String oldFolderParentId = getFileId(oldFilePath, "", service);

        //Provera da li ono sto se premesta  je fajl
        String[] splitOldPath = oldFilePath.split("/");
        String targetName = splitOldPath[splitOldPath.length - 1];
        String name = "name='" + targetName + "'";
        String nameAndMimeiType = name + " and mimeType!='" + "application/vnd.google-apps.folder" + "'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("", nameAndMimeiType, service);

        if (files.isEmpty()) {
            throw new CustomException("Action FAILED \t Only files can be moved");
        }


        // Retrieve the existing parents to remove
        File file;
        try {
            file = service.files().get(oldFolderParentId)
                    .setFields("id,name,parents,mimeType,size")
                    .execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        StringBuilder previousParents = new StringBuilder();
        for (String parent : file.getParents()) {
            previousParents.append(parent);
            previousParents.append(',');
        }

        // Move the file to the new folder
        String newFolderParentId = getFileId(newFilePath, "application/vnd.google-apps.folder", service);


        //Ako vec postoji fajl sa zadatim imenom na putanji na kojoj hocemo da premestimo
        for (File value : files) {
            if (value.getParents().contains(newFolderParentId) && value.getName().equals(targetName)) {
                String absoPath = StorageArguments.name + "/" + newFilePath + "/" + targetName;
                throw new CustomException("Action FAILED \t File : " + absoPath + " already exists");
            }
        }

        try {
            file = service.files().update(oldFolderParentId, null)
                    .setAddParents(newFolderParentId)
                    .setRemoveParents(previousParents.toString())
                    .setFields("id,name,parents,mimeType,size")
                    .execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    @Override
    public boolean renameFileObject(String foNewName, String foPath) {
        if (foPath.equals(".")) {
            foPath = "";
        }

        String[] folders = foPath.split("/");
        StringBuilder parentPath = Optional.ofNullable(folders[0]).map(StringBuilder::new).orElse(null);

        for (int i = 1; i < folders.length - 1; i++) {
            parentPath = (parentPath == null ? new StringBuilder("null") : parentPath).append("/").append(folders[i]);
        }

        String fileObjectId = getFileId(foPath, "", service); // id file object koji zelimo da preimenujemo
        String parentFileObjectId = getFileId(parentPath.toString(), "", service); // id parenta

        //Proverava da li postoji da li postoji fajl ili folder koji se vec tako zove
        String name = "name='" + foNewName + "'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName(name, "", service);

        for (File file : files) {
            if (file.getParents().contains(parentFileObjectId) && file.getName().equals(foNewName)) {
                String absoPath = StorageArguments.name + "/" + parentPath + "/" + foNewName;
                throw new CustomException("Action FAILED \t File : " + absoPath + " already exists");
            }
        }

        try {
            File foMetaData = service.files().get(fileObjectId).setFields("name").execute();
            foMetaData.setName(foNewName);

            service.files().update(fileObjectId, foMetaData).
                    setFields("name").
                    execute();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    @Override
    public boolean deleteFileObject(String foPath) {
        if (foPath.equals(".")) {
            throw new CustomException("Action FAILED \t  Storage can not be deleted");
        }

        String[] folders = foPath.split("/");
        String fileObjectId;

        if (folders[folders.length - 1].contains(".")) {
            fileObjectId = getFileId(foPath, "", service);
        } else {
            fileObjectId = getFileId(foPath, "application/vnd.google-apps.folder", service);
        }

        try {
            service.files().delete(fileObjectId).execute();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    @Override
    public boolean importFileObject(String[] importLocalPaths, String importStoragePath) {
        if (importStoragePath.equals(".")) {
            importStoragePath = "";
        }

        String driveFolderId = getFileId(importStoragePath, "", service);
        try {
            File driveFolder = service.files().get(driveFolderId).execute(); // folder u koji zelis da uploadas stvari

            if (!driveFolder.getMimeType().equals("application/vnd.google-apps.folder")) { // proveravas da li je tipa folder
                String absolutePath = StorageArguments.name + "/" + importStoragePath;
                throw new CustomException("Action FAILED \t" + absolutePath + " is not a directory");
            }

            for (String importLocalPath : importLocalPaths) {
                java.io.File localFile = new java.io.File(importLocalPath);
                FileContent fileContent = new FileContent("*/*", localFile);

                if (localFile.exists()) {
                    if (localFile.isDirectory()) {
                        throw new CustomException("Action FAILED \t" + importLocalPath + " \t Directory can not be upload on drive");
                    } else if (localFile.isFile()) {
                        try {
                            //Provera ekstenzije fajla
                            if (StorageArguments.restrictedExtensions.contains(FilenameUtils.getExtension(localFile.getName()))) {
                                throw new CustomException("Action FAILED \t Storage unsupported extensions:" + StorageArguments.restrictedExtensions);
                            }
                            //Provera prekoracenja maksimalne kolicine fajlova u  skladistu
                            if (StorageArguments.fileNumberInStorage + 1 > StorageArguments.maxFilesInStorage) {
                                throw new CustomException("Action FAILED \t Storage limit max files:" + StorageArguments.maxFilesInStorage);
                            }
                            File importMetaData = new File();
                            importMetaData.setName(localFile.getName());
                            importMetaData.setParents(Collections.singletonList(driveFolderId));

                            File file = service.files().create(importMetaData, fileContent).setFields("id,name,parents,mimeType,size").execute();

                            //Provera prekoracenje velicine skladista
                            if (StorageArguments.usedSpace + file.getSize() > StorageArguments.totalSpace) {
                                service.files().delete(file.getId()).execute();
                                throw new CustomException("Action FAILED \t Storage byte size:" + StorageArguments.totalSpace);
                            }

                            StorageArguments.fileNumberInStorage += 1;
                            StorageArguments.usedSpace = (int) (StorageArguments.usedSpace + file.getSize());

                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } else {
                    throw new CustomException("Action FAILED \t" + localFile.getAbsolutePath() + "  does not exists");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    @Override
    public boolean exportFileObject(String exportStoragePath, String exportLocalPath) {

        if (exportStoragePath.equals(".")) {
            exportStoragePath = "";
        }
        String fileId = getFileId(exportStoragePath, "", service);
        java.io.File localFile = new java.io.File(exportLocalPath);

        if (!localFile.exists()) {
            throw new CustomException(localFile.getAbsolutePath() + " does not exists");
        }
        if (!localFile.isDirectory()) {
            throw new CustomException(localFile.getAbsolutePath() + "is not directory");
        }

        try {
            File fileOnDrive = service.files().get(fileId).setFields("name,mimeType").execute();

            if (fileOnDrive.getMimeType().equals("application/vnd.google-apps.folder")) {
                String absolutePath = StorageArguments.name + "/" + exportStoragePath;
                throw new CustomException("Action FAILED \t" + absolutePath + "\t Directory can not be download");
            }

            java.io.File downloadedFile = new java.io.File(exportLocalPath, fileOnDrive.getName());
            OutputStream outputStream = new ByteArrayOutputStream();
            service.files().get(fileId).executeMediaAndDownloadTo(outputStream);
            FileWriter fileWriter = new FileWriter(downloadedFile);
            fileWriter.write(String.valueOf(outputStream));
            fileWriter.close();
            outputStream.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    @Override
    public List<String> searchFilesInFolder(String folderPath, String typeSort, String typeFilter, String fileExtension, String startDate, String endDate) {
        if (folderPath.equals(".")) {
            folderPath = "";
        }

        String folderDriveId = getFileId(folderPath, "application/vnd.google-apps.folder", service);
        List<String> resultList = new ArrayList<>(); //lista fajlova iz tog foldera

        String mimeiType = "mimeType!='application/vnd.google-apps.folder'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("", mimeiType, service);

        for (File file : files) {
            if (file.getParents() != null && file.getParents().contains(folderDriveId)) {
                resultList.add(file.getId());
            }
        }

        if (typeFilter != null) {
            switch (typeFilter) {
                case "FILE_EXTENSION": {
                    resultList = filterFilesByExt(resultList, TypeFilter.FILE_EXTENSION, fileExtension);
                    break;
                }
                case "MODIFIED_DATE": {
                    resultList = filterFilesByDate(resultList, TypeFilter.MODIFIED_DATE, startDate, endDate);
                    break;
                }
                case "CREATED_DATE": {
                    resultList = filterFilesByDate(resultList, TypeFilter.CREATED_DATE, startDate, endDate);
                    break;
                }
                default: {
                    break;
                }
            }
        }

        switch (typeSort) {
            case "ALPHABETICAL_ASC": {
                resultList = sortFiles(resultList, TypeSort.ALPHABETICAL_ASC);
                break;
            }
            case "ALPHABETICAL_DESC": {
                resultList = sortFiles(resultList, TypeSort.ALPHABETICAL_DESC);
                break;
            }
            case "CREATED_DATE_ASC": {
                resultList = sortFiles(resultList, TypeSort.CREATED_DATE_ASC);
                break;
            }
            case "CREATED_DATE_DESC": {
                resultList = sortFiles(resultList, TypeSort.CREATED_DATE_DESC);
                break;
            }
            case "MODIFIED_DATE_ASC": {
                resultList = sortFiles(resultList, TypeSort.MODIFIED_DATE_ASC);
                break;
            }
            case "MODIFIED_DATE_DESC": {
                resultList = sortFiles(resultList, TypeSort.MODIFIED_DATE_DESC);
                break;
            }
            default: {
                resultList = sortFiles(resultList, null);
                break;
            }
        }

        return resultList;
    }

    @Override
    public List<String> searchFilesInFolders(String folderPath, String typeSort, String typeFilter, String fileExtension, String startDate, String endDate) {
        if (folderPath.equals(".")) {
            folderPath = "";
        }

        String folderDriveId = getFileId(folderPath, "application/vnd.google-apps.folder", service);
        List<String> resultList = new ArrayList<>(); //konacna lista svih fajlova


        List<String> listIdSubFolders = new ArrayList<>();
        ArrayList<File> files = (ArrayList<File>) getFilesByName("", "", service);

        while (true) {
            for (File file : files) {
                if (file.getParents() != null && file.getParents().contains(folderDriveId)) {

                    if (file.getMimeType().equals("application/vnd.google-apps.folder")) {
                        listIdSubFolders.add(file.getId());
                    } else {
                        resultList.add(file.getId());
                    }
                }
            }
            if (listIdSubFolders.isEmpty()) {
                break;
            }

            folderDriveId = listIdSubFolders.get(0); //uzimas sledeci subfolder da prodjes kroz njega
            listIdSubFolders.remove(folderDriveId); //brises njegov id iz liste
        }

        if (typeFilter != null) {
            switch (typeFilter) {
                case "FILE_EXTENSION": {
                    resultList = filterFilesByExt(resultList, TypeFilter.FILE_EXTENSION, fileExtension);
                    break;
                }
                case "MODIFIED_DATE": {
                    resultList = filterFilesByDate(resultList, TypeFilter.MODIFIED_DATE, startDate, endDate);
                    break;
                }
                case "CREATED_DATE": {
                    resultList = filterFilesByDate(resultList, TypeFilter.CREATED_DATE, startDate, endDate);
                    break;
                }
                default: {
                    break;
                }
            }
        }

        switch (typeSort) {
            case "ALPHABETICAL_ASC": {
                resultList = sortFiles(resultList, TypeSort.ALPHABETICAL_ASC);
                break;
            }
            case "ALPHABETICAL_DESC": {
                resultList = sortFiles(resultList, TypeSort.ALPHABETICAL_DESC);
                break;
            }
            case "CREATED_DATE_ASC": {
                resultList = sortFiles(resultList, TypeSort.CREATED_DATE_ASC);
                break;
            }
            case "CREATED_DATE_DESC": {
                resultList = sortFiles(resultList, TypeSort.CREATED_DATE_DESC);
                break;
            }
            case "MODIFIED_DATE_ASC": {
                resultList = sortFiles(resultList, TypeSort.MODIFIED_DATE_ASC);
                break;
            }
            case "MODIFIED_DATE_DESC": {
                resultList = sortFiles(resultList, TypeSort.MODIFIED_DATE_DESC);
                break;
            }
            default: {
                resultList = sortFiles(resultList, null);
                break;
            }
        }

        return resultList;
    }

    @Override
    public List<String> searchFilesWithExtensionInFolder(String folderPath, String typeSort, String typeFilter, String fileExtension, String startDate, String endDate) {
        if (folderPath.equals(".")) {
            folderPath = "";
        }

        String folderDriveId = getFileId(folderPath, "application/vnd.google-apps.folder", service);
        List<String> resultList = new ArrayList<>(); //lista fajlova iz tog foldera

        String mimeiType = "mimeType!='application/vnd.google-apps.folder'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("", mimeiType, service);

        for (File file : files) {
            if (file.getParents() != null && file.getParents().contains(folderDriveId) && file.getName().contains("." + fileExtension)) {
                resultList.add(file.getId());
            }
        }

        if (typeFilter != null) {
            switch (typeFilter) {
                case "FILE_EXTENSION": {
                    resultList = filterFilesByExt(resultList, TypeFilter.FILE_EXTENSION, fileExtension);
                    break;
                }
                case "MODIFIED_DATE": {
                    resultList = filterFilesByDate(resultList, TypeFilter.MODIFIED_DATE, startDate, endDate);
                    break;
                }
                case "CREATED_DATE": {
                    resultList = filterFilesByDate(resultList, TypeFilter.CREATED_DATE, startDate, endDate);
                    break;
                }
                default: {
                    break;
                }
            }
        }

        switch (typeSort) {
            case "ALPHABETICAL_ASC": {
                resultList = sortFiles(resultList, TypeSort.ALPHABETICAL_ASC);
                break;
            }
            case "ALPHABETICAL_DESC": {
                resultList = sortFiles(resultList, TypeSort.ALPHABETICAL_DESC);
                break;
            }
            case "CREATED_DATE_ASC": {
                resultList = sortFiles(resultList, TypeSort.CREATED_DATE_ASC);
                break;
            }
            case "CREATED_DATE_DESC": {
                resultList = sortFiles(resultList, TypeSort.CREATED_DATE_DESC);
                break;
            }
            case "MODIFIED_DATE_ASC": {
                resultList = sortFiles(resultList, TypeSort.MODIFIED_DATE_ASC);
                break;
            }
            case "MODIFIED_DATE_DESC": {
                resultList = sortFiles(resultList, TypeSort.MODIFIED_DATE_DESC);
                break;
            }
            default: {
                resultList = sortFiles(resultList, null);
                break;
            }
        }

        return resultList;
    }

    @Override
    public List<String> searchFilesWithSubstringInFolder(String folderPath, String typeSort, String typeFilter, String fileSubstring, String fileExtension, String startDate, String endDate) {
        if (folderPath.equals(".")) {
            folderPath = "";
        }

        String folderDriveId = getFileId(folderPath, "application/vnd.google-apps.folder", service);
        List<String> resultList = new ArrayList<>(); //lista fajlova iz tog foldera

        String mimeiType = "mimeType!='application/vnd.google-apps.folder'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("", mimeiType, service);

        for (File file : files) {
            if (file.getParents() != null && file.getParents().contains(folderDriveId)
                    && file.getName().toLowerCase().contains(fileSubstring.toLowerCase())) {
                resultList.add(file.getId());
            }
        }

        if (typeFilter != null) {
            switch (typeFilter) {
                case "FILE_EXTENSION": {
                    resultList = filterFilesByExt(resultList, TypeFilter.FILE_EXTENSION, fileExtension);
                    break;
                }
                case "MODIFIED_DATE": {
                    resultList = filterFilesByDate(resultList, TypeFilter.MODIFIED_DATE, startDate, endDate);
                    break;
                }
                case "CREATED_DATE": {
                    resultList = filterFilesByDate(resultList, TypeFilter.CREATED_DATE, startDate, endDate);
                    break;
                }
                default: {
                    break;
                }
            }
        }

        switch (typeSort) {
            case "ALPHABETICAL_ASC": {
                resultList = sortFiles(resultList, TypeSort.ALPHABETICAL_ASC);
                break;
            }
            case "ALPHABETICAL_DESC": {
                resultList = sortFiles(resultList, TypeSort.ALPHABETICAL_DESC);
                break;
            }
            case "CREATED_DATE_ASC": {
                resultList = sortFiles(resultList, TypeSort.CREATED_DATE_ASC);
                break;
            }
            case "CREATED_DATE_DESC": {
                resultList = sortFiles(resultList, TypeSort.CREATED_DATE_DESC);
                break;
            }
            case "MODIFIED_DATE_ASC": {
                resultList = sortFiles(resultList, TypeSort.MODIFIED_DATE_ASC);
                break;
            }
            case "MODIFIED_DATE_DESC": {
                resultList = sortFiles(resultList, TypeSort.MODIFIED_DATE_DESC);
                break;
            }
            default: {
                resultList = sortFiles(resultList, null);
                break;
            }
        }

        return resultList;
    }

    @Override
    public boolean existsInFolder(String folderPath, String[] fileName) {
        if (folderPath.equals(".")) {
            folderPath = "";
        }
        String folderId = getFileId(folderPath, "application/vnd.google-apps.folder", service);
        boolean exists = false;

        for (String targetFileName : fileName) {
            String name = "name='" + targetFileName + "'";
            String nameAndMimeiType = name + " and mimeType!='application/vnd.google-apps.folder'";
            ArrayList<File> files = (ArrayList<File>) getFilesByName("", nameAndMimeiType, service);

            for (File file : files) {
                if (file.getParents().contains(folderId) && file.getName().equals(targetFileName)) {
                    exists = true;
                    break;
                }
            }

            if (exists) {
                exists = false;
            } else {
                return false;
            }
        }

        return true;
    }

    @Override
    public String findFileFolder(String fileName) {
        String name = "name='" + fileName + "'";
        String nameAndMimeiType = name + " and mimeType!='application/vnd.google-apps.folder'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("", nameAndMimeiType, service);
        String folderIdDrive;
        StringBuilder absoPath = new StringBuilder();
        List<String> resultList = new ArrayList<>(); // pravim listu zbog bin foldera

        //ako je prazan files baci da ne postoji fajl
        if (files.isEmpty()) {
            throw new CustomException("File " + "'" + fileName + "'" + "  does not exists");
        }

        for (File file : files) {
            if (file.getName().equals(fileName) && file.getParents() != null) {
                folderIdDrive = file.getParents().get(0);
                try {
                    File folderDrive = service.files().get(folderIdDrive).setFields("id,name,parents,mimeType,size").execute();

                    while (!folderDrive.getName().equals(StorageArguments.name)) {
                        absoPath.insert(0, "/" + folderDrive.getName());
                        folderIdDrive = folderDrive.getParents().get(0);
                        folderDrive = service.files().get(folderIdDrive).setFields("id,name,parents,mimeType,size").execute();
                    }
                    absoPath.insert(0, StorageArguments.name);

                    if (!resultList.contains(absoPath.toString())) {
                        resultList.add(absoPath.toString());
                    }
                    absoPath = new StringBuilder();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        StringBuilder result = new StringBuilder();

        for (String path : resultList) {
            result.append(path).append("\n");
        }

        return result.toString();
    }

    @Override
    public List<String> searchModifiedFilesInFolder(String folderPath, String typeSort, String typeFilter, String fileExtension, String startDate, String endDate) {
        if (folderPath.equals(".")) {
            folderPath = "";
        }

        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
        Date startUsableDate, endUsableDate;
        try {
            startUsableDate = dateFormatter.parse(startDate);
            endUsableDate = dateFormatter.parse(endDate);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        String folderDriveId = getFileId(folderPath, "application/vnd.google-apps.folder", service);
        List<String> resultList = new ArrayList<>();

        String mimeiType = "mimeType!='application/vnd.google-apps.folder'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("", mimeiType, service);

        for (File file : files) {
            if (file.getParents() != null && file.getParents().contains(folderDriveId)) {
                resultList.add(file.getId());
            }
        }

        List<File> driveFiles = new ArrayList<>();

        for (String resultListItem : resultList) {
            try {
                driveFiles.add(service.files().get(resultListItem).setFields("name, id, createdTime, modifiedTime").execute());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        resultList = new ArrayList<>();

        for (File driveFile : driveFiles) {
            if ((driveFile.getCreatedTime()).toString().compareTo(new DateTime(startUsableDate).toString()) >= 0 &&
                    (driveFile.getCreatedTime()).toString().compareTo(new DateTime(endUsableDate).toString()) <= 0) {
                resultList.add(driveFile.getId());
            }
        }

        if (typeFilter != null) {
            switch (typeFilter) {
                case "FILE_EXTENSION": {
                    resultList = filterFilesByExt(resultList, TypeFilter.FILE_EXTENSION, fileExtension);
                    break;
                }
                case "MODIFIED_DATE": {
                    resultList = filterFilesByDate(resultList, TypeFilter.MODIFIED_DATE, startDate, endDate);
                    break;
                }
                case "CREATED_DATE": {
                    resultList = filterFilesByDate(resultList, TypeFilter.CREATED_DATE, startDate, endDate);
                    break;
                }
                default: {
                    break;
                }
            }
        }

        switch (typeSort) {
            case "ALPHABETICAL_ASC": {
                resultList = sortFiles(resultList, TypeSort.ALPHABETICAL_ASC);
                break;
            }
            case "ALPHABETICAL_DESC": {
                resultList = sortFiles(resultList, TypeSort.ALPHABETICAL_DESC);
                break;
            }
            case "CREATED_DATE_ASC": {
                resultList = sortFiles(resultList, TypeSort.CREATED_DATE_ASC);
                break;
            }
            case "CREATED_DATE_DESC": {
                resultList = sortFiles(resultList, TypeSort.CREATED_DATE_DESC);
                break;
            }
            case "MODIFIED_DATE_ASC": {
                resultList = sortFiles(resultList, TypeSort.MODIFIED_DATE_ASC);
                break;
            }
            case "MODIFIED_DATE_DESC": {
                resultList = sortFiles(resultList, TypeSort.MODIFIED_DATE_DESC);
                break;
            }
            default: {
                resultList = sortFiles(resultList, null);
                break;
            }
        }

        return resultList;
    }

    /*-------------------------------------------------------------------------------------------------------------*/
    private List<File> getFilesByName(String name, String nameAndMimeiType, Drive service) {
        List<File> files;

        try {
            String pageToken = null;
            FileList result;

            if (name.equals("")) {
                result = service.files().list()
                        .setQ(nameAndMimeiType)
                        .setSpaces("drive")
                        .setFields("files(id,name,parents,mimeType,size)")
                        .setPageToken(pageToken)
                        .execute();
            } else {
                result = service.files().list()
                        .setQ(name)
                        .setSpaces("drive")
                        .setFields("files(id,name,parents,mimeType,size)")
                        .setPageToken(pageToken)
                        .execute();
            }
            files = new ArrayList<>(result.getFiles());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return files;
    }

    private String getFileId(String path, String mimeiType, Drive service) {
        if (path.equals("")) {
            return StorageArguments.driveStorage_Id;
        }

        String[] folder = path.split("/");
        String parentId = StorageArguments.driveStorage_Id;
        StringBuilder currPath = Optional.ofNullable(StorageArguments.name).map(StringBuilder::new).orElse(null);
        boolean badPath = true;

        for (String s : folder) {
            String name = "name='" + s + "'";
            currPath = (currPath == null ? new StringBuilder("null") : currPath).append("/").append(s);
            ArrayList<File> files;

            if (!Objects.equals(mimeiType, "")) {
                String nameAndMimeiType = name + " and mimeType='" + mimeiType + "'";
                files = (ArrayList<File>) getFilesByName("", nameAndMimeiType, service);

            } else {
                files = (ArrayList<File>) getFilesByName(name, "", service); //ovo vraca i fajlve i foldere sa datim imenom
            }

            for (File file : files) {
                if (file.getParents().contains(parentId)) {
                    parentId = file.getId();
                    badPath = false;
                    break;
                }
            }
            if (badPath) {
                throw new CustomException("Action FAILED \t Check the path?\t " + currPath);
            }
            badPath = true;
        }

        return parentId;
    }

    private int getUsedSpaceInStorage(String folderPath) {
        String folderDriveId = getFileId(folderPath, "application/vnd.google-apps.folder", service);
        int usedSpaceStorage = 0;

        List<String> listIdSubFolders = new ArrayList<>();
        ArrayList<File> files = (ArrayList<File>) getFilesByName("", "", service);

        while (true) {
            for (File file : files) {
                if (file.getParents() != null && file.getParents().contains(folderDriveId)) {

                    if (file.getMimeType().equals("application/vnd.google-apps.folder")) {
                        listIdSubFolders.add(file.getId());
                    } else {
                        usedSpaceStorage = (int) (usedSpaceStorage + file.getSize());
                    }
                }
            }
            if (listIdSubFolders.isEmpty()) {
                break;
            }

            folderDriveId = listIdSubFolders.get(0); //uzimas sledeci subfolder da prodjes kroz njega
            listIdSubFolders.remove(folderDriveId); //brises njegov id iz liste
        }
        return usedSpaceStorage;
    }

    /*-------------------------------------------------------------------------------------------------------------*/

    public List<String> sortFiles(List<String> files, TypeSort typeSort) {
        List<File> driveFiles = new ArrayList<>();

        for (String file : files) {
            try {
                driveFiles.add(service.files().get(file).setFields("name, id, createdTime, modifiedTime").execute());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (typeSort != null) {
            switch (typeSort) {
                case ALPHABETICAL_ASC: {
                    driveFiles.sort(Comparator.comparing(File::getName));
                    break;
                }
                case ALPHABETICAL_DESC: {
                    driveFiles.sort((o1, o2) -> o2.getName().compareTo(o1.getName()));
                    break;
                }
                case CREATED_DATE_ASC: {
                    driveFiles.sort(Comparator.comparing(o -> o.getCreatedTime().toString()));
                    break;
                }
                case CREATED_DATE_DESC: {
                    driveFiles.sort((o1, o2) -> o2.getCreatedTime().toString().compareTo(o1.getCreatedTime().toString()));
                    break;
                }
                case MODIFIED_DATE_ASC: {
                    driveFiles.sort(Comparator.comparing(o -> o.getModifiedTime().toString()));
                    break;
                }
                case MODIFIED_DATE_DESC: {
                    driveFiles.sort((o1, o2) -> o2.getModifiedTime().toString().compareTo(o1.getModifiedTime().toString()));
                    break;
                }
                default: {
                    break;
                }
            }
        }

        files = new ArrayList<>();

        for (File driveFile : driveFiles) {
            files.add("FILE NAME: " + driveFile.getName() + "\n" +
                    "FILE CREATION TIME: " + driveFile.getCreatedTime() + "\n" +
                    "FILE MODIFICATION TIME: " + driveFile.getModifiedTime() + "\n");
        }

        return files;
    }

    public List<String> filterFilesByExt(List<String> files, TypeFilter typeFilter, String ext) {
        List<File> driveFiles = new ArrayList<>();

        for (String file : files) {
            try {
                driveFiles.add(service.files().get(file).setFields("name, id").execute());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        files = new ArrayList<>();

        if (typeFilter == TypeFilter.FILE_EXTENSION) {
            for (File driveFile : driveFiles) {
                if (driveFile.getName().toLowerCase().contains("." + ext.toLowerCase())) {
                    files.add(driveFile.getId());
                }
            }
        }

        return files;
    }

    public List<String> filterFilesByDate(List<String> files, TypeFilter typeFilter, String startDate, String endDate) {
        List<File> driveFiles = new ArrayList<>();

        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");
        Date startUsableDate, endUsableDate;
        try {
            startUsableDate = dateFormatter.parse(startDate);
            endUsableDate = dateFormatter.parse(endDate);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        for (String file : files) {
            try {
                driveFiles.add(service.files().get(file).setFields("name, id, createdTime, modifiedTime").execute());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        files = new ArrayList<>();

        switch (typeFilter) {
            case CREATED_DATE: {
                for (File driveFile : driveFiles) {
                    if ((driveFile.getCreatedTime()).toString().compareTo(new DateTime(startUsableDate).toString()) >= 0 &&
                            (driveFile.getCreatedTime()).toString().compareTo(new DateTime(endUsableDate).toString()) <= 0) {
                        files.add(driveFile.getId());
                    }
                }
                break;
            }
            case MODIFIED_DATE: {
                for (File driveFile : driveFiles) {
                    if ((driveFile.getModifiedTime()).toString().compareTo(new DateTime(startUsableDate).toString()) >= 0 &&
                            (driveFile.getModifiedTime()).toString().compareTo(new DateTime(endUsableDate).toString()) <= 0) {
                        files.add(driveFile.getId());
                    }
                }
                break;
            }
        }

        return files;
    }
}
