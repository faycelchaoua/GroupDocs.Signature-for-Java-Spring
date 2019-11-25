package com.groupdocs.ui.util;

import com.groupdocs.ui.config.ServerConfiguration;
import com.groupdocs.ui.exception.TotalGroupDocsException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.groupdocs.ui.exception.PasswordExceptions.INCORRECT_PASSWORD;
import static com.groupdocs.ui.exception.PasswordExceptions.PASSWORD_REQUIRED;
import static org.springframework.http.HttpHeaders.CONTENT_LENGTH;

public class Utils {
    private static final Logger logger = LoggerFactory.getLogger(Utils.class);

    public static final FileNameComparator FILE_NAME_COMPARATOR = new FileNameComparator();
    public static final FileTypeComparator FILE_TYPE_COMPARATOR = new FileTypeComparator();
    public static final FileDateComparator FILE_DATE_COMPARATOR = new FileDateComparator();

    /**
     * Set local port from request to config
     *
     * @param request
     * @param server
     */
    public static void setLocalPort(HttpServletRequest request, ServerConfiguration server) {
        if (server.getHttpPort() == null) {
            server.setHttpPort(request.getLocalPort());
        }
    }

    /**
     * Read stream and convert to string
     *
     * @param inputStream
     * @return
     * @throws IOException
     */
    public static String getStringFromStream(InputStream inputStream) throws IOException {
        byte[] bytes = IOUtils.toByteArray(inputStream);
        // encode ByteArray into String
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Parse extension of the file's name
     *
     * @param documentGuid path to file
     * @return extension of the file's name
     */
    public static String parseFileExtension(String documentGuid) {
        String extension = FilenameUtils.getExtension(documentGuid);
        return extension == null ? null : extension.toLowerCase();
    }

    /**
     * Fill header HTTP response with file data
     */
    public static void addFileDownloadHeaders(HttpServletResponse response, String fileName, Long fileLength) {
        HttpHeaders fileDownloadHeaders = createFileDownloadHeaders(fileName, fileLength, MediaType.APPLICATION_OCTET_STREAM);
        for (Map.Entry<String, List<String>> entry : fileDownloadHeaders.entrySet()) {
            for (String value : entry.getValue()) {
                response.addHeader(entry.getKey(), value);
            }
        }
    }

    /**
     * Set "Content-Length" header into response
     *
     * @param response http response
     * @param length   the length of file
     */
    public static void addFileDownloadLengthHeader(HttpServletResponse response, Long length) {
        if (length != null) {
            response.setHeader(CONTENT_LENGTH, Long.toString(length));
        }
    }

    /**
     * Upload the file
     *
     * @param documentStoragePath path for uploading the file
     * @param content             file data
     * @param url                 url of file
     * @param rewrite             flag of rewriting the file
     * @return path to uploaded file
     */
    public static String uploadFile(String documentStoragePath, MultipartFile content, String url, Boolean rewrite) {
        String filePath;
        try {
            String fileName;
            // save from file content
            if (StringUtils.isEmpty(url)) {
                fileName = content.getOriginalFilename();
                try (InputStream inputStream = content.getInputStream()) {
                    filePath = uploadFileInternal(inputStream, documentStoragePath, fileName, rewrite);
                } catch (Exception ex) {
                    logger.error("Exception occurred while uploading document", ex);
                    throw new TotalGroupDocsException(ex.getMessage(), ex);
                }
            } else { // save from url
                URL fileUrl = new URL(url);
                try (InputStream inputStream = fileUrl.openStream()) {
                    fileName = FilenameUtils.getName(fileUrl.getPath());
                    filePath = uploadFileInternal(inputStream, documentStoragePath, fileName, rewrite);
                } catch (Exception ex) {
                    logger.error("Exception occurred while uploading document", ex);
                    throw new TotalGroupDocsException(ex.getMessage(), ex);
                }
            }
        } catch (Exception ex) {
            logger.error("Exception occurred while uploading document", ex);
            throw new TotalGroupDocsException(ex.getMessage(), ex);
        }
        return filePath;
    }

    /**
     * Upload file from input stream
     *
     * @param uploadedInputStream input stream of file
     * @param documentStoragePath path to storage
     * @param fileName            name of file
     * @param rewrite             flag for rewriting
     * @return path to file
     * @throws IOException
     */
    public static String uploadFileInternal(InputStream uploadedInputStream, String documentStoragePath, String fileName, boolean rewrite) throws IOException {
        String filePath = String.format("%s%s%s", documentStoragePath, File.separator, fileName);
        File file = new File(filePath);
        // check rewrite mode
        if (rewrite) {
            // save file with rewrite if exists
            Files.copy(uploadedInputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return filePath;
        } else {
            if (file.exists()) {
                // get file with new name
                file = getFreeFileName(documentStoragePath, fileName);
            }
            // save file without rewriting
            Path path = file.toPath();
            Files.copy(uploadedInputStream, path);
            return path.toString();
        }
    }

    /**
     * Get headers for downloading files
     */
    private static HttpHeaders createFileDownloadHeaders(String fileName, Long fileLength, MediaType mediaType) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentDispositionFormData("attachment", fileName);
        httpHeaders.setContentType(mediaType);
        httpHeaders.set("Content-Description", "File Transfer");
        httpHeaders.set("Content-Transfer-Encoding", "binary");
        httpHeaders.setExpires(0);
        httpHeaders.setCacheControl("must-revalidate");
        httpHeaders.setPragma("public");
        if (fileLength != null) {
            httpHeaders.setContentLength(fileLength);
        }
        return httpHeaders;
    }

    /**
     * Get correct message for security exceptions
     *
     * @param password
     * @return
     */
    public static String getExceptionMessage(String password) {
        return StringUtils.isEmpty(password) ? PASSWORD_REQUIRED : INCORRECT_PASSWORD;
    }

    /**
     * Rename file if exist
     *
     * @param directory directory where files are located
     * @param fileName  file name
     * @return new file with new file name
     */
    public static File getFreeFileName(String directory, String fileName) {
        File file = null;
        try {
            File folder = new File(directory);
            File[] listOfFiles = folder.listFiles();
            for (int i = 0; i < listOfFiles.length; i++) {
                int number = i + 1;
                String newFileName = FilenameUtils.removeExtension(fileName) + "-Copy(" + number + ")." + FilenameUtils.getExtension(fileName);
                file = new File(directory + File.separator + newFileName);
                if (file.exists()) {
                    continue;
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

    /**
     * FileNameComparator
     * Compare and sort file names alphabetically
     *
     * @author Aspose Pty Ltd
     */
    static class FileNameComparator implements Comparator<File> {

        /**
         * Compare two file names
         *
         * @param file1
         * @param file2
         * @return int
         */
        @Override
        public int compare(File file1, File file2) {

            return String.CASE_INSENSITIVE_ORDER.compare(file1.getName(),
                    file2.getName());
        }
    }

    /**
     * FileTypeComparator
     * Compare and sort file types - folders first
     *
     * @author Aspose Pty Ltd
     */
    static class FileTypeComparator implements Comparator<File> {

        /**
         * Compare two file types
         *
         * @param file1
         * @param file2
         * @return
         */
        @Override
        public int compare(File file1, File file2) {

            if (file1.isDirectory() && file2.isFile()) {
                return -1;
            }
            if (file1.isDirectory() && file2.isDirectory()) {
                return 0;
            }
            if (file1.isFile() && file2.isFile()) {
                return 0;
            }
            return 1;
        }
    }

    private static class FileDateComparator implements Comparator<File> {
        @Override
        public int compare(File file1, File file2) {
            try {
                BasicFileAttributes attr1 = Files.readAttributes(file1.toPath(), BasicFileAttributes.class);
                BasicFileAttributes attr2 = Files.readAttributes(file2.toPath(), BasicFileAttributes.class);
                return attr1.creationTime().compareTo(attr2.creationTime());
            } catch (IOException e) {
                logger.error("Error comparing files by creation date");
            }
            return 0;
        }
    }

    /**
     * Create file in previewPath and name imageGuid
     * if the file is already exist, create new file with next number in name
     * examples, 001, 002, 003, etc
     *
     * @param previewPath path to file folder
     * @param imageGuid   path to file
     * @return created file
     */
    public static File getFileWithUniqueName(String previewPath, String imageGuid, String ext) {
        if (!StringUtils.isEmpty(imageGuid) && new File(imageGuid).exists()) {
            return new File(imageGuid);
        } else {
            File[] listOfFiles = new File(previewPath).listFiles();
            return createUniqueFile(previewPath, listOfFiles, ext);
        }
    }

    private static File createUniqueFile(String previewPath, File[] listOfFiles, String ext) {
        for (int i = 0; i <= listOfFiles.length; i++) {
            // set file name, for example 001
            String fileName = String.format("%03d", i + 1);
            File file = new File(String.format("%s%s%s.%s", previewPath, File.separator, fileName, ext));
            // check if file with such name already exists
            if (file.exists()) {
                continue;
            } else {
                return file;
            }
        }
        return new File(String.format("%s%s001.png", previewPath, File.separator));
    }

    /**
     * Generate empty image for future signing with signature, such approach required to get signature as image
     *
     * @param width  image width
     * @param height image height
     * @return
     */
    public static BufferedImage getBufferedImage(int width, int height) {
        BufferedImage bufImage = null;
        try {
            bufImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            // Create a graphics contents on the buffered image
            Graphics2D g2d = bufImage.createGraphics();
            // Draw graphics
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, width, height);
            // Graphics context no longer needed so dispose it
            g2d.dispose();
            return bufImage;
        } catch (Exception ex) {
            throw new TotalGroupDocsException(ex.getMessage(), ex);
        } finally {
            if (bufImage != null) {
                bufImage.flush();
            }
        }
    }
}
