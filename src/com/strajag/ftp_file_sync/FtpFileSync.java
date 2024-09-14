package com.strajag.ftp_file_sync;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

class FtpFileSync
{

    final File settingsFile = new File("./data/settings.txt");
    final File lockFile = new File("./data/lock");

    private String localID;
    private String remoteID;

    private String serverIP;
    private String userName;
    private String password;

    private File backupFolder;
    private File remoteFolder;
    private List<File> folderList;

    FtpFileSync() throws Exception
    {
        updateSettings();
    }

    private void updateSettings() throws Exception
    {
        List<String> settingsLines = Files.readAllLines(settingsFile.toPath());
        localID = settingsLines.get(0).split("=")[1];
        remoteID = settingsLines.get(1).split("=")[1];
        serverIP = settingsLines.get(2).split("=")[1];
        userName = settingsLines.get(3).split("=")[1];
        password = settingsLines.get(4).split("=")[1];
        backupFolder = new File(settingsLines.get(5).split("=")[1]);
        remoteFolder = new File(settingsLines.get(6).split("=")[1]);

        folderList = new ArrayList<>();
        for(int i = 7; i < settingsLines.size(); i++)
            folderList.add(new File(settingsLines.get(i).split("=")[1]));
    }

    void upload() throws Exception
    {
        updateSettings();
        zipFolders(folderList, backupFolder, localID);
        File zippedFile = new File(backupFolder + "/" + localID + ".zip");
        uploadFileFTP(zippedFile, remoteFolder);

        if(!zippedFile.delete())
            throw new Exception("failed to delete uploaded zipped file - " + zippedFile);
    }

    void download() throws Exception
    {
        updateSettings();
        downloadFileFTP(new File(remoteFolder + "/" + remoteID + ".zip"), backupFolder, true);
        File zippedFile = new File(backupFolder + "/" + remoteID + ".zip");
        unZip(zippedFile, backupFolder);

        if(!zippedFile.delete())
            throw new Exception("failed to delete downloaded zipped file - " + zippedFile);

        File backupFile = new File(backupFolder + "/backup.zip");
        if(backupFile.exists() && !new File(backupFolder + "/backup.zip").delete())
            throw new Exception("failed to delete backup file - " + backupFolder + "/backup.zip");

        zipFolders(folderList, backupFolder, "backup");

        for(File folder : folderList)
            deleteFolder(folder);

        File unZippedFolder = new File(backupFolder + "/" + remoteID);
        List<File> downloadedFolders = new ArrayList<>(List.of(Objects.requireNonNull(unZippedFolder.listFiles())));

        loop:
        for(File folder : folderList)
        {
            for(File downloadedFolder : downloadedFolders)
            {
                if(downloadedFolder.getName().equals(folder.getName()))
                {
                    moveFolder(downloadedFolder, folder);
                    downloadedFolders.remove(downloadedFolder);
                    deleteFolder(downloadedFolder);
                    continue loop;
                }
            }
            throw new Exception("failed to find same downloaded folder as sync folder - " + folder);
        }
        if(!unZippedFolder.delete())
            throw new Exception("failed to delete downloaded unzipped folder folder - " + unZippedFolder);
    }

    private void moveFolder(File folder, File destinationFolderWithFolderName) throws Exception
    {
        if(folder.isDirectory())
        {
            if(!destinationFolderWithFolderName.mkdirs())
                throw new Exception("failed to create folder while moving - " + destinationFolderWithFolderName);

            for(File file : Objects.requireNonNull(folder.listFiles()))
                moveFolder(file, new File(destinationFolderWithFolderName + "/" + file.getName()));
        }
        else
            Files.move(folder.toPath(), destinationFolderWithFolderName.toPath());
    }

    private void deleteFolder(File folder) throws Exception
    {
        if(folder.exists())
        {
            File[] files = folder.listFiles();

            if(null != files)
            {
                for(File file : files)
                {
                    if(file.isDirectory())
                        deleteFolder(file);
                    else
                        if(!file.delete())
                            throw new Exception("failed to delete file - " + file);
                }
            }
        }

        if(!folder.delete())
            throw new Exception("failed to delete folder - " + folder);
    }

    private void uploadFileFTP(File file, File destinationFolder) throws Exception
    {
        FTPClient ftpClient = new FTPClient();
        Exception exception = null;

        try
        {
            ftpClient.connect(serverIP);
            ftpClient.enterLocalPassiveMode();
            ftpClient.login(userName, password);
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            File destinationFile = new File(destinationFolder + "/" + file.getName());
            if(ftpClient.mlistFile(destinationFile.getPath().replace("\\", "/")) != null)
                ftpClient.deleteFile(destinationFile.getPath().replace("\\", "/"));

            InputStream inputStream = new FileInputStream(file);
            boolean success = ftpClient.storeFile(destinationFile.getPath().replace("\\", "/"), inputStream);
            inputStream.close();
            if(!success)
                throw new Exception("failed to upload file to ftp - " + file);

        }
        catch(Exception exception_2)
        {
            exception = exception_2;
        }
        finally
        {
            if(ftpClient.isConnected())
            {
                ftpClient.logout();
                ftpClient.disconnect();
            }
        }

        if(exception != null)
            throw exception;
    }

    private void downloadFileFTP(File file, File destinationFolder, boolean removeFilesFromFTP) throws Exception
    {
        FTPClient ftpClient = new FTPClient();
        Exception exception = null;

        try
        {
            ftpClient.connect(serverIP);
            ftpClient.enterLocalPassiveMode();
            ftpClient.login(userName, password);
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);

            if(ftpClient.mlistFile(file.getPath().replace("\\", "/")) == null)
                throw new Exception("ftp file does not exist - " + file);

            OutputStream outputStream = new BufferedOutputStream(new FileOutputStream(destinationFolder + "/" + file.getName()));
            boolean success = ftpClient.retrieveFile(file.getPath().replace("\\", "/"), outputStream);
            outputStream.close();

            if(!success)
                throw new Exception("failed to download file from ftp - " + file);

            if(removeFilesFromFTP)
                ftpClient.deleteFile(file.getPath().replace("\\", "/"));
        }
        catch(Exception exception_2)
        {
            exception = exception_2;
        }
        finally
        {
            if(ftpClient.isConnected())
            {
                ftpClient.logout();
                ftpClient.disconnect();
            }
        }
        if(exception != null)
            throw exception;
    }

    private void unZip(File file, File destinationFolder) throws Exception
    {
        destinationFolder = new File(destinationFolder + "/" + file.getName().replaceFirst("[.][^.]+$", ""));
        byte[] buffer = new byte[1024];
        ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(file));
        ZipEntry zipEntry = zipInputStream.getNextEntry();

        while(zipEntry != null)
        {
            File newFile = new File(destinationFolder, zipEntry.getName());
            String destFolderPath = destinationFolder.getCanonicalPath();
            String destFilePath = newFile.getCanonicalPath();

            if(!destFilePath.startsWith(destFolderPath + File.separator))
                throw new IOException("zip is outside of the target dir: " + zipEntry.getName());

            if(zipEntry.isDirectory())
            {
                if(!newFile.isDirectory() && !newFile.mkdirs())
                    throw new IOException("zip failed to create directory " + newFile);
            }
            else
            {
                //fix for windows-created archives
                File parent = newFile.getParentFile();
                if(!parent.isDirectory() && !parent.mkdirs())
                    throw new IOException("zip failed to create directory " + parent);

                //write file content
                FileOutputStream fileOutputStream = new FileOutputStream(newFile);
                int len;

                while((len = zipInputStream.read(buffer)) > 0)
                    fileOutputStream.write(buffer, 0, len);

                fileOutputStream.close();
            }
            zipEntry = zipInputStream.getNextEntry();
        }
        zipInputStream.closeEntry();
        zipInputStream.close();
    }

    private void zipFolders(List<File> folderList, File destinationFolder, String fileName) throws Exception
    {
        ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(destinationFolder + "/" + fileName + ".zip"));

        for(File file : folderList)
            zipFolder(file, file.getName(), zipOutputStream);

        zipOutputStream.flush();
        zipOutputStream.close();
    }

    private void zipFolder(File folder, String parentFolder, ZipOutputStream zipOutputStream) throws Exception
    {
        for(File file : Objects.requireNonNull(folder.listFiles()))
        {
            if(file.isDirectory())
            {
                zipFolder(file, parentFolder + "/" + file.getName(), zipOutputStream);
                continue;
            }

            zipOutputStream.putNextEntry(new ZipEntry(parentFolder + "/" + file.getName()));
            BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(file));
            byte[] bytesIn = new byte[4096];
            int read;

            while((read = bufferedInputStream.read(bytesIn)) != -1)
                zipOutputStream.write(bytesIn, 0, read);

            zipOutputStream.closeEntry();
            bufferedInputStream.close();
        }
    }
}
