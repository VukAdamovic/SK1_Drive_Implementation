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

        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /*--------------------------------------------------------------------------------------*/

    @Override
    public boolean setPath(String apsolutePath) {
        java.io.File storage = new java.io.File(apsolutePath);
        java.io.File configFile;
        boolean operation = false;


        if(!storage.exists()){
            return  false;
        }
        else{
            for(java.io.File file : Objects.requireNonNull(storage.listFiles())){
                if(file.getName().contains("_CONFIGURATION.txt")){
                    try {
                        List<String> configAtributes = new ArrayList<>();
                        configFile = file;
                        Scanner myReader = new Scanner(configFile);

                        while(myReader.hasNextLine()){
                            String line = myReader.nextLine();
                            String[] value = line.split(":");
                            configAtributes.add(value[1]);
                        }

                        StorageArguments.name = configAtributes.get(0);
                        StorageArguments.path = apsolutePath;
                        StorageArguments.totalSpace = Integer.parseInt(configAtributes.get(1));
                        StorageArguments.restrictedExtensions = Collections.singletonList(configAtributes.get(2));
                        StorageArguments.maxFilesInStorage = Integer.parseInt(configAtributes.get(3));
                        StorageArguments.usedSpace = getUsedSpaceInStorage(apsolutePath);
                        StorageArguments.fileNumberInStorage = searchFilesInFolders("",TypeSort.ALPHABETICAL_ASC,TypeFilter.FILE_EXTENSION).size();
                        operation=true;
                    } catch (FileNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return  operation;
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
        java.io.File localFile = new java.io.File("D:/googleDriveFiles", storageName +"_Configuration.txt");
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
        readMeMetaData.setName(storageName +"_Configuration.txt");
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
        java.io.File localFile = new java.io.File("D:/googleDriveFiles", StorageArguments.name+"_CONFIGURATION.txt");
        try {
            FileWriter fileWriter = new FileWriter(localFile);
            fileWriter.write("Storage name: " + StorageArguments.path + "\n");
            fileWriter.write("Storage size in bytes: " + StorageArguments.totalSpace + "\n");
            fileWriter.write("Storage restricted extensions: " + StorageArguments.restrictedExtensions + "\n");
            fileWriter.write("Storage max file size number: " + StorageArguments.maxFilesInStorage);
            fileWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Kreiranje config file na drajvu
        File readMeMetaData = new File();
        readMeMetaData.setName(StorageArguments.name +"_CONFIGURATION.txt");
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

        if(folderPath.equals(".")){
            folderPath="";
        }

        //Provera da li postoji folder za datim imenom na zadatoj putanji
        String name = "name='"+folderName+"'";
        String nameAndMimeiType= name +" and mimeType='" + "application/vnd.google-apps.folder" +"'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("",nameAndMimeiType,service);

        String parentID = getFileId(folderPath,"",service);// pitaj
        for (int i = 0 ; i < files.size(); i++){

            if(files.get(i).getParents().contains(parentID) && files.get(i).getName().equals(folderName)){
                String apsoPath = StorageArguments.name + "/" + folderPath + "/" + folderName;
                throw new CustomException("Action FAILED \t Folder: " + apsoPath + " already exists");
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

        if(filePath.equals(".")){
            filePath="";
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
        String name = "name='"+fileName+"'";
        String nameAndMimeiType= name +" and mimeType!='" + "application/vnd.google-apps.folder" +"'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("",nameAndMimeiType,service);

        String parentID = getFileId(filePath,"application/vnd.google-apps.folder",service);//
        for (int i = 0 ; i < files.size(); i++){
            if(files.get(i).getParents().contains(parentID) && files.get(i).getName().equals(fileName)){
                String absolutePath = StorageArguments.name + "/" + filePath + "/" + fileName;
                throw new CustomException("Action FAILED \t File: " + absolutePath + " already exists");
            }
        }

        //Kreiranje
        File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setParents(Collections.singletonList(parentID));
        java.io.File localFile = new java.io.File("D:/googleDriveFiles/testSizeStorage.txt"); //zbog testiranja
        FileContent fileContent = new FileContent("txt/txt",localFile);

        try {
            File file = service.files().create(fileMetadata,fileContent).setFields("id,name,parents,mimeType,size").execute();

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

        //Proverava da li je dobra putanja, ako jeste uzima id poslednjeg parenta
        String oldFolderParentId = getFileId(oldFilePath,"",service);

        //Provera da li ono sto se premesta  je fajl
        String[] splitOldPath = oldFilePath.split("/");
        String targetName = splitOldPath[splitOldPath.length-1];
        String name = "name='"+targetName+"'";
        String nameAndMimeiType= name +" and mimeType!='" + "application/vnd.google-apps.folder" +"'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("",nameAndMimeiType,service);

        if(files.isEmpty()){
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
        String newFolderParentId = getFileId(newFilePath,"application/vnd.google-apps.folder",service);


        //Ako vec postoji fajl sa zadatim imenom na putanji na kojoj hocemo da premestimo
        for (int i = 0 ; i < files.size(); i++){

            if(files.get(i).getParents().contains(newFolderParentId) && files.get(i).getName().equals(targetName)){
                String apsoPath = StorageArguments.name + "/" + newFilePath + "/" + targetName;
                throw new CustomException("Action FAILED \t File : " + apsoPath + " already exists");
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
        String [] folders = foPath.split("/");
        String parentPath =folders[0];

        for(int i = 1 ; i < folders.length-1; i++){
            parentPath = parentPath + "/" + folders[i];
        }

        String fileObjectId =  getFileId(foPath,"",service); // id file object koji zelimo da preimenujemo
        String parentFileObjectId = getFileId(parentPath,"",service); // id parenta

        //Proverava da li postoji da li postoji fajl ili folder koji se vec tako zove
        String name = "name='"+foNewName+"'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName(name,"",service);

        for (int i = 0 ; i < files.size(); i++){
            if(files.get(i).getParents().contains(parentFileObjectId) && files.get(i).getName().equals(foNewName)){
                String apsoPath = StorageArguments.name + "/" + parentPath + "/" + foNewName;
                throw new CustomException("Action FAILED \t File : " + apsoPath + " already exists");
            }
        }

        try {
            File foMetaData = service.files().get(fileObjectId).setFields("name").execute();
            foMetaData.setName(foNewName);

            service.files().update(fileObjectId,foMetaData).
                    setFields("name").
                    execute();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return  true;
    }

    @Override
    public boolean deleteFileObject(String foPath) {
        String[] folders = foPath.split("/");
        String fileObjectId;

        if(folders[folders.length - 1].contains(".")){
            fileObjectId = getFileId(foPath,"",service);
        }else{
            fileObjectId = getFileId(foPath,"application/vnd.google-apps.folder",service);
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
        String driveFolderId = getFileId(importStoragePath,"",service);
        try {
            File driveFolder = service.files().get(driveFolderId).execute(); // folder u koji zelis da uploadas stvari

            if(!driveFolder.getMimeType().equals("application/vnd.google-apps.folder")){ // proveravas da li je tipa folder
                String apsoluthPath = StorageArguments.name + "/" + importStoragePath;
                throw new CustomException("Action FAILED \t"+ apsoluthPath  + " is not a directory");
            }

            for(String importLocalPath : importLocalPaths){
                java.io.File localFile = new java.io.File(importLocalPath);
                FileContent fileContent = new FileContent("*/*",localFile);

                if(localFile.exists()){
                    if(localFile.isDirectory()){
                        throw new CustomException("Action FAILED \t" + importLocalPath + " \t Directory can not be upload on drive");
                    }
                    else if(localFile.isFile()){
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

                            File file = service.files().create(importMetaData,fileContent).setFields("id,name,parents,mimeType,size").execute();

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
                }
                else{
                    throw new CustomException("Action FAILED \t"+ localFile.getAbsolutePath() + "  does not exists");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public boolean exportFileObject(String exportStoragePath, String exportLocalPath) {
        String fileId = getFileId(exportStoragePath,"",service);
        java.io.File localFile = new java.io.File(exportLocalPath);

        if(!localFile.exists()){
            throw new CustomException(localFile.getAbsolutePath() + " does not exists");
        }
        if (!localFile.isDirectory()) {
            throw new CustomException(localFile.getAbsolutePath() + "is not directory");
        }

        try {
            File fileOnDrive = service.files().get(fileId).setFields("name,mimeType").execute();

            if(fileOnDrive.getMimeType().equals("application/vnd.google-apps.folder")){
                String apsoluthPath = StorageArguments.name + "/" + exportStoragePath;
                throw new CustomException("Action FAILED \t"+ apsoluthPath  + "\t Directory can not be download");
            }

            java.io.File downloadedFile = new java.io.File(exportLocalPath,fileOnDrive.getName());
            OutputStream outputStream = new ByteArrayOutputStream();
            service.files().get(fileId).executeMediaAndDownloadTo(outputStream);
            FileWriter fileWriter = new FileWriter(downloadedFile);
            fileWriter.write(String.valueOf(outputStream));
            fileWriter.close();
            outputStream.close();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return  true;
    }

    @Override
    public List<String> searchFilesInFolder(String folderPath, TypeSort typeSort, TypeFilter typeFilter) {
        String folderDriveId = getFileId(folderPath,"application/vnd.google-apps.folder",service);
        List<String> resultList = new ArrayList<>(); //lista fajlova iz tog foldera

        String mimeiType= "mimeType!='application/vnd.google-apps.folder'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("",mimeiType,service);

        for(int i = 0; i < files.size(); i++){
            if(files.get(i).getParents() != null && files.get(i).getParents().contains(folderDriveId)){
                resultList.add(files.get(i).getId());//
            }
        }

        switch (typeFilter) {
            case FILE_EXTENSION -> {
                String ext = "html";
                resultList = filterFilesByExt(resultList, typeFilter, ext);
            }
            case MODIFIED_DATE, CREATED_DATE -> {
                Date d1 = new Date(2022 - 1900, Calendar.OCTOBER, 30);
                Date d2 = new Date(2022 - 1900, Calendar.NOVEMBER, 1);
                resultList = filterFilesByDate(resultList, typeFilter, d1, d2);
            }
        }

        if (typeSort != null) {
            resultList = sortFiles(resultList, typeSort);
        }

        return resultList;
    }

    @Override
    public List<String> searchFilesInFolders(String folderPath, TypeSort typeSort, TypeFilter typeFilter) {
        String folderDriveId = getFileId(folderPath,"application/vnd.google-apps.folder",service);
        List<String> resultList = new ArrayList<>(); //konacna lista svih fajlova


        List<String> listIdSubFolders = new ArrayList<>();
        ArrayList<File> files = (ArrayList<File>) getFilesByName("","",service);

        while (true) {
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i).getParents() != null && files.get(i).getParents().contains(folderDriveId)) {

                    if (files.get(i).getMimeType().equals("application/vnd.google-apps.folder")) {
                        listIdSubFolders.add(files.get(i).getId());
                    }
                    else {
                        resultList.add(files.get(i).getId());//
                    }
                }
            }
            if(listIdSubFolders.isEmpty()) {
                break;
            }

            folderDriveId = listIdSubFolders.get(0); //uzimas sledeci subfolder da prodjes kroz njega
            listIdSubFolders.remove(folderDriveId); //brises njegov id iz liste
        }

        switch (typeFilter) {
            case FILE_EXTENSION -> {
                String ext = "html";
                resultList = filterFilesByExt(resultList, typeFilter, ext);
            }
            case MODIFIED_DATE, CREATED_DATE -> {
                Date d1 = new Date(2022 - 1900, Calendar.OCTOBER, 30);
                Date d2 = new Date(2022 - 1900, Calendar.NOVEMBER, 1);
                resultList = filterFilesByDate(resultList, typeFilter, d1, d2);
            }
        }

        if (typeSort != null) {
            resultList = sortFiles(resultList, typeSort);
        }

        return resultList;
    }

    @Override
    public List<String> searchFilesWithExtensionInFolder(String fileExtension, String folderPath, TypeSort typeSort, TypeFilter typeFilter) {
        String folderDriveId = getFileId(folderPath,"application/vnd.google-apps.folder",service);
        List<String> resultList = new ArrayList<>(); //lista fajlova iz tog foldera

        String mimeiType= "mimeType!='application/vnd.google-apps.folder'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("",mimeiType,service);

        for(int i = 0; i < files.size(); i++){
            if(files.get(i).getParents() != null && files.get(i).getParents().contains(folderDriveId) && files.get(i).getName().contains("."+ fileExtension)){
                resultList.add(files.get(i).getId());//
            }
        }

        switch (typeFilter) {
            case FILE_EXTENSION -> {
                String ext = "html";
                resultList = filterFilesByExt(resultList, typeFilter, ext);
            }
            case MODIFIED_DATE, CREATED_DATE -> {
                Date d1 = new Date(2022 - 1900, Calendar.OCTOBER, 30);
                Date d2 = new Date(2022 - 1900, Calendar.NOVEMBER, 1);
                resultList = filterFilesByDate(resultList, typeFilter, d1, d2);
            }
        }

        if (typeSort != null) {
            resultList = sortFiles(resultList, typeSort);
        }

        return resultList;
    }

    @Override
    public List<String> searchFilesWithSubstringInFolder(String fileSubstring, String folderPath, TypeSort typeSort, TypeFilter typeFilter) {
        String folderDriveId = getFileId(folderPath,"application/vnd.google-apps.folder",service);
        List<String> resultList = new ArrayList<>(); //lista fajlova iz tog foldera

        String mimeiType= "mimeType!='application/vnd.google-apps.folder'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("",mimeiType,service);

        for(int i = 0; i < files.size(); i++){
            if(files.get(i).getParents() != null && files.get(i).getParents().contains(folderDriveId)
                    && files.get(i).getName().toLowerCase().contains(fileSubstring.toLowerCase())){
                resultList.add(files.get(i).getId());//
            }
        }

        switch (typeFilter) {
            case FILE_EXTENSION -> {
                String ext = "html";
                resultList = filterFilesByExt(resultList, typeFilter, ext);
            }
            case MODIFIED_DATE, CREATED_DATE -> {
                Date d1 = new Date(2022 - 1900, Calendar.OCTOBER, 30);
                Date d2 = new Date(2022 - 1900, Calendar.NOVEMBER, 1);
                resultList = filterFilesByDate(resultList, typeFilter, d1, d2);
            }
        }

        if (typeSort != null) {
            resultList = sortFiles(resultList, typeSort);
        }


        return resultList;
    }

    @Override
    public boolean existsInFolder(String[] fileName, String folderPath) {
        String folderId = getFileId(folderPath,"application/vnd.google-apps.folder",service);
        boolean exists = false;

        for (String targetFileName : fileName){
            String name ="name='" + targetFileName + "'";
            String nameAndMimeiType= name +" and mimeType!='application/vnd.google-apps.folder'";
            ArrayList<File> files = (ArrayList<File>) getFilesByName("",nameAndMimeiType,service);

            for(int i = 0; i < files.size(); i++){
                if(files.get(i).getParents().contains(folderId) && files.get(i).getName().equals(targetFileName)){
                    exists = true;
                }
            }

            if(exists){
                exists = false;
            }else{
                throw new CustomException("File:" + targetFileName + "  does not exists in folder:"
                        + StorageArguments.name + "/" + folderPath);
            }
        }
        return true;
    }

    @Override
    public String findFileFolder(String fileName) {
        String name ="name='" + fileName + "'";
        String nameAndMimeiType= name +" and mimeType!='application/vnd.google-apps.folder'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("",nameAndMimeiType,service);
        String folderIdDrive = "";
        String apolutepath = "";
        List<String> resultList = new ArrayList<>(); // pravim listu zbog bin foldera

        //ako je prazan files baci da ne postoji fajl
        if(files.isEmpty()){
            throw new CustomException("File " + "'" + fileName + "'" + "  does not exists");
        }

        for(int i = 0; i < files.size(); i++){
            if(files.get(i).getName().equals(fileName) && !(files.get(i).getParents().equals(null))){
                folderIdDrive = files.get(i).getParents().get(0);
                try {
                    File folderDrive = service.files().get(folderIdDrive).setFields("id,name,parents,mimeType,size").execute();

                    while(!folderDrive.getName().equals(StorageArguments.name)){
                        apolutepath = "/" + folderDrive.getName() + apolutepath;
                        folderIdDrive = folderDrive.getParents().get(0);
                        folderDrive = service.files().get(folderIdDrive).setFields("id,name,parents,mimeType,size").execute();
                    }
                    apolutepath = StorageArguments.name + apolutepath;

                    if(!resultList.contains(apolutepath)){
                        resultList.add(apolutepath);
                    }
                    apolutepath = "";
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        String result ="Folders:\n";  //ispis svih apsolutnih putanja gde se sve nalazi file sa datim imenom

        for(String path : resultList){
            result = result + path + "\n";
        }

        return result;
    }

    @Override
    public List<String> searchModifiedFilesInFolder(Date beginDate, Date endDate, String folderPath, TypeSort typeSort, TypeFilter typeFilter) {
        String folderDriveId = getFileId(folderPath, "application/vnd.google-apps.folder", service);
        List<String> resultList = new ArrayList<>();

        String mimeiType = "mimeType!='application/vnd.google-apps.folder'";
        ArrayList<File> files = (ArrayList<File>) getFilesByName("", mimeiType, service);

        for (File file : files) {
            if (file.getParents() != null && file.getParents().contains(folderDriveId)) {
                if ((file.getModifiedTime()).toString().compareTo(new DateTime(beginDate).toString()) >= 0 &&
                        (file.getModifiedTime()).toString().compareTo(new DateTime(endDate).toString()) <= 0) {
                    resultList.add(file.getId());
                }
            }
        }

        switch (typeFilter) {
            case FILE_EXTENSION -> {
                String ext = "html";
                resultList = filterFilesByExt(resultList, typeFilter, ext);
            }
            case MODIFIED_DATE, CREATED_DATE -> {
                Date d1 = new Date(2022 - 1900, Calendar.OCTOBER, 30);
                Date d2 = new Date(2022 - 1900, Calendar.NOVEMBER, 1);
                resultList = filterFilesByDate(resultList, typeFilter, d1, d2);
            }
        }

        if (typeSort != null) {
            resultList = sortFiles(resultList, typeSort);
        }

        return resultList;
    }

    /*-------------------------------------------------------------------------------------------------------------*/
    private List<File> getFilesByName(String name,String nameAndMimeiType,Drive service){
        List<File> files = new ArrayList<>();

        try{
            String pageToken = null;
            FileList result;

            if(name.equals("")){
                result = service.files().list()
                        .setQ(nameAndMimeiType)
                        .setSpaces("drive")
                        .setFields("files(id,name,parents,mimeType,size)")
                        .setPageToken(pageToken)
                        .execute();
            }else{
                result = service.files().list()
                        .setQ(name)
                        .setSpaces("drive")
                        .setFields("files(id,name,parents,mimeType,size)")
                        .setPageToken(pageToken)
                        .execute();
            }
            files.addAll(result.getFiles());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return files;
    }

    private String getFileId(String path, String mimeiType, Drive service){
        if(path.equals("")){ //""
            return StorageArguments.driveStorage_Id;
        }

        String[] folder = path.split("/");
        String parentId = StorageArguments.driveStorage_Id;
        String currPath = StorageArguments.name;
        boolean badPath = true;

        for( int i = 0; i < folder.length; i++){
            String name = "name='"+folder[i]+"'";
            currPath = currPath +"/" +folder[i];
            ArrayList<File> files;

            if(mimeiType!=""){
                String nameAndMimeiType= name +" and mimeType='" + mimeiType +"'";
                files = (ArrayList<File>) getFilesByName("",nameAndMimeiType,service);

            }
            else{
                files = (ArrayList<File>) getFilesByName(name,"",service); //ovo vraca i fajlve i foldere sa datim imenom
            }

            for(int j = 0; j < files.size(); j++)
            {
                if(files.get(j).getParents().contains(parentId)){
                    parentId=files.get(j).getId();
                    badPath = false;
                    break;
                }
            }
            if(badPath){
                throw new CustomException("Action FAILED \t Check the path?\t " + currPath);
            }
            badPath = true;
        }

        return parentId;
    }

    private int getUsedSpaceInStorage(String folderPath) {

        String folderDriveId = getFileId(folderPath,"application/vnd.google-apps.folder",service);
        int usedSpaceStorage = 0;

        List<String> listIdSubFolders = new ArrayList<>();
        ArrayList<File> files = (ArrayList<File>) getFilesByName("","",service);

        while (true) {
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i).getParents() != null && files.get(i).getParents().contains(folderDriveId)) {

                    if (files.get(i).getMimeType().equals("application/vnd.google-apps.folder")) {
                        listIdSubFolders.add(files.get(i).getId());
                    }
                    else {
                        usedSpaceStorage = (int) (usedSpaceStorage + files.get(i).getSize());
                    }
                }
            }
            if(listIdSubFolders.isEmpty()) {
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

        switch (typeSort) {
            case ALPHABETICAL_ASC -> driveFiles.sort(Comparator.comparing(File::getName));
            case ALPHABETICAL_DESC -> driveFiles.sort((o1, o2) -> o2.getName().compareTo(o1.getName()));
            case CREATED_DATE_ASC -> driveFiles.sort(Comparator.comparing(o -> o.getCreatedTime().toString()));
            case CREATED_DATE_DESC -> driveFiles.sort((o1, o2) -> o2.getCreatedTime().toString().compareTo(o1.getCreatedTime().toString()));
            case MODIFIED_DATE_ASC -> driveFiles.sort(Comparator.comparing(o -> o.getModifiedTime().toString()));
            case MODIFIED_DATE_DESC -> driveFiles.sort((o1, o2) -> o2.getModifiedTime().toString().compareTo(o1.getModifiedTime().toString()));
        }

        files = new ArrayList<>();

        for (File driveFile : driveFiles) {
            files.add(driveFile.getId());
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

    public List<String> filterFilesByDate(List<String> files, TypeFilter typeFilter, Date beginDate, Date endDate) {
        List<File> driveFiles = new ArrayList<>();

        for (String file : files) {
            try {
                driveFiles.add(service.files().get(file).setFields("name, id, createdTime, modifiedTime").execute());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        files = new ArrayList<>();

        switch (typeFilter) {
            case CREATED_DATE -> {
                for (File driveFile : driveFiles) {
                    if ((driveFile.getModifiedTime()).toString().compareTo(new DateTime(beginDate).toString()) >= 0 &&
                            (driveFile.getModifiedTime()).toString().compareTo(new DateTime(endDate).toString()) <= 0) {
                        files.add(driveFile.getId());
                    }
                }
            }
            case MODIFIED_DATE -> {
                for (File driveFile : driveFiles) {
                    if ((driveFile.getCreatedTime()).toString().compareTo(new DateTime(beginDate).toString()) >= 0 &&
                            (driveFile.getCreatedTime()).toString().compareTo(new DateTime(endDate).toString()) <= 0) {
                        files.add(driveFile.getId());
                    }
                }
            }
        }

        return files;
    }

}
